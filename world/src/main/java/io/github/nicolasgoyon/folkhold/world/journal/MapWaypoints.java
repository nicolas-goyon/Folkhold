package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModMapDecorations;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The waypoints of the villages on the vanilla maps, as the banners of the game.
 *
 * <ul>
 *   <li>A player uses a filled map of the Overworld on a village heart: the map gets a waypoint at the heart, with the
 *       name of the village. A second use removes the waypoint.</li>
 *   <li>The server keeps the waypoints of each map (data {@code folkhold_map_waypoints}). Every {@link #CHECK_TICKS}
 *       ticks, it gives the decorations to the maps in the hands of each player and in the item frames at
 *       {@link #FRAME_DISTANCE} blocks or less: the game does not save them in the map.</li>
 *   <li>A waypoint of a village that is not active any more (a ruin, a village with no heart) goes away.</li>
 * </ul>
 */
public final class MapWaypoints extends SavedData {
    private static final String FILE_ID = "folkhold_map_waypoints";
    private static final SavedData.Factory<MapWaypoints> FACTORY =
            new SavedData.Factory<>(MapWaypoints::new, MapWaypoints::load, null);
    /** The id of a decoration of a waypoint starts with this text, then the id of the settlement. */
    static final String DECORATION_PREFIX = "folkhold_village_";
    /** The server gives the waypoints to the maps near each player every this number of ticks. */
    static final int CHECK_TICKS = 40;
    /** The item frames with a map at this distance or less from a player get the waypoints, in blocks. */
    static final double FRAME_DISTANCE = 32.0;
    /** The decoration of a waypoint points down, as a banner. */
    private static final double ROTATION = 180.0;

    /**
     * A waypoint.
     *
     * @param x    the X of the heart
     * @param z    the Z of the heart
     * @param name the name of the village
     */
    record Waypoint(int x, int z, String name) {
    }

    /** The waypoints of each map, by id of settlement. */
    private final Map<Integer, Map<String, Waypoint>> maps = new HashMap<>();

    MapWaypoints() {
    }

    /** The waypoints of a server. */
    public static MapWaypoints get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * A player uses a filled map on a village heart: the waypoint of the village goes on the map, or goes away.
     *
     * @param event the event
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        MapId id = stack.get(DataComponents.MAP_ID);
        if (id == null || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || !(level.getBlockEntity(event.getPos()) instanceof VillageHeartBlockEntity heart)) {
            return;
        }
        MapItemSavedData data = MapItem.getSavedData(id, level);
        Optional<Settlement> settlement = Settlements.of(level.getServer(), heart);
        if (data == null || data.dimension != Level.OVERWORLD || settlement.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        boolean added = get(level).toggle(id.id(), settlement.get().id(),
                new Waypoint(event.getPos().getX(), event.getPos().getZ(), settlement.get().name()));
        if (!added) {
            data.removeDecoration(DECORATION_PREFIX + settlement.get().id());
        }
        apply(level, id.id(), data);
        player.displayClientMessage(Component.translatable(added ? "folkhold_world.map.waypoint.added"
                : "folkhold_world.map.waypoint.removed", settlement.get().name()), true);
    }

    /** Gives the waypoints to the maps of a player: in its hands, and in the item frames near it. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<ItemStack> stacks = new ArrayList<>();
        for (InteractionHand hand : InteractionHand.values()) {
            stacks.add(player.getItemInHand(hand));
        }
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class,
                player.getBoundingBox().inflate(FRAME_DISTANCE))) {
            stacks.add(frame.getItem());
        }
        for (ItemStack stack : stacks) {
            MapId id = stack.get(DataComponents.MAP_ID);
            MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
            if (data != null && data.dimension == Level.OVERWORLD) {
                apply(level, id.id(), data);
            }
        }
    }

    /**
     * Adds a waypoint to a map, or removes it.
     *
     * @return true if the map has the waypoint now
     */
    boolean toggle(int map, String settlement, Waypoint waypoint) {
        Map<String, Waypoint> waypoints = maps.computeIfAbsent(map, key -> new LinkedHashMap<>());
        boolean added = waypoints.remove(settlement) == null;
        if (added) {
            waypoints.put(settlement, waypoint);
        } else if (waypoints.isEmpty()) {
            maps.remove(map);
        }
        setDirty();
        return added;
    }

    /**
     * Gives the decorations of the waypoints to a map. A waypoint of a village that is not active goes away.
     *
     * @param level the level
     * @param map   the id of the map
     * @param data  the data of the map
     */
    public static void apply(ServerLevel level, int map, MapItemSavedData data) {
        MapWaypoints waypoints = get(level);
        Map<String, Waypoint> ofMap = waypoints.maps.get(map);
        if (ofMap == null) {
            return;
        }
        SettlementData settlements = SettlementData.get(level.getServer());
        for (Map.Entry<String, Waypoint> entry : Map.copyOf(ofMap).entrySet()) {
            String decoration = DECORATION_PREFIX + entry.getKey();
            boolean active = settlements.registry().byId(entry.getKey()).filter(Settlement::isActive).isPresent();
            if (active) {
                Waypoint waypoint = entry.getValue();
                data.addDecoration(ModMapDecorations.VILLAGE, level, decoration, waypoint.x() + 0.5,
                        waypoint.z() + 0.5, ROTATION, Component.literal(waypoint.name()));
            } else {
                data.removeDecoration(decoration);
                waypoints.toggle(map, entry.getKey(), entry.getValue());
            }
        }
    }

    /** The waypoints of a map, by id of settlement. */
    Map<String, Waypoint> waypoints(int map) {
        return maps.getOrDefault(map, Map.of());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        maps.forEach((map, waypoints) -> waypoints.forEach((settlement, waypoint) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Map", map);
            entry.putString("Settlement", settlement);
            entry.putLong("Pos", new BlockPos(waypoint.x(), 0, waypoint.z()).asLong());
            entry.putString("Name", waypoint.name());
            list.add(entry);
        }));
        tag.put("Waypoints", list);
        return tag;
    }

    private static MapWaypoints load(CompoundTag tag, HolderLookup.Provider registries) {
        MapWaypoints data = new MapWaypoints();
        ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            data.maps.computeIfAbsent(entry.getInt("Map"), key -> new LinkedHashMap<>())
                    .put(entry.getString("Settlement"), new Waypoint(pos.getX(), pos.getZ(), entry.getString("Name")));
        }
        return data;
    }
}
