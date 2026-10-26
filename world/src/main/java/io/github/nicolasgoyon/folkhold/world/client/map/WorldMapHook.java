package io.github.nicolasgoyon.folkhold.world.client.map;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Puts the discovered territories on the world map of Xaero's World Map. The game loads this class only when Xaero's
 * World Map is present.
 *
 * <p>Each world map session has its highlighters, and the session closes the list after its own highlighters. Thus
 * the hook adds the highlighter of the territories to the list of the session with reflection.
 */
final class WorldMapHook {
    private static final WorldMapTerritories HIGHLIGHTER = new WorldMapTerritories();

    private WorldMapHook() {
    }

    /**
     * Adds the highlighter to the current session if it is not there, and shows the territories again if they
     * changed.
     *
     * @param changed true if the territories or the options changed since the last update
     * @throws ReflectiveOperationException if the list of highlighters is not accessible
     */
    static void update(boolean changed) throws ReflectiveOperationException {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session == null ? null : session.getMapProcessor();
        if (processor == null) {
            return;
        }
        boolean added = add(processor.getHighlighterRegistry());
        if ((changed || added) && processor.getMapWorld() != null) {
            processor.getMapWorld().clearAllCachedHighlightHashes();
        }
    }

    /** Adds the highlighter to a registry. True if the registry did not have it. */
    private static boolean add(HighlighterRegistry registry) throws ReflectiveOperationException {
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
}
