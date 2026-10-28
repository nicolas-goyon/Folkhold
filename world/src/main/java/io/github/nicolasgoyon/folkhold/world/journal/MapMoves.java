package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.world.mixin.MapItemSavedDataAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The move of a vanilla map in the cartography table. The game puts the center of a map on a grid: a zoomed-out map
 * is not always around the player. The move gives a new map, one step from the old map in a direction.
 *
 * <ul>
 *   <li>In the cartography table, with a map and a paper, a button selects a direction (north, south, west or east).
 *       The result is the moved map in place of the zoomed map. The same button again selects the zoom of the
 *       game.</li>
 *   <li>A step is half of the side of the map: the new map has half of the area of the old map.</li>
 *   <li>The new map has the same scale. It keeps the explored pixels of the old map in their new positions.</li>
 *   <li>A locked map does not move.</li>
 * </ul>
 */
public final class MapMoves {
    /** The directions: the ids of the buttons of the screen. */
    public static final int NORTH = 0;
    public static final int SOUTH = 1;
    public static final int WEST = 2;
    public static final int EAST = 3;
    /** No direction: the zoom of the game. */
    public static final int NONE = 4;
    /** The side of a map, in pixels. */
    public static final int SIZE = 128;
    /** A step, in pixels: half of the side of a map. */
    public static final int STEP = SIZE / 2;
    /** The key of the direction in the custom data of the result of the table. */
    static final String KEY = "folkhold_move";

    private MapMoves() {
    }

    /** The move of a direction on the X axis, in steps. */
    public static int dx(int direction) {
        return direction == EAST ? 1 : direction == WEST ? -1 : 0;
    }

    /** The move of a direction on the Z axis, in steps. */
    public static int dz(int direction) {
        return direction == SOUTH ? 1 : direction == NORTH ? -1 : 0;
    }

    /**
     * The result of the table: a copy of the map with the direction of the move. The move happens when the player takes
     * the result (see {@link #postProcess}).
     *
     * @param map       a copy of the map
     * @param direction the direction
     * @return the copy
     */
    public static ItemStack marked(ItemStack map, int direction) {
        CompoundTag tag = map.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY, direction);
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return map;
    }

    /**
     * Moves a map that the player takes from the table: the map gets a new id, with the moved data.
     *
     * @param stack the map
     * @param level the level
     */
    public static void postProcess(ItemStack stack, Level level) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(KEY)) {
            return;
        }
        CompoundTag tag = custom.copyTag();
        int direction = tag.getInt(KEY);
        tag.remove(KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        if (!(level instanceof ServerLevel) || data == null || data.locked) {
            return;
        }
        MapId id = level.getFreeMapId();
        level.setMapData(id, moved(data, direction));
        stack.set(DataComponents.MAP_ID, id);
    }

    /**
     * The data of a map, one step in a direction.
     *
     * @param data      the data of the map
     * @param direction the direction
     * @return the new data, with the explored pixels of the map in their new positions
     */
    static MapItemSavedData moved(MapItemSavedData data, int direction) {
        int blocks = STEP << data.scale;
        MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) data;
        MapItemSavedData result = MapItemSavedDataAccessor.folkhold$create(data.centerX + dx(direction) * blocks,
                data.centerZ + dz(direction) * blocks, data.scale, accessor.folkhold$trackingPosition(),
                accessor.folkhold$unlimitedTracking(), false, data.dimension);
        result.colors = shifted(data.colors, dx(direction) * STEP, dz(direction) * STEP);
        result.setDirty();
        return result;
    }

    /**
     * The pixels of a map after a move: the pixel (x, z) of the new map is the pixel (x + dx, z + dz) of the old map, or
     * 0 (not explored) out of the old map.
     *
     * @param colors the pixels of the old map, at the index {@code x + z * SIZE}
     * @param dx     the move on the X axis, in pixels
     * @param dz     the move on the Z axis, in pixels
     * @return the pixels of the new map
     */
    static byte[] shifted(byte[] colors, int dx, int dz) {
        byte[] result = new byte[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int oldX = x + dx;
                int oldZ = z + dz;
                if (oldX >= 0 && oldX < SIZE && oldZ >= 0 && oldZ < SIZE) {
                    result[x + z * SIZE] = colors[oldX + oldZ * SIZE];
                }
            }
        }
        return result;
    }
}
