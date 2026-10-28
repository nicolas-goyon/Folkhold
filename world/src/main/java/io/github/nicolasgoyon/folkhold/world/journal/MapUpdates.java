package io.github.nicolasgoyon.folkhold.world.journal;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The update of a vanilla map at a distance, in the cartography table (see {@link MapMoves#UPDATE}).
 *
 * <ul>
 *   <li>The update reads the chunks of the area of the map that are already generated: it never generates a chunk. A
 *       chunk that is not loaded comes from the disk.</li>
 *   <li>An update reads {@link #LIMIT} chunks at most. The chunks with unexplored pixels come first, then the other
 *       chunks, each group from the middle of the map. Thus a large map needs more than one update.</li>
 *   <li>The server reads {@link #CHUNKS_PER_TICK} chunks at most each tick. The check of the chunks on the disk runs
 *       on the thread of the storage.</li>
 *   <li>The colors of the pixels follow the colors of the game: the most frequent color of the top blocks of the
 *       pixel, and a brightness from the height of the pixel to the north.</li>
 * </ul>
 */
public final class MapUpdates {
    /** The largest number of chunks of an update. */
    public static final int LIMIT = 1024;
    /** The largest number of chunks that the server reads each tick. */
    public static final int CHUNKS_PER_TICK = 4;
    /** The largest number of checks of the disk at the same time, for each update. */
    private static final int CHECKS = 32;
    private static final String FULL = "minecraft:full";
    private static final List<Job> JOBS = new ArrayList<>();

    private MapUpdates() {
    }

    /** An update in progress. */
    private static final class Job {
        final ServerLevel level;
        final MapId id;
        final Deque<ChunkPos> waiting;
        final Queue<ChunkPos> ready = new ConcurrentLinkedQueue<>();
        final double[] heights = new double[MapMoves.SIZE * MapMoves.SIZE];
        int checking;

        Job(ServerLevel level, MapId id, List<ChunkPos> chunks) {
            this.level = level;
            this.id = id;
            this.waiting = new ArrayDeque<>(chunks);
            Arrays.fill(heights, Double.NaN);
        }

        boolean done() {
            return waiting.isEmpty() && checking == 0 && ready.isEmpty();
        }
    }

    /**
     * Starts the update of a map.
     *
     * @param level the level of the map
     * @param id    the id of the map
     * @param data  the data of the map
     */
    public static void start(ServerLevel level, MapId id, MapItemSavedData data) {
        JOBS.removeIf(job -> job.id.equals(id) && job.level == level);
        JOBS.add(new Job(level, id, chunks(data)));
    }

    /**
     * True if a map has an update in progress.
     *
     * @param id the id of the map
     * @return true during the update
     */
    public static boolean running(MapId id) {
        return JOBS.stream().anyMatch(job -> job.id.equals(id));
    }

    /** The chunks of an update: the chunks with unexplored pixels first, each group from the middle of the map. */
    public static List<ChunkPos> chunks(MapItemSavedData data) {
        int factor = 1 << data.scale;
        int minX = blockX(data, 0);
        int minZ = blockZ(data, 0);
        int maxX = blockX(data, MapMoves.SIZE - 1) + factor - 1;
        int maxZ = blockZ(data, MapMoves.SIZE - 1) + factor - 1;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        int middleX = data.centerX >> 4;
        int middleZ = data.centerZ >> 4;
        chunks.sort(Comparator.<ChunkPos, Boolean>comparing(chunk -> explored(data, chunk))
                .thenComparingInt(chunk -> Math.max(Math.abs(chunk.x - middleX), Math.abs(chunk.z - middleZ))));
        return chunks.subList(0, Math.min(LIMIT, chunks.size()));
    }

    /** True if all the pixels of a chunk are explored. */
    private static boolean explored(MapItemSavedData data, ChunkPos chunk) {
        int[] range = pixels(data, chunk);
        for (int px = range[0]; px <= range[1]; px++) {
            for (int pz = range[2]; pz <= range[3]; pz++) {
                if (data.colors[px + pz * MapMoves.SIZE] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The X of the first block of a pixel, as in the game. */
    static int blockX(MapItemSavedData data, int px) {
        int factor = 1 << data.scale;
        return (data.centerX / factor + px - MapMoves.SIZE / 2) * factor;
    }

    /** The Z of the first block of a pixel, as in the game. */
    static int blockZ(MapItemSavedData data, int pz) {
        int factor = 1 << data.scale;
        return (data.centerZ / factor + pz - MapMoves.SIZE / 2) * factor;
    }

    /** The pixels of a chunk: {min X, max X, min Z, max Z}, in the map. An empty range has max smaller than min. */
    static int[] pixels(MapItemSavedData data, ChunkPos chunk) {
        int[] range = {MapMoves.SIZE, -1, MapMoves.SIZE, -1};
        for (int p = 0; p < MapMoves.SIZE; p++) {
            if (blockX(data, p) >> 4 == chunk.x) {
                range[0] = Math.min(range[0], p);
                range[1] = Math.max(range[1], p);
            }
            if (blockZ(data, p) >> 4 == chunk.z) {
                range[2] = Math.min(range[2], p);
                range[3] = Math.max(range[3], p);
            }
        }
        return range;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) {
            return;
        }
        int budget = CHUNKS_PER_TICK;
        for (Iterator<Job> iterator = JOBS.iterator(); iterator.hasNext();) {
            Job job = iterator.next();
            MapItemSavedData data = job.level.getMapData(job.id);
            if (data == null || data.locked) {
                iterator.remove();
                continue;
            }
            check(job);
            ChunkPos chunk;
            while (budget > 0 && (chunk = job.ready.poll()) != null) {
                paint(job, data, job.level.getChunk(chunk.x, chunk.z));
                budget--;
            }
            if (job.done()) {
                iterator.remove();
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    /** Starts the checks of the next chunks: a loaded chunk is ready, a chunk on the disk is ready when it is full. */
    private static void check(Job job) {
        while (job.checking < CHECKS && !job.waiting.isEmpty()) {
            ChunkPos chunk = job.waiting.poll();
            if (job.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                job.ready.add(chunk);
                continue;
            }
            job.checking++;
            CollectFields status = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
            // The check reports its result in the queues of the job: the future is not needed.
            var unused = job.level.getChunkSource().chunkMap.chunkScanner().scanChunk(chunk, status)
                    .whenComplete((result, error) -> {
                if (error == null && status.getResult() instanceof CompoundTag tag
                        && FULL.equals(tag.getString("Status"))) {
                    job.ready.add(chunk);
                }
                job.level.getServer().execute(() -> job.checking--);
            });
        }
    }

    /** Paints the pixels of a chunk on the map. */
    private static void paint(Job job, MapItemSavedData data, LevelChunk chunk) {
        int factor = 1 << data.scale;
        int[] range = pixels(data, chunk.getPos());
        ServerLevel level = job.level;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int px = range[0]; px <= range[1]; px++) {
            for (int pz = range[2]; pz <= range[3]; pz++) {
                int startX = blockX(data, px);
                int startZ = blockZ(data, pz);
                Multiset<MapColor> colors = LinkedHashMultiset.create();
                double height = 0.0;
                int depth = 0;
                for (int dx = 0; dx < factor; dx++) {
                    for (int dz = 0; dz < factor; dz++) {
                        pos.set(startX + dx, 0, startZ + dz);
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1;
                        BlockState state;
                        if (y <= level.getMinBuildHeight() + 1) {
                            state = Blocks.BEDROCK.defaultBlockState();
                        } else {
                            do {
                                pos.setY(--y);
                                state = chunk.getBlockState(pos);
                            } while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight());
                            if (y > level.getMinBuildHeight() && !state.getFluidState().isEmpty()) {
                                int bottom = y - 1;
                                below.set(pos);
                                BlockState under;
                                do {
                                    below.setY(bottom--);
                                    under = chunk.getBlockState(below);
                                    depth++;
                                } while (bottom > level.getMinBuildHeight() && !under.getFluidState().isEmpty());
                                if (!state.isFaceSturdy(level, pos, Direction.UP)) {
                                    state = state.getFluidState().createLegacyBlock();
                                }
                            }
                        }
                        height += (double) y / (factor * factor);
                        colors.add(state.getMapColor(level, pos));
                    }
                }
                depth /= factor * factor;
                MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colors), MapColor.NONE);
                int index = px + pz * MapMoves.SIZE;
                job.heights[index] = height;
                double north = pz > 0 && !Double.isNaN(job.heights[index - MapMoves.SIZE])
                        ? job.heights[index - MapMoves.SIZE] : height;
                MapColor.Brightness brightness = brightness(color, height, north, depth, px, pz, factor);
                data.updateColor(px, pz, color.getPackedId(brightness));
            }
        }
    }

    /** The brightness of a pixel, as in the game. */
    static MapColor.Brightness brightness(MapColor color, double height, double north, int depth, int px, int pz,
            int factor) {
        int parity = (px + pz) & 1;
        if (color == MapColor.WATER) {
            double shade = depth * 0.1 + parity * 0.2;
            return shade < 0.5 ? MapColor.Brightness.HIGH
                    : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        double shade = (height - north) * 4.0 / (factor + 4) + (parity - 0.5) * 0.4;
        return shade > 0.6 ? MapColor.Brightness.HIGH
                : shade < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }
}
