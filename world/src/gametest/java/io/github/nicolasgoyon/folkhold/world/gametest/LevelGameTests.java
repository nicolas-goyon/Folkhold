package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevelChangedEvent;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the level of the settlements. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LevelGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** Level changes that the tests saw, as "id:old>new". */
    private static final List<String> LEVEL_CHANGES = new ArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((SettlementLevelChangedEvent event) -> LEVEL_CHANGES.add(
                event.getSettlement().id() + ":" + event.getOldLevel() + ">" + event.getNewLevel()));
    }

    private LevelGameTests() {
    }

    private static Settlement settlementOfNewHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        return Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
    }

    /** A new settlement gets the start level of its id. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "level_start")
    public static void newSettlementGetsTheStartLevelOfItsId(GameTestHelper helper) {
        Settlement settlement = settlementOfNewHeart(helper);
        int expected = Settlements.startLevel(settlement.id());
        helper.assertTrue(settlement.level() == expected,
                "Expected the start level " + expected + ", not " + settlement.level());
        helper.assertTrue(settlement.level() <= 16, "A start level must not be a Capital level");
        helper.succeed();
    }

    /** The level system gives experience once each cycle, not at the other abstract ticks. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "level_cycle")
    public static void levelSystemGivesExperienceOnlyOnACycle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlementOfNewHeart(helper);
        SettlementLevels.setProgress(server, settlement, 1, 0);

        SettlementLevels.tick(server, settlement, new TickInfo(1, false));
        int afterTick = settlement.experience();
        SettlementLevels.tick(server, settlement, new TickInfo(10, true));
        int expected = (int) Math.round(SettlementLevels.prosperity(server, settlement)
                * WorldConfig.LEVEL_MAX_GAIN_PER_CYCLE.get());

        helper.assertTrue(afterTick == 0, "An abstract tick that is not a cycle must not give experience");
        helper.assertTrue(settlement.level() == 1 && settlement.experience() == expected,
                "Expected level 1 with " + expected + " experience, not " + settlement.level() + " with "
                        + settlement.experience());
        helper.succeed();
    }

    /** A change of level posts an event. A loss removes at most one level. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "level_event")
    public static void levelChangePostsAnEvent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LevelTable table = LevelTables.get();
        Settlement settlement = settlementOfNewHeart(helper);
        SettlementLevels.setProgress(server, settlement, 4, 0);
        LEVEL_CHANGES.removeIf(change -> change.startsWith(settlement.id()));

        SettlementLevels.addExperience(server, settlement, table.traits(4).experience());
        SettlementLevels.addExperience(server, settlement, -100_000);

        List<String> changes = LEVEL_CHANGES.stream().filter(change -> change.startsWith(settlement.id())).toList();
        helper.assertTrue(changes.equals(List.of(settlement.id() + ":4>5", settlement.id() + ":5>4")),
                "Expected the changes 4>5 then 5>4, not " + changes);
        helper.assertTrue(settlement.level() == 4 && settlement.experience() == 0,
                "Expected level 4 with 0 experience, not " + settlement.level() + " with " + settlement.experience());
        helper.succeed();
    }

    /** The death of the captain removes experience from its settlement. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "level_captain")
    public static void captainDeathRemovesExperience(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlementOfNewHeart(helper);
        int loss = WorldConfig.LEVEL_CAPTAIN_DEATH_LOSS.get();
        SettlementLevels.setProgress(server, settlement, 3, loss + 7);
        CaptainEntity captain = TestSupport.spawnUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        captain.setVillagePos(helper.absolutePos(HEART));

        captain.kill();

        helper.assertTrue(settlement.level() == 3 && settlement.experience() == 7,
                "Expected level 3 with 7 experience, not " + settlement.level() + " with " + settlement.experience());
        helper.succeed();
    }
}
