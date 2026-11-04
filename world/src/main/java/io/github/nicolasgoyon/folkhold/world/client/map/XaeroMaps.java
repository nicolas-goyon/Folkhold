package io.github.nicolasgoyon.folkhold.world.client.map;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.ClientTerritoryMap;
import io.github.nicolasgoyon.folkhold.world.client.TerritoryMapView;
import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The discovered territories and villages on the maps of Xaero's Minimap and Xaero's World Map. The two mods are
 * optional: with no Xaero's mod, this class does nothing.
 *
 * <ul>
 *   <li>The minimap and the world map show the territories of the discovered villages, in the color of their faction
 *       (see {@link TerritoryHighlights}). A tooltip gives the village and its faction.</li>
 *   <li>The minimap shows the discovered villages as waypoints. Xaero's World Map also shows them.</li>
 *   <li>The client config hides the territories ({@link ClientConfig#SHOW_MAP_TERRITORIES}) or the waypoints of the
 *       villages ({@link ClientConfig#SHOW_MAP_VILLAGES}).</li>
 *   <li>The hooks use internal parts of Xaero's mods. An error in a hook stops the hook of this mod for the rest of
 *       the game, and the log gives the error. The other parts of the game do not change.</li>
 * </ul>
 *
 * <p>This class refers to the classes of Xaero's mods only through the hooks, which load only when a Xaero's mod is
 * present.
 */
public final class XaeroMaps {
    /** The mod id of Xaero's World Map. */
    static final String WORLD_MAP_MOD = "xaeroworldmap";
    /** The mod id of Xaero's Minimap. */
    static final String MINIMAP_MOD = "xaerominimap";
    /** The hooks check the maps every this number of ticks. */
    private static final int CHECK_TICKS = 20;

    private static int ticks;
    /** The state of the territories and of the options at the last update, or empty before the first update. */
    private static String shown = "";
    private static boolean worldMapFailed;
    private static boolean minimapFailed;

    private XaeroMaps() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % CHECK_TICKS != 0 || Minecraft.getInstance().player == null) {
            return;
        }
        TerritoryMapView view = ClientTerritoryMap.get();
        boolean territories = ClientConfig.SHOW_MAP_TERRITORIES.get();
        boolean villages = ClientConfig.SHOW_MAP_VILLAGES.get();
        TerritoryHighlights.setShown(territories);
        String state = view.version() + "/" + territories + "/" + villages;
        boolean changed = !state.equals(shown);
        shown = state;
        if (!worldMapFailed && ModList.get().isLoaded(WORLD_MAP_MOD)) {
            try {
                WorldMapHook.update(changed);
            } catch (Throwable error) {
                worldMapFailed = true;
                FolkholdWorld.LOGGER.warn("The territories cannot show on the map of Xaero's World Map", error);
            }
        }
        if (!minimapFailed && ModList.get().isLoaded(MINIMAP_MOD)) {
            try {
                MinimapHook.update(changed, view, villages);
            } catch (Throwable error) {
                minimapFailed = true;
                FolkholdWorld.LOGGER.warn("The territories cannot show on the map of Xaero's Minimap", error);
            }
        }
    }
}
