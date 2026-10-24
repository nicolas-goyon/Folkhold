package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The roads of the Overworld for the routes of the caravans (see {@link CaravanRoutes}). Saved in the Overworld.
 *
 * <ul>
 *   <li>A column of blocks is a road when a block of the tag {@link #ROAD} has {@link #HEADROOM} blocks of free space
 *       above it: no collision and no fluid. The highest such block of the column counts, also under a roof. Thus a
 *       tunnel in a mountain, a covered bridge and a bridge above a river are roads.</li>
 *   <li>The mod reads the columns of a chunk when the chunk loads and when it unloads. It reads a column again after
 *       a block changes in it: a placed or a broken block, a path that a shovel makes.</li>
 *   <li>The network keeps an array of road heights for each chunk with a road. The mod replaces an array and does not
 *       change it, so the worker thread of the routes reads the network with no lock.</li>
 *   <li>When the roads of a chunk change, the routes near the chunk become old (see
 *       {@link MobileGroupData#forgetRoutesNear}). The mod does this for all the changed chunks at once, every
 *       {@link #FORGET_INTERVAL} ticks.</li>
 * </ul>
 */
public final class RoadNetwork extends SavedData {
    /** The blocks of a road floor. A data pack can change the tag. */
    public static final TagKey<Block> ROAD = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "road"));
    /** The free space above a road floor, in blocks. */
    static final int HEADROOM = 2;
    /** The routes near the changed chunks become old once in this time, in ticks: 30 seconds. */
    static final int FORGET_INTERVAL = 600;
    private static final String FILE_ID = "folkhold_world_roads";
    private static final SavedData.Factory<RoadNetwork> FACTORY =
            new SavedData.Factory<>(RoadNetwork::new, RoadNetwork::load, null);
    private static final short NONE = Short.MIN_VALUE;
    private static final int COLUMNS = 16 * 16;

    /** For each chunk with a road, the Y of the road floor of each column (index z * 16 + x), or {@link #NONE}. */
    private final Map<Long, short[]> chunks = new ConcurrentHashMap<>();
    /** The chunks with changed roads since the last time the routes near them became old. */
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();
    /** The columns to read again at the next tick, as {@link BlockPos#asLong} with Y 0. */
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();

    private RoadNetwork() {
    }

    /** The road network of the server: the network of the Overworld. */
    public static RoadNetwork get(MinecraftServer server) {
        return get(server.overworld());
    }

    /** The road network of the Overworld. Makes it when the level has none. */
    public static RoadNetwork get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * The Y of the road floor of a column, or {@link RouteFinder#NO_ROAD}.
     *
     * @param x block X
     * @param z block Z
     * @return the Y of the road block
     */
    public int roadAt(int x, int z) {
        short[] heights = chunks.get(ChunkPos.asLong(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z)));
        if (heights == null) {
            return RouteFinder.NO_ROAD;
        }
        short y = heights[index(x, z)];
        return y == NONE ? RouteFinder.NO_ROAD : y;
    }

    /**
     * The road of a square around a position: the road floor with the height nearest to a reference height. For a
     * cell of a route search, the reference is the floor of the terrain: a path on the ground wins against a cave
     * below it, and a tunnel wins against a mine deeper in the mountain.
     *
     * @param x         block X of the center
     * @param z         block Z of the center
     * @param radius    the half size of the square, in blocks
     * @param reference the reference height
     * @return the Y of the road block, or {@link RouteFinder#NO_ROAD}
     */
    public int roadNear(int x, int z, int radius, int reference) {
        int best = RouteFinder.NO_ROAD;
        long bestDistance = Long.MAX_VALUE;
        for (int chunkX = SectionPos.blockToSectionCoord(x - radius);
                chunkX <= SectionPos.blockToSectionCoord(x + radius); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(z - radius);
                    chunkZ <= SectionPos.blockToSectionCoord(z + radius); chunkZ++) {
                short[] heights = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (heights == null) {
                    continue;
                }
                int minX = Math.max(x - radius, SectionPos.sectionToBlockCoord(chunkX));
                int maxX = Math.min(x + radius, SectionPos.sectionToBlockCoord(chunkX) + 15);
                int minZ = Math.max(z - radius, SectionPos.sectionToBlockCoord(chunkZ));
                int maxZ = Math.min(z + radius, SectionPos.sectionToBlockCoord(chunkZ) + 15);
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        short y = heights[index(bx, bz)];
                        if (y == NONE) {
                            continue;
                        }
                        long distance = Math.abs((long) y - reference) * 1024L + (long) (bx - x) * (bx - x)
                                + (long) (bz - z) * (bz - z);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = y;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The number of chunks with a road. */
    public int chunkCount() {
        return chunks.size();
    }

    /**
     * Reads the roads of a chunk again.
     *
     * @param chunk the chunk
     * @return true if the roads of the chunk changed
     */
    public boolean scan(LevelChunk chunk) {
        return replace(chunk.getPos().toLong(), scanChunk(chunk));
    }

    /**
     * Reads the road of one column again. The chunk of the column must be loaded.
     *
     * @param level the level
     * @param x     block X
     * @param z     block Z
     * @return true if the road of the column changed
     */
    public boolean scanColumn(Level level, int x, int z) {
        long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        short[] old = chunks.get(key);
        short y = (short) roadFloor(level, x, z, level.getMinBuildHeight(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        int index = index(x, z);
        if ((old == null ? NONE : old[index]) == y) {
            return false;
        }
        short[] heights = old == null ? empty() : old.clone();
        heights[index] = y;
        return replace(key, heights);
    }

    private boolean replace(long key, @Nullable short[] heights) {
        boolean any = heights != null && hasRoad(heights);
        short[] old = chunks.get(key);
        if (any ? Arrays.equals(old, heights) : old == null) {
            return false;
        }
        if (any) {
            chunks.put(key, heights);
        } else {
            chunks.remove(key);
        }
        changed.add(key);
        setDirty();
        return true;
    }

    /** The road heights of a chunk, or null for a chunk with no road. */
    @Nullable
    static short[] scanChunk(LevelChunk chunk) {
        short[] heights = null;
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = sections.length - 1; index >= 0; index--) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ROAD))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int localY = 15; localY >= 0; localY--) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        int column = localZ * 16 + localX;
                        if (heights != null && heights[column] != NONE) {
                            continue;
                        }
                        if (!section.getBlockState(localX, localY, localZ).is(ROAD)) {
                            continue;
                        }
                        int y = baseY + localY;
                        if (hasHeadroom(chunk, pos, baseX + localX, y, baseZ + localZ)) {
                            if (heights == null) {
                                heights = empty();
                            }
                            heights[column] = (short) y;
                        }
                    }
                }
            }
        }
        return heights;
    }

    /**
     * The Y of the road floor of a column: the highest block of {@link #ROAD} with free space above it.
     *
     * @param level the blocks
     * @param x     block X
     * @param z     block Z
     * @param minY  the lowest Y to read
     * @param maxY  the Y above the highest block to read
     * @return the Y of the road block, or {@link RouteFinder#NO_ROAD}
     */
    public static int roadFloorAt(BlockGetter level, int x, int z, int minY, int maxY) {
        int y = roadFloor(level, x, z, minY, maxY);
        return y == NONE ? RouteFinder.NO_ROAD : y;
    }

    /** The Y of the road floor of a column, or {@link #NONE} (see {@link #roadFloorAt}). */
    static int roadFloor(BlockGetter level, int x, int z, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = maxY; y >= minY; y--) {
            if (level.getBlockState(pos.set(x, y, z)).is(ROAD) && hasHeadroom(level, pos, x, y, z)) {
                return y;
            }
        }
        return NONE;
    }

    private static boolean hasHeadroom(BlockGetter level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        for (int up = 1; up <= HEADROOM; up++) {
            pos.set(x, y + up, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The Y where a member of a group stands at a position of its route: on the road for a road segment of the
     * route, else on the terrain.
     *
     * @param level    the Overworld
     * @param route    the route
     * @param progress the progress of the position on the route
     * @param x        block X of the position
     * @param z        block Z of the position
     * @param radius   the distance of a road from the position, in blocks
     * @return the Y of the feet
     */
    public static int standY(ServerLevel level, Route route, double progress, int x, int z, int radius) {
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (route.surfaceAt(progress) != Route.Surface.ROAD) {
            return terrain;
        }
        int road = get(level.getServer()).roadNear(x, z, radius, terrain - 1);
        return road == RouteFinder.NO_ROAD ? terrain : road + 1;
    }

    private static int index(int x, int z) {
        return (z & 15) * 16 + (x & 15);
    }

    private static short[] empty() {
        short[] heights = new short[COLUMNS];
        Arrays.fill(heights, NONE);
        return heights;
    }

    private static boolean hasRoad(short[] heights) {
        for (short y : heights) {
            if (y != NONE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives the changed chunks since the last call, and forgets them.
     *
     * @return the keys of the chunks ({@link ChunkPos#toLong()})
     */
    Set<Long> takeChanged() {
        Set<Long> taken = Set.copyOf(changed);
        changed.removeAll(taken);
        return taken;
    }

    /** A chunk of the Overworld loads: the mod reads its roads. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && event.getChunk() instanceof LevelChunk chunk) {
            get(level).scan(chunk);
        }
    }

    /** A chunk of the Overworld unloads: the mod reads its roads, with the changes since its load. */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && event.getChunk() instanceof LevelChunk chunk) {
            get(level).scan(chunk);
        }
    }

    /** An entity places a block: the mod reads its column again at the next tick. */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        queue(event);
    }

    /** A player breaks a block: the mod reads its column again at the next tick. */
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        queue(event);
    }

    /** A tool changes a block, for example a shovel that makes a path: the mod reads its column again. */
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        queue(event);
    }

    private static void queue(BlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            QUEUED.add(BlockPos.asLong(event.getPos().getX(), 0, event.getPos().getZ()));
        }
    }

    /**
     * Reads the queued columns again. Every {@link #FORGET_INTERVAL} ticks, the routes near the changed chunks become
     * old.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (!QUEUED.isEmpty()) {
            RoadNetwork network = get(level);
            for (Long key : Set.copyOf(QUEUED)) {
                QUEUED.remove(key);
                int x = BlockPos.getX(key);
                int z = BlockPos.getZ(key);
                if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x),
                        SectionPos.blockToSectionCoord(z)) != null) {
                    network.scanColumn(level, x, z);
                }
            }
        }
        if (server.getTickCount() % FORGET_INTERVAL == 0) {
            Set<Long> taken = get(level).takeChanged();
            if (!taken.isEmpty()) {
                MobileGroupData.get(server).forgetRoutesNear(taken);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, short[]> entry : chunks.entrySet()) {
            short[] heights = entry.getValue();
            int count = 0;
            for (short y : heights) {
                if (y != NONE) {
                    count++;
                }
            }
            int[] columns = new int[count];
            int next = 0;
            for (int i = 0; i < heights.length; i++) {
                if (heights[i] != NONE) {
                    columns[next++] = (i << 16) | (heights[i] & 0xFFFF);
                }
            }
            CompoundTag chunk = new CompoundTag();
            chunk.putLong("Pos", entry.getKey());
            chunk.putIntArray("Columns", columns);
            list.add(chunk);
        }
        tag.put("Chunks", list);
        return tag;
    }

    /**
     * Reads a road network from its saved data.
     *
     * @param tag        the saved data
     * @param registries the registries
     * @return the road network
     */
    public static RoadNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        RoadNetwork network = new RoadNetwork();
        for (Tag element : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunk = (CompoundTag) element;
            short[] heights = empty();
            for (int column : chunk.getIntArray("Columns")) {
                int index = column >>> 16;
                if (index < COLUMNS) {
                    heights[index] = (short) (column & 0xFFFF);
                }
            }
            network.chunks.put(chunk.getLong("Pos"), heights);
        }
        return network;
    }
}
