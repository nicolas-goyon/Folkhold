package io.github.nicolasgoyon.folkhold.world.village.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Gate;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Segment;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Side;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** The terrain pieces, the water gates and the piers of the wall of a village. */
class VillageWallTest {
    private static final int BASE = 64;
    private static final int SEA = 63;
    private static final PierBlocks PIER = new PierBlocks(Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState());

    /** A square village with a gate on each side. */
    private static WallLayout layout() {
        List<Gate> gates = List.of(new Gate(Side.NORTH, 0), new Gate(Side.EAST, 0), new Gate(Side.SOUTH, 0),
                new Gate(Side.WEST, 0));
        return WallLayout.plan(List.of(new Box(-25, -25, 25, 25)), 4, 40, gates);
    }

    /**
     * On water, a section of the palisade gets no terrain piece: the palisade stands on its foundation. A water gate
     * gets the flat terrain behind it only.
     */
    @Test
    void waterSideHasNoFlatTerrainOutside() {
        WallLayout layout = layout();
        int[] bases = new int[layout.ringSize()];
        Arrays.fill(bases, BASE);
        boolean[] water = new boolean[layout.ringSize()];
        Segment first = layout.segments().get(0);
        water[layout.ringIndex(first.side(), first.from())] = true;
        water[layout.ringIndex(first.side(), first.from() + 1)] = true;
        Map<Gate, Integer> levels = new HashMap<>();
        layout.gates().forEach(gate -> levels.put(gate, BASE));
        Gate north = layout.gates().stream().filter(gate -> gate.side() == Side.NORTH).findFirst().orElseThrow();

        List<TerrainPiece> dry = VillageWall.terrain(layout, bases, levels, new boolean[layout.ringSize()], Set.of());
        List<TerrainPiece> wet = VillageWall.terrain(layout, bases, levels, water, Set.of(north));

        assertEquals(dry.size() - 1, wet.size(), "The first section is on water: no terrain piece");
        Box outline = layout.outline();
        BoundingBox gate = wet.stream().map(TerrainPiece::getBoundingBox)
                .filter(box -> box.minX() == -2 && box.maxX() == 2 && box.maxZ() < 0).findFirst().orElseThrow();
        assertEquals(outline.minZ(), gate.minZ(), "A water gate has no flat terrain outside the line");
        assertEquals(outline.minZ() + VillageWall.ENTRANCE, gate.maxZ(), "It has the flat terrain inside");
    }

    /** A gate opens on water when 2 of the 3 points in front of it are on water. */
    @Test
    void gateOnWaterHasWaterInFront() {
        WallLayout layout = layout();
        Gate north = layout.gates().stream().filter(gate -> gate.side() == Side.NORTH).findFirst().orElseThrow();
        int line = layout.outline().minZ();

        assertTrue(VillageWall.isWaterGate(layout, north, (x, z) -> z < line - 3 ? SEA - 1 : SEA + 2, SEA),
                "Water at 4 and 6 blocks: a water gate");
        assertFalse(VillageWall.isWaterGate(layout, north, (x, z) -> z < line - 5 ? SEA - 1 : SEA + 2, SEA),
                "Water at 6 blocks only: a gate on land");
    }

    /**
     * A pier starts after the outer row of the gate and goes over the water to the first position on land. It has 4
     * rows at least and 12 rows at most. Its deck is at the level of the gate.
     */
    @Test
    void pierGoesOverTheWater() {
        WallLayout layout = layout();
        Gate north = layout.gates().stream().filter(gate -> gate.side() == Side.NORTH).findFirst().orElseThrow();
        int line = layout.outline().minZ();

        PierPiece open = VillageWall.pier(layout, north, BASE, (x, z) -> SEA - 1, SEA, PIER);
        PierPiece shore = VillageWall.pier(layout, north, BASE, (x, z) -> z < line - 8 ? SEA + 2 : SEA - 1, SEA,
                PIER);
        PierPiece shallow = VillageWall.pier(layout, north, BASE, (x, z) -> z < line - 3 ? SEA + 2 : SEA - 1, SEA,
                PIER);

        assertEquals(PierPiece.MAX_LENGTH, open.length(), "Open water: the longest pier");
        assertEquals(7, shore.length(), "Land at 9 blocks from the line: 7 rows from the row at 2 blocks");
        assertEquals(PierPiece.MIN_LENGTH, shallow.length(), "Land near the gate: the shortest pier");
        assertEquals(BASE, open.deckY(), "The deck is at the level of the gate");
        BoundingBox box = open.getBoundingBox();
        assertEquals(line - VillageWall.PIER_START, box.maxZ(), "The pier starts after the outer row of the gate");
        assertEquals(-1, box.minX());
        assertEquals(1, box.maxX());
    }

    /**
     * The terrain is flat along the palisade, in sections of 2 columns at their highest base, on 2 blocks inside and
     * outside the line. In front of each gate, it is flat at the level of the gate, 6 blocks outside and 3 blocks
     * inside. The terrain of a gate also clears its space.
     */
    @Test
    void terrainIsFlatAlongThePalisadeAndAtTheGates() {
        List<Gate> gates = List.of(new Gate(Side.NORTH, 0), new Gate(Side.EAST, 0), new Gate(Side.SOUTH, 0),
                new Gate(Side.WEST, 0));
        WallLayout layout = WallLayout.plan(List.of(new Box(-25, -25, 25, 25)), 4, 40, gates);
        int[] bases = new int[layout.ringSize()];
        Arrays.fill(bases, BASE);
        Segment first = layout.segments().get(0);
        bases[layout.ringIndex(Side.NORTH, first.from() + 1)] = BASE + 1;
        Map<Gate, Integer> levels = new HashMap<>();
        for (Gate gate : layout.gates()) {
            levels.put(gate, BASE - 2);
        }

        List<TerrainPiece> pieces = VillageWall.terrain(layout, bases, levels, new boolean[layout.ringSize()],
                Set.of());

        int sections = layout.segments().stream().mapToInt(segment -> (segment.length() + 1) / 2).sum();
        assertEquals(sections + 4, pieces.size());
        Box outline = layout.outline();
        TerrainPiece section = pieces.get(0);
        assertEquals(BASE + 1, section.ground(), "A section is at its highest base");
        BoundingBox box = section.getBoundingBox();
        assertEquals(outline.minX() + first.from(), box.minX());
        assertEquals(outline.minX() + first.from() + 1, box.maxX());
        assertEquals(outline.minZ() - 2, box.minZ());
        assertEquals(outline.minZ() + 2, box.maxZ());
        TerrainPiece north = pieces.get(sections);
        BoundingBox gate = north.getBoundingBox();
        assertEquals(BASE - 2, north.ground(), "The terrain of a gate is at the level of the gate");
        assertEquals(new BoundingBox(-2, BASE - 2, outline.minZ() - 6, 2, gate.maxY(), outline.minZ() + 3), gate);
        for (int i = 0; i < pieces.size(); i++) {
            TerrainPiece piece = pieces.get(i);
            assertTrue(piece.getBeardifierBox().equals(piece.getBoundingBox()), "The terrain changes near the box");
            assertEquals(i >= sections, piece.clears(), "Only the terrain of a gate clears its space: piece " + i);
        }
    }
}
