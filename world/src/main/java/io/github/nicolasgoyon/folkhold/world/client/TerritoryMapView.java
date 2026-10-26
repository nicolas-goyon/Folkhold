package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The discovered territories for the maps: the owner of each chunk, as the server calculates it (see
 * {@link TerritoryClaims}). The maps show only the territories of the discovered villages.
 *
 * <p>A view does not change. The maps of other mods read it from their threads.
 */
public final class TerritoryMapView {
    /** No discovered territories. */
    public static final TerritoryMapView EMPTY = new TerritoryMapView(TerritoryMapPayload.EMPTY, 0);
    /** The size of a region of the maps, in chunks. */
    public static final int REGION_CHUNKS = 32;

    private final List<TerritoryMapPayload.Village> villages;
    private final Map<String, TerritoryMapPayload.Village> byId = new HashMap<>();
    private final TerritoryClaims claims;
    private final int version;

    /**
     * Makes the view of a payload.
     *
     * @param payload the territories of the payload
     * @param version the number of the view: a new payload gives a new number
     */
    public TerritoryMapView(TerritoryMapPayload payload, int version) {
        this.villages = payload.villages();
        this.version = version;
        List<Claimant> claimants = new ArrayList<>(payload.others());
        for (TerritoryMapPayload.Village village : villages) {
            byId.put(village.id(), village);
            claimants.add(village.claimant());
        }
        this.claims = TerritoryClaims.compute(claimants);
    }

    /** The discovered villages with a territory. */
    public List<TerritoryMapPayload.Village> villages() {
        return villages;
    }

    /** The number of the view: the maps show the territories again when it changes. */
    public int version() {
        return version;
    }

    /**
     * The id of the settlement that owns a chunk, discovered or not.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return the id, or empty for a chunk that no known settlement claims
     */
    public Optional<String> ownerOf(int chunkX, int chunkZ) {
        return claims.ownerOfChunk(chunkX, chunkZ);
    }

    /**
     * The discovered village that owns a chunk.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return the village, or empty if no discovered village owns the chunk
     */
    public Optional<TerritoryMapPayload.Village> villageAt(int chunkX, int chunkZ) {
        return ownerOf(chunkX, chunkZ).map(byId::get);
    }

    /**
     * True if the chunk next to a chunk has another owner: the side of the chunk is a border.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @param dx     -1, 0 or 1: the direction of the next chunk on the X axis
     * @param dz     -1, 0 or 1: the direction of the next chunk on the Z axis
     * @return true if the two chunks have different owners
     */
    public boolean isBorder(int chunkX, int chunkZ, int dx, int dz) {
        return !ownerOf(chunkX, chunkZ).equals(ownerOf(chunkX + dx, chunkZ + dz));
    }

    /**
     * True if a region of the maps can contain a discovered territory: the box of the claim of a discovered village
     * touches the region.
     *
     * @param regionX region X: chunk X divided by {@link #REGION_CHUNKS}
     * @param regionZ region Z: chunk Z divided by {@link #REGION_CHUNKS}
     * @return true if a discovered territory can be in the region
     */
    public boolean hasTerritoryIn(int regionX, int regionZ) {
        int minX = regionX * REGION_CHUNKS;
        int minZ = regionZ * REGION_CHUNKS;
        int maxX = minX + REGION_CHUNKS - 1;
        int maxZ = minZ + REGION_CHUNKS - 1;
        for (TerritoryMapPayload.Village village : villages) {
            Claimant claim = village.claimant();
            int chunkX = Math.floorDiv(claim.x(), 16);
            int chunkZ = Math.floorDiv(claim.z(), 16);
            int reach = claim.radius() + 1;
            if (chunkX + reach >= minX && chunkX - reach <= maxX && chunkZ + reach >= minZ
                    && chunkZ - reach <= maxZ) {
                return true;
            }
        }
        return false;
    }
}
