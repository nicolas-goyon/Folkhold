package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.RegionPos;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Finds the faction villages of a region before the game generates its chunks.
 *
 * <p>The village structure uses a random spread placement: each placement region (a square of {@code spacing}
 * chunks) has one candidate chunk, from the seed. The discovery asks the structure manager if the village can
 * start in this chunk (biome and terrain), as the {@code /locate} command does. It does not generate chunks.
 *
 * <p>The discovery checks each region once and saves the result. Around each player and around the world spawn,
 * it puts the regions that it did not check in a queue. Each server tick checks a small number of regions. The
 * simulation runs on the discovered settlements, so the world around the players changes with no visit.
 *
 * <p>An operator can check all the regions around a position now ({@link #discoverAround}).
 */
public final class SettlementDiscovery {
    /** The structure set of the faction villages. */
    public static final ResourceKey<StructureSet> VILLAGE_SET = ResourceKey.create(Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "villages"));
    /** The discovery adds the regions around the players to the queue every 5 seconds. */
    private static final int SCAN_INTERVAL = 100;

    private static final Deque<RegionPos> QUEUE = new ArrayDeque<>();
    private static final Set<RegionPos> QUEUED = new HashSet<>();

    private SettlementDiscovery() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        enqueueAround(overworld, overworld.getSharedSpawnPos());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        QUEUED.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() % SCAN_INTERVAL == 0) {
            for (ServerPlayer player : overworld.players()) {
                enqueueAround(overworld, player.blockPosition());
            }
        }
        int checks = WorldConfig.DISCOVERY_CHECKS_PER_TICK.get();
        for (int i = 0; i < checks && !QUEUE.isEmpty(); i++) {
            RegionPos region = QUEUE.poll();
            QUEUED.remove(region);
            checkRegion(overworld, region);
        }
    }

    /** Puts the regions in the discovery radius around a position in the queue, if they are not checked. */
    private static void enqueueAround(ServerLevel level, BlockPos center) {
        SettlementData data = SettlementData.get(level.getServer());
        for (RegionPos region : regionsAround(level, center)) {
            if (!data.isRegionChecked(region.x(), region.z()) && QUEUED.add(region)) {
                QUEUE.add(region);
            }
        }
    }

    /** The placement regions in the discovery radius around a position. */
    private static List<RegionPos> regionsAround(ServerLevel level, BlockPos center) {
        Optional<RandomSpreadStructurePlacement> placement = placement(level);
        if (placement.isEmpty()) {
            return List.of();
        }
        int spacing = placement.get().spacing();
        int radiusChunks = WorldConfig.DISCOVERY_RADIUS.get() >> 4;
        ChunkPos chunk = new ChunkPos(center);
        RegionPos min = RegionPos.ofChunk(chunk.x - radiusChunks, chunk.z - radiusChunks, spacing);
        RegionPos max = RegionPos.ofChunk(chunk.x + radiusChunks, chunk.z + radiusChunks, spacing);
        List<RegionPos> regions = new ArrayList<>();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                regions.add(new RegionPos(x, z));
            }
        }
        return regions;
    }

    /**
     * The result of a discovery around a position.
     *
     * @param inRadius the active settlements in the discovery radius
     * @param found    the settlements that the discovery added
     * @param radius   the discovery radius, in blocks
     */
    public record Discovery(int inRadius, int found, int radius) {
    }

    /**
     * Checks now all the regions in the discovery radius around a position that the discovery did not check.
     * The check of a region can take some milliseconds: an operator action calls it, not the game.
     *
     * @param level  the Overworld
     * @param center the position
     * @return the settlements in the radius and the new settlements
     */
    public static Discovery discoverAround(ServerLevel level, BlockPos center) {
        SettlementData data = SettlementData.get(level.getServer());
        int before = data.registry().all().size();
        for (RegionPos region : regionsAround(level, center)) {
            if (!data.isRegionChecked(region.x(), region.z())) {
                QUEUED.remove(region);
                QUEUE.remove(region);
                checkRegion(level, region);
            }
        }
        int radius = WorldConfig.DISCOVERY_RADIUS.get();
        long radiusSquared = (long) radius * radius;
        int inRadius = (int) data.registry().all().stream()
                .filter(Settlement::isActive)
                .filter(settlement -> settlement.distanceSquared(center.getX(), center.getZ()) <= radiusSquared)
                .count();
        return new Discovery(inRadius, data.registry().all().size() - before, radius);
    }

    /**
     * Checks one placement region. If a village can start in its candidate chunk and no settlement has this start
     * chunk, the method adds a settlement.
     *
     * @param level  the Overworld
     * @param region the placement region
     * @return the settlement of the region, if the region has a village
     */
    public static Optional<Settlement> checkRegion(ServerLevel level, RegionPos region) {
        Optional<RandomSpreadStructurePlacement> placement = placement(level);
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOptional(Settlements.VILLAGE_STRUCTURE);
        if (placement.isEmpty() || structure.isEmpty()) {
            return Optional.empty();
        }
        int spacing = placement.get().spacing();
        ChunkPos candidate = placement.get().getPotentialStructureChunk(level.getSeed(),
                region.minChunkX(spacing), region.minChunkZ(spacing));
        StructureCheckResult result = level.structureManager()
                .checkStructurePresence(candidate, structure.get(), placement.get(), false);
        SettlementData data = SettlementData.get(level.getServer());
        data.markRegionChecked(region.x(), region.z());
        // CHUNK_LOAD_NEEDED: the chunk is not generated, and the structure can start there.
        if (result == StructureCheckResult.START_NOT_PRESENT) {
            return Optional.empty();
        }
        List<Settlement> known = data.registry().byStart(candidate.x, candidate.z);
        if (!known.isEmpty()) {
            return Optional.of(known.get(0));
        }
        BlockPos pos = new BlockPos(candidate.getMiddleBlockX(), Settlements.seaLevel(level), candidate.getMiddleBlockZ());
        Settlement settlement = Settlements.create(level.getServer(), UUID.randomUUID(), candidate, pos);
        data.registry().add(settlement);
        data.setDirty();
        FolkholdWorld.LOGGER.debug("Discovered the settlement {}", settlement);
        return Optional.of(settlement);
    }

    /** The placement of the village structure set, if it is a random spread placement. */
    private static Optional<RandomSpreadStructurePlacement> placement(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getOptional(VILLAGE_SET)
                .map(StructureSet::placement)
                .filter(RandomSpreadStructurePlacement.class::isInstance)
                .map(RandomSpreadStructurePlacement.class::cast);
    }
}
