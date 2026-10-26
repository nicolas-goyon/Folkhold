package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import net.minecraft.client.gui.GuiGraphics;

/** Drawing for the maps of the journal: the relief and the lines. */
final class MapDrawing {
    /** The grey of the lowest floor of the relief (0 to 255). */
    private static final int RELIEF_LOW = 250;
    /** The grey of the highest floor of the relief (0 to 255). */
    private static final int RELIEF_HIGH = 175;
    /** The smallest height range of the relief, in blocks: a flat area stays light. */
    private static final int RELIEF_MIN_RANGE = 24;
    /** The color of the water on the map. */
    private static final int RELIEF_WATER = 0xFFD5E6F3;

    private MapDrawing() {
    }

    /**
     * Draws the relief of a map: a light grey for each cell, darker for a higher floor. A cell with water is light
     * blue. The method draws only the cells in the area of the map, and one rectangle for the cells of a row with
     * the same color.
     *
     * @param graphics the graphics
     * @param map      the projection of the map
     * @param relief   the relief
     */
    static void relief(GuiGraphics graphics, MapProjection map, RoutePayload.Relief relief) {
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
        UiRect area = map.area();
        int firstI = Math.max(0, (int) Math.floor((map.blockX(area.x()) - relief.minX() + half) / cell));
        int lastI = Math.min(relief.width() - 1,
                (int) Math.floor((map.blockX(area.right()) - relief.minX() + half) / cell));
        int firstJ = Math.max(0, (int) Math.floor((map.blockZ(area.y()) - relief.minZ() + half) / cell));
        int lastJ = Math.min(relief.height() - 1,
                (int) Math.floor((map.blockZ(area.bottom()) - relief.minZ() + half) / cell));
        for (int j = firstJ; j <= lastJ; j++) {
            int z = relief.minZ() + j * cell;
            int y0 = map.y(z - half);
            int y1 = Math.max(y0 + 1, map.y(z - half + cell));
            int runStart = 0;
            int runColor = 0;
            for (int i = firstI; i <= lastI; i++) {
                int x = relief.minX() + i * cell;
                int x0 = map.x(x - half);
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
                    graphics.fill(runStart, y0, Math.max(runStart + 1, map.x(x - half + cell)), y1, runColor);
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

    /** Draws a line of 2 pixels. */
    static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * (float) i / steps);
            int y = y0 + Math.round((y1 - y0) * (float) i / steps);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }
}
