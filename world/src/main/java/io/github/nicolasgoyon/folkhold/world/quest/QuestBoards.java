package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Entry;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The quest boards and the quest log of the players.
 *
 * <ul>
 *   <li>A click on a quest board, or on the captain of a village, opens the quest screen of the village: its offers
 *       and the active quests of the player (see {@link QuestBoardPayload}). A captain does not talk to a hostile
 *       player.</li>
 *   <li>The player accepts an offer, gives the items of a delivery (see {@link Deliveries}) or abandons an active
 *       quest with the buttons of the screen. The server checks the action, sends a message and updates the
 *       screen.</li>
 *   <li>A completed quest gives its reward: emeralds from the treasury of the faction of the village, and
 *       reputation (see {@link #complete(MinecraftServer, Quest)}).</li>
 *   <li>The player gets a message when a quest starts, succeeds or fails. The quest HUD shows the active quests
 *       (see {@link QuestLogPayload}).</li>
 * </ul>
 */
public final class QuestBoards {
    private static final long TICKS_PER_MINUTE = 1200L;
    private static final List<BiConsumer<ServerPlayer, Quest>> ACCEPT_LISTENERS = new ArrayList<>();
    private static final List<BiConsumer<MinecraftServer, Quest>> END_LISTENERS = new ArrayList<>();

    private QuestBoards() {
    }

    /**
     * Adds a listener for the accepted quests (for example the start of an escort).
     *
     * @param listener the listener
     */
    public static void addAcceptListener(BiConsumer<ServerPlayer, Quest> listener) {
        ACCEPT_LISTENERS.add(listener);
    }

    /**
     * Adds a listener for the quests that end: completed, failed or abandoned.
     *
     * @param listener the listener
     */
    public static void addEndListener(BiConsumer<MinecraftServer, Quest> listener) {
        END_LISTENERS.add(listener);
    }

    /** The active settlement of the village near a position, if a village heart is near. */
    public static Optional<Settlement> villageAt(ServerLevel level, BlockPos pos) {
        return VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .flatMap(heart -> Settlements.of(level.getServer(), heart))
                .filter(Settlement::isActive);
    }

    /** Opens the quest screen of the village near a quest board. */
    public static void openAt(ServerPlayer player, BlockPos pos) {
        Optional<Settlement> village = villageAt(player.serverLevel(), pos);
        if (village.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.folkhold_world.quest_board_no_village"),
                    true);
            return;
        }
        open(player, village.get());
    }

    /** Opens the quest screen of a village. A client with no channel for the screen gets nothing. */
    public static void open(ServerPlayer player, Settlement settlement) {
        if (player.connection.hasChannel(QuestBoardPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, board(player, settlement));
        }
    }

    /** A click on a captain opens the quest screen of its village. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof CaptainEntity captain)) {
            return;
        }
        Optional<Settlement> village = captain.getVillageHeart()
                .flatMap(heart -> Settlements.of(player.server, heart)).filter(Settlement::isActive);
        if (village.isEmpty()) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (captain.isHostileTo(player)) {
            player.displayClientMessage(Component.translatable("message.folkhold_world.captain_refuses",
                    captain.getDisplayName()), true);
            return;
        }
        open(player, village.get());
    }

    /** A player who joins gets the quest HUD. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendLog(player);
        }
    }

    /**
     * A button of the quest screen.
     *
     * @param player  the player
     * @param payload the action
     */
    public static void handle(ServerPlayer player, QuestActionPayload payload) {
        MinecraftServer server = player.server;
        QuestData data = QuestData.get(server);
        Quests quests = data.quests();
        Optional<Quest> quest = quests.byId(payload.questId());
        switch (payload.action()) {
            case QuestBoardPayload.ACTION_ACCEPT -> accept(player, payload.questId());
            case QuestBoardPayload.ACTION_DELIVER -> Deliveries.deliver(player, payload.questId());
            case QuestBoardPayload.ACTION_ABANDON -> {
                if (quest.isPresent() && quests.abandon(payload.questId(), player.getUUID())) {
                    message(player, "quest_abandoned", name(server, quest.get()));
                    END_LISTENERS.forEach(listener -> listener.accept(server, quest.get()));
                }
            }
            default -> {
            }
        }
        data.setDirty();
        SettlementData.get(server).registry().byId(payload.settlementId()).filter(Settlement::isActive)
                .ifPresent(settlement -> open(player, settlement));
        sendLog(player);
    }

    /**
     * A player accepts an offer.
     *
     * @param player  the player
     * @param questId the id of the quest
     * @return the result
     */
    public static Quests.AcceptResult accept(ServerPlayer player, String questId) {
        MinecraftServer server = player.server;
        QuestData data = QuestData.get(server);
        Optional<Quest> quest = data.quests().byId(questId);
        ReputationTier tier = quest.map(found -> tier(server, player, found)).orElse(ReputationTier.NEUTRAL);
        long now = server.overworld().getGameTime();
        Quests.AcceptResult result = data.quests().accept(questId, player.getUUID(), tier, now,
                WorldConfig.QUEST_MAX_ACTIVE.get());
        switch (result) {
            case ACCEPTED -> {
                message(player, "quest_accepted", name(server, quest.get()));
                ACCEPT_LISTENERS.forEach(listener -> listener.accept(player, quest.get()));
            }
            case TIER_TOO_LOW -> message(player, "quest_tier_too_low");
            case TOO_MANY -> message(player, "quest_too_many", WorldConfig.QUEST_MAX_ACTIVE.get());
            default -> message(player, "quest_gone");
        }
        data.setDirty();
        return result;
    }

    /**
     * Gives an offer to a player, with no check of the tier and of the maximum (for the commands of the
     * operators).
     *
     * @param player  the player
     * @param questId the id of the quest
     * @return true if the player has the quest now
     */
    public static boolean give(ServerPlayer player, String questId) {
        MinecraftServer server = player.server;
        QuestData data = QuestData.get(server);
        Optional<Quest> quest = data.quests().byId(questId);
        Quests.AcceptResult result = data.quests().accept(questId, player.getUUID(), ReputationTier.ALLY,
                server.overworld().getGameTime(), Integer.MAX_VALUE);
        if (result != Quests.AcceptResult.ACCEPTED) {
            return false;
        }
        message(player, "quest_accepted", name(server, quest.get()));
        ACCEPT_LISTENERS.forEach(listener -> listener.accept(player, quest.get()));
        data.setDirty();
        sendLog(player);
        return true;
    }

    /**
     * Completes an accepted quest: the player gets the emeralds (from the treasury of the faction of the village) and
     * the reputation.
     *
     * @param server the server
     * @param quest  the quest
     * @return true if the quest was accepted
     */
    public static boolean complete(MinecraftServer server, Quest quest) {
        UUID playerId = quest.playerId();
        QuestData data = QuestData.get(server);
        if (playerId == null || !data.quests().finish(quest.id(), QuestState.COMPLETED)) {
            return false;
        }
        data.setDirty();
        SettlementData settlements = SettlementData.get(server);
        long emeralds = Math.min(quest.rewardEmeralds(), settlements.treasury(quest.factionId()));
        settlements.addTreasury(quest.factionId(), -emeralds);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            giveEmeralds(player, emeralds);
            Reputations.apply(server, player, quest.factionId(), quest.settlementId(), quest.rewardReputation());
            message(player, "quest_completed", name(server, quest), emeralds);
            sendLog(player);
        }
        END_LISTENERS.forEach(listener -> listener.accept(server, quest));
        return true;
    }

    /**
     * Fails an accepted quest, for example when the merchant of an escorted caravan dies.
     *
     * @param server the server
     * @param quest  the quest
     * @return true if the quest was accepted
     */
    public static boolean fail(MinecraftServer server, Quest quest) {
        QuestData data = QuestData.get(server);
        if (!data.quests().finish(quest.id(), QuestState.FAILED)) {
            return false;
        }
        data.setDirty();
        onQuestEnded(server, quest);
        return true;
    }

    /**
     * A quest ended in the quest system or with {@link #fail}: the player gets a message for a failed quest, and
     * the listeners get the quest.
     *
     * @param server the server
     * @param quest  the quest
     */
    public static void onQuestEnded(MinecraftServer server, Quest quest) {
        if (quest.state() == QuestState.FAILED && quest.playerId() != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(quest.playerId());
            if (player != null) {
                message(player, "quest_failed", name(server, quest));
                sendLog(player);
            }
        }
        if (quest.state() == QuestState.FAILED) {
            END_LISTENERS.forEach(listener -> listener.accept(server, quest));
        }
    }

    /** Sends the active quests of a player to the quest HUD. A client with no channel for the HUD gets nothing. */
    public static void sendLog(ServerPlayer player) {
        if (!player.connection.hasChannel(QuestLogPayload.TYPE)) {
            return;
        }
        MinecraftServer server = player.server;
        List<Component> lines = new ArrayList<>();
        for (Quest quest : QuestData.get(server).quests().active(player.getUUID())) {
            lines.add(Component.translatable("hud.folkhold_world.quest", name(server, quest),
                    minutesLeft(server, quest)));
        }
        PacketDistributor.sendToPlayer(player, new QuestLogPayload(lines));
    }

    /** The quest screen of a village for a player. */
    public static QuestBoardPayload board(ServerPlayer player, Settlement settlement) {
        MinecraftServer server = player.server;
        Quests quests = QuestData.get(server).quests();
        List<Entry> offers = new ArrayList<>();
        for (Quest quest : quests.offers(settlement.id())) {
            Component details = Component.translatable("quest.folkhold_world.offer_details",
                    quest.rewardEmeralds(), quest.rewardReputation(), quest.duration() / TICKS_PER_MINUTE);
            if (quest.minTier() != ReputationTier.NEUTRAL) {
                details = Component.translatable("quest.folkhold_world.tier_required", details,
                        Component.translatable("folkhold_world.reputation_tier." + quest.minTier().serializedName()));
            }
            offers.add(new Entry(quest.id(), name(server, quest), details, QuestBoardPayload.ACTION_ACCEPT, false));
        }
        List<Entry> active = new ArrayList<>();
        for (Quest quest : quests.active(player.getUUID())) {
            Component details = Component.translatable("quest.folkhold_world.active_details",
                    quest.rewardEmeralds(), villageName(server, quest.settlementId()), minutesLeft(server, quest));
            active.add(new Entry(quest.id(), name(server, quest), details, mainAction(quest, settlement), true));
        }
        return new QuestBoardPayload(Component.literal(settlement.name()), settlement.id(), offers, active);
    }

    /** The main button of an active quest on the screen of a village: "Deliver" at the village of a delivery. */
    private static int mainAction(Quest quest, Settlement settlement) {
        return Deliveries.canDeliverAt(quest, settlement) ? QuestBoardPayload.ACTION_DELIVER
                : QuestBoardPayload.ACTION_NONE;
    }

    /** The text of a quest: what to do. */
    public static Component name(MinecraftServer server, Quest quest) {
        if (quest.kind() == QuestKind.DELIVERY) {
            return Component.translatable("quest.folkhold_world.delivery", quest.count(), itemName(quest.target()));
        }
        String destination = MobileGroupData.get(server).groups().byId(quest.target())
                .map(MobileGroup::destinationId).orElse("");
        return Component.translatable("quest.folkhold_world." + quest.kind().serializedName(),
                villageName(server, destination));
    }

    /** The reputation tier of a player for a quest: the effective tier in its village. */
    public static ReputationTier tier(MinecraftServer server, ServerPlayer player, Quest quest) {
        return Reputations.effective(server, player, quest.factionId(), quest.settlementId())
                .map(ReputationTier::of).orElse(ReputationTier.NEUTRAL);
    }

    private static Component itemName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Optional<Item> item = location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
        return item.map(found -> new ItemStack(found).getHoverName()).orElse(Component.literal(id));
    }

    private static Component villageName(MinecraftServer server, String settlementId) {
        return SettlementData.get(server).registry().byId(settlementId)
                .<Component>map(settlement -> Component.literal(settlement.name()))
                .orElse(Component.literal("?"));
    }

    private static long minutesLeft(MinecraftServer server, Quest quest) {
        long left = Math.max(0, quest.deadline() - server.overworld().getGameTime());
        return (left + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE;
    }

    private static void giveEmeralds(ServerPlayer player, long emeralds) {
        long remaining = emeralds;
        while (remaining > 0) {
            int count = (int) Math.min(64, remaining);
            ItemStack stack = new ItemStack(Items.EMERALD, count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= count;
        }
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.folkhold_world." + key, args), false);
    }
}
