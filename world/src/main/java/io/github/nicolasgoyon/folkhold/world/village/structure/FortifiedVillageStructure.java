package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The faction village: the pieces of the jigsaw pools, in a closed wall.
 *
 * <ul>
 *   <li>The start piece goes on the terrain. Its origin is the north-west corner of the start chunk, and its
 *       rotation is random.</li>
 *   <li>The terrain around the middle of the start piece must accept a village (see {@link VillageSite}): the plaza
 *       is not on water, the water does not cover half of the place, and the slope is not one of the steepest. The
 *       discovery of the settlements uses the same check: a settlement has a village.</li>
 *   <li>The other pieces of the pools go on the connectors of the start piece, as in a vanilla jigsaw
 *       structure.</li>
 *   <li>Then the structure adds the wall around all the pieces and around the space of a home on each free plot (see
 *       {@link VillageWall}, {@link VillagePlots}). The wall has towers, a gate on each axis of the start piece and
 *       the palisade. Paths go from the gates to the streets.</li>
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
 *   <li>{@code homes}: the pool of the homes that the builders of the village build on the free plots (see
 *       {@link VillagePlots}).</li>
 * </ul>
 */
public final class FortifiedVillageStructure extends Structure {
    /** The largest distance of the pieces from the middle, with the wall and the terrain adaptation, in blocks. */
    private static final int MAX_RANGE = JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE;
    /** The terrain adaptation changes the terrain at this distance from a piece, in blocks. */
    private static final int ADAPTATION_RANGE = 12;

    public static final MapCodec<FortifiedVillageStructure> CODEC = RecordCodecBuilder
            .<FortifiedVillageStructure>mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(FortifiedVillageStructure::startPool),
                    Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size")
                            .forGetter(FortifiedVillageStructure::size),
                    Codec.intRange(1, MAX_RANGE).fieldOf("max_distance_from_center")
                            .forGetter(FortifiedVillageStructure::maxDistanceFromCenter),
                    WallSettings.CODEC.fieldOf("wall").forGetter(FortifiedVillageStructure::wall),
                    StructureTemplatePool.CODEC.fieldOf("homes").forGetter(FortifiedVillageStructure::homes))
                    .apply(instance, FortifiedVillageStructure::new))
            .validate(FortifiedVillageStructure::verifyRange);

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final WallSettings wall;
    private final Holder<StructureTemplatePool> homes;

    public FortifiedVillageStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
            int maxDistanceFromCenter, WallSettings wall, Holder<StructureTemplatePool> homes) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.wall = wall;
        this.homes = homes;
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
        // The position of a stub is the middle of the start piece, on the surface of the terrain.
        return JigsawPlacement.addPieces(context, startPool, Optional.empty(), size, start, false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG), maxDistanceFromCenter, PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING)
                .filter(village -> checkSite(context, village.position()) == VillageSite.Result.ACCEPTED)
                .map(village -> new GenerationStub(village.position(),
                        builder -> generate(context, village, builder)));
    }

    /**
     * Checks the terrain around the middle of the start piece, before the generation of the chunks (see
     * {@link VillageSite}). The structure check of the game and the discovery of the settlements call this method
     * on the server thread: it reads few heights.
     *
     * @param context the context of the generation
     * @param middle  the middle of the start piece, on the surface of the terrain
     * @return the result of the check
     */
    static VillageSite.Result checkSite(GenerationContext context, BlockPos middle) {
        ChunkGenerator generator = context.chunkGenerator();
        int floor = generator.getBaseHeight(middle.getX(), middle.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        return VillageSite.check(middle.getY(), floor, generator.getSeaLevel(),
                (dx, dz) -> generator.getBaseHeight(middle.getX() + dx, middle.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
    }

    /**
     * Adds the pieces of the pools, then the pieces of the wall around them. The wall also keeps the space of a home on
     * each free plot (see {@link VillagePlots}).
     */
    private void generate(GenerationContext context, GenerationStub village, StructurePiecesBuilder builder) {
        List<StructurePiece> pieces = village.getPiecesBuilder().build().pieces();
        pieces.forEach(builder::addPiece);
        StructureTemplateManager templates = context.structureTemplateManager();
        List<BoundingBox> freePlots = VillagePlots.of(templates, pieces).stream()
                .filter(plot -> !plot.built())
                .flatMap(plot -> VillagePlots.space(templates, homes.value(), plot).stream())
                .toList();
        VillageWall.add(context, pieces, freePlots, village.position(), wall).forEach(builder::addPiece);
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

    /** The pool of the homes that the builders build on the free plots (see {@link VillagePlots}). */
    public Holder<StructureTemplatePool> homes() {
        return homes;
    }
}
