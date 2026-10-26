package io.github.nicolasgoyon.folkhold.world.village.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** The terrain pieces of the wall of a village. */
class VillageWallTest {
    private static final int BASE = 64;

    /**
     * The terrain is flat along the palisade, in sections of 2 columns at their highest base, on 2 blocks inside and
     * outside the line. In front of each gate, it is flat at the level of the gate, 6 blocks outside and 3 blocks
     * inside.
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

        List<TerrainPiece> pieces = VillageWall.terrain(layout, bases, levels);

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
        for (TerrainPiece piece : pieces) {
            assertTrue(piece.getBeardifierBox().equals(piece.getBoundingBox()), "The terrain changes near the box");
        }
    }
}
