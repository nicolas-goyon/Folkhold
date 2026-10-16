package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the link between the village hearts and the settlements. See {@link TestSupport} for the
 * templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private SettlementGameTests() {
    }

    /** A settlement as the discovery makes it: start chunk of the heart, no heart yet. */
    private static Settlement discover(GameTestHelper helper, FactionSpec faction, String name) {
        BlockPos pos = helper.absolutePos(HEART);
        ChunkPos chunk = new ChunkPos(pos);
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), chunk.x, chunk.z, faction.id(), name,
                chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /**
     * Makes a test settlement inactive, so it claims no territory. A settlement of another faction near the test
     * area changes the chase area of the units in the other tests.
     */
    private static void retire(GameTestHelper helper, Settlement settlement) {
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(helper.getLevel().getServer()).setDirty();
    }

    /** A faction that is not the owner of the heart position, so the test can see which faction the heart gets. */
    private static FactionSpec otherFaction(GameTestHelper helper) {
        FactionData factions = FactionData.get(helper.getLevel().getServer());
        String owner = factions.ownerAt(helper.absolutePos(HEART)).id();
        List<FactionSpec> all = factions.factions();
        return all.stream().filter(faction -> !faction.id().equals(owner)).findFirst().orElse(all.get(0));
    }

    /** A new heart with no settlement makes its settlement, with the same id, faction and name. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "settlement_new")
    public static void newHeartCreatesItsSettlement(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);

        heart.ensureSettlement();

        BlockPos pos = helper.absolutePos(HEART);
        Settlement settlement = SettlementData.get(helper.getLevel().getServer()).registry()
                .byId(heart.getVillageId().toString()).orElse(null);
        helper.assertTrue(settlement != null, "The heart must have a settlement with its id");
        helper.assertTrue(settlement.factionId().equals(heart.getFactionId()),
                "The settlement must have the faction of the heart");
        helper.assertTrue(settlement.name().equals(heart.getVillageName()),
                "The settlement must have the name of the heart");
        helper.assertTrue(settlement.hasHeart() && settlement.x() == pos.getX() && settlement.heartY() == pos.getY()
                && settlement.z() == pos.getZ(), "The settlement must have the position of the heart");
        helper.assertTrue(settlement.economy().isInitialized() && settlement.economy().population() > 0,
                "The economy of a new settlement must start at once");
        helper.succeed();
    }

    /** A new heart in a discovered settlement gets the id, the faction and the name of the settlement. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "settlement_discovered")
    public static void heartTakesTheDiscoveredSettlement(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        FactionSpec faction = otherFaction(helper);
        Settlement discovered = discover(helper, faction, "Discoveredton");
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);

        heart.ensureSettlement();
        boolean sameId = heart.getVillageId().toString().equals(discovered.id());
        boolean sameFaction = faction.id().equals(heart.getFactionId());
        boolean sameName = "Discoveredton".equals(heart.getVillageName());
        boolean knowsHeart = discovered.hasHeart();
        retire(helper, discovered);

        helper.assertTrue(sameId, "The heart must get the id of the settlement");
        helper.assertTrue(sameFaction, "The heart must get the faction of the settlement");
        helper.assertTrue(sameName, "The heart must get the name of the settlement");
        helper.assertTrue(knowsHeart, "The settlement must know its heart");
        helper.succeed();
    }

    /**
     * A heart that had a faction before the discovery (a village of an older world) keeps its id, its faction and
     * its name. The settlement takes them, because the reputations use the id of the heart.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "settlement_older_heart")
    public static void olderHeartKeepsItsIdentity(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        FactionSpec heartFaction = otherFaction(helper);
        UUID heartId = heart.getVillageId();
        heart.adoptSettlement(heartId, heartFaction.id(), "Oldbury");
        FactionSpec ownerFaction = FactionData.get(helper.getLevel().getServer()).ownerAt(helper.absolutePos(HEART));
        Settlement discovered = discover(helper, ownerFaction, "Newbury");
        String discoveredId = discovered.id();

        heart.ensureSettlement();

        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        boolean keepsId = heart.getVillageId().equals(heartId);
        boolean oldIdGone = data.registry().byId(discoveredId).isEmpty();
        boolean newId = data.registry().byId(heartId.toString()).orElse(null) == discovered;
        boolean identity = discovered.factionId().equals(heartFaction.id()) && "Oldbury".equals(discovered.name());
        retire(helper, discovered);

        helper.assertTrue(keepsId, "The heart must keep its id");
        helper.assertTrue(oldIdGone, "The discovered id must not exist any more");
        helper.assertTrue(newId, "The discovered settlement must get the id of the heart");
        helper.assertTrue(identity, "The settlement must get the faction and the name of the heart");
        helper.succeed();
    }

    /**
     * When the settlement takes the id of the heart, the groups, the routes and the quests that refer to the
     * settlement take the new id too, so a caravan toward the settlement still arrives.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "settlement_id_change")
    public static void idChangeFollowsTheGroupsRoutesAndQuests(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        MinecraftServer server = helper.getLevel().getServer();
        FactionSpec heartFaction = otherFaction(helper);
        String heartId = heart.getVillageId().toString();
        heart.adoptSettlement(heart.getVillageId(), heartFaction.id(), "Oldbury");
        Settlement discovered = discover(helper, FactionData.get(server).ownerAt(helper.absolutePos(HEART)),
                "Newbury");
        String discoveredId = discovered.id();
        String originId = "test_origin_" + UUID.randomUUID();
        MobileGroupData groups = MobileGroupData.get(server);
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(discovered.x(), discovered.z())));
        MobileGroup caravan = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, heartFaction.id(),
                originId, discoveredId, route);
        groups.groups().add(caravan);
        groups.putRoute(new MobileGroupData.RouteEntry(originId, discoveredId, 0, 0, discovered.x(), discovered.z(),
                Optional.of(route)));
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, discoveredId, heartFaction.id(),
                "minecraft:bread", 4, 2, 10, ReputationTier.NEUTRAL, helper.getLevel().getGameTime() + 1000, 1000);
        QuestData.get(server).quests().add(quest);

        heart.ensureSettlement();

        boolean newId = discovered.id().equals(heartId);
        boolean groupFollows = caravan.destinationId().equals(heartId)
                && groups.groups().bySettlement(heartId).contains(caravan);
        boolean routeFollows = groups.routeEntry(originId, heartId) != null
                && groups.routeEntry(originId, discoveredId) == null;
        boolean questFollows = quest.settlementId().equals(heartId)
                && QuestData.get(server).quests().offers(heartId).contains(quest);
        groups.groups().remove(caravan.id());
        retire(helper, discovered);

        helper.assertTrue(newId, "The discovered settlement must get the id of the heart");
        helper.assertTrue(groupFollows, "The caravan must go to the new id, found " + caravan.destinationId());
        helper.assertTrue(routeFollows, "The route must use the new id");
        helper.assertTrue(questFollows, "The quest must use the new id, found " + quest.settlementId());
        helper.succeed();
    }

    /** The start chunk of a heart outside a village structure is the chunk of the heart. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "settlement_start_chunk")
    public static void startChunkOutsideAStructureIsTheChunkOfThePosition(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(HEART);
        ChunkPos start = Settlements.startChunk(helper.getLevel(), pos);
        helper.assertTrue(start.equals(new ChunkPos(pos)), "Expected the chunk " + new ChunkPos(pos) + ", not " + start);
        helper.succeed();
    }
}
