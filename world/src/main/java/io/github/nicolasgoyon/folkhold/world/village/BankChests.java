package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The bank of a faction village. The bank chests show the stocks of the village and a part of the treasury of its
 * faction ({@link WorldConfig#BANK_TREASURY_SHARE}, shared between the active villages of the faction).
 *
 * <p>A pillage is one of these actions:
 * <ul>
 *   <li>a player opens a bank chest and confirms on the confirmation screen;</li>
 *   <li>a player breaks a bank chest;</li>
 *   <li>an explosion that a player caused breaks a bank chest (for example TNT that the player lit).</li>
 * </ul>
 * After a pillage, the village value and the faction value of the player go to {@link WorldConfig#BANK_PILLAGE_VALUE}
 * (a lower value does not change), the village goes to the alert state, and the settlement loses experience.
 * The bank chests and the bank walls resist explosions as obsidian, so a creeper or TNT does not break them. If an
 * explosion can break a bank chest (for example an explosive of another mod), a chest breaks only when a player
 * caused the explosion. A creeper is not a cause. Creative and spectator players open the chests with no
 * confirmation and no pillage.
 *
 * <p>Ruins: when the village becomes ruins, its stock and its share of the treasury go into its bank chests as real
 * items ({@link #storeInRuins}). With no active village, a bank chest is a normal chest: it keeps its items, and it
 * drops them when it breaks. When the village becomes active again, the items go back into the stock and the
 * treasury ({@link #recoverFromRuins}).
 */
public final class BankChests {
    /** A player farther than this distance from a bank chest cannot confirm the pillage. */
    private static final double MAX_CONFIRM_DISTANCE = 8.0;
    /** The bank chests of a village are at this vertical distance from the heart, or nearer. */
    private static final int VERTICAL_RANGE = 32;

    private BankChests() {
    }

    /** A bank chest with its village. */
    private record Bank(BankChestBlockEntity chest, @Nullable VillageHeartBlockEntity heart,
            @Nullable Settlement settlement) {
    }

    private static Optional<Bank> bank(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BankChestBlockEntity chest)) {
            return Optional.empty();
        }
        VillageHeartBlockEntity heart = heartOf(level, pos);
        Settlement settlement = heart == null ? null
                : Settlements.of(level.getServer(), heart).filter(Settlement::isActive).orElse(null);
        return Optional.of(new Bank(chest, heart, settlement));
    }

    @Nullable
    private static VillageHeartBlockEntity heartOf(ServerLevel level, BlockPos pos) {
        return VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .orElse(null);
    }

    /** True for a player in creative mode (instant build) or in spectator mode. */
    private static boolean isExempt(Player player) {
        return player.getAbilities().instabuild || player.isSpectator();
    }

    /**
     * A player uses a bank chest. With no village, or for a creative player, or after a pillage of this village,
     * the chest opens. Else the client shows the confirmation screen. When another player has the chest open, the
     * player gets a notice and nothing opens.
     *
     * @param player the player
     * @param pos    the position of the chest
     */
    public static void onUse(ServerPlayer player, BlockPos pos) {
        bank(player.serverLevel(), pos).ifPresent(bank -> {
            if (isInUse(player, bank)) {
                return;
            }
            if (bank.settlement() == null || isExempt(player) || bank.heart().hasPillaged(player)) {
                open(player, bank);
            } else {
                ModNetwork.send(player, request(player, pos, bank.heart(), bank.settlement()));
            }
        });
    }

    /**
     * True when another player has the chest of an active village open. The player then gets a notice. A chest
     * shows the stocks to one player at a time: a second opening would fill the chest again with the items that the
     * first player can take.
     */
    private static boolean isInUse(ServerPlayer player, Bank bank) {
        if (bank.settlement() == null) {
            return false;
        }
        Optional<ServerPlayer> viewer = viewerOf(player.server, bank.chest());
        if (viewer.isEmpty() || viewer.get() == player) {
            return false;
        }
        Notices.send(player, Component.translatable("message.folkhold_world.bank_in_use"), null, Notices.Tone.WARN);
        return true;
    }

    /**
     * The player who has the chest open in a chest menu.
     *
     * @param server the server
     * @param chest  the chest
     * @return the player, or empty when no player has the chest open. A player who left the game or who closed the
     *         menu without a close of the container does not count: the chest then forgets this viewer.
     */
    public static Optional<ServerPlayer> viewerOf(MinecraftServer server, BankChestBlockEntity chest) {
        UUID viewer = chest.viewer();
        if (viewer == null) {
            return Optional.empty();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(viewer);
        if (player != null && player.containerMenu instanceof ChestMenu menu
                && menu.getContainer() == chest.container()) {
            return Optional.of(player);
        }
        chest.clearViewer();
        return Optional.empty();
    }

    /**
     * The confirmation request of a pillage: the village, the faction, the reputation of the player before and after
     * the pillage, and the garrison that goes to the alert state.
     *
     * @param player     the player
     * @param pos        the position of the bank chest
     * @param heart      the heart of the village
     * @param settlement the settlement of the village
     * @return the payload
     */
    public static BankPillageRequestPayload request(ServerPlayer player, BlockPos pos, VillageHeartBlockEntity heart,
            Settlement settlement) {
        MinecraftServer server = player.server;
        Optional<FactionSpec> faction = FactionData.get(server).byId(settlement.factionId());
        int before = 0;
        int after = WorldConfig.BANK_PILLAGE_VALUE.get();
        if (faction.isPresent()) {
            PlayerReputation reputation = ReputationData.get(server).get(player.getUUID());
            ReputationSettings settings = Reputations.settings();
            before = reputation.effective(faction.get(), settlement.id(), settings);
            int value = WorldConfig.BANK_PILLAGE_VALUE.get();
            int villageAfter = Math.min(reputation.village(faction.get(), settlement.id()), value);
            int factionAfter = Math.min(reputation.faction(faction.get()), value);
            after = (int) Math.round(settings.villageWeight() * villageAfter
                    + (1.0 - settings.villageWeight()) * factionAfter);
        }
        int guards = 0;
        int archers = 0;
        for (Garrison.Entry entry : heart.getGarrison().entries()) {
            if (!entry.isDead()) {
                guards += entry.role() == GarrisonRole.GUARD ? 1 : 0;
                archers += entry.role() == GarrisonRole.ARCHER ? 1 : 0;
            }
        }
        Component factionName = faction.<Component>map(FactionVisuals::name)
                .orElse(Component.literal(settlement.factionId()));
        return new BankPillageRequestPayload(pos, Component.literal(settlement.name()), factionName,
                ReputationTier.of(before).serializedName(), before, ReputationTier.of(after).serializedName(), after,
                guards, archers);
    }

    /**
     * The player confirmed the pillage on the confirmation screen. The pillage applies, then the chest opens.
     *
     * @param player the player
     * @param pos    the position of the chest
     */
    public static void onConfirm(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > MAX_CONFIRM_DISTANCE * MAX_CONFIRM_DISTANCE) {
            return;
        }
        bank(player.serverLevel(), pos).ifPresent(bank -> {
            if (isInUse(player, bank)) {
                return;
            }
            if (bank.settlement() != null && !isExempt(player)) {
                pillage(player, bank.heart(), bank.settlement());
            }
            open(player, bank);
        });
    }

    /**
     * Applies a pillage of a village by a player.
     *
     * @param player     the player
     * @param heart      the heart of the village
     * @param settlement the settlement of the village
     */
    public static void pillage(ServerPlayer player, VillageHeartBlockEntity heart, Settlement settlement) {
        MinecraftServer server = player.server;
        Optional<FactionSpec> faction = FactionData.get(server).byId(settlement.factionId());
        if (faction.isPresent()) {
            ReputationData data = ReputationData.get(server);
            PlayerReputation reputation = data.get(player.getUUID());
            int value = WorldConfig.BANK_PILLAGE_VALUE.get();
            reputation.setFaction(faction.get().id(), Math.min(reputation.faction(faction.get()), value));
            reputation.setVillage(settlement.id(),
                    Math.min(reputation.village(faction.get(), settlement.id()), value));
            data.setDirty();
        }
        if (!heart.hasPillaged(player)) {
            heart.addPillager(player);
            SettlementLevels.addExperience(server, settlement, -WorldConfig.BANK_PILLAGE_LOSS.get());
        }
        heart.reportThreat(player);
        Notices.send(player, Component.translatable("message.folkhold_world.bank_pillaged", settlement.name()),
                Component.translatable("message.folkhold_world.bank_pillaged.details"), Notices.Tone.BAD);
    }

    /**
     * Opens the chest for the player. In an active village, the chest shows the stocks and the treasury: the content
     * comes from them at each opening. Items that are in the chest before the opening go into the stocks first
     * (the session of a player who left the game, see {@link #settle}). With no active village, the chest shows its
     * own items.
     */
    private static void open(ServerPlayer player, Bank bank) {
        BankChestBlockEntity chest = bank.chest();
        if (bank.settlement() != null) {
            settle(player.server, bank.settlement(), chest);
            List<ItemStack> stacks = contentOf(player.server, bank.settlement());
            int index = bank.heart().getLayout().bankChests().indexOf(chest.getBlockPos());
            int first = Math.max(0, index) * BankChestBlockEntity.SIZE;
            for (int slot = 0; slot < BankChestBlockEntity.SIZE && first + slot < stacks.size(); slot++) {
                chest.container().setItem(slot, stacks.get(first + slot));
            }
            chest.snapshot().putAll(count(chest));
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> ChestMenu.threeRows(id, inventory,
                chest.container()), Component.translatable("container.folkhold_world.bank_chest")));
    }

    /** The stacks of a village bank: the emeralds first, then the stocks, sorted by item id. */
    static List<ItemStack> contentOf(MinecraftServer server, Settlement settlement) {
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, Items.EMERALD, emeraldShare(server, settlement));
        for (Map.Entry<String, Integer> entry : settlement.economy().stock().asMap().entrySet()) {
            item(entry.getKey()).ifPresent(item -> addStacks(stacks, item, entry.getValue()));
        }
        return stacks;
    }

    /** The emeralds of the faction treasury that the bank of this village holds. */
    public static int emeraldShare(MinecraftServer server, Settlement settlement) {
        SettlementData data = SettlementData.get(server);
        long villages = data.registry().all().stream()
                .filter(other -> other.isActive() && other.factionId().equals(settlement.factionId()))
                .count();
        double share = data.treasury(settlement.factionId()) * WorldConfig.BANK_TREASURY_SHARE.get()
                / Math.max(1, villages);
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(share));
    }

    private static void addStacks(List<ItemStack> stacks, Item item, int count) {
        int max = new ItemStack(item).getMaxStackSize();
        int left = count;
        while (left > 0) {
            int size = Math.min(max, left);
            stacks.add(new ItemStack(item, size));
            left -= size;
        }
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }

    private static Map<String, Integer> count(BankChestBlockEntity chest) {
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
            ItemStack stack = chest.container().getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(ItemIds.id(stack.getItem()), stack.getCount(),
                        Integer::sum);
            }
        }
        return counts;
    }

    /**
     * A player closes a bank chest. The items that the player took leave the stocks (the emeralds leave the
     * treasury). The items that the player put in the chest go into the stocks.
     *
     * @param chest  the chest
     * @param player the player
     */
    public static void onClose(BankChestBlockEntity chest, Player player) {
        if (!(chest.getLevel() instanceof ServerLevel level)
                || (chest.snapshot().isEmpty() && chest.container().isEmpty())) {
            return;
        }
        VillageHeartBlockEntity heart = heartOf(level, chest.getBlockPos());
        Settlement settlement = heart == null ? null
                : Settlements.of(level.getServer(), heart).filter(Settlement::isActive).orElse(null);
        settle(level.getServer(), settlement, chest);
    }

    /**
     * A player leaves the game with a bank chest open: the chest closes now, as if the player closed it.
     *
     * @param event the event
     */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof ChestMenu menu
                && menu.getContainer() instanceof BankChestBlockEntity.Content) {
            player.doCloseContainer();
        }
    }

    /**
     * Settles the session of a chest. In an active village, the difference between the content at the opening and
     * the content now goes to the stocks and to the treasury, and the chest becomes empty. With no active village,
     * the chest is a normal chest and keeps its items.
     *
     * @param server     the server
     * @param settlement the active settlement of the village of the chest, or null
     * @param chest      the chest
     */
    private static void settle(MinecraftServer server, @Nullable Settlement settlement, BankChestBlockEntity chest) {
        if (chest.snapshot().isEmpty() && chest.container().isEmpty()) {
            return;
        }
        if (settlement != null) {
            applyChanges(server, settlement, chest.snapshot(), count(chest));
            chest.container().clearContent();
        }
        chest.snapshot().clear();
        chest.setChanged();
    }

    /**
     * Applies the difference between the content at the opening and the content at the closing.
     *
     * @param server     the server
     * @param settlement the settlement of the bank
     * @param before     quantity of each item at the opening
     * @param after      quantity of each item at the closing
     */
    static void applyChanges(MinecraftServer server, Settlement settlement, Map<String, Integer> before,
            Map<String, Integer> after) {
        SettlementData data = SettlementData.get(server);
        Stock stock = settlement.economy().stock();
        String emerald = ItemIds.id(Items.EMERALD);
        Set<String> items = new HashSet<>(before.keySet());
        items.addAll(after.keySet());
        for (String item : items) {
            int delta = after.getOrDefault(item, 0) - before.getOrDefault(item, 0);
            if (delta == 0) {
                continue;
            }
            if (item.equals(emerald)) {
                data.addTreasury(settlement.factionId(), delta);
            } else if (delta < 0) {
                stock.remove(item, -delta);
            } else {
                stock.add(item, delta, Integer.MAX_VALUE);
            }
        }
        data.setDirty();
    }

    /**
     * A bank chest is removed. With no active village, the chest drops its items. In an active village, the items
     * of the chest are a view of the stocks, so the chest drops nothing.
     *
     * @param level the level
     * @param pos   the position of the chest
     * @param chest the chest
     */
    public static void onChestRemoved(ServerLevel level, BlockPos pos, BankChestBlockEntity chest) {
        boolean active = bank(level, pos).map(bank -> bank.settlement() != null).orElse(false);
        if (!active) {
            Containers.dropContents(level, pos, chest.container());
        }
        chest.container().clearContent();
    }

    /**
     * The village becomes ruins: its share of the treasury and its stock go into its bank chests as real items,
     * as many as the chests can hold. The items leave the treasury and the stock. Call this method before the
     * settlement leaves the active state (the share of the treasury depends on the active villages).
     *
     * @param level      the level
     * @param settlement the settlement
     * @param center     the position of the village heart
     * @return the number of bank chests that got items
     */
    public static int storeInRuins(ServerLevel level, Settlement settlement, BlockPos center) {
        List<BankChestBlockEntity> chests = chestsNear(level, center);
        if (chests.isEmpty()) {
            return 0;
        }
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        List<ItemStack> treasury = new ArrayList<>();
        addStacks(treasury, Items.EMERALD, emeraldShare(server, settlement));
        List<ItemStack> stock = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : settlement.economy().stock().asMap().entrySet()) {
            item(entry.getKey()).ifPresent(item -> addStacks(stock, item, entry.getValue()));
        }
        int used = 0;
        int treasuryIndex = 0;
        int stockIndex = 0;
        for (BankChestBlockEntity chest : chests) {
            boolean filled = false;
            for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
                if (!chest.container().getItem(slot).isEmpty()) {
                    continue;
                }
                ItemStack stack;
                if (treasuryIndex < treasury.size()) {
                    stack = treasury.get(treasuryIndex++);
                    data.addTreasury(settlement.factionId(), -stack.getCount());
                } else if (stockIndex < stock.size()) {
                    stack = stock.get(stockIndex++);
                    settlement.economy().stock().remove(ItemIds.id(stack.getItem()),
                            stack.getCount());
                } else {
                    break;
                }
                chest.container().setItem(slot, stack);
                filled = true;
            }
            if (filled) {
                chest.snapshot().clear();
                chest.setChanged();
                used++;
            }
        }
        data.setDirty();
        return used;
    }

    /**
     * The village becomes active again: the items of its bank chests go back into the stock (the emeralds into
     * the treasury of the faction), and the chests become empty.
     *
     * @param level      the level
     * @param settlement the settlement
     * @param center     the position of the village heart
     */
    public static void recoverFromRuins(ServerLevel level, Settlement settlement, BlockPos center) {
        SettlementData data = SettlementData.get(level.getServer());
        for (BankChestBlockEntity chest : chestsNear(level, center)) {
            for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
                ItemStack stack = chest.container().getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(Items.EMERALD)) {
                    data.addTreasury(settlement.factionId(), stack.getCount());
                } else {
                    settlement.economy().stock().add(ItemIds.id(stack.getItem()),
                            stack.getCount(), Integer.MAX_VALUE);
                }
            }
            chest.container().clearContent();
            chest.snapshot().clear();
            chest.setChanged();
        }
        data.setDirty();
    }

    /** The bank chests in the village radius around a position, in the loaded chunks, sorted by position. */
    static List<BankChestBlockEntity> chestsNear(ServerLevel level, BlockPos center) {
        int radius = WorldConfig.VILLAGE_RADIUS.get();
        List<BankChestBlockEntity> chests = new ArrayList<>();
        for (int chunkX = (center.getX() - radius) >> 4; chunkX <= (center.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (center.getZ() - radius) >> 4; chunkZ <= (center.getZ() + radius) >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (entity instanceof BankChestBlockEntity chest
                            && Math.abs(pos.getX() - center.getX()) <= radius
                            && Math.abs(pos.getZ() - center.getZ()) <= radius
                            && Math.abs(pos.getY() - center.getY()) <= VERTICAL_RANGE) {
                        chests.add(chest);
                    }
                }
            }
        }
        chests.sort(Comparator.comparing(BlockEntity::getBlockPos));
        return chests;
    }

    /** A player breaks a bank chest: a pillage. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !isExempt(player)
                && event.getState().is(ModBlocks.BANK_CHEST.get()) && event.getLevel() instanceof ServerLevel level) {
            bank(level, event.getPos())
                    .filter(bank -> bank.settlement() != null)
                    .ifPresent(bank -> pillage(player, bank.heart(), bank.settlement()));
        }
    }

    /** A player starts to break a bank chest: a warning in the action bar. */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !isExempt(player)
                && event.getLevel().getBlockState(event.getPos()).is(ModBlocks.BANK_CHEST.get())) {
            Notices.warn(player, "bank_chest", Component.translatable("message.folkhold_world.bank_warning"),
                    Component.translatable("message.folkhold_world.bank_warning.details"));
        }
    }

    /**
     * An explosion: a bank chest breaks only if a player caused the explosion. This is then a pillage of this
     * player.
     */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer cause = causeOf(event.getExplosion());
        event.getAffectedBlocks().removeIf(pos -> {
            if (!level.getBlockState(pos).is(ModBlocks.BANK_CHEST.get())) {
                return false;
            }
            if (cause == null || isExempt(cause)) {
                return cause == null;
            }
            bank(level, pos).filter(bank -> bank.settlement() != null)
                    .ifPresent(bank -> pillage(cause, bank.heart(), bank.settlement()));
            return false;
        });
    }

    /** The player who caused an explosion (the player, or the player who lit the TNT), or null. */
    @Nullable
    public static ServerPlayer causeOf(Explosion explosion) {
        return explosion.getIndirectSourceEntity() instanceof ServerPlayer player ? player : null;
    }
}
