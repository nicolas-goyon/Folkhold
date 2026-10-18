package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.RegionPos;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the actions of the admin screen. See {@link TestSupport} for the templates and the helpers.
 *
 * <p>Each test makes a village heart and its settlement, does the actions, and puts the settlement in ruins at the
 * end, so it claims no territory for the next tests.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdminGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private AdminGameTests() {
    }

    private static VillageHeartBlockEntity newHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        return heart;
    }

    private static Settlement settlementOf(GameTestHelper helper, VillageHeartBlockEntity heart) {
        return Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
    }

    private static Optional<AdminActions.Result> act(GameTestHelper helper, String action, Settlement settlement,
            int value) {
        return AdminActions.perform(helper.getLevel().getServer(), null,
                new AdminActionPayload(action, settlement.id(), value));
    }

    private static void retire(GameTestHelper helper, Settlement settlement) {
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(helper.getLevel().getServer()).setDirty();
    }

    /**
     * The list contains each settlement, with its level and its distance. The details describe it: the level, the
     * state of the loaded heart, the position and the sections.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_list")
    public static void listAndDetailsDescribeTheSettlement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlementOf(helper, newHeart(helper));

        AdminListPayload list = AdminActions.list(server, helper.absolutePos(HEART));
        Optional<AdminListPayload.Summary> summary = list.settlements().stream()
                .filter(entry -> entry.id().equals(settlement.id()))
                .findFirst();
        Optional<AdminDetailsPayload> details = AdminActions.details(server, settlement.id());

        helper.assertTrue(summary.isPresent(), "The list must contain the settlement");
        helper.assertTrue(summary.get().level() == settlement.level(), "The summary must give the level");
        helper.assertTrue(summary.get().distance() <= 16, "The settlement must be near the player, not at "
                + summary.get().distance());
        helper.assertTrue(details.isPresent() && details.get().sections().size() >= 2,
                "The details of the settlement must have the village and economy sections");
        helper.assertTrue(details.get().level() == settlement.level() && details.get().active(),
                "The details must give the level and the state");
        helper.assertTrue(details.get().heartLoaded() && details.get().alert() == AlertState.CALM.index(),
                "The details must give the alert state of the loaded heart");
        helper.assertTrue(details.get().hasY() && details.get().x() == helper.absolutePos(HEART).getX(),
                "The details must give the position of the heart");
        helper.assertTrue(AdminActions.details(server, "unknown-settlement").isEmpty(),
                "An unknown settlement must have no details");
        retire(helper, settlement);
        helper.succeed();
    }

    /** The level actions change the level and the experience of the settlement. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_level")
    public static void levelActionsChangeTheLevel(GameTestHelper helper) {
        Settlement settlement = settlementOf(helper, newHeart(helper));
        int target = LevelTables.get().clamp(5);

        act(helper, "level_set", settlement, target);
        boolean levelSet = settlement.level() == target && settlement.experience() == 0;
        act(helper, "experience_add", settlement, 1);
        boolean experienceAdded = settlement.experience() == 1 || settlement.level() > target;
        act(helper, "level_set", settlement, 10_000);

        helper.assertTrue(levelSet, "Expected level " + target + " with 0 experience");
        helper.assertTrue(experienceAdded, "The experience must increase");
        helper.assertTrue(settlement.level() == LevelTables.get().clamp(10_000),
                "A level above the maximum must give the maximum level, not " + settlement.level());
        retire(helper, settlement);
        helper.succeed();
    }

    /**
     * The ruin and restore actions change the state of the settlement. The ruin of ruins and the restore of an active
     * settlement are refused.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_state")
    public static void ruinAndRestoreChangeTheState(GameTestHelper helper) {
        Settlement settlement = settlementOf(helper, newHeart(helper));

        boolean restoreRefused = act(helper, "restore", settlement, 0).map(result -> !result.success()).orElse(false);
        act(helper, "ruin", settlement, 0);
        boolean ruined = settlement.state() == SettlementState.RUINS;
        boolean ruinRefused = act(helper, "ruin", settlement, 0).map(result -> !result.success()).orElse(false);
        act(helper, "restore", settlement, 0);

        helper.assertTrue(restoreRefused, "The restore of an active settlement must be refused");
        helper.assertTrue(ruined, "The ruin action must put the settlement in ruins");
        helper.assertTrue(ruinRefused, "The ruin of ruins must be refused");
        helper.assertTrue(settlement.isActive(), "The restore action must make the settlement active");
        retire(helper, settlement);
        helper.succeed();
    }

    /** The economy reset removes the stock and starts the economy again. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_economy")
    public static void economyResetStartsTheEconomyAgain(GameTestHelper helper) {
        Settlement settlement = settlementOf(helper, newHeart(helper));
        settlement.economy().stock().set("minecraft:dragon_egg", 7);

        act(helper, "economy_reset", settlement, 0);

        helper.assertTrue(settlement.economy().stock().count("minecraft:dragon_egg") == 0,
                "The reset must remove the old stock");
        helper.assertTrue(settlement.economy().isInitialized() == EconomyData.isActive(),
                "The reset must start the economy again when the job data is active");
        retire(helper, settlement);
        helper.succeed();
    }

    /** The alert action changes the alert state of a loaded heart. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_alert")
    public static void alertActionChangesTheAlertState(GameTestHelper helper) {
        VillageHeartBlockEntity heart = newHeart(helper);
        Settlement settlement = settlementOf(helper, heart);

        act(helper, "alert", settlement, AlertState.ASSAULT.index());
        AlertState assault = heart.getAlertState();
        act(helper, "alert", settlement, AlertState.CALM.index());

        helper.assertTrue(assault == AlertState.ASSAULT, "Expected the assault state, not " + assault);
        helper.assertTrue(heart.getAlertState() == AlertState.CALM, "Expected the calm state");
        retire(helper, settlement);
        helper.succeed();
    }

    /** The teleport target is a free position on the floor near the heart, not the bottom of the world. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_teleport")
    public static void teleportTargetIsAFreePositionNearTheHeart(GameTestHelper helper) {
        Settlement settlement = settlementOf(helper, newHeart(helper));
        BlockPos heart = helper.absolutePos(HEART);

        BlockPos target = AdminActions.teleportTarget(helper.getLevel(), settlement);

        helper.assertTrue(target.getY() == heart.getY(), "The target must be at the level of the heart, not at "
                + target);
        helper.assertTrue(Math.abs(target.getX() - heart.getX()) <= 8 && Math.abs(target.getZ() - heart.getZ()) <= 8,
                "The target must be near the heart, not at " + target);
        helper.assertTrue(!target.equals(heart) && helper.getLevel().getBlockState(target).isAir()
                && helper.getLevel().getBlockState(target.above()).isAir(), "The target must be free, not " + target);
        retire(helper, settlement);
        helper.succeed();
    }

    /** An action on an unknown settlement does nothing and gives a refusal. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "admin_unknown")
    public static void unknownSettlementGivesAMessage(GameTestHelper helper) {
        Optional<AdminActions.Result> result = AdminActions.perform(helper.getLevel().getServer(), null,
                new AdminActionPayload("level_set", "unknown-settlement", 3));
        helper.assertTrue(result.isPresent() && !result.get().success(),
                "An action on an unknown settlement must give a refusal");
        helper.succeed();
    }

    /**
     * The discover action checks all the regions up to {@code adminDiscoveryRadius} blocks in each direction around
     * the player: the test checks the regions at the four corners. A second discovery at the same position finds no
     * new settlement.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "admin_discover")
    public static void discoverChecksTheRegionsAroundThePlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        int radius = WorldConfig.ADMIN_DISCOVERY_RADIUS.get();

        Optional<AdminActions.Result> first = AdminActions.perform(server, player,
                new AdminActionPayload("discover", "", 0));
        BlockPos pos = player.blockPosition();
        SettlementDiscovery.Discovery again = SettlementDiscovery.discoverAround(helper.getLevel(), pos, radius);
        int spacing = ((RandomSpreadStructurePlacement) server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(SettlementDiscovery.VILLAGE_SET).placement()).spacing();
        SettlementData data = SettlementData.get(server);
        int radiusChunks = radius >> 4;
        boolean cornersChecked = true;
        for (int dx : new int[] {-radiusChunks, radiusChunks}) {
            for (int dz : new int[] {-radiusChunks, radiusChunks}) {
                RegionPos region = RegionPos.ofChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, spacing);
                cornersChecked &= data.isRegionChecked(region.x(), region.z());
            }
        }

        server.getPlayerList().remove(player);
        helper.assertTrue(first.isPresent() && first.get().success(), "The discovery must give a result");
        helper.assertTrue(cornersChecked,
                "The discovery must check the regions up to " + radius + " blocks in each direction");
        helper.assertTrue(again.found() == 0, "A second discovery must find no new settlement: " + again.found());
        helper.assertTrue(again.radius() == radius && again.inRadius() >= 0, "The discovery must give the distance");
        helper.succeed();
    }
}
