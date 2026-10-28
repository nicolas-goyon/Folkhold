package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;

/**
 * The part of the world that the map of the journal shows: the block at the center of the map and the scale.
 *
 * <ul>
 *   <li>The scale is from {@link #MIN_SCALE} (one pixel for a chunk) to {@link #MAX_SCALE} pixels for a block.</li>
 *   <li>A zoom keeps the block under the mouse at the same place on the screen.</li>
 * </ul>
 */
final class MapViewport {
    /** The smallest scale: one pixel for a chunk. */
    static final double MIN_SCALE = 1.0 / 16.0;
    /** The largest scale, in pixels for a block. */
    static final double MAX_SCALE = 2.0;
    /** The factor of one step of the zoom. */
    static final double ZOOM_STEP = Math.sqrt(2.0);

    private double centerX;
    private double centerZ;
    private double scale = 0.5;
    private final double minScale;

    /** A viewport with the smallest scale {@link #MIN_SCALE}. */
    MapViewport() {
        this(MIN_SCALE);
    }

    /**
     * A viewport with a smallest scale.
     *
     * @param minScale the smallest scale, in pixels for each block
     */
    MapViewport(double minScale) {
        this.minScale = minScale;
    }

    /** The block X at the center of the map. */
    double centerX() {
        return centerX;
    }

    /** The block Z at the center of the map. */
    double centerZ() {
        return centerZ;
    }

    /** The pixels for one block. */
    double scale() {
        return scale;
    }

    /** The projection of the blocks on an area of the screen. */
    MapProjection projection(UiRect area) {
        return new MapProjection(area, centerX, centerZ, scale);
    }

    /**
     * Moves the map with the mouse: the block under the mouse follows it.
     *
     * @param dx the movement of the mouse on the X axis, in pixels
     * @param dy the movement of the mouse on the Y axis, in pixels
     */
    void drag(double dx, double dy) {
        centerX -= dx / scale;
        centerZ -= dy / scale;
    }

    /**
     * Changes the scale. The block at a point of the screen stays at this point.
     *
     * @param factor  the factor of the scale: more than 1 shows more details
     * @param area    the area of the map
     * @param screenX the screen X of the point
     * @param screenY the screen Y of the point
     */
    void zoom(double factor, UiRect area, double screenX, double screenY) {
        double blockX = centerX + (screenX - area.centerX()) / scale;
        double blockZ = centerZ + (screenY - area.centerY()) / scale;
        scale = clamp(scale * factor);
        centerX = blockX - (screenX - area.centerX()) / scale;
        centerZ = blockZ - (screenY - area.centerY()) / scale;
    }

    /** Changes the scale, with no change of the block at the center. */
    void setScale(double value) {
        scale = clamp(value);
    }

    /** Puts a block at the center of the map. The scale does not change. */
    void center(double blockX, double blockZ) {
        centerX = blockX;
        centerZ = blockZ;
    }

    /**
     * Shows a box of blocks: the center of the box goes to the center of the map, and the scale is the largest scale
     * that shows all the box.
     *
     * @param minX the lowest block X of the box
     * @param minZ the lowest block Z of the box
     * @param maxX the highest block X of the box
     * @param maxZ the highest block Z of the box
     * @param area the area of the map
     */
    void fit(double minX, double minZ, double maxX, double maxZ, UiRect area) {
        centerX = (minX + maxX) / 2.0;
        centerZ = (minZ + maxZ) / 2.0;
        double spanX = Math.max(1.0, maxX - minX);
        double spanZ = Math.max(1.0, maxZ - minZ);
        scale = clamp(Math.min(area.width() / spanX, area.height() / spanZ));
    }

    private double clamp(double value) {
        return Math.max(minScale, Math.min(MAX_SCALE, value));
    }
}
