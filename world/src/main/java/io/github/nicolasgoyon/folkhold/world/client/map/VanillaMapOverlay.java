package io.github.nicolasgoyon.folkhold.world.client.map;

import io.github.nicolasgoyon.folkhold.world.client.ClientMapTerritories;
import io.github.nicolasgoyon.folkhold.world.client.TerritoryMapView;
import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The territories on the vanilla maps: the borders of the territories in the color of their faction, and a marker at
 * each village (see {@link ClientMapTerritories}). The overlay changes only the texture of the map on the client: the
 * data of the map does not change.
 *
 * <ul>
 *   <li>A pixel is a border when its chunk has an owner, and the chunk of a next pixel has another owner. The border
 *       is inside the territory, with a width of one pixel.</li>
 *   <li>A village has a marker of {@link #MARKER} x {@link #MARKER} pixels in the color of its faction, with a dark
 *       outline.</li>
 *   <li>Only the maps of the overworld show the territories. The client setting
 *       {@link ClientConfig#SHOW_VANILLA_MAP_TERRITORIES} turns the overlay off.</li>
 * </ul>
 */
public final class VanillaMapOverlay {
    /** The side of a map, in pixels. */
    public static final int SIZE = 128;
    /** The side of the marker of a village, in pixels, without the outline. */
    public static final int MARKER = 3;
    /** The color of the outline of a marker, as 0xRRGGBB. */
    private static final int OUTLINE = 0x202020;
    /** No color: the pixel of the map stays. */
    public static final int NONE = -1;

    private VanillaMapOverlay() {
    }

    /**
     * The colors of the overlay of a map.
     *
     * @param centerX the X of the center of the map, in blocks
     * @param centerZ the Z of the center of the map, in blocks
     * @param scale   the scale of the map: a pixel is 2^scale blocks
     * @param owner   the owner of a chunk (chunk X, chunk Z), or empty
     * @param colors  the color of each owner, as 0xRRGGBB. An owner with no color has no border.
     * @param markers the markers of the villages
     * @return the color of each pixel as 0xRRGGBB, or {@link #NONE}, at the index {@code z * SIZE + x}
     */
    public static int[] colors(int centerX, int centerZ, int scale, BiFunction<Integer, Integer, Optional<String>> owner,
            Map<String, Integer> colors, List<Marker> markers) {
        int factor = 1 << scale;
        int originX = (Math.floorDiv(centerX, factor) - SIZE / 2) * factor;
        int originZ = (Math.floorDiv(centerZ, factor) - SIZE / 2) * factor;
        String[] owners = new String[(SIZE + 2) * (SIZE + 2)];
        Map<Long, String> cache = new HashMap<>();
        for (int z = -1; z <= SIZE; z++) {
            for (int x = -1; x <= SIZE; x++) {
                int chunkX = Math.floorDiv(originX + x * factor, 16);
                int chunkZ = Math.floorDiv(originZ + z * factor, 16);
                owners[(z + 1) * (SIZE + 2) + x + 1] = cache.computeIfAbsent(((long) chunkX << 32) ^ (chunkZ
                        & 0xFFFFFFFFL), key -> owner.apply(chunkX, chunkZ).orElse(null));
            }
        }
        int[] result = new int[SIZE * SIZE];
        Arrays.fill(result, NONE);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                String here = owners[(z + 1) * (SIZE + 2) + x + 1];
                Integer color = here == null ? null : colors.get(here);
                if (color == null) {
                    continue;
                }
                boolean border = false;
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    String next = owners[(z + 1 + side[1]) * (SIZE + 2) + x + 1 + side[0]];
                    border |= !here.equals(next);
                }
                if (border) {
                    result[z * SIZE + x] = color;
                }
            }
        }
        for (Marker marker : markers) {
            int px = Math.floorDiv(marker.x() - originX, factor);
            int pz = Math.floorDiv(marker.z() - originZ, factor);
            int half = MARKER / 2;
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                for (int dx = -half - 1; dx <= half + 1; dx++) {
                    int x = px + dx;
                    int z = pz + dz;
                    if (x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
                        boolean edge = Math.abs(dx) > half || Math.abs(dz) > half;
                        result[z * SIZE + x] = edge ? OUTLINE : marker.color();
                    }
                }
            }
        }
        return result;
    }

    /**
     * Draws the overlay on the pixels of a map texture.
     *
     * @param data   the data of the map
     * @param pixels the pixels of the texture: {@code setPixel(x, z, abgr)}
     */
    public static void draw(MapItemSavedData data, PixelSink pixels) {
        if (!ClientConfig.SHOW_VANILLA_MAP_TERRITORIES.get() || data.dimension != Level.OVERWORLD) {
            return;
        }
        TerritoryMapView view = ClientMapTerritories.get();
        if (view.villages().isEmpty()) {
            return;
        }
        Map<String, Integer> colors = new HashMap<>();
        List<Marker> markers = new ArrayList<>();
        for (TerritoryMapPayload.Village village : view.villages()) {
            colors.put(village.id(), village.color());
            markers.add(new Marker(village.claimant().x(), village.claimant().z(), village.color()));
        }
        int[] overlay = colors(data.centerX, data.centerZ, data.scale, view::ownerOf, colors, markers);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = overlay[z * SIZE + x];
                if (rgb != NONE) {
                    pixels.set(x, z, abgr(rgb));
                }
            }
        }
    }

    /**
     * The marker of a village.
     *
     * @param x     the X of the village, in blocks
     * @param z     the Z of the village, in blocks
     * @param color the color of the faction of the village, as 0xRRGGBB
     */
    public record Marker(int x, int z, int color) {
    }

    /** A color 0xRRGGBB as an opaque color 0xAABBGGRR, the format of the pixels of a texture. */
    static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    /** The pixels of a texture. */
    @FunctionalInterface
    public interface PixelSink {
        /**
         * Sets a pixel.
         *
         * @param x    the X of the pixel
         * @param z    the Y of the pixel in the texture: the Z of the map
         * @param abgr the color, as 0xAABBGGRR
         */
        void set(int x, int z, int abgr);
    }
}
