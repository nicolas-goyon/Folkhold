package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.TerritoryMessages;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Territories;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the territory claims. The tests add settlements far from the test area and far from each other,
 * so the settlements of the other tests do not claim the same chunks.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerritoryGameTests {
    /** Grid of the test positions in blocks. */
    private static final int GRID = 256;
    /** The 8 directions around a position, as unit vectors. */
    private static final double[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7071, 0.7071},
        {-0.7071, 0.7071}, {0.7071, -0.7071}, {-0.7071, -0.7071}};

    private TerritoryGameTests() {
    }

    /** A settlement with no heart at a block position. */
    private static Settlement settlement(MinecraftServer server, int x, int z, int level) {
        FactionSpec faction = FactionData.get(server).factions().get(0);
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, z >> 4, faction.id(), "Farhold",
                x, z);
        settlement.setProgress(level, 0);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** A settlement with no heart at a position of the test grid, not at the center of its chunk. */
    private static Settlement farSettlement(MinecraftServer server, int gridX, int gridZ, int level) {
        return settlement(server, gridX * GRID + 3, gridZ * GRID + 13, level);
    }

    /** The claim radius of a settlement in blocks. */
    private static int reach(int level) {
        int minimum = TerritoryClaims.minimumRadius(WorldConfig.VILLAGE_RADIUS.get());
        return Math.max(minimum, LevelTables.get().traits(level).claimRadius()) * 16;
    }

    /** The block at a distance from a settlement, in a direction. */
    private static BlockPos at(Settlement settlement, double[] direction, double distance) {
        return new BlockPos((int) Math.floor(settlement.x() + 0.5 + direction[0] * distance), 64,
                (int) Math.floor(settlement.z() + 0.5 + direction[1] * distance));
    }

    /**
     * The territory of a hamlet is centered on the hamlet: the same reach in the 8 directions. It contains the
     * village and its margin (1.5 times the village radius).
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_hamlet")
    public static void hamletTerritoryIsCenteredOnTheHamlet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement hamlet = farSettlement(server, 400, 400, 1);
        int reach = reach(1);
        double margin = TerritoryClaims.VILLAGE_MARGIN * WorldConfig.VILLAGE_RADIUS.get();

        for (double[] direction : DIRECTIONS) {
            BlockPos village = at(hamlet, direction, margin);
            BlockPos inside = at(hamlet, direction, reach - 12);
            BlockPos outside = at(hamlet, direction, reach + 12);
            helper.assertTrue(Territories.settlementAt(server, village).orElse(null) == hamlet,
                    "The territory must contain the village and its margin: " + village);
            helper.assertTrue(Territories.settlementAt(server, inside).orElse(null) == hamlet,
                    "The territory must reach " + (reach - 12) + " blocks in each direction: " + inside);
            helper.assertTrue(Territories.factionAt(server, inside).map(FactionSpec::id).orElse("")
                    .equals(hamlet.factionId()), "The territory must belong to the faction of the hamlet");
            helper.assertTrue(Territories.settlementAt(server, outside).isEmpty(),
                    "The territory must stop near " + reach + " blocks: " + outside);
        }
        helper.succeed();
    }

    /** When the level changes, the claims change: a city claims a larger disc. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_level")
    public static void claimsFollowTheLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = farSettlement(server, -400, 400, 1);
        BlockPos cityBorder = at(settlement, DIRECTIONS[0], reach(13) - 12);
        int before = SettlementData.get(server).claims().chunkCount(settlement.id());
        boolean hamletReaches = Territories.settlementAt(server, cityBorder).isPresent();

        SettlementLevels.setProgress(server, settlement, 13, 0);
        int after = SettlementData.get(server).claims().chunkCount(settlement.id());
        boolean cityReaches = Territories.settlementAt(server, cityBorder).orElse(null) == settlement;

        helper.assertTrue(before > 0 && after > before, "A city must claim more chunks: " + before + ", " + after);
        helper.assertTrue(!hamletReaches && cityReaches, "The territory must grow with the level");
        helper.succeed();
    }

    /** Two villages near each other share the chunks between them: each chunk goes to the nearest village. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_shared")
    public static void neighborsShareTheChunksByDistance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement west = farSettlement(server, 400, -420, 1);
        Settlement east = settlement(server, west.x() + 200, west.z(), 1);

        helper.assertTrue(Territories.settlementAt(server, at(west, DIRECTIONS[0], 60)).orElse(null) == west,
                "A chunk near the west village must belong to it");
        helper.assertTrue(Territories.settlementAt(server, at(east, DIRECTIONS[1], 60)).orElse(null) == east,
                "A chunk near the east village must belong to it");
        helper.assertTrue(Territories.settlementAt(server, at(west, DIRECTIONS[1], 120)).orElse(null) == west,
                "The far side of the west village must belong to it");
        helper.succeed();
    }

    /** A settlement that is not active claims nothing. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_ruins")
    public static void ruinsClaimNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = farSettlement(server, 400, -400, 5);
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(server).setDirty();

        helper.assertTrue(SettlementData.get(server).claims().chunkCount(settlement.id()) == 0,
                "A settlement in ruins must claim no chunk");
        helper.succeed();
    }

    /**
     * The territory HUD shows the nearest active village of the faction of the territory, also a village with no
     * heart. Ruins do not count.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_nearest")
    public static void nearestVillageOfTheFaction(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement near = farSettlement(server, 430, 430, 1);
        Settlement far = farSettlement(server, 433, 430, 1);
        BlockPos player = new BlockPos(near.x() + 50, 64, near.z());

        Settlement first = TerritoryMessages.nearestOfFaction(server, near.factionId(), player).orElse(null);
        near.setState(SettlementState.RUINS);
        Settlement second = TerritoryMessages.nearestOfFaction(server, near.factionId(), player).orElse(null);
        far.setState(SettlementState.RUINS);
        SettlementData.get(server).setDirty();

        helper.assertTrue(first == near, "The nearest village must be the near village");
        helper.assertTrue(second == far, "A village in ruins must not count");
        helper.succeed();
    }
}
