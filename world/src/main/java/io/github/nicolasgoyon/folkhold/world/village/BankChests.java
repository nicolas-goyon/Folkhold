package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageRequestPayload;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
 */
public final class BankChests {
    /** A player farther than this distance from a bank chest cannot confirm the pillage. */
    private static final double MAX_CONFIRM_DISTANCE = 8.0;

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
     * the chest opens. Else the client shows the confirmation screen.
     *
     * @param player the player
     * @param pos    the position of the chest
     */
    public static void onUse(ServerPlayer player, BlockPos pos) {
        bank(player.serverLevel(), pos).ifPresent(bank -> {
            if (bank.settlement() == null || isExempt(player) || bank.heart().hasPillaged(player)) {
                open(player, bank);
            } else {
                PacketDistributor.sendToPlayer(player, new BankPillageRequestPayload(pos));
            }
        });
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
        player.displayClientMessage(Component.translatable("message.folkhold_world.bank_pillaged",
                settlement.name()), true);
    }

    /** Fills the chest from the stocks and the treasury, then opens it for the player. */
    private static void open(ServerPlayer player, Bank bank) {
        BankChestBlockEntity chest = bank.chest();
        chest.container().clearContent();
        chest.snapshot().clear();
        if (bank.settlement() != null) {
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
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
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
        if (!(chest.getLevel() instanceof ServerLevel level) || chest.snapshot().isEmpty() && chest.container().isEmpty()) {
            return;
        }
        VillageHeartBlockEntity heart = heartOf(level, chest.getBlockPos());
        Optional<Settlement> settlement = heart == null ? Optional.empty()
                : Settlements.of(level.getServer(), heart);
        Map<String, Integer> now = count(chest);
        if (settlement.isPresent()) {
            applyChanges(level.getServer(), settlement.get(), chest.snapshot(), now);
        }
        chest.snapshot().clear();
        chest.container().clearContent();
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
        String emerald = BuiltInRegistries.ITEM.getKey(Items.EMERALD).toString();
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
            player.displayClientMessage(Component.translatable("message.folkhold_world.bank_warning"), true);
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
