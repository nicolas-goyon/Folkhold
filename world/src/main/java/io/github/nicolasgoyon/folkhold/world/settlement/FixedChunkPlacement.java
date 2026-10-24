package io.github.nicolasgoyon.folkhold.world.settlement;

import com.mojang.datafixers.Products.P2;
import com.mojang.datafixers.Products.P5;
import com.mojang.datafixers.Products.P7;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructurePlacements;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement with one start chunk. The data pack of the test world uses it for the faction villages: one
 * village starts next to the spawn point (see {@link io.github.nicolasgoyon.folkhold.world.testworld.TestWorldPack}).
 *
 * <p>Type {@code folkhold_world:fixed_chunk}. The JSON object has the fields of all the structure placements
 * ({@code salt}, and the optional fields), and the fields {@code chunk_x} and {@code chunk_z}: the start chunk.
 */
public final class FixedChunkPlacement extends StructurePlacement {
    // The fields of the base class contain the exclusion zone, which the game marks as deprecated.
    @SuppressWarnings("deprecation")
    public static final MapCodec<FixedChunkPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        P5<RecordCodecBuilder.Mu<FixedChunkPlacement>, Vec3i, FrequencyReductionMethod, Float, Integer,
                Optional<ExclusionZone>> base = placementCodec(instance);
        P2<RecordCodecBuilder.Mu<FixedChunkPlacement>, Integer, Integer> chunk = instance.group(
                Codec.INT.fieldOf("chunk_x").forGetter(placement -> placement.chunk.x),
                Codec.INT.fieldOf("chunk_z").forGetter(placement -> placement.chunk.z));
        return new P7<>(base.t1(), base.t2(), base.t3(), base.t4(), base.t5(), chunk.t1(), chunk.t2())
                .apply(instance, FixedChunkPlacement::new);
    });

    private final ChunkPos chunk;

    /**
     * Makes a placement.
     *
     * @param locateOffset              the offset of the position that the locate command gives
     * @param frequencyReductionMethod the method that applies the frequency
     * @param frequency                 the chance that the structure starts, from 0 to 1
     * @param salt                      the salt of the random source of the frequency
     * @param exclusionZone             the zone around another structure set where the structure does not start
     * @param chunkX                    the X coordinate of the start chunk
     * @param chunkZ                    the Z coordinate of the start chunk
     */
    // The constructor of the base class needs the exclusion zone, which the game marks as deprecated.
    @SuppressWarnings("deprecation")
    public FixedChunkPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
            int salt, Optional<ExclusionZone> exclusionZone, int chunkX, int chunkZ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.chunk = new ChunkPos(chunkX, chunkZ);
    }

    /** The start chunk. */
    public ChunkPos chunk() {
        return chunk;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z) {
        return x == chunk.x && z == chunk.z;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructurePlacements.FIXED_CHUNK.get();
    }
}
