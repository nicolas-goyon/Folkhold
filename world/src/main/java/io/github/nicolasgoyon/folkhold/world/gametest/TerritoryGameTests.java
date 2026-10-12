package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
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
 * In-game tests for the territory claims. The tests add settlements far from the test area, in the center of a
 * territory cell, so the settlements of the other tests do not claim the same cells.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerritoryGameTests {
    private static final int CELL = TerritoryClaims.CELL_BLOCKS;

    private TerritoryGameTests() {
    }

    /** A settlement with no heart at the center of the territory cell (cellX, cellZ). */
    private static Settlement farSettlement(MinecraftServer server, int cellX, int cellZ, int level) {
        FactionSpec faction = FactionData.get(server).factions().get(0);
        int x = cellX * CELL + CELL / 2;
        int z = cellZ * CELL + CELL / 2;
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, z >> 4, faction.id(), "Farhold",
                x, z);
        settlement.setProgress(level, 0);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** A hamlet claims its cell. The next cell is unclaimed. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_hamlet")
    public static void hamletClaimsItsCell(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement hamlet = farSettlement(server, 400, 400, 1);
        BlockPos inside = new BlockPos(hamlet.x() + 100, 64, hamlet.z() - 100);
        BlockPos outside = new BlockPos(hamlet.x() + CELL, 64, hamlet.z());

        helper.assertTrue(Territories.settlementAt(server, inside).orElse(null) == hamlet,
                "The cell of the hamlet must belong to the hamlet");
        helper.assertTrue(Territories.factionAt(server, inside).map(FactionSpec::id).orElse("")
                .equals(hamlet.factionId()), "The cell of the hamlet must belong to its faction");
        helper.assertTrue(Territories.settlementAt(server, outside).isEmpty(), "The next cell must be unclaimed");
        helper.succeed();
    }

    /** When the level changes, the claims change. A city claims 9 cells. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_level")
    public static void claimsFollowTheLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = farSettlement(server, -400, 400, 1);
        int before = SettlementData.get(server).claims().cellCount(settlement.id());

        SettlementLevels.setProgress(server, settlement, 13, 0);
        int after = SettlementData.get(server).claims().cellCount(settlement.id());
        int expected = LevelTables.get().traits(13).claimCells();

        helper.assertTrue(before == 1, "A hamlet must claim 1 cell, not " + before);
        helper.assertTrue(after == expected, "A city must claim " + expected + " cells, not " + after);
        helper.succeed();
    }

    /** A settlement that is not active claims nothing. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "territory_ruins")
    public static void ruinsClaimNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = farSettlement(server, 400, -400, 5);
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(server).setDirty();

        helper.assertTrue(SettlementData.get(server).claims().cellCount(settlement.id()) == 0,
                "A settlement in ruins must claim no cell");
        helper.succeed();
    }
}
