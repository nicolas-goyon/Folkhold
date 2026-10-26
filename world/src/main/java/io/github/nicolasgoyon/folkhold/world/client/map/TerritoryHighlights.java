package io.github.nicolasgoyon.folkhold.world.client.map;

import io.github.nicolasgoyon.folkhold.world.client.ClientTerritoryMap;
import io.github.nicolasgoyon.folkhold.world.client.TerritoryMapView;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The highlights of the discovered territories on the maps of Xaero's mods: the colors of a chunk and its tooltip.
 * The highlighters of the two mods use these methods. The territories are only in the Overworld.
 *
 * <p>A chunk of a discovered territory has the color of its faction: a light fill, and a strong color on the sides
 * that touch another owner (the border).
 */
final class TerritoryHighlights {
    /** The opacity of the fill of a territory, from 0 to 255. */
    static final int FILL_ALPHA = 64;
    /** The opacity of the border of a territory, from 0 to 255. */
    static final int BORDER_ALPHA = 204;
    /** The translation key of the tooltip of a territory: the village and its faction. */
    static final String TOOLTIP_KEY = "folkhold_world.map.territory";

    /** True if the maps show the territories: the option of the client config, read on the client thread. */
    private static volatile boolean shown = true;

    private TerritoryHighlights() {
    }

    static void setShown(boolean value) {
        shown = value;
    }

    /** The view of the territories, or empty when the maps do not show them, for example in the Nether. */
    static Optional<TerritoryMapView> view(ResourceKey<Level> dimension) {
        return shown && Level.OVERWORLD.equals(dimension) ? Optional.of(ClientTerritoryMap.get()) : Optional.empty();
    }

    /** A number that changes when the territories of a region change, or 0 for a region with no territory. */
    static int regionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return view(dimension).filter(view -> view.hasTerritoryIn(regionX, regionZ))
                .map(view -> 31 * view.version() + 1).orElse(0);
    }

    static boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return view(dimension).map(view -> view.hasTerritoryIn(regionX, regionZ)).orElse(false);
    }

    static boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return view(dimension).flatMap(view -> view.villageAt(chunkX, chunkZ)).isPresent();
    }

    /**
     * The colors of a chunk, as the highlighters of Xaero's mods read them: the center, then the top, right, bottom
     * and left sides (north, east, south and west).
     *
     * @param store the array of 5 colors to fill
     * @return the array, or null for a chunk with no discovered territory
     */
    static int[] colors(ResourceKey<Level> dimension, int chunkX, int chunkZ, int[] store) {
        Optional<TerritoryMapView> view = view(dimension);
        Optional<TerritoryMapPayload.Village> village = view.flatMap(found -> found.villageAt(chunkX, chunkZ));
        if (village.isEmpty()) {
            return null;
        }
        int fill = color(village.get().color(), FILL_ALPHA);
        int border = color(village.get().color(), BORDER_ALPHA);
        TerritoryMapView found = view.get();
        store[0] = fill;
        store[1] = found.isBorder(chunkX, chunkZ, 0, -1) ? border : fill;
        store[2] = found.isBorder(chunkX, chunkZ, 1, 0) ? border : fill;
        store[3] = found.isBorder(chunkX, chunkZ, 0, 1) ? border : fill;
        store[4] = found.isBorder(chunkX, chunkZ, -1, 0) ? border : fill;
        return store;
    }

    /**
     * A color in the format of Xaero's mods: blue, green, red and alpha, from the highest byte.
     *
     * @param rgb   the color as 0xRRGGBB
     * @param alpha the opacity, from 0 to 255
     * @return the color
     */
    static int color(int rgb, int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (blue << 24) | (green << 16) | (red << 8) | (alpha & 0xFF);
    }

    /** The tooltip of a chunk: the village and its faction, or null for a chunk with no discovered territory. */
    static Component tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return view(dimension).flatMap(view -> view.villageAt(chunkX, chunkZ)).map(TerritoryHighlights::tooltip)
                .orElse(null);
    }

    /** The tooltip of a village: its name and its faction, in the color of the faction. */
    static Component tooltip(TerritoryMapPayload.Village village) {
        return Component.translatable(TOOLTIP_KEY, village.name(), Component.literal(village.faction())
                .withStyle(style -> style.withColor(TextColor.fromRgb(village.color()))));
    }
}
