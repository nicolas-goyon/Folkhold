package io.github.nicolasgoyon.folkhold.core.sim.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The territory chunks that the settlements claim.
 *
 * <p>Rules:
 * <ul>
 *   <li>Each settlement claims a disc of chunks around its position. A chunk is in the disc when the distance from
 *       the position of the settlement to the center of the chunk is not more than the claim radius.</li>
 *   <li>A chunk that two settlements claim belongs to the nearest settlement (distance to the center of the chunk).
 *       At equal distance, the settlement with the higher level gets the chunk. Then the lower id.</li>
 *   <li>A chunk that no settlement claims is unclaimed.</li>
 * </ul>
 *
 * <p>The claims keep no map of the claimed chunks: a large settlement claims thousands of chunks. An index of the
 * settlements by area gives the settlements that can claim a chunk.
 */
public final class TerritoryClaims {
    /** The territory contains the village and a margin: this number of times the radius of the village. */
    public static final double VILLAGE_MARGIN = 1.5;
    /** Size of an area of the index, in chunks. */
    private static final int AREA_CHUNKS = 64;

    /** No claims. */
    public static final TerritoryClaims EMPTY = new TerritoryClaims(List.of());

    /**
     * One settlement that claims chunks.
     *
     * @param id     the id of the settlement
     * @param x      block X of the settlement
     * @param z      block Z of the settlement
     * @param level  the level of the settlement
     * @param radius the claim radius in chunks
     */
    public record Claimant(String id, int x, int z, int level, int radius) {
    }

    private final Map<String, Claimant> byId = new HashMap<>();
    private final Map<Long, List<Claimant>> areas = new HashMap<>();

    private TerritoryClaims(Collection<Claimant> claimants) {
        for (Claimant claimant : claimants) {
            if (claimant.radius() <= 0) {
                continue;
            }
            byId.put(claimant.id(), claimant);
            // The chunks of the disc are in this square, with a margin of one chunk.
            int centerX = Math.floorDiv(claimant.x(), 16);
            int centerZ = Math.floorDiv(claimant.z(), 16);
            int reach = claimant.radius() + 1;
            for (int areaX = Math.floorDiv(centerX - reach, AREA_CHUNKS);
                    areaX <= Math.floorDiv(centerX + reach, AREA_CHUNKS); areaX++) {
                for (int areaZ = Math.floorDiv(centerZ - reach, AREA_CHUNKS);
                        areaZ <= Math.floorDiv(centerZ + reach, AREA_CHUNKS); areaZ++) {
                    areas.computeIfAbsent(key(areaX, areaZ), k -> new ArrayList<>()).add(claimant);
                }
            }
        }
    }

    /**
     * Calculates the claims of the settlements.
     *
     * @param claimants the settlements
     * @return the claims
     */
    public static TerritoryClaims compute(Collection<Claimant> claimants) {
        return new TerritoryClaims(claimants);
    }

    /**
     * The smallest claim radius for a village. With this radius, the territory contains all the blocks at
     * {@link #VILLAGE_MARGIN} times the radius of the village or nearer: a block is at most 7.5·√2 blocks from the
     * center of its chunk.
     *
     * @param villageRadius the radius of the village in blocks
     * @return the radius in chunks
     */
    public static int minimumRadius(int villageRadius) {
        return (int) Math.ceil((VILLAGE_MARGIN * villageRadius + 7.5 * Math.sqrt(2.0)) / 16.0);
    }

    /**
     * True if a chunk is in the disc of a settlement.
     *
     * @param claimant the settlement
     * @param chunkX   chunk X
     * @param chunkZ   chunk Z
     * @return true if the distance from the settlement to the center of the chunk is not more than its radius
     */
    static boolean inDisc(Claimant claimant, int chunkX, int chunkZ) {
        long reach = 32L * claimant.radius();
        return distanceSquared(claimant, chunkX, chunkZ) <= reach * reach;
    }

    /**
     * The settlement that owns the chunk of a block position.
     *
     * @param blockX block X
     * @param blockZ block Z
     * @return the id of the settlement, or empty if the chunk is unclaimed
     */
    public Optional<String> ownerAt(int blockX, int blockZ) {
        return ownerOfChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    /**
     * The settlement that owns a chunk.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return the id of the settlement, or empty if the chunk is unclaimed
     */
    public Optional<String> ownerOfChunk(int chunkX, int chunkZ) {
        List<Claimant> candidates = areas.get(key(Math.floorDiv(chunkX, AREA_CHUNKS), Math.floorDiv(chunkZ,
                AREA_CHUNKS)));
        if (candidates == null) {
            return Optional.empty();
        }
        Claimant owner = null;
        for (Claimant candidate : candidates) {
            if (inDisc(candidate, chunkX, chunkZ) && (owner == null || better(chunkX, chunkZ, candidate, owner))) {
                owner = candidate;
            }
        }
        return owner == null ? Optional.empty() : Optional.of(owner.id());
    }

    /** Number of chunks that a settlement owns. */
    public int chunkCount(String settlementId) {
        Claimant claimant = byId.get(settlementId);
        if (claimant == null) {
            return 0;
        }
        int centerX = Math.floorDiv(claimant.x(), 16);
        int centerZ = Math.floorDiv(claimant.z(), 16);
        int count = 0;
        for (int chunkX = centerX - claimant.radius() - 1; chunkX <= centerX + claimant.radius() + 1; chunkX++) {
            for (int chunkZ = centerZ - claimant.radius() - 1; chunkZ <= centerZ + claimant.radius() + 1; chunkZ++) {
                if (inDisc(claimant, chunkX, chunkZ)
                        && ownerOfChunk(chunkX, chunkZ).map(settlementId::equals).orElse(false)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** True if {@code a} gets the chunk before {@code b}. */
    private static boolean better(int chunkX, int chunkZ, Claimant a, Claimant b) {
        int byDistance = Long.compare(distanceSquared(a, chunkX, chunkZ), distanceSquared(b, chunkX, chunkZ));
        if (byDistance != 0) {
            return byDistance < 0;
        }
        if (a.level() != b.level()) {
            return a.level() > b.level();
        }
        return a.id().compareTo(b.id()) < 0;
    }

    /**
     * The square of the distance from the center of the block of a settlement to the center of a chunk, in half
     * blocks: the values are integers.
     */
    private static long distanceSquared(Claimant claimant, int chunkX, int chunkZ) {
        long dx = 32L * chunkX + 16 - (2L * claimant.x() + 1);
        long dz = 32L * chunkZ + 16 - (2L * claimant.z() + 1);
        return dx * dx + dz * dz;
    }

    private static long key(int areaX, int areaZ) {
        return ((long) areaX << 32) | (areaZ & 0xFFFFFFFFL);
    }
}
