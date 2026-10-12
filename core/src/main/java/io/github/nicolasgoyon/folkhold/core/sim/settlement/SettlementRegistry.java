package io.github.nicolasgoyon.folkhold.core.sim.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All settlements of a world, by id and by start chunk.
 *
 * <p>Two settlements cannot have the same id. Two settlements can have the same start chunk (for example two
 * hearts that a player placed in the same chunk).
 */
public final class SettlementRegistry {
    private final Map<String, Settlement> byId = new LinkedHashMap<>();
    private final Map<Long, List<Settlement>> byStart = new HashMap<>();

    /**
     * Packs chunk coordinates into one key.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return the key
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Adds a settlement.
     *
     * @param settlement the settlement
     * @throws IllegalArgumentException if a settlement with the same id exists
     */
    public void add(Settlement settlement) {
        if (byId.containsKey(settlement.id())) {
            throw new IllegalArgumentException("A settlement with the id " + settlement.id() + " exists");
        }
        byId.put(settlement.id(), settlement);
        byStart.computeIfAbsent(chunkKey(settlement.startChunkX(), settlement.startChunkZ()), key -> new ArrayList<>())
                .add(settlement);
    }

    public Optional<Settlement> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * The settlements with this start chunk, in the order of addition.
     *
     * @param chunkX chunk X of the structure start
     * @param chunkZ chunk Z of the structure start
     * @return the settlements, or an empty list
     */
    public List<Settlement> byStart(int chunkX, int chunkZ) {
        List<Settlement> found = byStart.get(chunkKey(chunkX, chunkZ));
        return found == null ? List.of() : Collections.unmodifiableList(found);
    }

    /** All settlements, in the order of addition. */
    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /**
     * The settlements in a horizontal radius around a position, nearest first.
     *
     * @param blockX block X of the center
     * @param blockZ block Z of the center
     * @param radius radius in blocks
     * @return the settlements in the radius
     */
    public List<Settlement> within(int blockX, int blockZ, int radius) {
        long max = (long) radius * radius;
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : byId.values()) {
            if (settlement.distanceSquared(blockX, blockZ) <= max) {
                result.add(settlement);
            }
        }
        result.sort(Comparator.comparingLong(settlement -> settlement.distanceSquared(blockX, blockZ)));
        return result;
    }

    /**
     * Gives a new id to a settlement.
     *
     * @param oldId the current id
     * @param newId the new id
     * @throws IllegalArgumentException if no settlement has the current id, or if a settlement has the new id
     */
    public void changeId(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return;
        }
        Settlement settlement = byId.get(oldId);
        if (settlement == null) {
            throw new IllegalArgumentException("No settlement with the id " + oldId);
        }
        if (byId.containsKey(newId)) {
            throw new IllegalArgumentException("A settlement with the id " + newId + " exists");
        }
        // Keep the order of addition.
        Map<String, Settlement> copy = new LinkedHashMap<>(byId);
        byId.clear();
        for (Map.Entry<String, Settlement> entry : copy.entrySet()) {
            byId.put(entry.getKey().equals(oldId) ? newId : entry.getKey(), entry.getValue());
        }
        settlement.setId(newId);
    }
}
