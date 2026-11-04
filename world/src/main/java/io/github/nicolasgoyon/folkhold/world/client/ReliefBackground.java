package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.cartofold.api.client.LayerRenderContext;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The background of the map frames of the journal: the relief that the server calculates (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryReliefs} and
 * {@link io.github.nicolasgoyon.folkhold.world.journal.RouteReliefs}). The frame draws the explored chunks above it.
 */
final class ReliefBackground {
    /** The grey of the lowest floor of the relief (0 to 255). */
    private static final int RELIEF_LOW = 250;
    /** The grey of the highest floor of the relief (0 to 255). */
    private static final int RELIEF_HIGH = 175;
    /** The smallest height range of the relief, in blocks: a flat area stays light. */
    private static final int RELIEF_MIN_RANGE = 24;
    /** The color of the water on the map. */
    private static final int RELIEF_WATER = 0xFFD5E6F3;

    private ReliefBackground() {
    }

    /**
     * The background of a frame.
     *
     * @param relief gives the relief at each draw, or null when the client has no relief
     * @return the function for {@code MapFrame.background}
     */
    static Consumer<LayerRenderContext> of(Supplier<RoutePayload.Relief> relief) {
        return context -> {
            RoutePayload.Relief current = relief.get();
            if (current != null) {
                draw(context.graphics(), context.projection(), current);
            }
        };
    }

    /**
     * Draws the relief of a map: a light grey for each cell, darker for a higher floor. A cell with water is light
     * blue. The method draws only the cells in the area of the map, and one rectangle for the cells of a row with
     * the same color.
     *
     * @param graphics the graphics
     * @param map      the projection of the frame, with the north at the top
     * @param relief   the relief
     */
    static void draw(GuiGraphics graphics, MapProjection map, RoutePayload.Relief relief) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int j = 0; j < relief.height(); j++) {
            for (int i = 0; i < relief.width(); i++) {
                if (relief.water(i, j) == 0) {
                    low = Math.min(low, relief.floor(i, j));
                    high = Math.max(high, relief.floor(i, j));
                }
            }
        }
        double range = Math.max(RELIEF_MIN_RANGE, high - low);
        int cell = relief.cell();
        int half = cell / 2;
        double[] bounds = map.blockBounds();
        int firstI = Math.max(0, (int) Math.floor((bounds[0] - relief.minX() + half) / cell));
        int lastI = Math.min(relief.width() - 1, (int) Math.floor((bounds[2] - relief.minX() + half) / cell));
        int firstJ = Math.max(0, (int) Math.floor((bounds[1] - relief.minZ() + half) / cell));
        int lastJ = Math.min(relief.height() - 1, (int) Math.floor((bounds[3] - relief.minZ() + half) / cell));
        for (int j = firstJ; j <= lastJ; j++) {
            int z = relief.minZ() + j * cell;
            int y0 = y(map, z - half);
            int y1 = Math.max(y0 + 1, y(map, z - half + cell));
            int runStart = 0;
            int runColor = 0;
            for (int i = firstI; i <= lastI; i++) {
                int x = relief.minX() + i * cell;
                int x0 = x(map, x - half);
                int color = color(relief, i, j, low, range);
                if (i == firstI) {
                    runStart = x0;
                    runColor = color;
                } else if (color != runColor) {
                    graphics.fill(runStart, y0, Math.max(runStart + 1, x0), y1, runColor);
                    runStart = x0;
                    runColor = color;
                }
                if (i == lastI) {
                    graphics.fill(runStart, y0, Math.max(runStart + 1, x(map, x - half + cell)), y1, runColor);
                }
            }
        }
    }

    /** The color of a cell of the relief. */
    private static int color(RoutePayload.Relief relief, int i, int j, int low, double range) {
        if (relief.water(i, j) > 0) {
            return RELIEF_WATER;
        }
        double t = Math.max(0.0, Math.min(1.0, (relief.floor(i, j) - low) / range));
        int grey = (int) Math.round(RELIEF_LOW - t * (RELIEF_LOW - RELIEF_HIGH));
        return 0xFF000000 | grey << 16 | grey << 8 | grey;
    }

    /** The screen X of a block X. */
    private static int x(MapProjection map, double blockX) {
        return (int) Math.round(map.screenCenterX() + (blockX - map.centerX()) * map.scale());
    }

    /** The screen Y of a block Z. */
    private static int y(MapProjection map, double blockZ) {
        return (int) Math.round(map.screenCenterY() + (blockZ - map.centerZ()) * map.scale());
    }
}
