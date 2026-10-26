package io.github.nicolasgoyon.folkhold.world.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Helpers for the GameTests of the world mod.
 *
 * <p>Templates (made with {@code tools/make_test_structure.py}):
 * <ul>
 *   <li>{@code folkhold_world:empty}: 1 air block.</li>
 *   <li>{@code folkhold_world:arena}: 16 × 4 × 16 blocks of air. Each test builds the floor at y = 0 with
 *       {@link #buildFloor(GameTestHelper)}.</li>
 * </ul>
 *
 * <p>The floor is not part of the template. The position of a template block and the position from
 * {@link GameTestHelper#absolutePos(BlockPos)} do not always agree, and an entity can spawn inside a floor
 * block. Blocks that a test places with {@link GameTestHelper#setBlock(BlockPos, net.minecraft.world.level.block.Block)}
 * use the same positions as the entities.
 *
 * <p>Each test removes its entities when it passes. The next batch runs near the same place, and a unit
 * that stays alive can attack the monsters of a later test.
 */
final class TestSupport {
    static final int ARENA_SIZE = 16;
    /** The day time of a test: noon. */
    static final long NOON = 6000L;
    /** The settlements of the earlier tests in this radius go into ruins, in blocks. */
    private static final long RETIRE_RADIUS = 4096;

    private TestSupport() {
    }

    /**
     * A structure state of the level with the structure set of the faction villages. The GameTest world is a vanilla
     * Superflat world: its generator does not generate the faction villages. The tests of the discovery use this state.
     */
    static ChunkGeneratorStructureState villageStructures(ServerLevel level) {
        ServerChunkCache chunks = level.getChunkSource();
        return ChunkGeneratorStructureState.createForFlat(chunks.randomState(), level.getSeed(),
                chunks.getGenerator().getBiomeSource(), Stream.of(level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SettlementDiscovery.VILLAGE_SET)));
    }

    /**
     * Puts the settlements of the earlier tests in ruins (see {@link #retireEarlierSettlements(GameTestHelper)}),
     * then fills the bottom layer of the arena (y = 0) with smooth stone.
     */
    static void buildFloor(GameTestHelper helper) {
        buildFloor(helper, ARENA_SIZE);
    }

    /** As {@link #buildFloor(GameTestHelper)}, for an arena of the given size in blocks. */
    static void buildFloor(GameTestHelper helper, int size) {
        sweep(helper);
        retireEarlierSettlements(helper);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    /** The entities of an earlier test this far from the test area are removed, in blocks. */
    private static final double SWEEP_DISTANCE = 64.0;

    /** The largest number of beds that {@link #placeBeds} puts in an arena. */
    static final int MAX_ARENA_BEDS = 2 * ARENA_SIZE + 2 * (ARENA_SIZE - 6);

    /**
     * Places beds on the floor of the arena (y = 1): along the north edge, along the south edge, then along the west
     * edge and the east edge. The 2 parts of a bed go with no updates of the neighbors.
     *
     * @param helper the helper of the test
     * @param count  the number of beds, at most {@link #MAX_ARENA_BEDS}
     */
    static void placeBeds(GameTestHelper helper, int count) {
        List<BlockPos> feet = new ArrayList<>();
        List<Direction> directions = new ArrayList<>();
        for (int x = 0; x < ARENA_SIZE; x++) {
            feet.add(new BlockPos(x, 1, 0));
            directions.add(Direction.SOUTH);
            feet.add(new BlockPos(x, 1, ARENA_SIZE - 1));
            directions.add(Direction.NORTH);
        }
        for (int z = 3; z < ARENA_SIZE - 3; z++) {
            feet.add(new BlockPos(0, 1, z));
            directions.add(Direction.EAST);
            feet.add(new BlockPos(ARENA_SIZE - 1, 1, z));
            directions.add(Direction.WEST);
        }
        if (count > feet.size()) {
            throw new IllegalArgumentException("An arena has room for " + feet.size() + " beds, not " + count);
        }
        for (int i = 0; i < count; i++) {
            placeBed(helper, feet.get(i), directions.get(i));
        }
    }

    /**
     * Places a red bed with no updates of the neighbors.
     *
     * @param helper    the helper of the test
     * @param foot      the position of the foot, relative to the test
     * @param direction the direction from the foot to the head
     */
    static void placeBed(GameTestHelper helper, BlockPos foot, Direction direction) {
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, direction);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(foot);
        level.setBlock(absolute, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
        level.setBlock(absolute.relative(direction), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
    }

    /**
     * Removes what an earlier test can leave behind when it fails: its units, monsters and villagers within
     * {@link #SWEEP_DISTANCE} blocks of the test area, the kept groups of the materializer, and a day time that
     * is not noon. A test does not depend on the success of the test before it.
     */
    static void sweep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB area = helper.getBounds().inflate(SWEEP_DISTANCE);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, entity ->
                entity instanceof FactionUnit || entity instanceof Monster || entity instanceof Villager)) {
            entity.discard();
        }
        for (MobileGroup group : MobileGroupData.get(level.getServer()).groups().all()) {
            GroupMaterializer.keep(group.id(), false);
        }
        level.setDayTime(NOON);
    }

    /**
     * Puts in ruins the active settlements near the test that are outside the test area. Each batch has one test,
     * so these settlements are of earlier tests. An active settlement claims the territory around it, and a
     * territory of another faction changes the chase area of the units of the test.
     */
    static void retireEarlierSettlements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SettlementData data = SettlementData.get(server);
        AABB area = helper.getBounds().inflate(2.0);
        BlockPos center = BlockPos.containing(area.getCenter());
        boolean changed = false;
        for (Settlement settlement : data.registry().all()) {
            boolean inside = settlement.x() >= area.minX && settlement.x() <= area.maxX
                    && settlement.z() >= area.minZ && settlement.z() <= area.maxZ;
            if (settlement.isActive() && !inside
                    && settlement.distanceSquared(center.getX(), center.getZ()) <= RETIRE_RADIUS * RETIRE_RADIUS) {
                settlement.setState(SettlementState.RUINS);
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /**
     * Removes the bank chests of earlier tests near the test area, with their items. A village puts its stock into
     * the bank chests near its heart when it becomes ruins, so a bank chest of an earlier test would get the items
     * of the test.
     */
    static void removeEarlierBankChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB area = helper.getBounds().inflate(2.0);
        BlockPos center = BlockPos.containing(area.getCenter());
        int radius = WorldConfig.VILLAGE_RADIUS.get() + 16;
        List<BankChestBlockEntity> found = new ArrayList<>();
        for (int chunkX = (center.getX() - radius) >> 4; chunkX <= (center.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (center.getZ() - radius) >> 4; chunkZ <= (center.getZ() + radius) >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity blockEntity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (blockEntity instanceof BankChestBlockEntity chest
                            && !area.contains(Vec3.atCenterOf(chest.getBlockPos()))) {
                        found.add(chest);
                    }
                }
            }
        }
        for (BankChestBlockEntity chest : found) {
            chest.container().clearContent();
            level.setBlockAndUpdate(chest.getBlockPos(), Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Finds the heart in the area of a test with a village template. The test does not use a fixed relative position:
     * the position of a template block and the relative positions of {@link GameTestHelper} do not always agree.
     * The entities and the settlements of earlier tests go away first (see {@link #sweep} and
     * {@link #retireEarlierSettlements}).
     */
    static VillageHeartBlockEntity findHeart(GameTestHelper helper) {
        sweep(helper);
        retireEarlierSettlements(helper);
        AABB bounds = helper.getBounds();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (helper.getLevel().getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
                return heart;
            }
        }
        throw new GameTestAssertException("No village heart in the test area");
    }

    /** Spawns a unit as a spawn egg or a command does: equipment, configured values, home, village. */
    static <T extends FactionUnit> T spawnUnit(GameTestHelper helper, EntityType<T> type, BlockPos relativePos) {
        T unit = helper.spawn(type, relativePos);
        EventHooks.finalizeMobSpawn(unit, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(unit.blockPosition()),
                MobSpawnType.COMMAND, null);
        return unit;
    }

    /** Sets the level of the settlement of a heart. The heart gets its settlement first. */
    static void setLevel(GameTestHelper helper, VillageHeartBlockEntity heart, int level) {
        MinecraftServer server = helper.getLevel().getServer();
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        SettlementLevels.setProgress(server, settlement, level, 0);
    }

    /** Spawns a guard. See {@link #spawnUnit(GameTestHelper, EntityType, BlockPos)}. */
    static GuardEntity spawnGuard(GameTestHelper helper, BlockPos relativePos) {
        return spawnUnit(helper, ModEntities.GUARD.get(), relativePos);
    }

    /** Waits for the condition, removes the entities of the test, then marks the test as passed. */
    static void succeedWhenAndRemove(GameTestHelper helper, Runnable condition, Entity... entities) {
        helper.startSequence()
                .thenWaitUntil(condition)
                .thenExecute(() -> {
                    for (Entity entity : entities) {
                        entity.discard();
                    }
                })
                .thenSucceed();
    }

    /**
     * Sets each item of the stock of a settlement to zero. The start stock of a settlement depends on its level and
     * on its biome, which change with the position of the test: a test that checks a stock puts its own items.
     */
    static void emptyStock(Settlement settlement) {
        Stock stock = settlement.economy().stock();
        List.copyOf(stock.asMap().keySet()).forEach(item -> stock.set(item, 0));
    }

    /**
     * A mock server player in the level of the test, with a connection that sends nothing. It is creative for
     * {@link Player#isCreative()} (as the vanilla mock player), but its game mode is survival: the units can attack
     * it. Remove it at the end of the test with {@code getPlayerList().remove(player)}.
     */
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        return mockPlayer(helper, UUID.randomUUID());
    }

    /** A mock server player with a given id. See {@link #mockPlayer(GameTestHelper)}. */
    static ServerPlayer mockPlayer(GameTestHelper helper, UUID id) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(id, "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The default game mode of the GameTest server is creative.
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
