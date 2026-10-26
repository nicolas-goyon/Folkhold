package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadeBlocks;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** The reference of the repairs and of the growth of a village: its palisade, its plots and its saved data. */
class VillageReferenceTest {
    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("folkhold_world",
            "village/tower");
    private static final ResourceLocation HOUSE_FRONT = ResourceLocation.fromNamespaceAndPath("folkhold_world",
            "house_front");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath("folkhold_world",
            "village/homes");
    private static final BlockState LOG = Blocks.SPRUCE_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final PalisadeBlocks BLOCKS = new PalisadeBlocks(LOG, Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());

    private static VillageReference reference() {
        PalisadePiece piece = new PalisadePiece(10, -4, 0, 1, new int[] {64, 65, 65}, BLOCKS);
        return new VillageReference(List.of(new VillageReference.Piece(TOWER, new BlockPos(10, 63, -9),
                Rotation.CLOCKWISE_90)), List.of(VillageReference.Palisade.of(piece)),
                Optional.of(new BoundingBox(-20, 60, -9, 10, 80, 20)));
    }

    /** The palisade of a piece has the base block and the logs of each column, along the direction of the piece. */
    @Test
    void palisadeHasTheBaseAndTheLogsOfEachColumn() {
        Map<BlockPos, BlockState> blocks = reference().palisadeBlocks();

        assertEquals(3 * 5, blocks.size(), "Each column has its base and 4 logs: " + blocks.keySet());
        assertEquals(Blocks.COBBLESTONE.defaultBlockState(), blocks.get(new BlockPos(10, 64, -4)));
        assertEquals(LOG, blocks.get(new BlockPos(10, 68, -4)));
        assertEquals(Blocks.COBBLESTONE.defaultBlockState(), blocks.get(new BlockPos(10, 65, -2)));
        assertEquals(LOG, blocks.get(new BlockPos(10, 69, -2)));
        assertFalse(blocks.containsKey(new BlockPos(10, 70, -2)), "The point is not a part of the reference");
    }

    /** The area contains the positions inside the palisade, the palisade included, at all heights. */
    @Test
    void areaContainsThePalisade() {
        VillageReference reference = reference();

        assertTrue(reference.isInArea(new BlockPos(10, 0, -4)));
        assertTrue(reference.isInArea(new BlockPos(-20, 200, 20)));
        assertFalse(reference.isInArea(new BlockPos(11, 64, 0)));
        assertFalse(new VillageReference(reference.pieces()).isInArea(BlockPos.ZERO), "No area: no position");
    }

    /** A reference keeps its pieces, its palisades and its area after a save and a load. */
    @Test
    void referenceKeepsItsDataAfterALoad() {
        VillageReference reference = reference();
        CompoundTag tag = reference.save();

        VillageReference loaded = VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), tag).orElseThrow();

        assertEquals(reference, loaded);
        assertEquals(tag, loaded.save());
    }

    /** A reference keeps its plots and the pool of its homes after a save and a load. */
    @Test
    void referenceKeepsItsPlotsAfterALoad() {
        VillageReference base = reference();
        VillagePlots.Plot free = new VillagePlots.Plot(new BlockPos(0, 64, 8), Direction.WEST, HOUSE_FRONT, false);
        VillagePlots.Plot built = new VillagePlots.Plot(new BlockPos(4, 64, 8), Direction.EAST, HOUSE_FRONT, true);
        VillageReference reference = new VillageReference(base.pieces(), base.palisades(), base.area(),
                List.of(free, built), Optional.empty()).withHomes(HOMES);

        VillageReference loaded = VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), reference.save())
                .orElseThrow();

        assertEquals(reference, loaded);
        assertEquals(new BlockPos(-1, 64, 8), loaded.plots().get(0).front(), "The front of a plot to the west");
    }

    /** A reference keeps the boxes of its piers after a save and a load. Data with no piers gives no piers. */
    @Test
    void referenceKeepsItsPiersAfterALoad() {
        VillageReference base = reference();
        List<BoundingBox> piers = List.of(new BoundingBox(12, 58, -2, 22, 66, 0), new BoundingBox(-4, 58, 21, -2, 66,
                31));
        VillageReference reference = new VillageReference(base.pieces(), base.palisades(), base.area(), List.of(),
                Optional.empty(), piers);
        CompoundTag tag = reference.save();

        VillageReference loaded = VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), tag).orElseThrow();
        tag.remove("Piers");
        VillageReference old = VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), tag).orElseThrow();

        assertEquals(reference, loaded);
        assertEquals(piers, loaded.withHomes(HOMES).piers(), "The pool of the homes keeps the piers");
        assertTrue(old.piers().isEmpty(), "Data with no piers gives no piers");
    }

    /** The data of a reference with no template piece gives no reference. */
    @Test
    void referenceWithNoPieceIsNotValid() {
        CompoundTag tag = reference().save();
        tag.remove("Pieces");

        assertTrue(VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), tag).isEmpty());
    }
}
