package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.group.CaravanInteractions;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.quest.Deliveries;
import io.github.nicolasgoyon.folkhold.world.quest.Escorts;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the escort quests. The tests call the escort checks directly with a chosen game time. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscortGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 3);
    private static final BlockPos START = new BlockPos(1, 1, 10);

    private EscortGameTests() {
    }

    /** The origin village of the test, with a heart. */
    private static Settlement village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        return Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
    }

    /** A waiting caravan of the village on a route along the arena: merchant, mule, guard, payment 20. */
    private static MobileGroup caravan(GameTestHelper helper, Settlement origin) {
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + 14, start.getZ())));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, origin.factionId(),
                origin.id(), "test_destination", route);
        group.setPayment(20);
        group.cargo().set("minecraft:bread", 20);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        MobileGroupData.get(helper.getLevel().getServer()).groups().add(group);
        return group;
    }

    /** A neutral player with an accepted escort of a caravan. */
    private static Quest acceptedEscort(GameTestHelper helper, Settlement origin, MobileGroup caravan,
            ServerPlayer player) {
        MinecraftServer server = helper.getLevel().getServer();
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(origin.factionId(), 0);
        reputation.get(player.getUUID()).setVillage(origin.id(), 0);
        Quest quest = Escorts.offer(server, caravan, origin, 100).orElseThrow();
        QuestBoards.accept(player, quest.id());
        return quest;
    }

    private static void clear(GameTestHelper helper, MobileGroup caravan, Quest quest) {
        MinecraftServer server = helper.getLevel().getServer();
        Quests quests = QuestData.get(server).quests();
        quests.withdraw(quest.id());
        quests.finish(quest.id(), QuestState.FAILED);
        quests.removeFinished();
        GroupMaterializer.keep(caravan.id(), false);
        GroupMaterializer.dematerialize(helper.getLevel(), caravan, false);
        MobileGroupData.get(server).groups().remove(caravan.id());
    }

    /** A new caravan offers an escort quest and waits at its village. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_offer")
    public static void newCaravanOffersAnEscort(GameTestHelper helper) {
        Settlement origin = village(helper);
        MobileGroup caravan = caravan(helper, origin);

        Quest quest = Escorts.offer(helper.getLevel().getServer(), caravan, origin, 100).orElse(null);

        long departure = caravan.departureTick();
        QuestState state = quest == null ? null : quest.state();
        clear(helper, caravan, quest);
        helper.assertTrue(quest != null && quest.kind() == QuestKind.ESCORT && quest.target().equals(caravan.id())
                && quest.settlementId().equals(origin.id()) && state == QuestState.OFFERED,
                "The village must offer an escort of its caravan");
        helper.assertTrue(departure == 100 + WorldConfig.QUEST_ESCORT_WAIT_TICKS.get(),
                "The caravan must wait for an escort: " + departure);
        helper.assertTrue(quest.rewardEmeralds() == Math.max(1,
                Math.round(20 * WorldConfig.QUEST_ESCORT_REWARD_SHARE.get())), "The reward comes from the cargo");
        helper.succeed();
    }

    /** An accepted escort: the caravan leaves at once and keeps its entities. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_accept")
    public static void acceptedEscortStartsTheCaravan(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement origin = village(helper);
        MobileGroup caravan = caravan(helper, origin);
        ServerPlayer player = TestSupport.mockPlayer(helper);

        Quest quest = acceptedEscort(helper, origin, caravan, player);

        boolean accepted = quest.state() == QuestState.ACCEPTED;
        boolean traveling = caravan.state() == GroupState.TRAVELING;
        boolean kept = GroupMaterializer.isKept(caravan.id());
        server.getPlayerList().remove(player);
        clear(helper, caravan, quest);
        helper.assertTrue(accepted, "The player must accept the escort");
        helper.assertTrue(traveling && kept, "The caravan must leave and keep its entities");
        helper.succeed();
    }

    /** With the player far away, the caravan stops. After the delay, the quest fails and the caravan continues. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_away")
    public static void caravanStopsAndTheQuestFailsWhenThePlayerIsAway(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement origin = village(helper);
        MobileGroup caravan = caravan(helper, origin);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedEscort(helper, origin, caravan, player);
        BlockPos far = helper.absolutePos(START).offset(1000, 0, 0);
        player.teleportTo(far.getX(), far.getY(), far.getZ());
        long now = helper.getLevel().getGameTime();

        Escorts.update(server, now);
        GroupState stopped = caravan.state();
        QuestState stillActive = quest.state();
        Escorts.update(server, now + WorldConfig.QUEST_ESCORT_AWAY_SECONDS.get() * 20L + 20);
        QuestState failed = quest.state();
        GroupState after = caravan.state();
        boolean kept = GroupMaterializer.isKept(caravan.id());

        server.getPlayerList().remove(player);
        clear(helper, caravan, quest);
        helper.assertTrue(stopped == GroupState.STOPPED && stillActive == QuestState.ACCEPTED,
                "The caravan must stop and wait for the player");
        helper.assertTrue(failed == QuestState.FAILED, "The quest must fail after the delay");
        helper.assertTrue(after == GroupState.TRAVELING && !kept, "The caravan must continue with no escort");
        helper.succeed();
    }

    /**
     * The quest HUD gets the state of the escort: the progress on the route, the escort distance, and the time to
     * come back when the player is too far.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_hud")
    public static void questLogGivesTheStateOfTheEscort(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement origin = village(helper);
        MobileGroup caravan = caravan(helper, origin);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedEscort(helper, origin, caravan, player);

        QuestLogPayload.Escort near = escortOf(QuestBoards.log(player), quest);
        boolean target = QuestBoards.log(player).quests().stream()
                .filter(entry -> entry.questId().equals(quest.id()))
                .allMatch(entry -> entry.target().isPresent());
        BlockPos far = helper.absolutePos(START).offset(1000, 0, 0);
        player.teleportTo(far.getX(), far.getY(), far.getZ());
        Escorts.update(server, helper.getLevel().getGameTime());
        QuestLogPayload.Escort away = escortOf(QuestBoards.log(player), quest);

        server.getPlayerList().remove(player);
        clear(helper, caravan, quest);
        helper.assertTrue(near != null && near.length() == 14 && !near.away()
                && near.maxDistance() == WorldConfig.QUEST_ESCORT_DISTANCE.get(),
                "The log must give the route and the escort distance");
        helper.assertTrue(away != null && away.away() && away.distance() > away.maxDistance()
                && away.awaySeconds() == WorldConfig.QUEST_ESCORT_AWAY_SECONDS.get(),
                "The log must give the distance and the time to come back");
        helper.assertTrue(target, "The log must give the position of the caravan as the target");
        helper.succeed();
    }

    private static QuestLogPayload.Escort escortOf(QuestLogPayload log, Quest quest) {
        return log.quests().stream().filter(entry -> entry.questId().equals(quest.id())).findFirst()
                .flatMap(QuestLogPayload.Entry::escort).orElse(null);
    }

    /** The caravan arrives with its merchant alive: the escort is complete and the player gets the reward. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_success")
    public static void arrivalCompletesTheEscort(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement origin = village(helper);
        SettlementData settlements = SettlementData.get(server);
        long treasury = settlements.treasury(origin.factionId());
        MobileGroup caravan = caravan(helper, origin);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.getInventory().clearContent();
        Quest quest = acceptedEscort(helper, origin, caravan, player);
        BlockPos near = helper.absolutePos(START);
        player.teleportTo(near.getX(), near.getY(), near.getZ());

        caravan.setState(GroupState.ARRIVED);
        Escorts.update(server, helper.getLevel().getGameTime());

        QuestState state = quest.state();
        int emeralds = Deliveries.count(player.getInventory(), Items.EMERALD);
        server.getPlayerList().remove(player);
        settlements.addTreasury(origin.factionId(), treasury - settlements.treasury(origin.factionId()));
        clear(helper, caravan, quest);
        helper.assertTrue(state == QuestState.COMPLETED, "The escort must be complete: " + state);
        helper.assertTrue(emeralds == quest.rewardEmeralds(), "The player must get the reward: " + emeralds);
        helper.succeed();
    }

    /** The player of an escort raids the caravan: the escort fails, and the treasury pays nothing. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_raid")
    public static void raidFailsTheEscort(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement origin = village(helper);
        SettlementData settlements = SettlementData.get(server);
        long treasury = settlements.treasury(origin.factionId());
        MobileGroup caravan = caravan(helper, origin);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedEscort(helper, origin, caravan, player);

        CaravanInteractions.raid(helper.getLevel(), player, caravan);

        QuestState state = quest.state();
        long paid = treasury - settlements.treasury(origin.factionId());
        server.getPlayerList().remove(player);
        settlements.addTreasury(origin.factionId(), treasury - settlements.treasury(origin.factionId()));
        clear(helper, caravan, quest);
        helper.assertTrue(state == QuestState.FAILED, "The escort must fail: " + state);
        helper.assertTrue(paid == 0, "The treasury must pay nothing: " + paid);
        helper.succeed();
    }

    /** An ambush of monsters appears in front of the caravan and attacks the merchant. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "escort_ambush")
    public static void ambushAttacksTheMerchant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Settlement origin = village(helper);
        MobileGroup caravan = caravan(helper, origin);
        caravan.setState(GroupState.STOPPED);
        GroupMaterializer.keep(caravan.id(), true);
        GroupMaterializer.materialize(level, caravan);
        Entity merchant = level.getEntity(caravan.merchant().orElseThrow().id());

        List<Mob> monsters = Escorts.spawnAmbush(level, caravan, 3);

        boolean attack = !monsters.isEmpty() && monsters.stream().allMatch(mob -> mob.getTarget() == merchant
                && mob.getPersistentData().getBoolean(Escorts.AMBUSH_TAG));
        int count = monsters.size();
        monsters.forEach(Entity::discard);
        Quest none = new Quest(UUID.randomUUID().toString(), QuestKind.ESCORT, origin.id(), origin.factionId(),
                caravan.id(), 0, 1, 1, ReputationTier.NEUTRAL, 0, 0);
        Deque<Double> plan = Escorts.plan(none, caravan);
        clear(helper, caravan, none);
        helper.assertTrue(count == 3 && attack, "3 monsters must attack the merchant: " + count);
        helper.assertTrue(plan.size() >= 1 && plan.size() <= 3, "An escort has 1 to 3 ambushes: " + plan.size());
        helper.assertTrue(Escorts.ambushSize(400, 1) == 2 && Escorts.ambushSize(1600, 10) == 7
                && Escorts.ambushSize(10_000, 20) == 8, "The size of an ambush follows the route and the level");
        helper.succeed();
    }

    /** The time of an escort comes from the length of its route, with the configured time as the minimum. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "escort_time")
    public static void escortTimeFollowsTheRouteLength(GameTestHelper helper) {
        long minimum = WorldConfig.QUEST_ESCORT_MINUTES.get();
        long shortRoute = Escorts.escortMinutes(100);
        long longRoute = Escorts.escortMinutes(20_000);
        long expected = (long) Math.ceil(20_000.0 / WorldConfig.QUEST_ESCORT_BLOCKS_PER_MINUTE.get()) + 10;

        helper.assertTrue(shortRoute == minimum, "A short route must give the minimum time: " + shortRoute);
        helper.assertTrue(longRoute == Math.max(minimum, expected),
                "A long route must give more time: " + longRoute + " minutes");
        helper.succeed();
    }
}
