package io.github.nicolasgoyon.folkhold.world.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    /** The settlements of the earlier tests in this radius go into ruins, in blocks. */
    private static final long RETIRE_RADIUS = 4096;

    private TestSupport() {
    }

    /**
     * Puts the settlements of the earlier tests in ruins (see {@link #retireEarlierSettlements(GameTestHelper)}),
     * then fills the bottom layer of the arena (y = 0) with smooth stone.
     */
    static void buildFloor(GameTestHelper helper) {
        retireEarlierSettlements(helper);
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
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

    /** Spawns a unit as a spawn egg or a command does: equipment, configured values, home, village. */
    static <T extends FactionUnit> T spawnUnit(GameTestHelper helper, EntityType<T> type, BlockPos relativePos) {
        T unit = helper.spawn(type, relativePos);
        unit.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(unit.blockPosition()),
                MobSpawnType.COMMAND, null);
        return unit;
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
     * A mock server player in the level of the test, with a connection that sends nothing. It is creative for
     * {@link Player#isCreative()}, but its game mode is survival (as the vanilla mock player). Remove it at the end
     * of the test with {@code getPlayerList().remove(player)}.
     */
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
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
        return player;
    }
}
