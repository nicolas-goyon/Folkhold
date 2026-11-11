package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class WalkSearchTest {
    private static final BuildBlock STONE = BuildBlock.solid("minecraft:stone");
    private static final BuildBlock FENCE = BuildBlock.connected("minecraft:oak_fence");
    /** The ground is out of this zone of 3 blocks around the position 0 0 0, with the feet at y = 1 or lower. */
    private static final WalkSearch.Zone ZONE = new WalkSearch.Zone(-3, 3, -3, 3, 1);
    /** A builder stands here, on the ground. */
    private static final BuildPos FEET = pos(0, 1, 0);
    /** The feet of a builder on the ledge of {@link #stairsToTheLedge}. */
    private static final BuildPos LEDGE = new BuildPos(0, 4, 0);
    /** The step of {@link #stairsToTheLedge} that the stairs do not have. */
    private static final BuildPos MISSING_STEP = new BuildPos(-2, 2, 0);

    private static BuildPos pos(int x, int y, int z) {
        return new BuildPos(x, y, z);
    }

    /** Stone at y = 0 and below, air above, and the given blocks. */
    private static WalkSearch search(Map<BuildPos, BuildBlock> blocks, Predicate<BuildPos> scaffolding) {
        return new WalkSearch(pos -> blocks.getOrDefault(pos, pos.y() <= 0 ? STONE : BuildBlock.AIR).walk(),
                scaffolding, pos -> false, ZONE, -10, 10);
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
        assertEquals(WalkSearch.Way.CLEAR, search(Map.of()).climbWay(FEET));
        Map<BuildPos, BuildBlock> blocks = ring(1, 2, STONE);
        assertEquals(WalkSearch.Way.NONE, search(blocks).climbWay(FEET),
                "The bottom of the column is in a closed room");
    }

    /**
     * A search around the positions of a target: the ground is around the box of the positions and the reach. A
     * builder climbs a high column of scaffolding next to the box. It does not get on the top of a wall of the box.
     */
    @Test
    void searchAroundATarget() {
        Map<BuildPos, BuildBlock> blocks = ring(1, 4, STONE);
        Set<BuildPos> column = new HashSet<>();
        for (int y = 1; y <= 4; y++) {
            column.add(pos(2, y, 0));
        }
        WalkSearch search = WalkSearch.around(List.of(pos(-1, 1, -1), pos(1, 4, 1)), Reach.DEFAULT,
                pos -> blocks.getOrDefault(pos, pos.y() <= 0 ? STONE : BuildBlock.AIR).walk(), column::contains,
                pos -> false);

        assertTrue(search.canReach(pos(2, 5, 0)), "On the top of the column");
        assertFalse(search.canReach(pos(1, 5, 0)), "On the top of the wall");
        assertFalse(search.canReach(FEET), "In the closed room");
        assertTrue(search.canReach(pos(-3, 1, 0)), "On the ground near the wall");
    }

    /**
     * A room with walls of 2 blocks and a step of 1 block out of the room. The builder goes into the room over the top
     * of the wall: the step, the top of the wall, then a drop into the room. With an opening in the wall, the search
     * finds the clear way through the opening. With no step and no opening, there is no way.
     */
    @Test
    void aClearWayComesBeforeAWayOverAWall() {
        Map<BuildPos, BuildBlock> blocks = ring(2, 2, STONE);
        Set<BuildPos> wallTops = new HashSet<>(ring(2, 2, STONE).keySet());
        wallTops.removeIf(pos -> pos.y() != 2);
        assertEquals(WalkSearch.Way.NONE, search(blocks, wallTops).way(FEET), "No step and no opening");

        BuildPos step = pos(3, 1, 0);
        blocks.put(step, STONE);
        WalkSearch search = search(blocks, wallTops);
        assertEquals(WalkSearch.Way.OVER_WALL, search.way(FEET), "Only the way over the wall");
        assertEquals(WalkSearch.Way.OVER_WALL, search.way(FEET), "The closed area of the clear ways stays");
        assertTrue(search.canReach(FEET));

        BuildPos opening = pos(-2, 1, 0);
        blocks.put(opening, BuildBlock.AIR);
        blocks.put(opening.above(), BuildBlock.AIR);
        wallTops.remove(opening.above());
        search.blockChanged(opening);
        search.blockChanged(opening.above());
        assertEquals(WalkSearch.Way.CLEAR, search.way(FEET), "The way through the opening after the change");
    }

    /**
     * Stairs from the ground to a ledge, 3 blocks above the ground, with no block at {@link #MISSING_STEP}: no way goes
     * to the ledge.
     */
    private static Map<BuildPos, BuildBlock> stairsToTheLedge() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(pos(-3, 1, 0), STONE);
        blocks.put(pos(-2, 1, 0), STONE);
        for (int y = 1; y <= 3; y++) {
            blocks.put(pos(-1, y, 0), STONE);
            blocks.put(pos(0, y, 0), STONE);
        }
        return blocks;
    }

    /** A column of stone from y = 1 to the given height. */
    private static void pillar(Map<BuildPos, BuildBlock> blocks, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            blocks.put(pos(x, y, z), STONE);
        }
    }

    /**
     * A closed area can depend on an other closed area: its search does not go into the other area. When a change
     * opens the other area, the area that depends on it also opens, also far from the change.
     *
     * <p>A shaft next to the ledge: the only way into the shaft is a drop from the ledge. The first search closes the
     * ledge. The second search closes the shaft, with no visit of the ledge. The new step opens a way to the ledge,
     * 3 blocks from the shaft. The shaft has a way after the change, as for a new search.
     */
    @Test
    void aChangeOpensTheAreasThatDependOnTheOpenArea() {
        Map<BuildPos, BuildBlock> blocks = stairsToTheLedge();
        BuildPos shaft = pos(1, 2, 0);
        blocks.put(shaft.below(), STONE);
        pillar(blocks, 2, 0, 3);
        pillar(blocks, 1, -1, 3);
        pillar(blocks, 1, 1, 3);
        WalkSearch search = search(blocks);
        assertEquals(WalkSearch.Way.NONE, search.way(LEDGE));
        assertEquals(WalkSearch.Way.NONE, search.way(shaft));

        blocks.put(MISSING_STEP, STONE);
        search.blockChanged(MISSING_STEP);
        assertEquals(WalkSearch.Way.CLEAR, search(blocks).way(shaft), "A new search");
        assertEquals(WalkSearch.Way.CLEAR, search.way(shaft), "The search after the change");
    }

    /**
     * The search of the bottom of a column does not close a position with a move from the bottom. The builder goes
     * into the bottom of the column only with a walk or a jump, but it can go to the position with a drop into the
     * bottom.
     *
     * <p>A pit of 2 positions next to the ledge, with high walls: the bottom of the column and a position next to it.
     * The only way into the pit is a drop from the ledge into the bottom of the column.
     */
    @Test
    void theBottomOfAColumnDoesNotCloseAPositionAfterIt() {
        Map<BuildPos, BuildBlock> blocks = stairsToTheLedge();
        blocks.put(MISSING_STEP, STONE);
        BuildPos bottom = pos(1, 2, 0);
        BuildPos next = pos(1, 2, 1);
        blocks.put(bottom.below(), STONE);
        blocks.put(next.below(), STONE);
        pillar(blocks, 2, 0, 5);
        pillar(blocks, 1, -1, 5);
        pillar(blocks, 0, 1, 5);
        pillar(blocks, 2, 1, 5);
        pillar(blocks, 1, 2, 5);
        WalkSearch search = search(blocks);
        assertEquals(WalkSearch.Way.NONE, search.climbWay(bottom), "No walk and no jump into the bottom");
        assertEquals(WalkSearch.Way.CLEAR, search(blocks).way(next), "A new search");
        assertEquals(WalkSearch.Way.CLEAR, search.way(next), "The search after the search of the column");
    }

    /**
     * A search keeps its closed areas for many changes. After each random change, the search gives the same way as a
     * new search on the blocks now, for random positions, also for the bottom of a column. The changes add stone of
     * the plan, scaffolding and air. The seed is fixed: the test always does the same changes.
     */
    @Test
    void aSearchAfterChangesFindsTheWayOfANewSearch() {
        Random random = new Random(765);
        int queries = 0;
        for (int round = 0; round < 250; round++) {
            Map<BuildPos, BuildBlock> blocks = new HashMap<>();
            Set<BuildPos> placed = new HashSet<>();
            Set<BuildPos> scaffolding = new HashSet<>();
            int size = 4 + random.nextInt(3);
            int height = 3 + random.nextInt(4);
            int count = 20 + random.nextInt(80);
            for (int i = 0; i < count; i++) {
                BuildPos pos = randomPos(random, size, height);
                blocks.put(pos, STONE);
                if (random.nextBoolean()) {
                    placed.add(pos);
                }
            }
            Function<BuildPos, BuildBlock> block = pos -> blocks.getOrDefault(pos,
                    pos.y() <= 0 ? STONE : BuildBlock.AIR);
            Function<BuildPos, Walk> walk = pos -> scaffolding.contains(pos) ? Walk.ON : block.apply(pos).walk();
            Predicate<BuildPos> wallTop = pos -> ConstructionPlanner.isWallTop(pos, placed::contains,
                    floor -> scaffolding.contains(floor) || block.apply(floor).floor());
            List<BuildPos> target = List.of(pos(3 - size, 1, 3 - size), pos(size - 3, height, size - 3));
            Supplier<WalkSearch> newSearch = () -> WalkSearch.around(target, Reach.DEFAULT, walk,
                    scaffolding::contains, wallTop);
            WalkSearch search = newSearch.get();
            for (int change = 0; change < 40; change++) {
                for (int query = 0; query < 6; query++) {
                    BuildPos feet = randomPos(random, size, height + 1);
                    boolean column = random.nextInt(4) == 0;
                    WalkSearch fresh = newSearch.get();
                    WalkSearch.Way expected = column ? fresh.climbWay(feet) : fresh.way(feet);
                    WalkSearch.Way way = column ? search.climbWay(feet) : search.way(feet);
                    queries++;
                    assertEquals(expected, way, "Round " + round + ", change " + change + ", "
                            + (column ? "bottom of a column " : "feet ") + feet);
                }
                BuildPos pos = randomPos(random, size, height);
                int kind = random.nextInt(6);
                if (kind <= 1) {
                    blocks.put(pos, BuildBlock.AIR);
                    scaffolding.remove(pos);
                } else if (kind == 2) {
                    blocks.remove(pos);
                    scaffolding.add(pos);
                } else {
                    scaffolding.remove(pos);
                    blocks.put(pos, STONE);
                    placed.add(pos);
                }
                search.blockChanged(pos);
            }
        }
        assertEquals(250 * 40 * 6, queries);
    }

    private static BuildPos randomPos(Random random, int size, int height) {
        return pos(random.nextInt(2 * size + 1) - size, 1 + random.nextInt(height),
                random.nextInt(2 * size + 1) - size);
    }

    /** Stone at y = 0 and below, air above, the given blocks, and the given tops of new walls. */
    private static WalkSearch search(Map<BuildPos, BuildBlock> blocks, Set<BuildPos> wallTops) {
        return new WalkSearch(pos -> blocks.getOrDefault(pos, pos.y() <= 0 ? STONE : BuildBlock.AIR).walk(),
                pos -> false, wallTops::contains, ZONE, -10, 10);
    }
}
