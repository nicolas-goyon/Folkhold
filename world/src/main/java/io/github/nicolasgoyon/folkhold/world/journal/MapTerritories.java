package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.network.MapTerritoryPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The territories on the vanilla maps near each player (see {@link MapTerritoryPayload}).
 *
 * <ul>
 *   <li>The maps are the filled maps in the hands of the player, and in the item frames at
 *       {@link #FRAME_DISTANCE} blocks or less. Only the maps of the overworld count, when the player is in the
 *       overworld: the settlements are there.</li>
 *   <li>The payload has all the settlements whose territory can touch the area of a map: also the territories that the
 *       player did not discover. The client calculates the borders from the claims (see {@link TerritoryMaps}).</li>
 *   <li>The server checks the maps of each player every {@link #CHECK_TICKS} ticks. It sends the payload only when it
 *       changes.</li>
 * </ul>
 */
public final class MapTerritories {
    /** The server checks the maps of each player every this number of ticks. */
    static final int CHECK_TICKS = 40;
    /** The item frames with a map at this distance or less from the player count, in blocks. */
    static final double FRAME_DISTANCE = 32.0;
    /** Half of the side of a map of scale 0, in blocks. */
    private static final int HALF_MAP = 64;
    private static final Map<UUID, MapTerritoryPayload> SENT = new HashMap<>();

    private MapTerritories() {
    }

    /**
     * The area of a map, in blocks.
     *
     * @param minX the lowest X
     * @param minZ the lowest Z
     * @param maxX the highest X
     * @param maxZ the highest Z
     */
    record Area(int minX, int minZ, int maxX, int maxZ) {
        static Area of(MapItemSavedData data) {
            int half = HALF_MAP << data.scale;
            return new Area(data.centerX - half, data.centerZ - half, data.centerX + half, data.centerZ + half);
        }

        /** The center of the area. */
        int centerX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        /** The center of the area. */
        int centerZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }

        /** True if the disc of a claim can have a chunk in this area, with a margin of one chunk. */
        boolean touches(Claimant claimant) {
            int reach = 16 * (claimant.radius() + 1);
            return claimant.x() + reach >= minX && claimant.x() - reach <= maxX && claimant.z() + reach >= minZ
                    && claimant.z() - reach <= maxZ;
        }
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

    /** Sends the territories of the maps of a player if they changed since the last payload. */
    static void update(ServerPlayer player) {
        Map<Integer, Area> maps = maps(player);
        List<MapTerritoryPayload.MapCenter> centers = maps.entrySet().stream()
                .map(map -> new MapTerritoryPayload.MapCenter(map.getKey(), map.getValue().centerX(),
                        map.getValue().centerZ()))
                .sorted(Comparator.comparingInt(MapTerritoryPayload.MapCenter::id)).toList();
        MapTerritoryPayload payload = new MapTerritoryPayload(payload(player.serverLevel(),
                List.copyOf(maps.values())), centers);
        MapTerritoryPayload sent = SENT.getOrDefault(player.getUUID(), MapTerritoryPayload.EMPTY);
        if (!payload.equals(sent) && ModNetwork.send(player, payload)) {
            SENT.put(player.getUUID(), payload);
        }
    }

    /** The areas of the maps of a player, by map id: in its hands, and in the item frames near it. */
    static Map<Integer, Area> maps(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Map<Integer, Area> maps = new HashMap<>();
        for (InteractionHand hand : InteractionHand.values()) {
            add(level, player.getItemInHand(hand), maps);
        }
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class,
                player.getBoundingBox().inflate(FRAME_DISTANCE))) {
            add(level, frame.getItem(), maps);
        }
        return maps;
    }

    private static void add(ServerLevel level, ItemStack stack, Map<Integer, Area> maps) {
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
        if (data != null && data.dimension == level.dimension() && level.dimension() == Level.OVERWORLD) {
            maps.put(id.id(), Area.of(data));
        }
    }

    /**
     * The territories of areas: the settlements whose territory can touch an area, and the settlements that claim
     * chunks near them.
     *
     * @param level the level of the maps
     * @param areas the areas of the maps
     * @return the payload
     */
    static TerritoryMapPayload payload(ServerLevel level, List<Area> areas) {
        if (areas.isEmpty()) {
            return TerritoryMapPayload.EMPTY;
        }
        SettlementData settlements = SettlementData.get(level.getServer());
        FactionData factions = FactionData.get(level.getServer());
        List<TerritoryMapPayload.Village> villages = new ArrayList<>();
        for (Claimant claimant : settlements.claimants()) {
            Optional<Settlement> settlement = settlements.registry().byId(claimant.id());
            if (settlement.isPresent() && areas.stream().anyMatch(area -> area.touches(claimant))) {
                villages.add(TerritoryMaps.village(claimant, settlement.get(), factions));
            }
        }
        return new TerritoryMapPayload(villages, TerritoryMaps.neighbors(settlements.claimants(), villages));
    }
}
