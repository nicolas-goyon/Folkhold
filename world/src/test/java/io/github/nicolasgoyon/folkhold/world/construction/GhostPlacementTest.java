package io.github.nicolasgoyon.folkhold.world.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

/** The positions of a ghost: the structure stands on the anchor, centered, with each turn and each mirror. */
class GhostPlacementTest {
    private static final Vec3i SIZE = new Vec3i(9, 10, 6);
    private static final BlockPos ANCHOR = new BlockPos(100, 64, -40);

    /** The lowest layer is at the height of the anchor, and the center of the box is above the anchor. */
    @Test
    void structureStandsOnTheAnchor() {
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                BlockPos origin = GhostPlacement.originFor(SIZE, rotation, mirror, ANCHOR);
                BoundingBox box = GhostPlacement.box(SIZE, rotation, mirror, origin);
                String name = rotation + " " + mirror;
                assertEquals(ANCHOR.getY(), box.minY(), name);
                assertEquals(ANCHOR.getX(), Math.floorDiv(box.minX() + box.maxX(), 2), name);
                assertEquals(ANCHOR.getZ(), Math.floorDiv(box.minZ() + box.maxZ(), 2), name);
                boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
                assertEquals(turned ? SIZE.getZ() : SIZE.getX(), box.getXSpan(), name);
                assertEquals(turned ? SIZE.getX() : SIZE.getZ(), box.getZSpan(), name);
                assertEquals(SIZE.getY(), box.getYSpan(), name);
            }
        }
    }

    /**
     * Each block of the template is in the box, and its position is the position of the vanilla placement with the same
     * settings: the server makes the target of the construction with these settings.
     */
    @Test
    void blocksAreAtThePositionsOfTheVanillaPlacement() {
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                BlockPos origin = GhostPlacement.originFor(SIZE, rotation, mirror, ANCHOR);
                BoundingBox box = GhostPlacement.box(SIZE, rotation, mirror, origin);
                for (BlockPos local : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(SIZE.getX() - 1,
                        SIZE.getY() - 1, SIZE.getZ() - 1))) {
                    BlockPos pos = GhostPlacement.levelPos(local, rotation, mirror, origin);
                    assertTrue(box.isInside(pos), rotation + " " + mirror + " " + local);
                    assertEquals(StructureTemplate.calculateRelativePosition(
                            GhostPlacement.settings(rotation, mirror), local).offset(origin), pos);
                }
            }
        }
    }
}
