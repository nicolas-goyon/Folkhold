package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class WalkSearchTest {
    private static final BuildBlock STONE = BuildBlock.solid("minecraft:stone");
    private static final BuildBlock FENCE = BuildBlock.connected("minecraft:oak_fence");
    /** The ground is out of this zone of 3 blocks around the position 0 0 0, with the feet at y = 1 or lower. */
    private static final WalkSearch.Zone ZONE = new WalkSearch.Zone(-3, 3, -3, 3, 1);
    /** A builder stands here, on the ground. */
    private static final BuildPos FEET = pos(0, 1, 0);

    private static BuildPos pos(int x, int y, int z) {
        return new BuildPos(x, y, z);
    }

    /** Stone at y = 0 and below, air above, and the given blocks. */
    private static WalkSearch search(Map<BuildPos, BuildBlock> blocks, Predicate<BuildPos> scaffolding) {
        return new WalkSearch(pos -> blocks.getOrDefault(pos, pos.y() <= 0 ? STONE : BuildBlock.AIR), scaffolding,
                ZONE, -10, 10);
    }

    private static WalkSearch search(Map<BuildPos, BuildBlock> blocks) {
        return search(blocks, pos -> false);
    }

    /** A ring of blocks around the position 0 0 0, at the given distance, from y = 1 to the given height. */
    private static Map<BuildPos, BuildBlock> ring(int distance, int height, BuildBlock block) {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -distance; x <= distance; x++) {
            for (int z = -distance; z <= distance; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == distance) {
                    for (int y = 1; y <= height; y++) {
                        blocks.put(pos(x, y, z), block);
                    }
                }
            }
        }
        return blocks;
    }

    /** A builder walks on open ground. */
    @Test
    void walksOnOpenGround() {
        assertTrue(search(Map.of()).canReach(FEET));
    }

    /** A wall of 2 blocks around the builder closes the way. A wall of 1 block does not: the builder jumps on it. */
    @Test
    void jumpsOnOneBlockAndNotOnTwo() {
        assertFalse(search(ring(1, 2, STONE)).canReach(FEET));
        assertTrue(search(ring(1, 1, STONE)).canReach(FEET));
    }

    /** A fence of 1 block is higher than 1 block: it closes the way. */
    @Test
    void aFenceStopsTheBuilder() {
        assertFalse(search(ring(1, 1, FENCE)).canReach(FEET));
    }

    /** A builder walks through a wooden door. An iron door closes the way. */
    @Test
    void walksThroughAWoodenDoor() {
        Map<BuildPos, BuildBlock> blocks = ring(1, 2, STONE);
        BuildPos lower = pos(1, 1, 0);
        BuildPos upper = pos(1, 2, 0);
        blocks.put(lower, BuildBlock.twoPart("minecraft:oak_door[half=lower]", upper, true, Optional.of(Side.DOWN))
                .withWalk(Walk.THROUGH));
        blocks.put(upper, BuildBlock.twoPart("minecraft:oak_door[half=upper]", lower, false, Optional.empty())
                .withWalk(Walk.THROUGH));
        assertTrue(search(blocks).canReach(FEET));

        blocks.put(lower, BuildBlock.twoPart("minecraft:iron_door[half=lower]", upper, true, Optional.of(Side.DOWN)));
        blocks.put(upper, BuildBlock.twoPart("minecraft:iron_door[half=upper]", lower, false, Optional.empty()));
        assertFalse(search(blocks).canReach(FEET));
    }

    /**
     * A builder jumps on a block only with a free block above its head. Under a low ceiling, it does not jump on a
     * platform of 1 block.
     */
    @Test
    void doesNotJumpUnderALowCeiling() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean platform = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                if (platform) {
                    blocks.put(pos(x, 1, z), STONE);
                } else {
                    blocks.put(pos(x, 3, z), STONE);
                }
            }
        }
        BuildPos onPlatform = pos(0, 2, 0);

        assertFalse(search(blocks).canReach(onPlatform));
        for (int x = -3; x <= 3; x++) {
            blocks.remove(pos(x, 3, 2));
        }
        assertTrue(search(blocks).canReach(onPlatform), "With a free row above the heads, the builder jumps");
    }

    /** A roof that goes out of the zone is not the ground: no way goes from the ground to the roof. */
    @Test
    void aRoofOutOfTheZoneIsNotTheGround() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                blocks.put(pos(x, 3, z), STONE);
            }
        }
        assertFalse(search(blocks).canReach(pos(0, 4, 0)));
        assertTrue(search(blocks).canReach(FEET), "Under the roof, the builder walks out of the zone");
    }

    /** A builder goes down 3 blocks into a pit, and not 4. */
    @Test
    void dropsThreeBlocksAtMost() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int y = -3; y <= 0; y++) {
            blocks.put(pos(0, y, 0), BuildBlock.AIR);
        }
        assertFalse(search(blocks).canReach(pos(0, -3, 0)), "A drop of 4 blocks");
        blocks.put(pos(0, -3, 0), STONE);
        assertTrue(search(blocks).canReach(pos(0, -2, 0)), "A drop of 3 blocks");
    }

    /**
     * A builder climbs a scaffolding column of the plan from its bottom. In a closed room, the bottom of the column
     * has no way from the ground. The builder does not walk from the top of a column to another position.
     */
    @Test
    void climbsAColumnFromItsBottom() {
        Set<BuildPos> column = Set.of(pos(0, 1, 0), pos(0, 2, 0), pos(0, 3, 0));
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        column.forEach(cell -> blocks.put(cell, BuildBlock.SCAFFOLDING));
        // A platform next to the top of the column, with no other way to it.
        blocks.put(pos(1, 3, 0), STONE);
        BuildPos top = pos(0, 4, 0);

        assertTrue(search(blocks, column::contains).canReach(top));
        assertFalse(search(blocks, column::contains).canReach(pos(1, 4, 0)), "From the top of the column");

        blocks.putAll(ring(1, 2, STONE));
        blocks.put(pos(1, 3, 0), STONE);
        assertFalse(search(blocks, column::contains).canReach(top), "The bottom of the column is in a closed room");
    }

    /**
     * A search keeps the closed areas for the next steps of a plan. A change of a block near a closed area can open a
     * way to it: the search finds the way after the change.
     */
    @Test
    void aChangeOpensAClosedArea() {
        Map<BuildPos, BuildBlock> blocks = ring(1, 2, STONE);
        WalkSearch search = search(blocks);
        assertFalse(search.canReach(FEET));

        BuildPos door = pos(1, 1, 0);
        blocks.put(door, BuildBlock.AIR);
        blocks.put(door.above(), BuildBlock.AIR);
        assertFalse(search.canReach(FEET), "The search does not know the change yet");
        search.blockChanged(door);
        search.blockChanged(door.above());
        assertTrue(search.canReach(FEET));
    }

    /** A builder goes into the bottom of a new column with a walk, and not with a drop. */
    @Test
    void walksToTheBottomOfANewColumn() {
        assertTrue(search(Map.of()).canClimb(FEET));
        Map<BuildPos, BuildBlock> blocks = ring(1, 2, STONE);
        assertFalse(search(blocks).canClimb(FEET), "The bottom of the column is in a closed room");
    }
}
