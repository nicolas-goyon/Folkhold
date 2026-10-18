package io.github.nicolasgoyon.folkhold.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

/**
 * Checks that chunks are ready, with no wait.
 *
 * <p>On the server, {@code Level.isLoaded} and {@code Level.hasChunk} are true for a chunk that has a ticket, also
 * when the generation of the chunk is not complete. A read in such a chunk ({@code getBlockState},
 * {@code getBlockEntity}, {@code getHeight}) waits on the server thread until the generation is complete, and the
 * server does not tick during this time. The methods of this class use {@code ChunkSource.getChunkNow}, which does
 * not wait: a chunk is ready when it is loaded and complete.
 */
public final class ReadyChunks {
    private ReadyChunks() {
    }

    /**
     * True if the chunk that contains the position is loaded and complete.
     *
     * @param level the level
     * @param pos   a block position
     * @return true if a read at this position does not wait
     */
    public static boolean isReady(Level level, BlockPos pos) {
        return isReady(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * True if the chunk is loaded and complete.
     *
     * @param level  the level
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return true if a read in this chunk does not wait
     */
    public static boolean isReady(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    /**
     * True if all the chunks with a part of the area are loaded and complete.
     *
     * @param level the level
     * @param minX  the lowest block X of the area
     * @param minZ  the lowest block Z of the area
     * @param maxX  the highest block X of the area
     * @param maxZ  the highest block Z of the area
     * @return true if a read in the area does not wait
     */
    public static boolean isAreaReady(Level level, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX);
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ);
                    chunkZ++) {
                if (!isReady(level, chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
