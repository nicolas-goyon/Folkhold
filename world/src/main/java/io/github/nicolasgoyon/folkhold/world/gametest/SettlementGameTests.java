package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

        helper.assertTrue(heart.getVillageId().toString().equals(discovered.id()),
                "The heart must get the id of the settlement");
        helper.assertTrue(faction.id().equals(heart.getFactionId()), "The heart must get the faction of the settlement");
        helper.assertTrue("Discoveredton".equals(heart.getVillageName()), "The heart must get the name of the settlement");
        helper.assertTrue(discovered.hasHeart(), "The settlement must know its heart");
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
        helper.assertTrue(heart.getVillageId().equals(heartId), "The heart must keep its id");
        helper.assertTrue(data.registry().byId(discoveredId).isEmpty(), "The discovered id must not exist any more");
        helper.assertTrue(data.registry().byId(heartId.toString()).orElse(null) == discovered,
                "The discovered settlement must get the id of the heart");
        helper.assertTrue(discovered.factionId().equals(heartFaction.id()) && "Oldbury".equals(discovered.name()),
                "The settlement must get the faction and the name of the heart");
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
