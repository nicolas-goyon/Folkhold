package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.server.LayerService;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.RegionPos;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.admin.AdminMap;
import io.github.nicolasgoyon.folkhold.world.admin.AdminTeleports;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Kind;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
    /** Block X of the far settlements of the teleport tests. The chunks there are not generated. */
    private static final int FAR_X = 27_000_000;
    /**
     * The tick limit of the tests that wait for the generation of chunks. The GameTest server does not wait between
     * two ticks, but the generation takes real time: these tests wait with a real-time limit
     * ({@link TestSupport#thenWaitForWorker}), and this tick limit is a safety limit only.
     */
    private static final int TELEPORT_TEST_TICKS = TestSupport.WORKER_TICKS;

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

    /** A settlement with no heart in the state ruins, far from the test area, so the simulation ignores it. */
    private static Settlement farRuins(MinecraftServer server, int z) {
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, z >> 4, "test_faction",
                "Teleport target", FAR_X, z);
        settlement.setState(SettlementState.RUINS);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** A mock player who is an operator. {@link #logOut} removes the player and the operator entry. */
    private static ServerPlayer operator(GameTestHelper helper) {
        ServerPlayer player = TestSupport.mockPlayer(helper);
        // The operator level of the GameTest server is 0: the entry gives the level of the admin screen.
        helper.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(),
                AdminActions.PERMISSION_LEVEL, false));
        return player;
    }

    private static void logOut(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().getOps().remove(player.getGameProfile());
        server.getPlayerList().remove(player);
    }

    private static boolean isLoadingMessage(Optional<AdminActions.Result> result) {
        return result.isPresent() && result.get().success()
                && result.get().message().getContents() instanceof TranslatableContents contents
                && contents.getKey().endsWith("teleport.loading");
    }

    private static boolean isAreaReady(ServerLevel level, AdminTeleports.Area area) {
        return ReadyChunks.isAreaReady(level, area.minX(), area.minZ(), area.maxX(), area.maxZ());
    }

    /**
     * True if no ticket keeps a chunk of the area at the full status. The first line of the debug data of a chunk is
     * its ticket level, or "null" for a chunk with no holder. A level above the level of the full status means that no
     * ticket gives the full status to the chunk.
     */
    private static boolean hasNoFullTicket(ServerLevel level, AdminTeleports.Area area) {
        int fullLevel = ChunkLevel.byStatus(ChunkStatus.FULL);
        for (ChunkPos chunk : area.chunks()) {
            String data = level.getChunkSource().getChunkDebugData(chunk);
            String first = data.lines().findFirst().orElse("null");
            if (!first.equals("null") && Integer.parseInt(first) <= fullLevel) {
                return false;
            }
        }
        return true;
    }

    /**
     * The teleport to a settlement in chunks that are not generated does not wait for the chunks: the action gives
     * the loading message at once and the player does not move in the tick of the request. Some ticks later, the
     * player is at a safe position near the settlement: a solid floor and two free blocks.
     */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_teleport_async")
    public static void teleportDoesNotWaitForTheChunks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, 0);
        AdminTeleports.Area area = AdminActions.teleportArea(settlement);
        boolean readyBefore = isAreaReady(level, area);
        BlockPos start = player.blockPosition();

        Optional<AdminActions.Result> result = AdminActions.perform(server, player,
                new AdminActionPayload("teleport", settlement.id(), 0));

        boolean readyAfter = isAreaReady(level, area);

        helper.assertTrue(!readyBefore, "The chunks of the far settlement must not be ready before the request");
        helper.assertTrue(!readyAfter, "The request must not wait for the chunks of the area");
        helper.assertTrue(isLoadingMessage(result), "The action must give the loading message at once");
        helper.assertTrue(AdminTeleports.isPending(player), "The request must wait for the chunks");
        helper.assertTrue(player.blockPosition().equals(start), "The player must not move in the tick of the request");
        TestSupport.thenWaitForWorker(helper, helper.startSequence(), () -> !AdminTeleports.isPending(player),
                        "The request must end when the chunks are ready")
                .thenExecute(() -> {
                    BlockPos feet = player.blockPosition();
                    BlockPos below = feet.below();
                    logOut(server, player);
                    helper.assertTrue(Math.abs(feet.getX() - settlement.x()) <= 8
                            && Math.abs(feet.getZ() - settlement.z()) <= 8,
                            "The player must be near the settlement, not at " + feet);
                    helper.assertTrue(level.getBlockState(below).isFaceSturdy(level, below, Direction.UP),
                            "The player must stand on a solid floor at " + feet);
                    helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(),
                            "The player must have two free blocks at " + feet);
                })
                .thenSucceed();
    }

    /**
     * A request that is not complete after its time limit stops: the player does not move and the request releases
     * its tickets.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "admin_teleport_timeout")
    public static void teleportStopsAfterTheTimeLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, 4000);
        AdminTeleports.Area area = AdminActions.teleportArea(settlement);
        BlockPos start = player.blockPosition();

        AdminActions.Result result = AdminTeleports.request(player, level, area,
                found -> Optional.of(AdminActions.teleportTarget(found, settlement)), Component.literal("done"), 0);

        helper.assertTrue(isLoadingMessage(Optional.of(result)), "The request must give the loading message");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!AdminTeleports.isPending(player),
                        "The request must stop after the time limit"))
                .thenExecute(() -> helper.assertTrue(player.blockPosition().equals(start),
                        "The player must not move after the time limit, not to " + player.blockPosition()))
                .thenWaitUntil(() -> helper.assertTrue(hasNoFullTicket(level, area),
                        "The request must release its tickets after the time limit"))
                .thenExecute(() -> logOut(server, player))
                .thenSucceed();
    }

    /**
     * A second request of a player replaces the first request, and the logout of the player stops the request. The
     * two requests release their tickets.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "admin_teleport_logout")
    public static void teleportStopsAtTheLogout(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        ServerPlayer player = operator(helper);
        List<Settlement> settlements = List.of(farRuins(server, 8000), farRuins(server, 12_000));
        List<AdminTeleports.Area> areas = settlements.stream().map(AdminActions::teleportArea).toList();

        Optional<AdminActions.Result> first = AdminActions.perform(server, player,
                new AdminActionPayload("teleport", settlements.get(0).id(), 0));
        Optional<AdminActions.Result> second = AdminActions.perform(server, player,
                new AdminActionPayload("teleport", settlements.get(1).id(), 0));
        boolean pending = AdminTeleports.isPending(player);
        logOut(server, player);

        helper.assertTrue(isLoadingMessage(first) && isLoadingMessage(second),
                "The two requests must give the loading message");
        helper.assertTrue(pending, "The second request must wait for its chunks");
        helper.assertTrue(!AdminTeleports.isPending(player), "The logout must stop the request");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(hasNoFullTicket(level, areas.get(0)),
                        "The second request must release the tickets of the first request"))
                .thenWaitUntil(() -> helper.assertTrue(hasNoFullTicket(level, areas.get(1)),
                        "The logout must release the tickets of the request"))
                .thenSucceed();
    }

    /**
     * A request stops with no teleport when the player goes to another dimension during the wait. The request ends
     * when the area is ready, before the time limit.
     */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_teleport_dimension")
    public static void teleportStopsAfterADimensionChange(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The GameTest server must have the Nether");
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, 16_000);

        Optional<AdminActions.Result> result = AdminActions.perform(server, player,
                new AdminActionPayload("teleport", settlement.id(), 0));
        player.teleportTo(nether, 0.5, 130, 0.5, player.getYRot(), player.getXRot());

        helper.assertTrue(isLoadingMessage(result), "The action must give the loading message");
        assertRefusedWhenReady(helper, server, player, settlement, Level.NETHER);
    }

    /** A request stops with no teleport when the player is not an operator now. */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_teleport_permission")
    public static void teleportStopsWhenThePlayerIsNotAnOperator(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, 20_000);

        Optional<AdminActions.Result> result = AdminActions.perform(server, player,
                new AdminActionPayload("teleport", settlement.id(), 0));
        server.getPlayerList().getOps().remove(player.getGameProfile());

        helper.assertTrue(isLoadingMessage(result), "The action must give the loading message");
        helper.assertTrue(!AdminActions.isAllowed(player), "The player must not be an operator now");
        assertRefusedWhenReady(helper, server, player, settlement, Level.OVERWORLD);
    }

    /**
     * The restore of a settlement in chunks that are not generated does not wait for the chunks: the action gives the
     * loading message at once and the settlement stays in ruins in the tick of the request. Some ticks later, the
     * settlement is active and the request has released its tickets. The heart is on the edge of a chunk: the area
     * contains the chunks of all the neighbors of the heart, because the new heart updates its neighbors.
     */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_restore_async")
    public static void restoreDoesNotWaitForTheChunks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, 24_000);
        AdminTeleports.Area area = AdminActions.restoreArea(settlement);
        boolean readyBefore = isAreaReady(level, area);
        BlockPos heart = new BlockPos(settlement.x(), 0, settlement.z());
        List<ChunkPos> chunks = area.chunks();
        helper.assertTrue((heart.getX() & 15) == 0 && (heart.getZ() & 15) == 0,
                "The heart must be on the edge of a chunk");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.assertTrue(chunks.contains(new ChunkPos(heart.relative(direction))),
                    "The area must contain the chunk of the neighbor " + direction + " of the heart");
        }

        Optional<AdminActions.Result> result = AdminActions.perform(server, player,
                new AdminActionPayload("restore", settlement.id(), 0));

        boolean readyAfter = isAreaReady(level, area);

        helper.assertTrue(!readyBefore, "The chunk of the far settlement must not be ready before the request");
        helper.assertTrue(!readyAfter, "The request must not wait for the chunk of the heart");
        helper.assertTrue(result.isPresent() && result.get().success()
                && result.get().message().getContents() instanceof TranslatableContents contents
                && contents.getKey().endsWith("restore.loading"), "The action must give the loading message at once");
        helper.assertTrue(AdminActions.isRestorePending(player), "The request must wait for the chunk");
        helper.assertTrue(!settlement.isActive(), "The settlement must stay in ruins in the tick of the request");
        TestSupport.thenWaitForWorker(helper, helper.startSequence(), () -> !AdminActions.isRestorePending(player),
                        "The request must end when the chunk is ready")
                .thenExecute(() -> {
                    boolean active = settlement.isActive();
                    logOut(server, player);
                    retire(helper, settlement);
                    helper.assertTrue(active, "The restore must make the settlement active when the chunk is ready");
                })
                .thenWaitUntil(() -> helper.assertTrue(hasNoFullTicket(level, area),
                        "The request must release its tickets"))
                .thenSucceed();
    }

    /** The restore stops with no change when the settlement has a new id during the wait (message "unknown"). */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_restore_unknown")
    public static void restoreStopsWhenTheSettlementIsGone(GameTestHelper helper) {
        assertRestoreRefusedWhenReady(helper, 28_000, (player, settlement) -> Settlements.changeId(player.server,
                settlement, UUID.randomUUID().toString()));
    }

    /** The restore stops with no new heart when the settlement is active during the wait ("already_active"). */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_restore_active")
    public static void restoreStopsWhenTheSettlementIsActive(GameTestHelper helper) {
        assertRestoreRefusedWhenReady(helper, 32_000,
                (player, settlement) -> settlement.setState(SettlementState.ACTIVE));
    }

    /** The restore stops with no change when the player is not an operator now (message "denied"). */
    @GameTest(template = "empty", timeoutTicks = TELEPORT_TEST_TICKS, batch = "admin_restore_permission")
    public static void restoreStopsWhenThePlayerIsNotAnOperator(GameTestHelper helper) {
        assertRestoreRefusedWhenReady(helper, 36_000,
                (player, settlement) -> player.server.getPlayerList().getOps().remove(player.getGameProfile()));
    }

    /**
     * Requests the restore of far ruins and does a change during the wait. The request must stop before its time
     * limit, and the restore must not occur: the settlement gets no heart. Then logs the player out and puts the
     * settlement in ruins.
     */
    private static void assertRestoreRefusedWhenReady(GameTestHelper helper, int z,
            BiConsumer<ServerPlayer, Settlement> change) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = operator(helper);
        Settlement settlement = farRuins(server, z);
        long deadline = Util.getMillis() + AdminTeleports.TIMEOUT_SECONDS * 1000L;

        AdminActions.perform(server, player, new AdminActionPayload("restore", settlement.id(), 0));
        boolean pending = AdminActions.isRestorePending(player);
        change.accept(player, settlement);

        helper.assertTrue(pending, "The request must wait for the chunks");
        TestSupport.thenWaitForWorker(helper, helper.startSequence(), () -> !AdminActions.isRestorePending(player),
                        "The request must stop when the area is ready")
                .thenExecute(() -> {
                    long now = Util.getMillis();
                    boolean heart = settlement.hasHeart();
                    logOut(server, player);
                    retire(helper, settlement);
                    helper.assertTrue(now < deadline, "The request must stop before the time limit");
                    helper.assertTrue(!heart, "The restore must not occur");
                })
                .thenSucceed();
    }

    /**
     * Waits for the end of the request, before its time limit, and checks that the player did not go to the
     * settlement. Then logs the player out.
     */
    private static void assertRefusedWhenReady(GameTestHelper helper, MinecraftServer server, ServerPlayer player,
            Settlement settlement, ResourceKey<Level> dimension) {
        long deadline = Util.getMillis() + AdminTeleports.TIMEOUT_SECONDS * 1000L;
        TestSupport.thenWaitForWorker(helper, helper.startSequence(), () -> !AdminTeleports.isPending(player),
                        "The request must stop when the area is ready")
                .thenExecute(() -> {
                    long now = Util.getMillis();
                    BlockPos feet = player.blockPosition();
                    ResourceKey<Level> current = player.level().dimension();
                    logOut(server, player);
                    helper.assertTrue(now < deadline, "The request must stop before the time limit");
                    helper.assertTrue(current.equals(dimension), "The player must stay in " + dimension.location());
                    helper.assertTrue(Math.abs(feet.getX() - settlement.x()) > 8,
                            "The player must not go to the settlement, not to " + feet);
                })
                .thenSucceed();
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
     * The discover action gives the result of the discovery around the player. The generator of the GameTest world
     * does not generate the faction villages, so the test also runs the discovery with a structure state that has
     * them. This discovery checks all the regions up to {@code adminDiscoveryRadius} blocks in each direction around
     * the player: the test checks the regions at the four corners. A second discovery at the same position finds no
     * new settlement.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "admin_discover")
    public static void discoverChecksTheRegionsAroundThePlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        int radius = WorldConfig.ADMIN_DISCOVERY_RADIUS.get();
        ChunkGeneratorStructureState structures = TestSupport.villageStructures(helper.getLevel());

        Optional<AdminActions.Result> first = AdminActions.perform(server, player,
                new AdminActionPayload("discover", "", 0));
        BlockPos pos = player.blockPosition();
        SettlementDiscovery.discoverAround(helper.getLevel(), structures, pos, radius);
        SettlementDiscovery.Discovery again = SettlementDiscovery.discoverAround(helper.getLevel(), structures, pos,
                radius);
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

    /** True if Cartofold keeps the admin layer for the player. */
    private static boolean hasAdminLayer(ServerPlayer player) {
        return LayerService.get().flatMap(service -> service.layer(player.getUUID(), AdminMap.LAYER)).isPresent();
    }

    /**
     * The admin layer has the village with the color of its faction and its territory. After the ruin of the village,
     * the next check gives a ruin marker and no territory. Only the map frames show the layer.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_map")
    public static void mapHasTheSettlementsAndTheirTerritories(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlementOf(helper, newHeart(helper));
        ServerPlayer player = operator(helper);

        AdminActions.handle(player, new AdminActionPayload("map", "", 0));
        LayerContent active = AdminMap.shown(player).orElseThrow();
        retire(helper, settlement);
        AdminMap.check(server);
        LayerContent ruined = AdminMap.shown(player).orElseThrow();
        logOut(server, player);

        Optional<MapMarker> village = active.marker(Kind.VILLAGE.markerId(settlement.id()));
        helper.assertTrue(village.isPresent(), "The layer must have the village");
        helper.assertTrue(village.get().pos().getX() == settlement.x() && village.get().pos().getZ() == settlement.z(),
                "The marker is at the village");
        helper.assertTrue(active.info().views().equals(Set.of(MapViewKind.FRAME)), "Only the frames show the layer");
        helper.assertTrue(active.areas().stream().anyMatch(area -> area.id().equals(settlement.id())),
                "The layer must have the territory of the village");
        helper.assertTrue(ruined.marker(Kind.RUIN.markerId(settlement.id())).isPresent(), "A ruin is a ruin on the map");
        helper.assertTrue(ruined.areas().stream().noneMatch(area -> area.id().equals(settlement.id())),
                "A ruin has no territory");
        helper.succeed();
    }

    /**
     * Only an operator gets the admin layer. The layer leaves the operator who closes the map, who is not an operator
     * now, or who logs out.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "admin_map_access")
    public static void mapLayerIsOnlyForAnOperator(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        AdminActionPayload open = new AdminActionPayload("map", "", 0);

        AdminActions.handle(player, open);
        boolean refused = AdminMap.shown(player).isEmpty() && !hasAdminLayer(player);
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(),
                AdminActions.PERMISSION_LEVEL, false));
        AdminActions.handle(player, open);
        boolean opened = AdminMap.shown(player).isPresent() && hasAdminLayer(player);
        AdminActions.handle(player, new AdminActionPayload("map_close", "", 0));
        boolean closed = AdminMap.shown(player).isEmpty() && !hasAdminLayer(player);
        AdminActions.handle(player, open);
        server.getPlayerList().getOps().remove(player.getGameProfile());
        AdminMap.check(server);
        boolean demoted = AdminMap.shown(player).isEmpty() && !hasAdminLayer(player);
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(),
                AdminActions.PERMISSION_LEVEL, false));
        AdminActions.handle(player, open);
        boolean reopened = hasAdminLayer(player);
        logOut(server, player);

        helper.assertTrue(refused, "A player who is not an operator must not get the layer");
        helper.assertTrue(opened, "An operator gets the layer");
        helper.assertTrue(closed, "The close of the map removes the layer");
        helper.assertTrue(demoted, "A player who is not an operator now loses the layer");
        helper.assertTrue(reopened, "The operator gets the layer again");
        helper.assertTrue(AdminMap.shown(player).isEmpty() && !hasAdminLayer(player), "The logout removes the layer");
        helper.succeed();
    }
}
