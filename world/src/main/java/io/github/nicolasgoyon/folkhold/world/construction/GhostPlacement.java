package io.github.nicolasgoyon.folkhold.world.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The position of a structure template that an operator places with a ghost (see {@link ConstructionPlacement}).
 *
 * <p>The structure stands on an anchor block: the lowest layer of the structure is at the height of the anchor, and
 * the center of the structure is above the anchor. After a turn or a mirror, the structure stays centered on the
 * anchor. The pivot of the turn is the origin of the template, as in {@link StructurePlaceSettings}. Thus the client
 * and the server get the same positions from the origin, the turn and the mirror.
 */
public final class GhostPlacement {
    private GhostPlacement() {
    }

    /** The settings of a template with a turn and a mirror. */
    public static StructurePlaceSettings settings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
    }

    /**
     * The position in the level of a block of a template.
     *
     * @param local    the position of the block in the template
     * @param rotation the turn of the template
     * @param mirror   the mirror of the template
     * @param origin   the position of the origin of the template in the level
     * @return the position of the block in the level
     */
    public static BlockPos levelPos(BlockPos local, Rotation rotation, Mirror mirror, BlockPos origin) {
        return StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO).offset(origin);
    }

    /**
     * The box of a template in the level.
     *
     * @param size     the size of the template
     * @param rotation the turn of the template
     * @param mirror   the mirror of the template
     * @param origin   the position of the origin of the template in the level
     * @return the box of the template
     */
    public static BoundingBox box(Vec3i size, Rotation rotation, Mirror mirror, BlockPos origin) {
        BlockPos first = levelPos(BlockPos.ZERO, rotation, mirror, origin);
        BlockPos last = levelPos(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1), rotation, mirror,
                origin);
        return BoundingBox.fromCorners(first, last);
    }

    /**
     * The origin of a template that stands on an anchor block.
     *
     * @param size     the size of the template
     * @param rotation the turn of the template
     * @param mirror   the mirror of the template
     * @param anchor   the anchor: the lowest layer of the structure is at its height, and the center of the structure
     *                 is above it
     * @return the position of the origin of the template in the level
     */
    public static BlockPos originFor(Vec3i size, Rotation rotation, Mirror mirror, BlockPos anchor) {
        BoundingBox box = box(size, rotation, mirror, BlockPos.ZERO);
        int centerX = Math.floorDiv(box.minX() + box.maxX(), 2);
        int centerZ = Math.floorDiv(box.minZ() + box.maxZ(), 2);
        return new BlockPos(anchor.getX() - centerX, anchor.getY() - box.minY(), anchor.getZ() - centerZ);
    }
}
