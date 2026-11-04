package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.cartofold.api.CartofoldLayers;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.api.MarkerShapes;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.journal.CartofoldMaps;
import io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Kind;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The map of the admin screen: the layer {@link #LAYER} of Cartofold, with all the settlements and their territories,
 * the caravans and the traveling merchants. The operator selects a marker and teleports to it.
 *
 * <ul>
 *   <li>Only the map frames show the layer ({@link MapViewKind#FRAME}). The server sends it only to an operator who
 *       opens the admin map. The server removes it when the operator closes the map, logs out or is not an operator
 *       now (check every {@link #CHECK_TICKS} ticks).</li>
 *   <li>A settlement has the color of its faction: a square for a village, a ring for a settlement that exists only
 *       in the simulation, a cross for a ruin. A caravan or a traveling merchant is a diamond at its position on its
 *       route, with its journey as its name.</li>
 *   <li>The identifier of a marker gives its kind (see {@link Kind}). The markers are not clickable: the screen gets
 *       the clicks on the client, and the server gets no click.</li>
 *   <li>Every {@link #CHECK_TICKS} ticks, the server sends the layer again when a settlement or a claim changes.
 *       Else it sends only the markers of the groups that move, start or end ({@link LayerUpdate}).</li>
 * </ul>
 */
public final class AdminMap {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin");
    /** The server updates the admin maps every this number of ticks. */
    public static final int CHECK_TICKS = 20;
    private static final LayerInfo INFO = new LayerInfo(LAYER, Component.translatable("folkhold_world.layer.admin"),
            40, Set.of(MapViewKind.FRAME), false);
    /** The color of a settlement with no faction, as 0xRRGGBB. */
    private static final int NO_FACTION = 0x808080;
    private static final int CARAVAN_COLOR = 0x8B5A2B;
    private static final int MERCHANT_COLOR = 0x7B4FA0;
    /** The Y of a marker with no heart. */
    private static final int DEFAULT_Y = 64;
    private static final int SHORT_ID = 8;

    /** The layer that each operator has, with the settlements and the claims that gave it. */
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private record Shown(Object settlements, LayerContent content) {
    }

    private AdminMap() {
    }

    /**
     * Sends the layer to an operator, and opens the admin map. The caller checks the permission.
     *
     * @param player the operator
     */
    public static void open(ServerPlayer player) {
        SHOWN.remove(player.getUUID());
        update(player);
        ModNetwork.send(player, box(SettlementData.get(player.server)));
    }

    /** The box of all the settlements and of the discs of their claims. */
    static AdminMapPayload box(SettlementData settlements) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Settlement settlement : settlements.registry().all()) {
            minX = Math.min(minX, settlement.x());
            minZ = Math.min(minZ, settlement.z());
            maxX = Math.max(maxX, settlement.x());
            maxZ = Math.max(maxZ, settlement.z());
        }
        for (Claimant claimant : settlements.claimants()) {
            int reach = (claimant.radius() + 1) * 16;
            minX = Math.min(minX, claimant.x() - reach);
            minZ = Math.min(minZ, claimant.z() - reach);
            maxX = Math.max(maxX, claimant.x() + reach);
            maxZ = Math.max(maxZ, claimant.z() + reach);
        }
        return minX > maxX ? AdminMapPayload.EMPTY : new AdminMapPayload(minX, minZ, maxX, maxZ);
    }

    /**
     * Removes the layer of a player, if the player has it.
     *
     * @param player the player
     */
    public static void close(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) != null) {
            CartofoldLayers.remove(player, LAYER);
        }
    }

    /**
     * The layer that a player has.
     *
     * @param player the player
     * @return the last content that the server sent, or empty
     */
    public static Optional<LayerContent> shown(ServerPlayer player) {
        return Optional.ofNullable(SHOWN.get(player.getUUID())).map(Shown::content);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SHOWN.isEmpty() || event.getServer().getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        check(event.getServer());
    }

    /** Updates the layer of each operator, and removes it from a player who is not an operator now. */
    public static void check(MinecraftServer server) {
        for (UUID id : List.copyOf(SHOWN.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                SHOWN.remove(id);
            } else if (!AdminActions.isAllowed(player)) {
                close(player);
            } else {
                update(player);
            }
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SHOWN.clear();
    }

    /** Sends all the layer when a settlement or a claim changed, else the changes of the groups. */
    private static void update(ServerPlayer player) {
        MinecraftServer server = player.server;
        List<Claimant> claimants = SettlementData.get(server).claimants();
        List<MapMarker> settlements = settlementMarkers(server);
        List<MapMarker> groups = groupMarkers(server);
        Object source = List.of(claimants, settlements);
        Shown last = SHOWN.get(player.getUUID());
        boolean full = last == null || !last.settlements().equals(source)
                || settlements.size() + groups.size() > LayerContent.MAX_ELEMENTS;
        if (full) {
            List<MapMarker> markers = new ArrayList<>(settlements);
            markers.addAll(groups);
            LayerContent content = new LayerContent(INFO, CartofoldMaps.areas(villages(server, claimants), claimants),
                    List.of(), markers.subList(0, Math.min(markers.size(), LayerContent.MAX_ELEMENTS)));
            SHOWN.put(player.getUUID(), new Shown(source, content));
            CartofoldLayers.set(player, content);
            return;
        }
        Optional<LayerUpdate> change = groupChange(last.content(), groups);
        if (change.isPresent()) {
            SHOWN.put(player.getUUID(), new Shown(source, last.content().apply(change.get())));
            CartofoldLayers.update(player, LAYER, change.get());
        }
    }

    /**
     * The change of the markers of the groups: the new and the moved groups, and the groups that ended.
     *
     * @param content the layer that the player has
     * @param groups  the markers of the groups now
     * @return the change, or empty when no group changed
     */
    static Optional<LayerUpdate> groupChange(LayerContent content, List<MapMarker> groups) {
        List<MapMarker> changed = groups.stream()
                .filter(marker -> !content.marker(marker.id()).equals(Optional.of(marker))).toList();
        Set<String> ids = groups.stream().map(MapMarker::id).collect(Collectors.toSet());
        List<String> removed = content.markers().stream().map(MapMarker::id)
                .filter(id -> Kind.of(id).map(kind -> !kind.settlement()).orElse(false) && !ids.contains(id))
                .toList();
        if (changed.isEmpty() && removed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LayerUpdate(List.of(), List.of(), changed, List.of(), List.of(), removed));
    }

    /** The villages of the territories: all the settlements that claim chunks. */
    private static List<TerritoryMapPayload.Village> villages(MinecraftServer server, List<Claimant> claimants) {
        SettlementData settlements = SettlementData.get(server);
        FactionData factions = FactionData.get(server);
        List<TerritoryMapPayload.Village> villages = new ArrayList<>();
        for (Claimant claimant : claimants) {
            settlements.registry().byId(claimant.id())
                    .ifPresent(settlement -> villages.add(TerritoryMaps.village(claimant, settlement, factions)));
        }
        return villages;
    }

    /** A marker for each settlement, in the color of its faction. */
    static List<MapMarker> settlementMarkers(MinecraftServer server) {
        FactionData factions = FactionData.get(server);
        List<MapMarker> markers = new ArrayList<>();
        for (Settlement settlement : SettlementData.get(server).registry().all()) {
            Kind kind = !settlement.isActive() ? Kind.RUIN : settlement.hasHeart() ? Kind.VILLAGE : Kind.SIMULATED;
            int color = factions.byId(settlement.factionId()).map(FactionSpec::color).orElse(NO_FACTION);
            int y = settlement.hasHeart() ? settlement.heartY() : DEFAULT_Y;
            markers.add(marker(kind, settlement.id(), Component.literal(settlement.name()),
                    new BlockPos(settlement.x(), y, settlement.z()), color));
        }
        return markers;
    }

    /** A marker for each caravan and each traveling merchant, at its position on its route. */
    static List<MapMarker> groupMarkers(MinecraftServer server) {
        SettlementData settlements = SettlementData.get(server);
        List<MapMarker> markers = new ArrayList<>();
        for (MobileGroup group : MobileGroupData.get(server).groups().all()) {
            Route.Position position = group.position();
            String journey = shortId(group.id()) + " " + name(settlements, group.originId()) + " → "
                    + name(settlements, group.destinationId());
            boolean merchant = group.kind() == GroupKind.TRAVELING_MERCHANT;
            markers.add(marker(merchant ? Kind.MERCHANT : Kind.CARAVAN, group.id(), Component.literal(journey),
                    new BlockPos((int) Math.floor(position.x()), DEFAULT_Y, (int) Math.floor(position.z())),
                    merchant ? MERCHANT_COLOR : CARAVAN_COLOR));
        }
        return markers;
    }

    /** A marker with the shape of its kind. Its tooltip gives the kind and the position. */
    private static MapMarker marker(Kind kind, String id, Component name, BlockPos pos, int color) {
        ResourceLocation shape = switch (kind) {
            case VILLAGE -> MarkerShapes.SQUARE;
            case SIMULATED -> MarkerShapes.RING;
            case RUIN -> MarkerShapes.CROSS;
            case CARAVAN, MERCHANT -> MarkerShapes.DIAMOND;
        };
        Component tooltip = Component.translatable("folkhold_world.map.admin",
                Component.translatable("screen.folkhold_world.admin.map.kind." + kind.key()), pos.getX(),
                pos.getZ());
        return new MapMarker(kind.markerId(id), Level.OVERWORLD, pos, color & 0xFFFFFF, Optional.of(shape), name,
                Optional.of(tooltip), false, true);
    }

    private static String name(SettlementData settlements, String id) {
        return settlements.registry().byId(id).map(Settlement::name).orElse("?");
    }

    private static String shortId(String id) {
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }
}
