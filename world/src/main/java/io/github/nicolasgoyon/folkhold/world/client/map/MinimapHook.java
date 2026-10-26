package io.github.nicolasgoyon.folkhold.world.client.map;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.TerritoryMapView;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Puts the discovered territories and villages on the minimap of Xaero's Minimap. The game loads this class only when
 * Xaero's Minimap is present.
 *
 * <ul>
 *   <li>Each minimap session has its highlighters, and the session closes the list after its own highlighters. Thus
 *       the hook adds the highlighter of the territories to the list of the session with reflection.</li>
 *   <li>The discovered villages are waypoints of the Overworld, from the origin {@link #ORIGIN}. Xaero's World Map
 *       also shows the waypoints of the minimap.</li>
 * </ul>
 */
final class MinimapHook {
    /** The origin of the waypoints of the villages. */
    static final ResourceLocation ORIGIN = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "villages");
    private static final MinimapTerritories HIGHLIGHTER = new MinimapTerritories();
    /** The session that has the waypoints of the current villages. */
    private static WeakReference<MinimapSession> waypointsSession = new WeakReference<>(null);

    private MinimapHook() {
    }

    /**
     * Adds the highlighter to the current session if it is not there, shows the territories again if they changed,
     * and updates the waypoints of the villages.
     *
     * @param changed  true if the territories or the options changed since the last update
     * @param view     the discovered territories
     * @param villages true to show the villages as waypoints
     * @throws ReflectiveOperationException if the list of highlighters is not accessible
     */
    static void update(boolean changed, TerritoryMapView view, boolean villages) throws ReflectiveOperationException {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getProcessor() == null) {
            return;
        }
        MinimapWriter writer = session.getProcessor().getMinimapWriter();
        if (writer != null) {
            boolean added = add(writer);
            DimensionHighlighterHandler handler = writer.getDimensionHighlightHandler();
            if ((changed || added) && handler != null) {
                handler.requestRefresh();
            }
        }
        if (changed || waypointsSession.get() != session) {
            updateWaypoints(session, view, villages);
            waypointsSession = new WeakReference<>(session);
        }
    }

    /** Adds the highlighter to the registry of a minimap writer. True if the registry did not have it. */
    private static boolean add(MinimapWriter writer) throws ReflectiveOperationException {
        Field registryField = MinimapWriter.class.getDeclaredField("highlighterRegistry");
        registryField.setAccessible(true);
        HighlighterRegistry registry = (HighlighterRegistry) registryField.get(writer);
        if (registry == null || registry.getHighlighters().contains(HIGHLIGHTER)) {
            return false;
        }
        Field field = HighlighterRegistry.class.getDeclaredField("highlighters");
        field.setAccessible(true);
        List<AbstractHighlighter> highlighters = new ArrayList<>(registry.getHighlighters());
        highlighters.add(HIGHLIGHTER);
        field.set(registry, Collections.unmodifiableList(highlighters));
        return true;
    }

    /** Replaces the waypoints of the villages in the Overworld. With no villages to show, it removes them. */
    private static void updateWaypoints(MinimapSession session, TerritoryMapView view, boolean villages) {
        MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
        if (root == null) {
            return;
        }
        String node = session.getDimensionHelper().getDimensionDirectoryName(Level.OVERWORLD);
        MinimapWorldContainer overworld = root.addSubContainer(root.getPath().resolve(node));
        ThirdPartyWaypoints waypoints = overworld.getThirdPartyWaypointManager().get(ORIGIN);
        waypoints.clear();
        if (!villages) {
            return;
        }
        for (TerritoryMapPayload.Village village : view.villages()) {
            String name = village.name();
            String initials = name.isEmpty() ? "V" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            waypoints.add(village.id(), new Waypoint(village.claimant().x(), village.y(), village.claimant().z(),
                    name, initials, nearest(village.color()), WaypointPurpose.NORMAL));
        }
    }

    /** The waypoint color nearest to a color, as 0xRRGGBB. */
    static WaypointColor nearest(int rgb) {
        return Arrays.stream(WaypointColor.values())
                .min(Comparator.comparingInt(color -> distance(color.getHex(), rgb)))
                .orElse(WaypointColor.WHITE);
    }

    private static int distance(int first, int second) {
        int red = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int green = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return red * red + green * green + blue * blue;
    }
}
