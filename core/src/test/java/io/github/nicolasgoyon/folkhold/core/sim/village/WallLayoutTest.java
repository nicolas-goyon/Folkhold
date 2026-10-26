package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Cell;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Gate;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Segment;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Side;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Tower;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTest {
    private static final int MARGIN = 4;
    private static final int SPACING = 40;

    /** The outline is the box of the pieces with the margin, and the line of the palisade outside the margin. */
    @Test
    void outlineIsTheBoxOfThePiecesWithTheMargin() {
        List<Box> pieces = List.of(new Box(0, 0, 14, 14), new Box(-20, 5, -1, 9), new Box(5, 15, 9, 40));

        WallLayout wall = WallLayout.plan(pieces, MARGIN, SPACING, List.of());

        assertEquals(new Box(-25, -5, 19, 45), wall.outline());
    }

    /** A small village gets the minimum size on each side, around its pieces. */
    @Test
    void smallVillageGetsTheMinimumSize() {
        WallLayout wall = WallLayout.plan(List.of(new Box(0, 0, 4, 4)), MARGIN, SPACING, List.of());

        assertEquals(WallLayout.MIN_SIDE, wall.outline().width());
        assertEquals(WallLayout.MIN_SIDE, wall.outline().depth());
        assertTrue(wall.outline().contains(0, 0) && wall.outline().contains(4, 4), "The outline: " + wall.outline());
    }

    /**
     * A square village of 61 blocks has a gate in the middle of each side. Each side has a corner tower and one more
     * tower after the gate.
     */
    @Test
    void squareVillageHasTwoTowersOnEachSide() {
        Box pieces = new Box(-25, -25, 25, 25);
        WallLayout wall = WallLayout.plan(List.of(pieces), MARGIN, SPACING, gatesAtTheMiddle(0, 0));

        assertEquals(61, wall.outline().width());
        assertEquals(8, wall.towers().size(), "The towers: " + wall.towers());
        for (Side side : Side.values()) {
            List<Tower> towers = wall.towers().stream().filter(tower -> tower.side() == side).toList();
            assertEquals(List.of(new Tower(side, 0), new Tower(side, 35)), towers);
        }
        assertEquals(4, wall.gates().size());
        assertClosed(wall);
    }

    /** A gate near a corner goes away from the corner tower and its stair. */
    @Test
    void gateNearACornerMoves() {
        WallLayout wall = WallLayout.plan(List.of(new Box(0, 0, 40, 40)), MARGIN, SPACING,
                List.of(new Gate(Side.NORTH, -5), new Gate(Side.EAST, 100)));

        Box outline = wall.outline();
        int first = WallLayout.TOWER + WallLayout.STAIR + WallLayout.GATE_CLEARANCE + WallLayout.GATE / 2;
        assertEquals(new Gate(Side.NORTH, outline.minX() + first), wall.gates().get(0));
        int last = WallLayout.length(outline, Side.EAST) - 1 - WallLayout.TOWER - WallLayout.GATE_CLEARANCE
                - WallLayout.GATE / 2;
        assertEquals(new Gate(Side.EAST, outline.minZ() + last), wall.gates().get(1));
        assertClosed(wall);
    }

    /** A second gate on a side does not count. */
    @Test
    void secondGateOfASideDoesNotCount() {
        WallLayout wall = WallLayout.plan(List.of(new Box(0, 0, 40, 40)), MARGIN, SPACING,
                List.of(new Gate(Side.SOUTH, 20), new Gate(Side.SOUTH, 30)));

        assertEquals(List.of(new Gate(Side.SOUTH, 20)), wall.gates());
    }

    /** The ring goes clockwise from the north-west corner, and the last block of a side starts the next side. */
    @Test
    void ringGoesClockwiseFromTheNorthWestCorner() {
        WallLayout wall = WallLayout.plan(List.of(new Box(0, 0, 30, 20)), 0, SPACING, List.of());
        Box outline = wall.outline();

        assertEquals(2 * (outline.width() - 1) + 2 * (outline.depth() - 1), wall.ringSize());
        assertEquals(new Cell(outline.minX(), outline.minZ()), wall.ringCell(0));
        assertEquals(new Cell(outline.maxX(), outline.minZ()), wall.ringCell(wall.ringIndex(Side.EAST, 0)));
        assertEquals(new Cell(outline.maxX(), outline.maxZ()), wall.ringCell(wall.ringIndex(Side.SOUTH, 0)));
        assertEquals(new Cell(outline.minX(), outline.maxZ()), wall.ringCell(wall.ringIndex(Side.WEST, 0)));
        for (Side side : Side.values()) {
            int length = WallLayout.length(outline, side);
            assertEquals(wall.ringIndex(side.next(), 0), wall.ringIndex(side, length - 1), "The corner after " + side);
            for (int along = 0; along < length; along++) {
                assertEquals(wall.cell(side, along), wall.ringCell(wall.ringIndex(side, along)));
            }
        }
        Set<Cell> cells = new HashSet<>();
        for (int i = 0; i < wall.ringSize(); i++) {
            Cell cell = wall.ringCell(i);
            assertTrue(cells.add(cell), "Each block of the ring is in the ring once: " + cell);
            boolean onLine = cell.x() == outline.minX() || cell.x() == outline.maxX() || cell.z() == outline.minZ()
                    || cell.z() == outline.maxZ();
            assertTrue(onLine && outline.contains(cell.x(), cell.z()), "A block of the ring is on the line: " + cell);
        }
    }

    /** The box of a tower is inside the outline, on the line of its side and of the corner. */
    @Test
    void towerBoxesAreInsideTheOutline() {
        WallLayout wall = WallLayout.plan(List.of(new Box(0, 0, 40, 30)), MARGIN, SPACING, List.of());
        Box outline = wall.outline();

        assertEquals(new Box(outline.minX(), outline.minZ(), outline.minX() + 4, outline.minZ() + 4),
                wall.towerBox(new Tower(Side.NORTH, 0)));
        assertEquals(new Box(outline.maxX() - 4, outline.minZ(), outline.maxX(), outline.minZ() + 4),
                wall.towerBox(new Tower(Side.EAST, 0)));
        assertEquals(new Box(outline.maxX() - 4, outline.maxZ() - 4, outline.maxX(), outline.maxZ()),
                wall.towerBox(new Tower(Side.SOUTH, 0)));
        assertEquals(new Box(outline.minX(), outline.maxZ() - 4, outline.minX() + 4, outline.maxZ()),
                wall.towerBox(new Tower(Side.WEST, 0)));
        assertEquals(new Box(outline.minX() + 20, outline.minZ(), outline.minX() + 24, outline.minZ() + 4),
                wall.towerBox(new Tower(Side.NORTH, 20)));
        assertEquals(9, wall.ringIndices(wall.towerBox(new Tower(Side.WEST, 0))).size(),
                "A corner tower covers 9 blocks of the ring");
    }

    /** For many villages, the wall has no gap, and the towers obey the rules of the spacing and of the gates. */
    @Test
    void manyVillagesHaveAClosedWall() {
        Random random = new Random(83);
        for (int run = 0; run < 500; run++) {
            List<Box> pieces = new ArrayList<>();
            int count = 1 + random.nextInt(6);
            for (int i = 0; i < count; i++) {
                int x = random.nextInt(120) - 60;
                int z = random.nextInt(120) - 60;
                pieces.add(new Box(x, z, x + random.nextInt(20), z + random.nextInt(20)));
            }
            List<Gate> gates = new ArrayList<>();
            for (Side side : Side.values()) {
                if (random.nextBoolean()) {
                    gates.add(new Gate(side, random.nextInt(160) - 80));
                }
            }
            int spacing = WallLayout.MIN_TOWER_SPACING + random.nextInt(30);

            WallLayout wall = WallLayout.plan(pieces, random.nextInt(8), spacing, gates);

            assertClosed(wall);
            assertTowerRules(wall, spacing);
        }
    }

    /** The plan needs a piece, a margin of 0 or more and a sufficient tower spacing. */
    @Test
    void badArgumentsAreRefused() {
        List<Box> pieces = List.of(new Box(0, 0, 10, 10));

        assertThrows(IllegalArgumentException.class, () -> WallLayout.plan(List.of(), MARGIN, SPACING, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WallLayout.plan(pieces, -1, SPACING, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> WallLayout.plan(pieces, MARGIN, WallLayout.MIN_TOWER_SPACING - 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Box(1, 0, 0, 0));
    }

    private static List<Gate> gatesAtTheMiddle(int x, int z) {
        return List.of(new Gate(Side.NORTH, x), new Gate(Side.EAST, z), new Gate(Side.SOUTH, x),
                new Gate(Side.WEST, z));
    }

    /** Each block of the ring is under one tower, one gate or one segment of palisade: the wall has no gap. */
    private static void assertClosed(WallLayout wall) {
        int[] cover = new int[wall.ringSize()];
        for (Tower tower : wall.towers()) {
            for (int index : wall.ringIndices(wall.towerBox(tower))) {
                cover[index]++;
            }
        }
        for (Gate gate : wall.gates()) {
            for (int index : wall.ringIndices(gate)) {
                cover[index]++;
            }
        }
        for (Segment segment : wall.segments()) {
            assertTrue(segment.from() <= segment.to(), "A segment is not empty: " + segment);
            for (int along = segment.from(); along <= segment.to(); along++) {
                cover[wall.ringIndex(segment.side(), along)]++;
            }
        }
        for (int i = 0; i < cover.length; i++) {
            assertEquals(1, cover[i], "The block " + wall.ringCell(i) + " of " + wall.outline() + " is under "
                    + cover[i] + " parts of the wall: " + wall.towers() + " " + wall.gates() + " "
                    + wall.segments());
        }
    }

    /**
     * The stair of a tower is before the next corner tower and away from the gates. The distance between the middles of
     * 2 towers is the maximum or less.
     */
    private static void assertTowerRules(WallLayout wall, int spacing) {
        for (Side side : Side.values()) {
            int length = WallLayout.length(wall.outline(), side);
            List<Tower> towers = wall.towers().stream().filter(tower -> tower.side() == side).toList();
            assertEquals(new Tower(side, 0), towers.get(0), "The corner tower of " + side);
            int previous = WallLayout.TOWER / 2;
            for (Tower tower : towers) {
                int stairEnd = tower.along() + WallLayout.TOWER + WallLayout.STAIR - 1;
                assertTrue(stairEnd < length - WallLayout.TOWER, "The stair of " + tower + " ends before the corner");
                for (Gate gate : wall.gates()) {
                    if (gate.side() == side) {
                        int middle = wall.along(gate);
                        int from = middle - WallLayout.GATE / 2 - WallLayout.GATE_CLEARANCE;
                        int to = middle + WallLayout.GATE / 2 + WallLayout.GATE_CLEARANCE;
                        assertFalse(stairEnd >= from && tower.along() <= to, tower + " is away from " + gate);
                    }
                }
                if (tower.along() > 0) {
                    int middle = tower.along() + WallLayout.TOWER / 2;
                    assertTrue(middle - previous <= spacing, "The spacing before " + tower + " in " + wall.towers());
                    previous = middle;
                }
            }
            int nextCorner = length - 1 - WallLayout.TOWER / 2;
            assertTrue(nextCorner - previous <= spacing, "The spacing at the end of " + side + ": " + wall.towers());
        }
    }
}
