package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.construction.PlanWarning.Problem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstructionPlannerTest {
    private static final String STONE = "minecraft:stone";
    private static final Reach REACH = Reach.DEFAULT;

    /** A level with a floor of stone at y = 0, from -10 to 10 on X and Z, and the given blocks. */
    private static LevelView level(Map<BuildPos, BuildBlock> blocks) {
        return pos -> {
            BuildBlock block = blocks.get(pos);
            if (block != null) {
                return block;
            }
            boolean floor = pos.y() == 0 && Math.abs(pos.x()) <= 10 && Math.abs(pos.z()) <= 10;
            return floor ? BuildBlock.solid(STONE) : BuildBlock.AIR;
        };
    }

    private static LevelView emptyLevel() {
        return level(Map.of());
    }

    private static BuildPos pos(int x, int y, int z) {
        return new BuildPos(x, y, z);
    }

    private static int index(Plan plan, StepType type, BuildPos pos) {
        List<Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).type() == type && steps.get(i).pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    /** The blocks of a column go from the bottom to the top. A block equal to the level gets no step. */
    @Test
    void placementsGoFromTheBottomToTheTop() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int y = 1; y <= 3; y++) {
            blocks.put(pos(0, y, 0), BuildBlock.solid(STONE));
        }
        blocks.put(pos(0, 0, 0), BuildBlock.solid(STONE));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertEquals(3, plan.count(StepType.PLACE));
        assertTrue(index(plan, StepType.PLACE, pos(0, 1, 0)) < index(plan, StepType.PLACE, pos(0, 2, 0)));
        assertTrue(index(plan, StepType.PLACE, pos(0, 2, 0)) < index(plan, StepType.PLACE, pos(0, 3, 0)));
        assertEquals(-1, index(plan, StepType.PLACE, pos(0, 0, 0)));
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    /**
     * An attached block comes after its support, also when the support is higher: a lantern that hangs from a beam.
     * A wall torch comes after its wall, and a falling block after the block below it.
     */
    @Test
    void supportsComeFirst() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(pos(0, 1, 0), BuildBlock.solid(STONE));
        blocks.put(pos(0, 2, 0), BuildBlock.solid(STONE));
        blocks.put(pos(0, 3, 0), BuildBlock.solid(STONE));
        blocks.put(pos(1, 3, 0), BuildBlock.solid("minecraft:oak_planks"));
        blocks.put(pos(1, 2, 0), BuildBlock.attached("minecraft:lantern[hanging=true]", Side.UP));
        blocks.put(pos(-1, 2, 0), BuildBlock.attached("minecraft:wall_torch[facing=west]", Side.EAST));
        blocks.put(pos(0, 4, 0), BuildBlock.falling("minecraft:sand"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(index(plan, StepType.PLACE, pos(1, 3, 0)) < index(plan, StepType.PLACE, pos(1, 2, 0)),
                "The lantern must come after the beam");
        assertTrue(index(plan, StepType.PLACE, pos(0, 2, 0)) < index(plan, StepType.PLACE, pos(-1, 2, 0)),
                "The wall torch must come after its wall");
        assertTrue(index(plan, StepType.PLACE, pos(0, 3, 0)) < index(plan, StepType.PLACE, pos(0, 4, 0)),
                "The sand must come after the block below it");
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    /** The plan reports a falling block above air, and an attached block with no support. */
    @Test
    void reportsBlocksWithNoSupport() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(pos(0, 3, 0), BuildBlock.falling("minecraft:gravel"));
        blocks.put(pos(2, 1, 0), BuildBlock.attached("minecraft:wall_torch[facing=east]", Side.WEST));
        blocks.put(pos(4, 1, 0), BuildBlock.optionalSupport("minecraft:oak_trapdoor[facing=east]", Side.WEST));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertEquals(List.of(new PlanWarning(Problem.NO_SUPPORT, pos(2, 1, 0)),
                new PlanWarning(Problem.FALLING_BLOCK_ABOVE_AIR, pos(0, 3, 0))), plan.warnings());
    }

    /** One step places the two parts of a door. The step of the upper half does not exist. */
    @Test
    void oneStepForTheTwoPartsOfADoor() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        BuildPos lower = pos(0, 1, 0);
        BuildPos upper = pos(0, 2, 0);
        blocks.put(lower, BuildBlock.twoPart("minecraft:oak_door[half=lower]", upper, true, Optional.of(Side.DOWN)));
        blocks.put(upper, BuildBlock.twoPart("minecraft:oak_door[half=upper]", lower, false, Optional.empty()));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertEquals(1, plan.count(StepType.PLACE));
        Step step = plan.steps().get(index(plan, StepType.PLACE, lower));
        assertEquals(Optional.of(upper), step.partner());
        assertEquals(Optional.of("minecraft:oak_door[half=upper]"), step.partnerState());
    }

    /** A fence comes after the solid blocks next to it. */
    @Test
    void connectedBlocksComeAfterTheirSolidNeighbors() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(pos(0, 1, 0), BuildBlock.connected("minecraft:oak_fence[east=true]"));
        blocks.put(pos(1, 1, 0), BuildBlock.solid("minecraft:oak_log"));
        blocks.put(pos(-1, 1, 0), BuildBlock.connected("minecraft:oak_fence[east=true,west=true]"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(index(plan, StepType.PLACE, pos(1, 1, 0)) < index(plan, StepType.PLACE, pos(0, 1, 0)));
    }

    /**
     * The obstacles go first: the attached blocks, then from the top to the bottom. A block on a replaced support goes
     * and comes back.
     */
    @Test
    void obstaclesGoFirst() {
        Map<BuildPos, BuildBlock> levelBlocks = new HashMap<>();
        levelBlocks.put(pos(0, 1, 0), BuildBlock.solid("minecraft:dirt"));
        levelBlocks.put(pos(0, 2, 0), BuildBlock.solid("minecraft:dirt"));
        levelBlocks.put(pos(1, 2, 0), BuildBlock.attached("minecraft:wall_torch[facing=east]", Side.WEST));
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(pos(0, 1, 0), BuildBlock.solid(STONE));
        blocks.put(pos(0, 2, 0), BuildBlock.solid(STONE));
        blocks.put(pos(1, 2, 0), BuildBlock.attached("minecraft:wall_torch[facing=east]", Side.WEST));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), level(levelBlocks), REACH);

        int torchRemoval = index(plan, StepType.REMOVE, pos(1, 2, 0));
        int topRemoval = index(plan, StepType.REMOVE, pos(0, 2, 0));
        int bottomRemoval = index(plan, StepType.REMOVE, pos(0, 1, 0));
        assertTrue(torchRemoval >= 0 && torchRemoval < topRemoval && topRemoval < bottomRemoval);
        assertTrue(bottomRemoval < index(plan, StepType.PLACE, pos(0, 1, 0)));
        assertTrue(index(plan, StepType.PLACE, pos(0, 2, 0)) < index(plan, StepType.PLACE, pos(1, 2, 0)),
                "The torch must come back after its wall");
    }

    /** A waterlogged block comes with no water, and the water comes at the end. A water source also. */
    @Test
    void fluidsComeAtTheEnd() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        BuildBlock slab = BuildBlock.solid("minecraft:oak_slab[waterlogged=true]")
                .withFluid("minecraft:oak_slab[waterlogged=false]");
        blocks.put(pos(0, 1, 0), slab);
        blocks.put(pos(1, 1, 0), BuildBlock.fluid("minecraft:water[level=0]"));
        blocks.put(pos(2, 1, 0), BuildBlock.solid(STONE));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        Step dry = plan.steps().get(index(plan, StepType.PLACE, pos(0, 1, 0)));
        assertEquals("minecraft:oak_slab[waterlogged=false]", dry.state());
        int stone = index(plan, StepType.PLACE, pos(2, 1, 0));
        assertTrue(stone < index(plan, StepType.ADD_FLUID, pos(0, 1, 0)));
        assertTrue(stone < index(plan, StepType.ADD_FLUID, pos(1, 1, 0)));
        assertEquals(-1, index(plan, StepType.PLACE, pos(1, 1, 0)));
    }

    /**
     * A block high above the floor needs scaffolding. The column comes before the block, and the builder stands on it
     * in reach of the block. The column goes after the block, from the top.
     */
    @Test
    void scaffoldingForABlockOutOfReach() {
        BuildPos high = pos(0, 9, 0);
        Map<BuildPos, BuildBlock> blocks = Map.of(high, BuildBlock.solid(STONE));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        long columnHeight = plan.count(StepType.PLACE_SCAFFOLDING);
        assertTrue(columnHeight > 0, "The plan must add scaffolding");
        assertEquals(columnHeight, plan.count(StepType.REMOVE_SCAFFOLDING));
        int place = index(plan, StepType.PLACE, high);
        List<Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                assertTrue(i < place, "The scaffolding must come before the block");
            } else if (step.type() == StepType.REMOVE_SCAFFOLDING) {
                assertTrue(i > place, "The scaffolding must go after the block");
            }
        }
        Step step = steps.get(place);
        assertTrue(step.stand().isPresent());
        assertTrue(REACH.contains(step.stand().get(), high));
        List<Step> removals = steps.stream().filter(s -> s.type() == StepType.REMOVE_SCAFFOLDING).toList();
        for (int i = 1; i < removals.size(); i++) {
            assertTrue(removals.get(i).pos().y() < removals.get(i - 1).pos().y(), "From the top to the bottom");
        }
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    /** The scaffolding does not take a position that a later step fills. */
    @Test
    void scaffoldingStaysOutOfTheTarget() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 9; y++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    blocks.put(pos(x, y, z), wall ? BuildBlock.solid(STONE) : BuildBlock.AIR);
                }
            }
        }

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        for (Step step : plan.steps()) {
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                BuildBlock target = blocks.getOrDefault(step.pos(), BuildBlock.AIR);
                assertEquals(BlockKind.AIR, target.kind(), "Scaffolding in the target at " + step.pos());
            }
            if (step.type() == StepType.PLACE) {
                assertTrue(step.stand().isPresent(), "No position to stand for " + step.pos());
                assertTrue(REACH.contains(step.stand().get(), step.pos()), "Out of reach: " + step.pos());
            }
        }
        assertEquals(plan.count(StepType.PLACE_SCAFFOLDING), plan.count(StepType.REMOVE_SCAFFOLDING));
        assertFalse(plan.count(StepType.PLACE) == 0);
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    /** A scaffolding column does not stand on a block that a later step of the plan removes. */
    @Test
    void scaffoldingDoesNotStandOnABlockThatThePlanRemoves() {
        BuildPos high = pos(0, 8, 0);
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        blocks.put(high, BuildBlock.AIR);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.put(pos(x, 0, z), BuildBlock.AIR);
            }
        }

        Plan plan = ConstructionPlanner.plan(new Target(blocks), level(Map.of(high, BuildBlock.solid(STONE))), REACH);

        assertEquals(0, plan.count(StepType.PLACE_SCAFFOLDING));
        assertEquals(26, plan.count(StepType.REMOVE));
        assertEquals(List.of(new PlanWarning(Problem.NO_STAND, high)), plan.warnings());
    }

    /** The reach zone is a box around the feet: 2 blocks on X and Z, from the layer of the floor to above the head. */
    @Test
    void reachIsABoxAroundTheFeet() {
        BuildPos feet = pos(0, 1, 0);
        assertTrue(REACH.contains(feet, pos(2, 1, -2)));
        assertTrue(REACH.contains(feet, pos(1, 0, 0)), "The layer of the floor");
        assertTrue(REACH.contains(feet, pos(0, 3, 1)), "One block above the head");
        assertFalse(REACH.contains(feet, pos(3, 1, 0)), "Too far on X");
        assertFalse(REACH.contains(feet, pos(0, 1, -3)), "Too far on Z");
        assertFalse(REACH.contains(feet, pos(1, -1, 0)), "Below the layer of the floor");
        assertFalse(REACH.contains(feet, pos(0, 4, 0)), "Too high");
        assertThrows(IllegalArgumentException.class, () -> new Reach(0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Reach(2, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Reach(2, 1, 0));
    }

    /**
     * A room with walls of 4 blocks and a flat roof. The builder does not climb on the new walls: the plan adds
     * scaffolding before the roof. When a part of the roof is in place, the builder can stand on it.
     */
    @Test
    void theBuilderDoesNotStandOnANewWall() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        Set<BuildPos> walls = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
                for (int y = 1; y <= 4; y++) {
                    blocks.put(pos(x, y, z), wall ? BuildBlock.solid(STONE) : BuildBlock.AIR);
                    if (wall) {
                        walls.add(pos(x, y, z));
                    }
                }
                blocks.put(pos(x, 5, z), BuildBlock.solid("minecraft:oak_planks"));
            }
        }

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        int firstScaffolding = -1;
        int firstRoof = -1;
        List<Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                firstScaffolding = firstScaffolding < 0 ? i : firstScaffolding;
            } else if (step.type() == StepType.PLACE) {
                firstRoof = firstRoof < 0 && step.pos().y() == 5 ? i : firstRoof;
                BuildPos floor = step.stand().orElseThrow().below();
                assertFalse(walls.contains(floor), "The builder stands on the wall at " + floor + " for " + step.pos());
                assertTrue(REACH.contains(step.stand().get(), step.pos()), "Out of reach: " + step.pos());
            }
        }
        assertTrue(firstScaffolding >= 0 && firstScaffolding < firstRoof, "The scaffolding must come before the roof");
    }

    /** A block that the plan puts on the ground is a floor for the builder, for example the floor of a house. */
    @Test
    void theBuilderStandsOnANewFloorOnTheGround() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                blocks.put(pos(x, 1, z), BuildBlock.solid("minecraft:oak_planks"));
            }
        }
        BuildPos lamp = pos(0, 4, 0);
        blocks.put(lamp, BuildBlock.solid("minecraft:glowstone"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertEquals(0, plan.count(StepType.PLACE_SCAFFOLDING));
        assertEquals(2, plan.steps().get(index(plan, StepType.PLACE, lamp)).stand().orElseThrow().y());
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    /**
     * The floor of a house on a foundation: the plan puts the planks on the cobblestone. The builder stands on the
     * planks, a floor of the plan in a square of 2 × 2 blocks, for a lamp 3 blocks above the floor.
     */
    @Test
    void theBuilderStandsOnAFloorOverAFoundation() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                blocks.put(pos(x, 1, z), BuildBlock.solid("minecraft:cobblestone"));
                blocks.put(pos(x, 2, z), BuildBlock.solid("minecraft:oak_planks"));
            }
        }
        BuildPos lamp = pos(0, 5, 0);
        blocks.put(lamp, BuildBlock.solid("minecraft:glowstone"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        assertEquals(0, plan.count(StepType.PLACE_SCAFFOLDING));
        assertEquals(3, plan.steps().get(index(plan, StepType.PLACE, lamp)).stand().orElseThrow().y());
    }

    /**
     * A house of two storeys: the floor of the upper storey is on the walls of the lower storey. A villager walks into
     * the house through the doorway, and goes up the steps to the upper floor. The builder stands on the upper floor,
     * and the scaffolding of the roof stands on it.
     */
    @Test
    void theBuilderStandsOnTheFloorOfAnUpperStorey() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                for (int storey = 0; storey < 2; storey++) {
                    int floorY = 1 + storey * 5;
                    blocks.put(pos(x, floorY, z), BuildBlock.solid("minecraft:oak_planks"));
                    for (int y = floorY + 1; y <= floorY + 4; y++) {
                        blocks.put(pos(x, y, z), wall ? BuildBlock.solid(STONE) : BuildBlock.AIR);
                    }
                }
                blocks.put(pos(x, 11, z), BuildBlock.solid("minecraft:oak_planks"));
            }
        }
        // The doorway, the steps from the lower floor to the upper floor, and the hole of the steps in the upper floor.
        blocks.put(pos(0, 2, -4), BuildBlock.AIR);
        blocks.put(pos(0, 3, -4), BuildBlock.AIR);
        for (int step = 0; step < 4; step++) {
            blocks.put(pos(-3 + step, 2 + step, 3), BuildBlock.solid(STONE));
            blocks.put(pos(-3 + step, 6, 3), BuildBlock.AIR);
        }

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        boolean onUpperFloor = plan.steps().stream().anyMatch(step -> step.type() == StepType.PLACE
                && step.stand().map(stand -> stand.y() == 7 && Math.abs(stand.x()) < 4 && Math.abs(stand.z()) < 4)
                        .orElse(false));
        boolean scaffoldingOnUpperFloor = plan.steps().stream()
                .anyMatch(step -> step.type() == StepType.PLACE_SCAFFOLDING && step.pos().y() == 7
                        && Math.abs(step.pos().x()) < 4 && Math.abs(step.pos().z()) < 4);
        assertTrue(onUpperFloor, "The builder must stand on the upper floor");
        assertTrue(scaffoldingOnUpperFloor, "The scaffolding of the roof must stand on the upper floor");
    }

    /**
     * A room with no way into it. For a block of the roof, the nearest position to stand is in the room. The builder
     * stands out of the room, where a villager can walk from the ground.
     */
    @Test
    void theBuilderStandsWhereAVillagerCanWalk() {
        Map<BuildPos, BuildBlock> levelBlocks = new HashMap<>();
        for (int x = 0; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = x == 0 || x == 3 || Math.abs(z) == 2;
                for (int y = 1; y <= 2; y++) {
                    if (wall) {
                        levelBlocks.put(pos(x, y, z), BuildBlock.solid(STONE));
                    }
                }
                levelBlocks.put(pos(x, 3, z), BuildBlock.solid(STONE));
            }
        }
        BuildPos roof = pos(1, 3, 0);
        Map<BuildPos, BuildBlock> blocks = Map.of(roof, BuildBlock.solid("minecraft:oak_planks"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), level(levelBlocks), REACH);

        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        assertEquals(Optional.of(pos(-1, 1, 0)), plan.steps().get(index(plan, StepType.REMOVE, roof)).stand());
        assertEquals(Optional.of(pos(-1, 1, 0)), plan.steps().get(index(plan, StepType.PLACE, roof)).stand());
    }

    /**
     * A tower with no way into it, and a block above its top. The plan does not put scaffolding in the tower: the
     * columns stand out of the tower, where a villager can walk to their bottom.
     */
    @Test
    void scaffoldingStandsWhereAVillagerCanWalk() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = 1; y <= 5; y++) {
                    blocks.put(pos(x, y, z), wall ? BuildBlock.solid(STONE) : BuildBlock.AIR);
                }
            }
        }
        BuildPos top = pos(1, 6, 0);
        blocks.put(top, BuildBlock.solid("minecraft:oak_planks"));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        assertTrue(plan.count(StepType.PLACE_SCAFFOLDING) > 0, "The tower must need scaffolding");
        for (Step step : plan.steps()) {
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                BuildPos cell = step.pos();
                assertTrue(Math.abs(cell.x()) > 2 || Math.abs(cell.z()) > 2, "Scaffolding in the tower at " + cell);
            }
        }
        BuildPos stand = plan.steps().get(index(plan, StepType.PLACE, top)).stand().orElseThrow();
        assertTrue(stand.x() > 2 && REACH.contains(stand, top), "The builder stands at " + stand);
    }

    /**
     * A column goes after the last step that uses it: the column of a high block goes before the column of the next
     * high block. The column of a pillar stays until the top of the pillar.
     */
    @Test
    void columnGoesAfterItsLastStep() {
        BuildPos first = pos(-6, 9, 0);
        BuildPos second = pos(6, 9, 0);
        Map<BuildPos, BuildBlock> blocks = Map.of(first, BuildBlock.solid(STONE), second, BuildBlock.solid(STONE));

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        List<Step> steps = plan.steps();
        int placeFirst = index(plan, StepType.PLACE, first);
        int placeSecond = index(plan, StepType.PLACE, second);
        assertTrue(placeFirst < placeSecond, "The blocks go from the west to the east");
        BuildPos column = steps.get(placeFirst).stand().orElseThrow().below();
        int removal = index(plan, StepType.REMOVE_SCAFFOLDING, column);
        assertTrue(removal > placeFirst && removal < placeSecond,
                "The column of the first block must go between the two blocks: " + removal);
        for (int i = placeFirst + 1; i < removal; i++) {
            assertEquals(StepType.REMOVE_SCAFFOLDING, steps.get(i).type(), "The column goes after its last step");
        }
        assertEquals(plan.count(StepType.PLACE_SCAFFOLDING), plan.count(StepType.REMOVE_SCAFFOLDING));
    }

    /** For a high pillar, the plan makes one scaffolding column higher, and makes no other column. */
    @Test
    void scaffoldingColumnGrowsWithThePillar() {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (int y = 1; y <= 12; y++) {
            blocks.put(pos(0, y, 0), BuildBlock.solid(STONE));
        }

        Plan plan = ConstructionPlanner.plan(new Target(blocks), emptyLevel(), REACH);

        Set<List<Integer>> columns = new HashSet<>();
        Set<BuildPos> cells = new HashSet<>();
        for (Step step : plan.steps()) {
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                columns.add(List.of(step.pos().x(), step.pos().z()));
                assertTrue(cells.add(step.pos()), "Two steps for the scaffolding at " + step.pos());
            }
        }
        assertEquals(1, columns.size(), "Scaffolding columns: " + columns);
        assertEquals(cells.size(), plan.count(StepType.REMOVE_SCAFFOLDING));
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }
}
