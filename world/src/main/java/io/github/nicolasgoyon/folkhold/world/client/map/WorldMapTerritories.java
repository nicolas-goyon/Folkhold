package io.github.nicolasgoyon.folkhold.world.client.map;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/**
 * The discovered territories on the world map of Xaero's World Map (see {@link TerritoryHighlights}). The game loads
 * this class only when Xaero's World Map is present.
 */
final class WorldMapTerritories extends ChunkHighlighter {

    WorldMapTerritories() {
        // The territories show also on the parts of the map that the player did not explore.
        super(true);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return TerritoryHighlights.regionHash(dimension, regionX, regionZ);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return TerritoryHighlights.regionHasHighlights(dimension, regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return TerritoryHighlights.chunkIsHighlit(dimension, chunkX, chunkZ);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return TerritoryHighlights.colors(dimension, chunkX, chunkZ, resultStore);
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return TerritoryHighlights.tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return TerritoryHighlights.tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension, int blockX,
            int blockZ, int width) {
        // The minimap has its own highlighter (see MinimapTerritories).
    }
}
