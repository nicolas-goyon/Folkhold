package io.github.nicolasgoyon.folkhold.world.level;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.Fluids;

/**
 * The columns of the terrain before the generation of the chunks, with the rules of {@link TerrainColumns}, kept for
 * the next reads. One cache serves one task on one thread, for example the layout of one village. It is not
 * thread-safe. Do not share it between threads.
 *
 * <p>The noise of the terrain has cells of 4 blocks on the horizontal plane. A grid column has an X and a Z that are
 * multiples of {@link #GRID}. The first read of a grid column ({@link #column}) reads all the grid columns of its chunk
 * from one noise object ({@link NoiseChunk}): the noise object calculates the noise of the chunk one time. One noise
 * object covers one chunk only, because its aquifer covers one chunk. The cache does not keep the noise object.
 *
 * <p>The other reads read one column from a noise object of one cell, as {@link ChunkGenerator#getBaseColumn} does.
 * {@link #point} reads one column also for a grid column: the check of a site reads few columns, far apart. A generator
 * that is not a {@link NoiseBasedChunkGenerator} (for example the flat world) gives each column from
 * {@link ChunkGenerator#getBaseColumn}. The columns are the same in all modes when the lowest Y of the noise is a
 * multiple of the cell height (all the vanilla noise settings).
 */
public final class TerrainCache {
    /** The distance between 2 grid columns, in blocks. */
    public static final int GRID = 4;

    private static final Predicate<BlockState> SURFACE = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
    private static final Predicate<BlockState> FLOOR = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();

    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor height;
    /** The generator of the noise, or null when the cache reads each column with {@code getBaseColumn}. */
    @Nullable
    private final NoiseBasedChunkGenerator noise;
    private final Long2ObjectMap<PlotTerrain.Column> columns = new Long2ObjectOpenHashMap<>();
    private int chunks;

    private TerrainCache(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, boolean chunked) {
        this.generator = generator;
        this.random = random;
        this.height = height;
        this.noise = chunked && generator.getClass() == NoiseBasedChunkGenerator.class
                && hasGridCells((NoiseBasedChunkGenerator) generator, height)
                        ? (NoiseBasedChunkGenerator) generator : null;
    }

    /**
     * Makes the cache of a generator, with one noise object for each chunk when the generator is a
     * {@link NoiseBasedChunkGenerator}.
     *
     * @param generator the chunk generator
     * @param random    the random state of the generator
     * @param height    the heights of the level
     * @return the cache
     */
    public static TerrainCache of(ChunkGenerator generator, RandomState random, LevelHeightAccessor height) {
        return new TerrainCache(generator, random, height, true);
    }

    /**
     * Makes the cache of the generator of a structure context.
     *
     * @param context the context of the generation
     * @return the cache
     */
    public static TerrainCache of(Structure.GenerationContext context) {
        return of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
    }

    /**
     * Makes a cache that reads each column with {@link ChunkGenerator#getBaseColumn}, with no noise object for each
     * chunk. It gives the same columns, more slowly. The tests compare the two caches.
     *
     * @param generator the chunk generator
     * @param random    the random state of the generator
     * @param height    the heights of the level
     * @return the cache
     */
    public static TerrainCache direct(ChunkGenerator generator, RandomState random, LevelHeightAccessor height) {
        return new TerrainCache(generator, random, height, false);
    }

    /**
     * The column at a position. The first read of a grid column reads all the grid columns of its chunk.
     *
     * @param x the X of the column
     * @param z the Z of the column
     * @return the column
     */
    public PlotTerrain.Column column(int x, int z) {
        PlotTerrain.Column column = columns.get(BlockPos.asLong(x, 0, z));
        if (column == null && noise != null && Math.floorMod(x, GRID) == 0 && Math.floorMod(z, GRID) == 0) {
            readChunk(noise, x >> 4 << 4, z >> 4 << 4);
            column = columns.get(BlockPos.asLong(x, 0, z));
        }
        return column != null ? column : point(x, z);
    }

    /**
     * The column at a position, with no read of the other columns of its chunk.
     *
     * @param x the X of the column
     * @param z the Z of the column
     * @return the column
     */
    public PlotTerrain.Column point(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        PlotTerrain.Column column = columns.get(key);
        if (column != null) {
            return column;
        }
        if (noise == null) {
            column = TerrainColumns.column(generator.getBaseColumn(x, z, height, random), height.getMinBuildHeight(),
                    height.getMaxBuildHeight());
        } else {
            int width = settings(noise).getCellWidth();
            int cellX = Math.floorDiv(x, width) * width;
            int cellZ = Math.floorDiv(z, width) * width;
            NoiseChunk cell = noiseChunk(noise, 1, cellX, cellZ);
            cell.initializeForFirstCellX();
            cell.advanceCellX(0);
            column = scan(noise, cell, 0, x, z, (x - cellX) / (double) width, (z - cellZ) / (double) width);
            cell.stopInterpolation();
        }
        columns.put(key, column);
        return column;
    }

    /**
     * The Y of the first free block above the terrain at a position, water included: the value of
     * {@link ChunkGenerator#getBaseHeight} with {@link Heightmap.Types#WORLD_SURFACE_WG}.
     *
     * @param x the X of the column
     * @param z the Z of the column
     * @return the Y
     */
    public int surface(int x, int z) {
        return column(x, z).surface();
    }

    /** The number of chunks that the cache read with one noise object. */
    public int chunks() {
        return chunks;
    }

    /** Reads the grid columns of the chunk with a north-west corner, with one noise object. */
    private void readChunk(NoiseBasedChunkGenerator generator, int minX, int minZ) {
        chunks++;
        int width = settings(generator).getCellWidth();
        int cells = 16 / width;
        NoiseChunk chunk = noiseChunk(generator, cells, minX, minZ);
        chunk.initializeForFirstCellX();
        for (int cellX = 0; cellX < cells; cellX++) {
            chunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < cells; cellZ++) {
                for (int dx = 0; dx < width; dx += GRID) {
                    for (int dz = 0; dz < width; dz += GRID) {
                        int x = minX + cellX * width + dx;
                        int z = minZ + cellZ * width + dz;
                        columns.putIfAbsent(BlockPos.asLong(x, 0, z), scan(generator, chunk, cellZ, x, z,
                                dx / (double) width, dz / (double) width));
                    }
                }
            }
            chunk.swapSlices();
        }
        chunk.stopInterpolation();
    }

    /**
     * Reads one column of a noise object from the top, as {@code NoiseBasedChunkGenerator.iterateNoiseColumn}. The
     * read stops at the floor.
     */
    private PlotTerrain.Column scan(NoiseBasedChunkGenerator generator, NoiseChunk chunk, int cellZ, int x, int z,
            double fractionX, double fractionZ) {
        NoiseSettings settings = settings(generator);
        int cellHeight = settings.getCellHeight();
        int firstCellY = Math.floorDiv(settings.minY(), cellHeight);
        BlockState fill = generator.generatorSettings().value().defaultBlock();
        int minY = height.getMinBuildHeight();
        int surface = minY;
        boolean lava = false;
        boolean surfaceFound = false;
        for (int cellY = Math.floorDiv(settings.height(), cellHeight) - 1; cellY >= 0; cellY--) {
            chunk.selectCellYZ(cellY, cellZ);
            for (int inCell = cellHeight - 1; inCell >= 0; inCell--) {
                int y = (firstCellY + cellY) * cellHeight + inCell;
                chunk.updateForY(y, inCell / (double) cellHeight);
                chunk.updateForX(x, fractionX);
                chunk.updateForZ(z, fractionZ);
                BlockState state = chunk.getInterpolatedState();
                if (state == null) {
                    state = fill;
                }
                if (!surfaceFound && SURFACE.test(state)) {
                    surface = y + 1;
                    lava = state.getFluidState().getType().isSame(Fluids.LAVA);
                    surfaceFound = true;
                }
                if (FLOOR.test(state)) {
                    return new PlotTerrain.Column(surface, y + 1, lava);
                }
            }
        }
        return new PlotTerrain.Column(surface, minY, lava);
    }

    /** A noise object of a square of cells, with no structure and no blending, as {@code getBaseColumn}. */
    private NoiseChunk noiseChunk(NoiseBasedChunkGenerator generator, int cells, int minX, int minZ) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        return new NoiseChunk(cells, random, minX, minZ, settings(generator),
                DensityFunctions.BeardifierMarker.INSTANCE, settings, generator.globalFluidPicker.get(),
                Blender.empty());
    }

    private NoiseSettings settings(NoiseBasedChunkGenerator generator) {
        return generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(height);
    }

    /** True if the cells of a generator fill a chunk and hold the grid columns at their corners. */
    private static boolean hasGridCells(NoiseBasedChunkGenerator generator, LevelHeightAccessor height) {
        NoiseSettings settings = generator.generatorSettings().value().noiseSettings()
                .clampToHeightAccessor(height);
        int width = settings.getCellWidth();
        return 16 % width == 0 && width % GRID == 0 && settings.height() >= settings.getCellHeight();
    }
}
