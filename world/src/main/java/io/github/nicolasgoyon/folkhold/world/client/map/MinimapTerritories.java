package io.github.nicolasgoyon.folkhold.world.client.map;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * The discovered territories on the minimap of Xaero's Minimap (see {@link TerritoryHighlights}). The game loads this
 * class only when Xaero's Minimap is present.
 */
final class MinimapTerritories extends ChunkHighlighter {

    MinimapTerritories() {
        // The territories show also on the parts of the map that the player did not explore.
        super(true);
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
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX,
            int chunkZ, int width) {
        Component tooltip = TerritoryHighlights.tooltip(dimension, chunkX, chunkZ);
        if (tooltip != null) {
            compiler.addLine(tooltip);
        }
    }
}
