package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

/**
 * The reference state of a village for the repairs (see {@link VillageRepairs}): the structure pieces of the village
 * at the generation. Each piece is a structure template at a position, with a turn. The pieces come in the order of
 * the generation: a piece replaces the blocks of the pieces before it.
 *
 * @param pieces the pieces of the village
 */
public record VillageReference(List<Piece> pieces) {
    /**
     * A structure piece of a village.
     *
     * @param template the id of the structure template
     * @param origin   the position of the origin of the template in the level
     * @param rotation the turn of the template
     */
    public record Piece(ResourceLocation template, BlockPos origin, Rotation rotation) {
        public Piece {
            origin = origin.immutable();
        }
    }

    public VillageReference {
        pieces = List.copyOf(pieces);
    }

    /**
     * The reference of the village at a position: the pieces of the Folkhold village structure that contains the
     * position. A piece that is not a structure template does not count.
     *
     * @param level the level
     * @param heart the position of the village heart
     * @return the reference, or empty if no village structure contains the position
     */
    public static Optional<VillageReference> find(ServerLevel level, BlockPos heart) {
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOptional(Settlements.VILLAGE_STRUCTURE);
        if (structure.isEmpty()) {
            return Optional.empty();
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(heart, structure.get());
        if (!start.isValid()) {
            return Optional.empty();
        }
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        List<Piece> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pool) {
                templateOf(ops, pool.getElement())
                        .ifPresent(template -> pieces.add(new Piece(template, pool.getPosition(), pool.getRotation())));
            }
        }
        return pieces.isEmpty() ? Optional.empty() : Optional.of(new VillageReference(pieces));
    }

    /** The id of the template of a pool element, from the saved form of the element. */
    private static Optional<ResourceLocation> templateOf(RegistryOps<Tag> ops, StructurePoolElement element) {
        return StructurePoolElement.CODEC.encodeStart(ops, element)
                .resultOrPartial(error -> FolkholdWorld.LOGGER.debug("Repair: pool element not saved: {}", error))
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .filter(tag -> tag.contains("location", Tag.TAG_STRING))
                .map(tag -> ResourceLocation.tryParse(tag.getString("location")));
    }
}
