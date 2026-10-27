package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;

/**
 * The projection of the block coordinates on a map of the journal. North is up.
 *
 * @param area    the area of the map on the screen
 * @param centerX the block X at the center of the area
 * @param centerZ the block Z at the center of the area
 * @param scale   the pixels for one block
 */
record MapProjection(UiRect area, double centerX, double centerZ, double scale) {

    /** The screen X of a block X. */
    int x(double blockX) {
        return (int) Math.round(area.centerX() + (blockX - centerX) * scale);
    }

    /** The screen Y of a block Z. */
    int y(double blockZ) {
        return (int) Math.round(area.centerY() + (blockZ - centerZ) * scale);
    }

    /** The block X at a screen X. */
    double blockX(double screenX) {
        return centerX + (screenX - area.centerX()) / scale;
    }

    /** The block Z at a screen Y. */
    double blockZ(double screenY) {
        return centerZ + (screenY - area.centerY()) / scale;
    }
}
