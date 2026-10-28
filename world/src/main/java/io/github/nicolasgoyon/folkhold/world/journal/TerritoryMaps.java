package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The territories and the villages that each player discovered, for the maps of the client (see
 * {@link TerritoryMapPayload}).
 *
 * <ul>
 *   <li>The maps show the territories of the villages of the journal of the player (see {@link Journals}), with the
 *       color of their faction. A ruin has no territory.</li>
 *   <li>The payload also has the other settlements that claim chunks near these villages: the client needs them to
 *       calculate the borders. The payload does not give their names.</li>
 *   <li>The server checks the map of each player every {@link #CHECK_TICKS} ticks, and after each discovery. It sends
 *       the payload only when the map changes.</li>
 * </ul>
 */
public final class TerritoryMaps {
    /** The server checks the map of each player every this number of ticks. */
    static final int CHECK_TICKS = 100;
    /** The Y of a waypoint for a village with no heart. */
    private static final int DEFAULT_Y = 64;
    private static final Map<UUID, TerritoryMapPayload> SENT = new HashMap<>();

    private TerritoryMaps() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_TICKS == 0) {
            update(player);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    /**
     * Sends the map of a player if it changed since the last payload.
     *
     * @param player the player
     */
    public static void update(ServerPlayer player) {
        TerritoryMapPayload payload = payload(player);
        if (!payload.equals(SENT.get(player.getUUID())) && ModNetwork.send(player, payload)) {
            SENT.put(player.getUUID(), payload);
        }
    }

    /**
     * The map of a player: the discovered villages with a territory, and the other settlements whose territory can
     * touch them.
     *
     * @param player the player
     * @return the payload
     */
    public static TerritoryMapPayload payload(ServerPlayer player) {
        MinecraftServer server = player.server;
        SettlementData settlements = SettlementData.get(server);
        FactionData factions = FactionData.get(server);
        Map<String, Claimant> claims = new HashMap<>();
        for (Claimant claimant : settlements.claimants()) {
            claims.put(claimant.id(), claimant);
        }
        List<TerritoryMapPayload.Village> villages = new ArrayList<>();
        for (String id : JournalData.get(server).visits(player.getUUID()).keySet()) {
            Claimant claimant = claims.get(id);
            Optional<Settlement> settlement = settlements.registry().byId(id);
            if (claimant == null || settlement.isEmpty()) {
                continue;
            }
            villages.add(village(claimant, settlement.get(), factions));
        }
        return new TerritoryMapPayload(villages, neighbors(settlements.claimants(), villages));
    }

    /**
     * A village of the maps: its claim, the Y of its heart, its name, and the name and the color of its faction.
     *
     * @param claimant   the claim of the settlement
     * @param settlement the settlement
     * @param factions   the factions
     * @return the village
     */
    public static TerritoryMapPayload.Village village(Claimant claimant, Settlement settlement,
            FactionData factions) {
        Optional<FactionSpec> faction = factions.byId(settlement.factionId());
        return new TerritoryMapPayload.Village(claimant, settlement.hasHeart() ? settlement.heartY() : DEFAULT_Y,
                settlement.name(), faction.map(FactionSpec::name).orElse(settlement.factionId()),
                faction.map(FactionSpec::color).orElse(0xFFFFFF));
    }

    /**
     * The settlements that are not in a list of villages, and whose territory can touch the territory of one of these
     * villages: the discs of the two claims can have a chunk in common.
     */
    static List<Claimant> neighbors(List<Claimant> claimants, List<TerritoryMapPayload.Village> villages) {
        List<Claimant> neighbors = new ArrayList<>();
        for (Claimant claimant : claimants) {
            boolean village = villages.stream().anyMatch(known -> known.id().equals(claimant.id()));
            if (!village && villages.stream().anyMatch(known -> touches(known.claimant(), claimant))) {
                neighbors.add(claimant);
            }
        }
        return neighbors;
    }

    /** True if the discs of two claims can have a chunk in common, with a margin of one chunk. */
    static boolean touches(Claimant first, Claimant second) {
        long reach = 16L * (first.radius() + second.radius() + 2);
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz <= reach * reach;
    }
}
