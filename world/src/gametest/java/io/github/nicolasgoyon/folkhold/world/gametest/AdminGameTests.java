package io.github.nicolasgoyon.folkhold.world.gametest;

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
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.ChunkPos;
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
    @GameTest(template = "empty", timeoutTicks = 600, batch = "admin_teleport_async")
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

        helper.assertTrue(!readyBefore, "The chunks of the far settlement must not be ready before the request");
        helper.assertTrue(isLoadingMessage(result), "The action must give the loading message at once");
        helper.assertTrue(AdminTeleports.isPending(player), "The request must wait for the chunks");
        helper.assertTrue(player.blockPosition().equals(start), "The player must not move in the tick of the request");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!AdminTeleports.isPending(player),
                        "The request must end when the chunks are ready"))
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
                found -> AdminActions.teleportTarget(found, settlement), Component.literal("done"), 0);

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

    /**
     * The map of the admin screen has the village with the color of its faction, its territory, and the ruin of a
     * village as a ruin.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "admin_map")
    public static void mapHasTheSettlementsAndTheirTerritories(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlementOf(helper, newHeart(helper));

        AdminMapPayload active = AdminMap.build(server);
        retire(helper, settlement);
        AdminMapPayload ruined = AdminMap.build(server);

        Optional<AdminMapPayload.Mark> mark = active.marks().stream()
                .filter(entry -> entry.id().equals(settlement.id())).findFirst();
        helper.assertTrue(mark.isPresent(), "The map must have the village");
        helper.assertTrue(mark.get().kind() == AdminMapPayload.VILLAGE, "The village has a heart: " + mark.get());
        helper.assertTrue(mark.get().x() == settlement.x() && mark.get().z() == settlement.z(),
                "The mark is at the village");
        helper.assertTrue(active.territories().villages().stream().anyMatch(village -> village.id()
                .equals(settlement.id())), "The map must have the territory of the village");
        helper.assertTrue(ruined.marks().stream().anyMatch(entry -> entry.id().equals(settlement.id())
                && entry.kind() == AdminMapPayload.RUIN), "A ruin is a ruin on the map");
        helper.assertTrue(ruined.territories().villages().stream().noneMatch(village -> village.id()
                .equals(settlement.id())), "A ruin has no territory");
        helper.succeed();
    }
}
