package io.github.nicolasgoyon.folkhold.events.storm;

/**
 * The formulas of the storms. They use no game class, so the JUnit tests check them.
 *
 * <p>The angle of the wind is in degrees: 0 blows to the east (+X), 90 blows to the south (+Z), 180 to the west and
 * 270 to the north.
 */
public final class StormMath {
    /** The names of the 8 directions of the compass, from the east, in the order of the angles. */
    static final String[] COMPASS = {"east", "southeast", "south", "southwest", "west", "northwest", "north",
        "northeast"};

    private StormMath() {
    }

    /** The X part of the unit vector of the wind. */
    public static double windX(double angleDegrees) {
        return Math.cos(Math.toRadians(angleDegrees));
    }

    /** The Z part of the unit vector of the wind. */
    public static double windZ(double angleDegrees) {
        return Math.sin(Math.toRadians(angleDegrees));
    }

    /**
     * The position of a column along the wind: 0 on the upwind edge of the area, 1 on the downwind edge. The front of
     * the storm crosses the area from 0 to 1.
     *
     * @param dx     the X offset of the column from the center
     * @param dz     the Z offset of the column from the center
     * @param windX  the X part of the unit vector of the wind
     * @param windZ  the Z part of the unit vector of the wind
     * @param radius the radius of the area
     * @return the position, from 0 to 1
     */
    public static double front(double dx, double dz, double windX, double windZ, int radius) {
        if (radius <= 0) {
            return 0.0;
        }
        return Math.clamp(((dx * windX + dz * windZ) / radius + 1.0) / 2.0, 0.0, 1.0);
    }

    /**
     * True if the wind hits a face of a block: the face looks into the wind.
     *
     * @param stepX    the X step of the face, from -1 to 1
     * @param stepZ    the Z step of the face, from -1 to 1
     * @param windX    the X part of the unit vector of the wind
     * @param windZ    the Z part of the unit vector of the wind
     * @param minFacing the smallest part of the wind against the face, from 0 to 1
     * @return true for a face on the upwind side
     */
    public static boolean facesWind(int stepX, int stepZ, double windX, double windZ, double minFacing) {
        return stepX * windX + stepZ * windZ < -minFacing;
    }

    /**
     * The name of the direction of the compass to which the wind blows.
     *
     * @param angleDegrees the angle of the wind
     * @return one of {@code east}, {@code southeast}, {@code south}, {@code southwest}, {@code west},
     *         {@code northwest}, {@code north} and {@code northeast}
     */
    public static String compass(double angleDegrees) {
        return COMPASS[Math.floorMod((int) Math.round(angleDegrees / 45.0), COMPASS.length)];
    }

    /**
     * The progress of the front of the storm.
     *
     * @param elapsed the ticks from the start of the front
     * @param ticks   the time of the crossing of the area, in ticks
     * @return 0 at the start, 1 at the end of the crossing and after it
     */
    public static double progress(long elapsed, long ticks) {
        if (ticks <= 0L) {
            return 1.0;
        }
        return Math.clamp(elapsed / (double) ticks, 0.0, 1.0);
    }
}
