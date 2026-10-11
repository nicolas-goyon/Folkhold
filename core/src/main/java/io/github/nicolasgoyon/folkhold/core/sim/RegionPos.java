package io.github.nicolasgoyon.folkhold.core.sim;

/**
 * Position of a region in a grid of square regions. Each region contains {@code size × size} chunks.
 *
 * <p>This class is pure Java. It has no dependency on Minecraft classes, so JUnit tests can use it
 * without the game.
 *
 * @param x region coordinate on the X axis
 * @param z region coordinate on the Z axis
 */
public record RegionPos(int x, int z) {

    /**
     * Gives the region that contains a chunk.
     *
     * <p>The calculation uses floor division. Chunk -1 is in region -1, not in region 0.
     *
     * @param chunkX chunk coordinate on the X axis
     * @param chunkZ chunk coordinate on the Z axis
     * @param size   number of chunks on each side of a region. Must be 1 or more.
     * @return the region that contains the chunk
     * @throws IllegalArgumentException if {@code size} is less than 1
     */
    public static RegionPos ofChunk(int chunkX, int chunkZ, int size) {
        requireValidSize(size);
        return new RegionPos(Math.floorDiv(chunkX, size), Math.floorDiv(chunkZ, size));
    }

    /**
     * Gives the X coordinate of the first chunk (west edge) of this region.
     *
     * @param size number of chunks on each side of a region. Must be 1 or more.
     * @return the minimum chunk X coordinate
     */
    public int minChunkX(int size) {
        requireValidSize(size);
        return x * size;
    }

    /**
     * Gives the Z coordinate of the first chunk (north edge) of this region.
     *
     * @param size number of chunks on each side of a region. Must be 1 or more.
     * @return the minimum chunk Z coordinate
     */
    public int minChunkZ(int size) {
        requireValidSize(size);
        return z * size;
    }

    private static void requireValidSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Region size must be 1 or more. Value: " + size);
        }
    }
}
