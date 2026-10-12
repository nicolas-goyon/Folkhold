package io.github.nicolasgoyon.folkhold.core.sim.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The territory cells that the settlements claim.
 *
 * <p>Rules:
 * <ul>
 *   <li>Each settlement claims the {@code cells} cells nearest to its position (distance to the center of the
 *       cell). The first cell is the cell of the settlement.</li>
 *   <li>A cell that two settlements claim belongs to the nearest settlement. At equal distance, the settlement
 *       with the higher level gets the cell. Then the lower id.</li>
 *   <li>A cell that no settlement claims is unclaimed.</li>
 * </ul>
 */
public final class TerritoryClaims {
    /** Size of a territory cell in chunks. */
    public static final int CELL_CHUNKS = 16;
    /** Size of a territory cell in blocks. */
    public static final int CELL_BLOCKS = CELL_CHUNKS * 16;

    /** No claims. */
    public static final TerritoryClaims EMPTY = new TerritoryClaims(Map.of());

    /**
     * One settlement that claims cells.
     *
     * @param id    the id of the settlement
     * @param x     block X of the settlement
     * @param z     block Z of the settlement
     * @param level the level of the settlement
     * @param cells the number of cells that the settlement claims
     */
    public record Claimant(String id, int x, int z, int level, int cells) {
    }

    private final Map<CellPos, String> owners;

    private TerritoryClaims(Map<CellPos, String> owners) {
        this.owners = owners;
    }

    /**
     * Calculates the claims of the settlements.
     *
     * @param claimants the settlements
     * @return the claims
     */
    public static TerritoryClaims compute(Collection<Claimant> claimants) {
        Map<CellPos, Claimant> winners = new HashMap<>();
        for (Claimant claimant : claimants) {
            for (CellPos cell : nearestCells(claimant)) {
                winners.merge(cell, claimant, (current, other) -> better(cell, current, other));
            }
        }
        Map<CellPos, String> owners = new HashMap<>();
        winners.forEach((cell, claimant) -> owners.put(cell, claimant.id()));
        return new TerritoryClaims(owners);
    }

    /** The cells that a settlement wants: its {@code cells} nearest cells, nearest first. */
    static List<CellPos> nearestCells(Claimant claimant) {
        if (claimant.cells() <= 0) {
            return List.of();
        }
        CellPos home = CellPos.ofBlock(claimant.x(), claimant.z());
        int radius = (int) Math.ceil(Math.sqrt(claimant.cells())) + 1;
        List<CellPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                candidates.add(new CellPos(home.x() + dx, home.z() + dz));
            }
        }
        candidates.sort(Comparator.<CellPos>comparingDouble(cell -> cell.distanceSquared(claimant.x(), claimant.z()))
                .thenComparingInt(CellPos::x)
                .thenComparingInt(CellPos::z));
        // The cell of the settlement is always the first cell.
        candidates.remove(home);
        candidates.add(0, home);
        return candidates.subList(0, Math.min(claimant.cells(), candidates.size()));
    }

    private static Claimant better(CellPos cell, Claimant a, Claimant b) {
        int byDistance = Double.compare(cell.distanceSquared(a.x(), a.z()), cell.distanceSquared(b.x(), b.z()));
        if (byDistance != 0) {
            return byDistance < 0 ? a : b;
        }
        if (a.level() != b.level()) {
            return a.level() > b.level() ? a : b;
        }
        return a.id().compareTo(b.id()) <= 0 ? a : b;
    }

    /**
     * The settlement that owns the cell of a block position.
     *
     * @param blockX block X
     * @param blockZ block Z
     * @return the id of the settlement, or empty if the cell is unclaimed
     */
    public Optional<String> ownerAt(int blockX, int blockZ) {
        return Optional.ofNullable(owners.get(CellPos.ofBlock(blockX, blockZ)));
    }

    /** The settlement that owns a cell, or empty if the cell is unclaimed. */
    public Optional<String> owner(CellPos cell) {
        return Optional.ofNullable(owners.get(cell));
    }

    /** Number of cells that a settlement owns. */
    public int cellCount(String settlementId) {
        int count = 0;
        for (String owner : owners.values()) {
            if (owner.equals(settlementId)) {
                count++;
            }
        }
        return count;
    }

    /** All claimed cells and their owners. */
    public Map<CellPos, String> asMap() {
        return Collections.unmodifiableMap(owners);
    }
}
