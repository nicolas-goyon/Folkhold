package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.ArrayList;
import java.util.List;

/**
 * The work zone of a construction site: the box of the target and a free band around it. The builders stand in this
 * band, and the plan puts its scaffolding there (see {@link Reach}). A ring of fences marks the edge of the zone, with
 * an opening on the side of the village.
 *
 * @param minX   the lowest X of the box of the target
 * @param minY   the lowest Y of the box of the target
 * @param minZ   the lowest Z of the box of the target
 * @param maxX   the highest X of the box of the target
 * @param maxY   the highest Y of the box of the target
 * @param maxZ   the highest Z of the box of the target
 * @param margin the width of the free band between the box and the ring, in blocks
 */
public record WorkZone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int margin) {
    /** The zone goes this number of layers below the box: the ground around a building can be lower than its base. */
    public static final int BELOW = 3;
    /** The zone goes this number of layers above the box. */
    public static final int ABOVE = 3;

    public WorkZone {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("The box is not valid: " + minX + " " + minY + " " + minZ + " to "
                    + maxX + " " + maxY + " " + maxZ);
        }
        if (margin < 0) {
            throw new IllegalArgumentException("margin must be 0 or more: " + margin);
        }
    }

    /**
     * A column of the ring.
     *
     * @param x block X
     * @param z block Z
     */
    public record Column(int x, int z) {
    }

    /** The lowest X of the ring. */
    public int ringMinX() {
        return minX - margin - 1;
    }

    /** The lowest Z of the ring. */
    public int ringMinZ() {
        return minZ - margin - 1;
    }

    /** The highest X of the ring. */
    public int ringMaxX() {
        return maxX + margin + 1;
    }

    /** The highest Z of the ring. */
    public int ringMaxZ() {
        return maxZ + margin + 1;
    }

    /**
     * True if a point is in the zone: on the ring or inside it, from {@link #BELOW} layers below the box to
     * {@link #ABOVE} layers above it.
     *
     * @param x the X of the point
     * @param y the Y of the point, for example the feet of an entity
     * @param z the Z of the point
     * @return true in the zone
     */
    public boolean contains(double x, double y, double z) {
        return x >= ringMinX() && x < ringMaxX() + 1 && z >= ringMinZ() && z < ringMaxZ() + 1
                && y >= minY - BELOW && y < maxY + ABOVE + 1;
    }

    /**
     * The columns of the ring, in their order around the zone. The north side comes first, from west to east. Then
     * come the east side, the south side and the west side.
     *
     * @return the columns
     */
    public List<Column> ring() {
        int x0 = ringMinX();
        int x1 = ringMaxX();
        int z0 = ringMinZ();
        int z1 = ringMaxZ();
        List<Column> ring = new ArrayList<>(2 * (x1 - x0 + z1 - z0));
        for (int x = x0; x <= x1; x++) {
            ring.add(new Column(x, z0));
        }
        for (int z = z0 + 1; z <= z1; z++) {
            ring.add(new Column(x1, z));
        }
        for (int x = x1 - 1; x >= x0; x--) {
            ring.add(new Column(x, z1));
        }
        for (int z = z1 - 1; z > z0; z--) {
            ring.add(new Column(x0, z));
        }
        return ring;
    }

    /**
     * The opening of the ring: the column of the ring nearest to a point, for example the heart of the village. A
     * corner of the ring is never the opening: an entity cannot go through a corner between two fences.
     *
     * @param towardX the X of the point
     * @param towardZ the Z of the point
     * @return the column of the opening
     */
    public Column opening(int towardX, int towardZ) {
        Column best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Column column : ring()) {
            if (isCorner(column)) {
                continue;
            }
            long dx = (long) column.x() - towardX;
            long dz = (long) column.z() - towardZ;
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = column;
                bestDistance = distance;
            }
        }
        if (best == null) {
            throw new IllegalStateException("A ring has columns that are not corners");
        }
        return best;
    }

    private boolean isCorner(Column column) {
        return (column.x() == ringMinX() || column.x() == ringMaxX())
                && (column.z() == ringMinZ() || column.z() == ringMaxZ());
    }

    /**
     * The columns of the ring that get a fence, in the order of the work. The order goes from the opening to the far
     * side of the ring, with one column on each side of the opening in turn. The opening gets no fence.
     *
     * @param towardX the X of the point of the opening (see {@link #opening})
     * @param towardZ the Z of the point of the opening
     * @return the columns, with no opening
     */
    public List<Column> fenceColumns(int towardX, int towardZ) {
        List<Column> ring = ring();
        int size = ring.size();
        int start = ring.indexOf(opening(towardX, towardZ));
        List<Column> order = new ArrayList<>(size - 1);
        for (int k = 1; order.size() < size - 1; k++) {
            order.add(ring.get((start + k) % size));
            if (order.size() < size - 1) {
                order.add(ring.get(Math.floorMod(start - k, size)));
            }
        }
        return order;
    }
}
