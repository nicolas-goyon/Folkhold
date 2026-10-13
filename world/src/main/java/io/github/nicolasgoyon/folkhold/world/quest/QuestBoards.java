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
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Active;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Offer;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private static final long TICKS_PER_SECOND = 20L;
    /** The item of the icon of an escort. */
    private static final String ESCORT_ICON = "minecraft:chest";
    /** Colors of the texts on the parchment of the screen. */
    private static final int INK_GOOD = 0x3F6B28;
    private static final int INK_BAD = 0x8E2F22;
    private static final int INK_MUTED = 0x6B5A43;
    /** Colors of the texts on the wood of the screen. */
    private static final int TEXT_GOOD = 0x9AD07E;
    private static final int TEXT_WARN = 0xE0A635;
    /** The keys of the messages of a success. */
    private static final Set<String> GOOD_MESSAGES = Set.of("quest_accepted", "quest_completed", "quest_abandoned");

    /** The player of a screen action: its messages go to the status line of the screen, not to the chat. */
    @Nullable
    private static ServerPlayer capturePlayer;
    @Nullable
    private static Component capturedMessage;
    private static boolean capturedGood;
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

    /**
     * The player opens the quest screen of a village from the village heart screen. The player must be in the
     * village radius, and the village must give quests to the player (not hostile).
     *
     * @param player       the player
     * @param settlementId the id of the village
     */
    public static void request(ServerPlayer player, String settlementId) {
        MinecraftServer server = player.server;
        Optional<Settlement> village = SettlementData.get(server).registry().byId(settlementId)
                .filter(Settlement::isActive);
        if (village.isEmpty()) {
            return;
        }
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        if (village.get().distanceSquared(player.getBlockX(), player.getBlockZ()) > radius * radius) {
            return;
        }
        boolean hostile = Reputations.effective(server, player, village.get().factionId(), settlementId)
                .map(ReputationTier::of).map(ReputationTier::isHostile).orElse(false);
        if (!hostile) {
            open(player, village.get());
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
        capturePlayer = player;
        capturedMessage = null;
        try {
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
        } finally {
            capturePlayer = null;
        }
        data.setDirty();
        Component status = capturedMessage;
        boolean good = capturedGood;
        capturedMessage = null;
        Optional<Settlement> village = SettlementData.get(server).registry().byId(payload.settlementId())
                .filter(Settlement::isActive);
        if (village.isPresent() && player.connection.hasChannel(QuestBoardPayload.TYPE)) {
            QuestBoardPayload board = board(player, village.get());
            PacketDistributor.sendToPlayer(player, status == null ? board : board.withStatus(status, good));
        } else if (status != null) {
            player.displayClientMessage(status, false);
        }
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
        PacketDistributor.sendToPlayer(player, log(player));
    }

    /**
     * The active quests of a player, for the quest HUD.
     *
     * @param player the player
     * @return the payload
     */
    public static QuestLogPayload log(ServerPlayer player) {
        MinecraftServer server = player.server;
        long now = server.overworld().getGameTime();
        List<QuestLogPayload.Entry> entries = new ArrayList<>();
        for (Quest quest : QuestData.get(server).quests().active(player.getUUID())) {
            int secondsLeft = (int) Math.max(0, (quest.deadline() - now) / TICKS_PER_SECOND);
            if (quest.kind() == QuestKind.DELIVERY) {
                entries.add(new QuestLogPayload.Entry(quest.id(), quest.target(),
                        Component.translatable("quest.folkhold_world.active.delivery", quest.count(),
                                itemName(quest.target())),
                        quest.count(), secondsLeft, Optional.empty()));
            } else {
                String destination = Escorts.caravan(server, quest).map(MobileGroup::destinationId).orElse("");
                entries.add(new QuestLogPayload.Entry(quest.id(), ESCORT_ICON,
                        Component.translatable("quest.folkhold_world.active.escort", villageName(server, destination)),
                        0, secondsLeft, Escorts.hudState(server, quest, player)));
            }
        }
        return new QuestLogPayload(WorldConfig.QUEST_MAX_ACTIVE.get(), entries);
    }

    /**
     * The quest screen of a village for a player.
     *
     * <ul>
     *   <li>Offers: what to do, the kind, the distance of an escort, the time, the minimum tier, the reward, the items
     *       of the player for a delivery, the departure of the caravan of an escort. The reason why the player
     *       cannot accept: too many active quests, or a tier too low.</li>
     *   <li>Active quests: what to do, the village, the reward, the progress of a delivery or the state of the
     *       caravan of an escort, the time left. The deliver button at the village of a delivery, or the reason why
     *       the player cannot deliver now.</li>
     * </ul>
     */
    public static QuestBoardPayload board(ServerPlayer player, Settlement settlement) {
        MinecraftServer server = player.server;
        Quests quests = QuestData.get(server).quests();
        long now = server.overworld().getGameTime();
        ReputationTier playerTier = Reputations.effective(server, player, settlement.factionId(), settlement.id())
                .map(ReputationTier::of).orElse(ReputationTier.NEUTRAL);
        int maxActive = WorldConfig.QUEST_MAX_ACTIVE.get();
        List<Quest> mine = quests.active(player.getUUID());
        List<Offer> offers = new ArrayList<>();
        for (Quest quest : quests.offers(settlement.id())) {
            offers.add(offer(server, player, quest, now, playerTier, mine.size(), maxActive));
        }
        List<Active> active = new ArrayList<>();
        for (Quest quest : mine) {
            active.add(active(server, player, quest, now, settlement));
        }
        return new QuestBoardPayload(Component.literal(settlement.name()), settlement.id(),
                playerTier.serializedName(), maxActive, WorldConfig.QUEST_OFFER_MINUTES.get(), offers, active,
                Optional.empty(), true);
    }

    private static Offer offer(MinecraftServer server, ServerPlayer player, Quest quest, long now,
            ReputationTier playerTier, int activeCount, int maxActive) {
        int minutes = (int) (quest.duration() / TICKS_PER_MINUTE);
        Component kind = Component.translatable("quest.folkhold_world.kind." + quest.kind().serializedName());
        Component info;
        Component progress = Component.empty();
        int countdown = -1;
        String icon;
        if (quest.kind() == QuestKind.DELIVERY) {
            icon = quest.target();
            info = Component.translatable("quest.folkhold_world.info.delivery", kind, minutes);
            int have = Deliveries.count(player.getInventory(), item(quest.target()).orElse(Items.AIR));
            progress = Component.translatable("quest.folkhold_world.have", have, quest.count())
                    .withColor(have >= quest.count() ? INK_GOOD : have > 0 ? INK_BAD : INK_MUTED);
        } else {
            icon = ESCORT_ICON;
            long blocks = Escorts.caravan(server, quest).map(group -> Math.round(group.route().length())).orElse(0L);
            info = Component.translatable("quest.folkhold_world.info.escort", kind, blocks, minutes);
            countdown = (int) Math.max(0, (quest.offerEnd() - now) / TICKS_PER_SECOND);
        }
        if (quest.minTier() != ReputationTier.NEUTRAL) {
            info = Component.translatable("quest.folkhold_world.info.tier", info, tierMark(quest.minTier()));
        }
        Optional<Component> blocked = Optional.empty();
        if (activeCount >= maxActive) {
            blocked = Optional.of(Component.translatable("quest.folkhold_world.blocked.full", activeCount,
                    maxActive));
        } else if (playerTier.ordinal() < quest.minTier().ordinal()) {
            blocked = Optional.of(Component.translatable("quest.folkhold_world.blocked.tier",
                    tierTitle(quest.minTier())));
        }
        return new Offer(quest.id(), quest.kind().serializedName(), icon, name(server, quest), info,
                Component.translatable("quest.folkhold_world.reward", quest.rewardEmeralds(),
                        quest.rewardReputation()),
                progress, countdown, (int) Math.min(Integer.MAX_VALUE, quest.rewardEmeralds()), minutes, blocked);
    }

    private static Active active(MinecraftServer server, ServerPlayer player, Quest quest, long now,
            Settlement here) {
        int secondsLeft = (int) Math.max(0, (quest.deadline() - now) / TICKS_PER_SECOND);
        MutableComponent info = Component.translatable("quest.folkhold_world.active.info",
                villageName(server, quest.settlementId()), quest.rewardEmeralds());
        int action = QuestBoardPayload.ACTION_NONE;
        Optional<Component> blocked = Optional.empty();
        boolean ready = false;
        if (quest.kind() == QuestKind.DELIVERY) {
            Optional<Item> item = item(quest.target());
            int have = Deliveries.count(player.getInventory(), item.orElse(Items.AIR));
            info.append(" · ").append(Component.translatable("quest.folkhold_world.active.progress",
                    Math.min(have, quest.count()), quest.count(), have >= quest.count() ? " ✓" : "")
                    .withColor(have >= quest.count() ? TEXT_GOOD : 0xA89F8C));
            if (Deliveries.canDeliverAt(quest, here)) {
                action = QuestBoardPayload.ACTION_DELIVER;
                ready = have >= quest.count();
                if (!ready) {
                    blocked = Optional.of(Component.translatable("quest.folkhold_world.missing",
                            quest.count() - have, itemName(quest.target())));
                }
            } else {
                blocked = Optional.of(Component.translatable("quest.folkhold_world.deliver_at",
                        villageName(server, quest.settlementId())));
            }
            return new Active(quest.id(), quest.target(),
                    Component.translatable("quest.folkhold_world.active.delivery", quest.count(),
                            itemName(quest.target())),
                    info, secondsLeft, action, blocked, ready);
        }
        Optional<MobileGroup> caravan = Escorts.caravan(server, quest);
        caravan.map(QuestBoards::caravanState).ifPresent(state -> info.append(" · ").append(state));
        String destination = caravan.map(MobileGroup::destinationId).orElse("");
        return new Active(quest.id(), ESCORT_ICON,
                Component.translatable("quest.folkhold_world.active.escort", villageName(server, destination)),
                info, secondsLeft, action, blocked, false);
    }

    /** The state of the caravan of an escort, for the screen. */
    private static Component caravanState(MobileGroup group) {
        return switch (group.state()) {
            case WAITING -> Component.translatable("quest.folkhold_world.caravan.waiting").withColor(0xA89F8C);
            case TRAVELING -> Component.translatable("quest.folkhold_world.caravan.traveling",
                    Math.round(group.progress()), Math.round(group.route().length())).withColor(TEXT_GOOD);
            case STOPPED -> Component.translatable("quest.folkhold_world.caravan.stopped").withColor(TEXT_WARN);
            default -> Component.empty();
        };
    }

    /** The minimum tier of an offer: a tick and the name of the tier, in green. */
    private static Component tierMark(ReputationTier tier) {
        return Component.literal("✓ ").append(tierTitle(tier)).withColor(INK_GOOD);
    }

    /** The name of a tier with a capital letter, for a title or a chip. */
    public static Component tierTitle(ReputationTier tier) {
        return Component.translatable("folkhold_world.reputation_tier_title." + tier.serializedName());
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location)
                .filter(found -> found != Items.AIR);
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

    /**
     * Sends a message to a player. During a screen action of this player, the message goes to the status line of
     * the screen.
     */
    static void message(ServerPlayer player, String key, Object... args) {
        Component text = Component.translatable("message.folkhold_world." + key, args);
        if (player == capturePlayer) {
            capturedMessage = text;
            capturedGood = GOOD_MESSAGES.contains(key);
            return;
        }
        player.displayClientMessage(text, false);
    }
}
