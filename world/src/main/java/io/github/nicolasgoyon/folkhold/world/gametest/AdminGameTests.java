package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

    private static Optional<Component> act(GameTestHelper helper, String action, Settlement settlement, int value) {
        return AdminActions.perform(helper.getLevel().getServer(), null,
                new AdminActionPayload(action, settlement.id(), value));
    }

    private static void retire(GameTestHelper helper, Settlement settlement) {
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(helper.getLevel().getServer()).setDirty();
    }

    /** The list contains each settlement, with its level and its distance, and the details describe it. */
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
        helper.assertTrue(details.isPresent() && !details.get().rows().isEmpty(),
                "The details of the settlement must have rows");
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

    /** The ruin and restore actions change the state of the settlement. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_state")
    public static void ruinAndRestoreChangeTheState(GameTestHelper helper) {
        Settlement settlement = settlementOf(helper, newHeart(helper));

        act(helper, "ruin", settlement, 0);
        boolean ruined = settlement.state() == SettlementState.RUINS;
        act(helper, "restore", settlement, 0);

        helper.assertTrue(ruined, "The ruin action must put the settlement in ruins");
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

        act(helper, "alert", settlement, AlertState.ASSAULT.ordinal());
        AlertState assault = heart.getAlertState();
        act(helper, "alert", settlement, AlertState.CALM.ordinal());

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

    /** An action on an unknown settlement does nothing and gives a message. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "admin_unknown")
    public static void unknownSettlementGivesAMessage(GameTestHelper helper) {
        Optional<Component> message = AdminActions.perform(helper.getLevel().getServer(), null,
                new AdminActionPayload("level_set", "unknown-settlement", 3));
        helper.assertTrue(message.isPresent(), "An action on an unknown settlement must give a message");
        helper.succeed();
    }
}
