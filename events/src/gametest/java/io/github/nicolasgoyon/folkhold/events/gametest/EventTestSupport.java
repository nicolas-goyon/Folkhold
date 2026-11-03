package io.github.nicolasgoyon.folkhold.events.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Tools that the GameTests of the events share. */
final class EventTestSupport {
    /** Time for the last falling or flying blocks to land, in ticks. */
    static final int LANDING_TICKS = 60;

    private EventTestSupport() {
    }

    /** Fills the bottom layer of the test area (y = 0) with stone. */
    static void stoneFloor(GameTestHelper helper, int sizeX, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * Builds a hollow house of one block: walls of 5 × 5 blocks from y = 1 to y = 3, and a flat roof at y = 4. The
     * edge of the roof is a part of the walls.
     *
     * @return the relative positions of the blocks of the house, and their blocks
     */
    static Map<BlockPos, Block> buildHouse(GameTestHelper helper, int minX, int minZ, Block wall, Block roof) {
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        for (int x = minX; x < minX + 5; x++) {
            for (int z = minZ; z < minZ + 5; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean isWall = x == minX || x == minX + 4 || z == minZ || z == minZ + 4;
                    if (isWall || y == 4) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Block block = isWall ? wall : roof;
                        helper.setBlock(pos, block);
                        blocks.put(pos, block);
                    }
                }
            }
        }
        return blocks;
    }

    /** The number of positions that do not have their block any more. */
    static int lost(GameTestHelper helper, Map<BlockPos, Block> blocks) {
        int lost = 0;
        for (Map.Entry<BlockPos, Block> entry : blocks.entrySet()) {
            if (!helper.getBlockState(entry.getKey()).is(entry.getValue())) {
                lost++;
            }
        }
        return lost;
    }

    /** Removes the falling blocks and the items that an event leaves in the test area. */
    static void removeDebris(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, helper.getBounds().inflate(16.0),
                entity -> entity instanceof FallingBlockEntity || entity instanceof ItemEntity)) {
            entity.discard();
        }
    }

    /** The time of the last complete tick of the server, in nanoseconds. */
    static long lastTickNanos(MinecraftServer server) {
        return server.getTickTimesNanos()[(server.getTickCount() - 1) % 100];
    }

    /** The tick times of the server in a period. */
    static final class TickTimes {
        private long total;
        private long max;
        private int count;

        void add(long nanos) {
            total += nanos;
            max = Math.max(max, nanos);
            count++;
        }

        double meanMillis() {
            return count == 0 ? 0.0 : total / 1e6 / count;
        }

        double maxMillis() {
            return max / 1e6;
        }
    }
}
