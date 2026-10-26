package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * The faction village: the pieces of the jigsaw pools, in a closed wall.
 *
 * <ul>
 *   <li>The terrain around the start must accept a village (see {@link VillageSite}): no water at the place of the
 *       plaza, little water, and no steep relief. The check reads a grid of {@link #SITE_GRID} x {@link #SITE_GRID}
 *       points, {@link #SITE_STEP} blocks apart.</li>
 *   <li>The start piece is on the terrain, in the middle of the start chunk. The other pieces of the pools go on its
 *       connectors, as in a vanilla jigsaw structure.</li>
 *   <li>Then the structure adds the wall around all the pieces (see {@link VillageWall}). The wall has towers, a gate
 *       on each axis of the start piece and the palisade. Paths go from the gates to the streets.</li>
 * </ul>
 *
 * <p>Type {@code folkhold_world:fortified_village}. The JSON object has the fields of all the structures, and these
 * fields:
 *
 * <ul>
 *   <li>{@code start_pool}: the pool of the start piece.</li>
 *   <li>{@code size}: the maximum number of connections from the start piece to a piece, as in a jigsaw
 *       structure.</li>
 *   <li>{@code max_distance_from_center}: the pieces of the pools stay in this distance from the middle of the start
 *       piece, in blocks.</li>
 *   <li>{@code wall}: the settings of the wall (see {@link WallSettings}).</li>
 * </ul>
 */
public final class FortifiedVillageStructure extends Structure {
    /** The largest distance of the pieces from the middle, with the wall and the terrain adaptation, in blocks. */
    private static final int MAX_RANGE = JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE;
    /** The terrain adaptation changes the terrain at this distance from a piece, in blocks. */
    private static final int ADAPTATION_RANGE = 12;
    /** The number of points on each side of the grid of the check of the terrain. */
    static final int SITE_GRID = 9;
    /** The distance between 2 points of the grid of the check of the terrain, in blocks. */
    static final int SITE_STEP = 10;

    public static final MapCodec<FortifiedVillageStructure> CODEC = RecordCodecBuilder
            .<FortifiedVillageStructure>mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(FortifiedVillageStructure::startPool),
                    Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size")
                            .forGetter(FortifiedVillageStructure::size),
                    Codec.intRange(1, MAX_RANGE).fieldOf("max_distance_from_center")
                            .forGetter(FortifiedVillageStructure::maxDistanceFromCenter),
                    WallSettings.CODEC.fieldOf("wall").forGetter(FortifiedVillageStructure::wall))
                    .apply(instance, FortifiedVillageStructure::new))
            .validate(FortifiedVillageStructure::verifyRange);

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final WallSettings wall;

    public FortifiedVillageStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
            int maxDistanceFromCenter, WallSettings wall) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.wall = wall;
    }

    /**
     * The game places the pieces of a structure only in a range of 8 chunks from the start chunk. The wall is outside
     * the pieces of the pools: it needs the margin, the line of the palisade and the outer row of the gates.
     */
    private static DataResult<FortifiedVillageStructure> verifyRange(FortifiedVillageStructure structure) {
        int adaptation = structure.terrainAdaptation() == TerrainAdjustment.NONE ? 0 : ADAPTATION_RANGE;
        int range = structure.maxDistanceFromCenter + structure.wall.margin() + 2 + adaptation;
        return range > MAX_RANGE
                ? DataResult.error(() -> "The village with its wall and its terrain adaptation goes " + range
                        + " blocks from its middle: the limit is " + MAX_RANGE)
                : DataResult.success(structure);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos start = new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
        if (checkSite(context, start) != VillageSite.Result.ACCEPTED) {
            return Optional.empty();
        }
        return JigsawPlacement.addPieces(context, startPool, Optional.empty(), size, start, false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG), maxDistanceFromCenter, PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING)
                .map(village -> new GenerationStub(village.position(),
                        builder -> generate(context, village, builder)));
    }

    /**
     * Checks the terrain around the start of a village, before the generation of the chunks (see
     * {@link VillageSite}). The start piece turns around the start: the grid has the start in its middle.
     *
     * @param context the context of the generation
     * @param start   the start of the village
     * @return the result of the check
     */
    static VillageSite.Result checkSite(GenerationContext context, BlockPos start) {
        int half = SITE_GRID / 2;
        int[] surface = new int[SITE_GRID * SITE_GRID];
        int[] floor = new int[SITE_GRID * SITE_GRID];
        for (int row = 0; row < SITE_GRID; row++) {
            for (int column = 0; column < SITE_GRID; column++) {
                int x = start.getX() + (column - half) * SITE_STEP;
                int z = start.getZ() + (row - half) * SITE_STEP;
                int i = row * SITE_GRID + column;
                surface[i] = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                floor[i] = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
            }
        }
        return VillageSite.check(SITE_GRID, surface, floor);
    }

    /** Adds the pieces of the pools, then the pieces of the wall around them. */
    private void generate(GenerationContext context, GenerationStub village, StructurePiecesBuilder builder) {
        List<StructurePiece> pieces = village.getPiecesBuilder().build().pieces();
        pieces.forEach(builder::addPiece);
        VillageWall.add(context, pieces, village.position(), wall).forEach(builder::addPiece);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.FORTIFIED_VILLAGE.get();
    }

    /** The pool of the start piece. */
    public Holder<StructureTemplatePool> startPool() {
        return startPool;
    }

    /** The maximum number of connections from the start piece to a piece. */
    public int size() {
        return size;
    }

    /** The maximum distance of the pieces of the pools from the middle of the start piece, in blocks. */
    public int maxDistanceFromCenter() {
        return maxDistanceFromCenter;
    }

    /** The settings of the wall. */
    public WallSettings wall() {
        return wall;
    }
}
