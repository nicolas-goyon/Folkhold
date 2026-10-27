package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The chunks of the discovered territories for the journal map: the village of each chunk, and the sides of the chunk
 * on a border. The raster of a view of the territories does not change (see {@link TerritoryMapView}).
 *
 * <p>A side is on a border when the next chunk has another owner: another settlement, discovered or not, or no
 * owner.
 */
final class TerritoryRaster {
    /** The north side of a chunk (lower Z). */
    static final int NORTH = 1;
    /** The east side of a chunk (higher X). */
    static final int EAST = 2;
    /** The south side of a chunk (higher Z). */
    static final int SOUTH = 4;
    /** The west side of a chunk (lower X). */
    static final int WEST = 8;

    /** The chunks in rows: by Z, then by X. */
    private static final Comparator<Cell> ROW_ORDER = Comparator.comparingInt(Cell::chunkZ)
            .thenComparingInt(Cell::chunkX);

    /** A raster with no chunks. */
    static final TerritoryRaster EMPTY = new TerritoryRaster(List.of(), -1);

    /**
     * A chunk of a discovered territory.
     *
     * @param chunkX  chunk X
     * @param chunkZ  chunk Z
     * @param village the index of the village in the list of the view
     * @param borders the sides on a border: a sum of {@link #NORTH}, {@link #EAST}, {@link #SOUTH} and
     *                {@link #WEST}
     */
    record Cell(int chunkX, int chunkZ, int village, int borders) {
    }

    private final List<Cell> cells;
    private final Map<Long, Cell> byChunk = new HashMap<>();
    private final int version;

    private TerritoryRaster(List<Cell> cells, int version) {
        this.cells = cells;
        this.version = version;
        for (Cell cell : cells) {
            byChunk.put(key(cell.chunkX(), cell.chunkZ()), cell);
        }
    }

    /**
     * Calculates the chunks of the discovered territories of a view.
     *
     * @param view the view of the territories
     * @return the raster
     */
    static TerritoryRaster of(TerritoryMapView view) {
        List<Cell> cells = new ArrayList<>();
        List<TerritoryMapPayload.Village> villages = view.villages();
        for (int index = 0; index < villages.size(); index++) {
            Claimant claim = villages.get(index).claimant();
            // The chunks of the claim, and a margin of one chunk for the sides.
            int reach = claim.radius() + 2;
            int minX = Math.floorDiv(claim.x(), 16) - reach;
            int minZ = Math.floorDiv(claim.z(), 16) - reach;
            int size = 2 * reach + 1;
            boolean[] owned = new boolean[size * size];
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    owned[j * size + i] = view.ownerOf(minX + i, minZ + j).map(claim.id()::equals).orElse(false);
                }
            }
            for (int j = 1; j < size - 1; j++) {
                for (int i = 1; i < size - 1; i++) {
                    if (!owned[j * size + i]) {
                        continue;
                    }
                    int borders = 0;
                    borders |= owned[(j - 1) * size + i] ? 0 : NORTH;
                    borders |= owned[j * size + i + 1] ? 0 : EAST;
                    borders |= owned[(j + 1) * size + i] ? 0 : SOUTH;
                    borders |= owned[j * size + i - 1] ? 0 : WEST;
                    cells.add(new Cell(minX + i, minZ + j, index, borders));
                }
            }
        }
        cells.sort(ROW_ORDER);
        return new TerritoryRaster(List.copyOf(cells), view.version());
    }

    /** The chunks of the discovered territories, in rows: by Z, then by X. */
    List<Cell> cells() {
        return cells;
    }

    /** The number of the view of the territories. */
    int version() {
        return version;
    }

    /** The chunk of a discovered territory at a chunk position. */
    Optional<Cell> at(int chunkX, int chunkZ) {
        return Optional.ofNullable(byChunk.get(key(chunkX, chunkZ)));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
