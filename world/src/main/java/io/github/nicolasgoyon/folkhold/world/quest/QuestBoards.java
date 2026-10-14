package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiStrings;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Active;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Offer;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The quest boards and the quest log of the players.
 *
 * <ul>
 *   <li>A click on a quest board, or on the captain of a village, opens the quest screen of the village: its offers
 *       and the active quests of the player (see {@link QuestBoardPayload}). A captain does not talk to a hostile
 *       player.</li>
 *   <li>The player accepts an offer, gives the items of a delivery (see {@link Deliveries}) or abandons an active
 *       quest with the buttons of the screen. The server checks that the player is in the village and not hostile,
 *       does the action, shows the result on the status line of the screen and updates the screen.</li>
 *   <li>A completed quest gives its reward: emeralds from the treasury of the faction of the village, and
 *       reputation (see {@link #complete(MinecraftServer, Quest)}). A player who is not in the game gets the
 *       reward at the next login.</li>
 *   <li>The player gets a message when a quest starts, succeeds or fails. The quest HUD shows the active quests
 *       (see {@link QuestLogPayload}).</li>
 * </ul>
 */
public final class QuestBoards {
    /** The item of the icon of an escort. */
    private static final String ESCORT_ICON = "minecraft:chest";
    /** Colors of the texts on the parchment of the screen. */
    private static final int INK_GOOD = 0x3F6B28;
    private static final int INK_BAD = 0x8E2F22;
    private static final int INK_MUTED = 0x6B5A43;
    /** Colors of the texts on the wood of the screen. */
    private static final int TEXT_GOOD = 0x9AD07E;
    private static final int TEXT_WARN = 0xE0A635;
    /** A player this far outside the village radius can still use the quest screen: the reach of a click. */
    private static final double ACTION_MARGIN = 8.0;
    private static final List<BiConsumer<ServerPlayer, Quest>> ACCEPT_LISTENERS = new ArrayList<>();
    private static final List<BiConsumer<MinecraftServer, Quest>> END_LISTENERS = new ArrayList<>();

    /**
     * The result of an action of the quest screen.
     *
     * @param done    true if the action succeeded
     * @param message the message for the status line of the screen
     */
    public record Outcome(boolean done, Component message) {
        static Outcome of(boolean done, String key, Object... args) {
            return new Outcome(done, Component.translatable("message.folkhold_world." + key, args));
        }
    }

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
        ModNetwork.send(player, board(player, settlement));
    }

    /**
     * The player opens the quest screen of a village from the village heart screen. The player must be in the
     * village radius, and the village must give quests to the player (not hostile).
     *
     * @param player       the player
     * @param settlementId the id of the village
     */
    public static void request(ServerPlayer player, String settlementId) {
        SettlementData.get(player.server).registry().byId(settlementId)
                .filter(Settlement::isActive)
                .filter(village -> canAct(player, village))
                .ifPresent(village -> open(player, village));
    }

    /**
     * True if the player can use the quest screen of a village: the player is in the village radius (plus the
     * reach of a click), and the village is not hostile to the player.
     */
    public static boolean canAct(ServerPlayer player, Settlement village) {
        double radius = WorldConfig.VILLAGE_RADIUS.get() + ACTION_MARGIN;
        if (village.distanceSquared(player.getBlockX(), player.getBlockZ()) > radius * radius) {
            return false;
        }
        return !Reputations.effective(player.server, player, village.factionId(), village.id())
                .map(ReputationTier::of).map(ReputationTier::isHostile).orElse(false);
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

    /** A player who joins gets the rewards of the quests that completed while the player was away, and the HUD. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (QuestData.Reward reward : QuestData.get(player.server).takePendingRewards(player.getUUID())) {
                Notices.send(player, pay(player, reward), null, Notices.Tone.GOOD);
            }
            sendLog(player);
        }
    }

    /**
     * A button of the quest screen. The action happens only when the player is in the village of the screen and
     * the village is not hostile (see {@link #canAct}). Then the screen shows the result.
     *
     * @param player  the player
     * @param payload the action
     */
    public static void handle(ServerPlayer player, QuestActionPayload payload) {
        MinecraftServer server = player.server;
        Optional<Settlement> village = SettlementData.get(server).registry().byId(payload.settlementId())
                .filter(Settlement::isActive);
        Optional<Outcome> outcome;
        if (village.isEmpty() || !canAct(player, village.get())) {
            outcome = Optional.of(Outcome.of(false, "quest_gone"));
        } else {
            outcome = switch (payload.action()) {
                case QuestBoardPayload.ACTION_ACCEPT -> Optional.of(acceptAt(player, payload.questId(), village.get()));
                case QuestBoardPayload.ACTION_DELIVER ->
                        Optional.of(Deliveries.deliver(player, payload.questId(), village.get()));
                case QuestBoardPayload.ACTION_ABANDON -> Optional.of(abandon(player, payload.questId()));
                default -> Optional.empty();
            };
        }
        QuestData.get(server).setDirty();
        boolean shown = false;
        if (village.isPresent()) {
            QuestBoardPayload board = board(player, village.get());
            shown = ModNetwork.send(player,
                    outcome.map(result -> board.withStatus(result.message(), result.done())).orElse(board));
        }
        if (!shown) {
            outcome.ifPresent(result -> player.displayClientMessage(result.message(), false));
        }
        sendLog(player);
    }

    /** A player accepts an offer of the quest screen of a village. The offer must come from this village. */
    private static Outcome acceptAt(ServerPlayer player, String questId, Settlement village) {
        MinecraftServer server = player.server;
        Optional<Quest> quest = QuestData.get(server).quests().byId(questId)
                .filter(found -> found.settlementId().equals(village.id()));
        if (quest.isEmpty()) {
            return Outcome.of(false, "quest_gone");
        }
        return switch (accept(player, questId)) {
            case ACCEPTED -> Outcome.of(true, "quest_accepted", name(server, quest.get()));
            case TIER_TOO_LOW -> Outcome.of(false, "quest_tier_too_low");
            case TOO_MANY -> Outcome.of(false, "quest_too_many", WorldConfig.QUEST_MAX_ACTIVE.get());
            default -> Outcome.of(false, "quest_gone");
        };
    }

    /** A player abandons an active quest. */
    private static Outcome abandon(ServerPlayer player, String questId) {
        MinecraftServer server = player.server;
        QuestData data = QuestData.get(server);
        Optional<Quest> quest = data.quests().byId(questId);
        if (quest.isEmpty() || !data.quests().abandon(questId, player.getUUID())) {
            return Outcome.of(false, "quest_gone");
        }
        data.setDirty();
        END_LISTENERS.forEach(listener -> listener.accept(server, quest.get()));
        return Outcome.of(true, "quest_abandoned", name(server, quest.get()));
    }

    /**
     * A player accepts an offer. The caller shows the result to the player.
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
        if (result == Quests.AcceptResult.ACCEPTED) {
            ACCEPT_LISTENERS.forEach(listener -> listener.accept(player, quest.get()));
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
        player.displayClientMessage(Component.translatable("message.folkhold_world.quest_accepted",
                name(server, quest.get())), false);
        ACCEPT_LISTENERS.forEach(listener -> listener.accept(player, quest.get()));
        data.setDirty();
        sendLog(player);
        return true;
    }

    /**
     * Completes an accepted quest: the player gets the emeralds (from the treasury of the faction of the village) and
     * the reputation. A player who is not in the game gets the reward at the next login: the treasury pays at that
     * time.
     *
     * @param server the server
     * @param quest  the quest
     * @return the message of the reward when the player got it now, or empty
     */
    public static Optional<Component> complete(MinecraftServer server, Quest quest) {
        UUID playerId = quest.playerId();
        QuestData data = QuestData.get(server);
        if (playerId == null || !data.quests().finish(quest.id(), QuestState.COMPLETED)) {
            return Optional.empty();
        }
        data.setDirty();
        QuestData.Reward reward = new QuestData.Reward(playerId, quest.factionId(), quest.settlementId(),
                quest.rewardEmeralds(), quest.rewardReputation(), name(server, quest));
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        Optional<Component> message = Optional.empty();
        if (player != null) {
            message = Optional.of(pay(player, reward));
            sendLog(player);
        } else {
            data.addPendingReward(reward);
        }
        END_LISTENERS.forEach(listener -> listener.accept(server, quest));
        return message;
    }

    /**
     * Completes an accepted quest and sends the message of the reward to the player (see {@link #complete}). For
     * the code that runs with no quest screen open.
     *
     * @param server the server
     * @param quest  the quest
     */
    public static void completeAndNotify(MinecraftServer server, Quest quest) {
        complete(server, quest).ifPresent(message -> {
            ServerPlayer player = server.getPlayerList().getPlayer(quest.playerId());
            if (player != null) {
                Notices.send(player, message, null, Notices.Tone.GOOD);
            }
        });
    }

    /**
     * Pays a reward to a player: the emeralds, up to the treasury of the faction, and the reputation.
     *
     * @param player the player
     * @param reward the reward
     * @return the message of the reward
     */
    private static Component pay(ServerPlayer player, QuestData.Reward reward) {
        MinecraftServer server = player.server;
        SettlementData settlements = SettlementData.get(server);
        long emeralds = Math.min(reward.emeralds(), settlements.treasury(reward.factionId()));
        settlements.addTreasury(reward.factionId(), -emeralds);
        giveEmeralds(player, emeralds);
        Reputations.apply(server, player, reward.factionId(), reward.settlementId(), reward.reputation());
        return Notices.asText(Component.translatable("message.folkhold_world.quest_completed", reward.name()),
                Component.translatable("message.folkhold_world.quest_reward", emeralds));
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
                Notices.send(player, Component.translatable("message.folkhold_world.quest_failed",
                        name(server, quest)), null, Notices.Tone.BAD);
                sendLog(player);
            }
        }
        if (quest.state() == QuestState.FAILED) {
            END_LISTENERS.forEach(listener -> listener.accept(server, quest));
        }
    }

    /** Sends the active quests of a player to the quest HUD. A client with no channel for the HUD gets nothing. */
    public static void sendLog(ServerPlayer player) {
        ModNetwork.send(player, log(player));
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
            int secondsLeft = (int) Math.max(0, (quest.deadline() - now) / SharedConstants.TICKS_PER_SECOND);
            if (quest.kind() == QuestKind.DELIVERY) {
                Optional<BlockPos> village = SettlementData.get(server).registry().byId(quest.settlementId())
                        .map(settlement -> Settlements.position(server, settlement));
                entries.add(new QuestLogPayload.Entry(quest.id(), quest.target(),
                        Component.translatable("quest.folkhold_world.active.delivery", quest.count(),
                                itemName(quest.target())),
                        quest.count(), secondsLeft, Optional.empty(), village, QuestLogPayload.NO_ENTITY));
            } else {
                String destination = Escorts.caravan(server, quest).map(MobileGroup::destinationId).orElse("");
                Optional<Entity> merchant = Escorts.merchantEntity(server, quest);
                entries.add(new QuestLogPayload.Entry(quest.id(), ESCORT_ICON,
                        Component.translatable("quest.folkhold_world.active.escort", villageName(server, destination)),
                        0, secondsLeft, Escorts.hudState(server, quest, player), Escorts.position(server, quest),
                        merchant.map(Entity::getId).orElse(QuestLogPayload.NO_ENTITY)));
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
        int minutes = (int) (quest.duration() / SharedConstants.TICKS_PER_MINUTE);
        Component kind = Component.translatable("quest.folkhold_world.kind." + quest.kind().serializedName());
        Component info;
        Component progress = Component.empty();
        int countdown = -1;
        String icon;
        if (quest.kind() == QuestKind.DELIVERY) {
            icon = quest.target();
            info = Component.translatable("quest.folkhold_world.info.delivery", kind, minutes);
            int have = Deliveries.count(player.getInventory(), ItemIds.item(quest.target()).orElse(Items.AIR));
            progress = Component.translatable("quest.folkhold_world.have", have, quest.count())
                    .withColor(have >= quest.count() ? INK_GOOD : have > 0 ? INK_BAD : INK_MUTED);
        } else {
            icon = ESCORT_ICON;
            long blocks = Escorts.caravan(server, quest).map(group -> Math.round(group.route().length())).orElse(0L);
            info = Component.translatable("quest.folkhold_world.info.escort", kind, blocks, minutes);
            countdown = (int) Math.max(0, (quest.offerEnd() - now) / SharedConstants.TICKS_PER_SECOND);
        }
        if (quest.minTier() != ReputationTier.NEUTRAL) {
            info = Component.translatable("quest.folkhold_world.info.tier", info, tierMark(quest.minTier()));
        }
        Optional<Component> blocked = Optional.empty();
        if (activeCount >= maxActive) {
            blocked = Optional.of(Component.translatable("quest.folkhold_world.blocked.full", activeCount,
                    maxActive));
        } else if (!playerTier.isAtLeast(quest.minTier())) {
            blocked = Optional.of(Component.translatable("quest.folkhold_world.blocked.tier",
                    FactionVisuals.tierName(quest.minTier())));
        }
        return new Offer(quest.id(), quest.kind().serializedName(), icon, name(server, quest), info,
                Component.translatable("quest.folkhold_world.reward", quest.rewardEmeralds(),
                        quest.rewardReputation()),
                progress, countdown, (int) Math.min(Integer.MAX_VALUE, quest.rewardEmeralds()), minutes, blocked);
    }

    private static Active active(MinecraftServer server, ServerPlayer player, Quest quest, long now,
            Settlement here) {
        int secondsLeft = (int) Math.max(0, (quest.deadline() - now) / SharedConstants.TICKS_PER_SECOND);
        MutableComponent info = Component.translatable("quest.folkhold_world.active.info",
                villageName(server, quest.settlementId()), quest.rewardEmeralds());
        int action = QuestBoardPayload.ACTION_NONE;
        Optional<Component> blocked = Optional.empty();
        boolean ready = false;
        if (quest.kind() == QuestKind.DELIVERY) {
            Optional<Item> item = ItemIds.item(quest.target());
            int have = Deliveries.count(player.getInventory(), item.orElse(Items.AIR));
            MutableComponent progress = Component.translatable("quest.folkhold_world.active.progress",
                    Math.min(have, quest.count()), quest.count());
            if (have >= quest.count()) {
                progress = Component.translatable("quest.folkhold_world.active.done", progress);
            }
            info.append(UiStrings.separator()).append(progress.withColor(have >= quest.count() ? TEXT_GOOD : 0xA89F8C));
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
        caravan.map(QuestBoards::caravanState).ifPresent(state -> info.append(UiStrings.separator()).append(state));
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
        return Component.literal("✓ ").append(FactionVisuals.tierName(tier)).withColor(INK_GOOD);
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
}
