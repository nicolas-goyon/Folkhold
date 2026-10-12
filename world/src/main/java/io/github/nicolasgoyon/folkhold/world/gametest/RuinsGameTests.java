package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the destruction of a village. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuinsGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private RuinsGameTests() {
    }

    private record Village(Settlement settlement, CaptainEntity captain) {
    }

    private static Village village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        CaptainEntity captain = TestSupport.spawnUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        captain.setVillagePos(helper.absolutePos(HEART));
        return new Village(settlement, captain);
    }

    private static int claims(GameTestHelper helper, Settlement settlement) {
        return SettlementData.get(helper.getLevel().getServer()).claims().cellCount(settlement.id());
    }

    /** Kill the captain, then destroy the heart: the village becomes ruins and claims nothing. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_captain_first")
    public static void captainThenHeartMakesRuins(GameTestHelper helper) {
        Village village = village(helper);

        village.captain().kill();
        boolean activeAfterCaptain = village.settlement().isActive();
        helper.setBlock(HEART, Blocks.AIR);

        helper.assertTrue(activeAfterCaptain, "The village must stay active while its heart exists");
        helper.assertTrue(village.settlement().state() == SettlementState.RUINS, "The village must be ruins");
        helper.assertTrue(claims(helper, village.settlement()) == 0, "Ruins must claim no cell");
        helper.succeed();
    }

    /** Destroy the heart, then kill the captain: the village becomes ruins when the captain dies. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_heart_first")
    public static void heartThenCaptainMakesRuins(GameTestHelper helper) {
        Village village = village(helper);

        helper.setBlock(HEART, Blocks.AIR);
        boolean activeAfterHeart = village.settlement().isActive();
        village.captain().kill();

        helper.assertTrue(activeAfterHeart, "The village must stay active while its captain lives");
        helper.assertTrue(village.settlement().state() == SettlementState.RUINS, "The village must be ruins");
        helper.succeed();
    }
}
