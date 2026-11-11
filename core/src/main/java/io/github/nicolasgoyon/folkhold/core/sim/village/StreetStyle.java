package io.github.nicolasgoyon.folkhold.core.sim.village;

import java.util.List;

/**
 * The dimensions of the planned street pieces and of the homes of a style, for {@link VillagePlan}.
 *
 * <p>Each street piece is {@link #WIDTH} blocks wide (x = 0 to 4) and goes along z. The plots are on the 2 edges of a
 * piece: x = 0 faces west, x = 4 faces east. A home is centered on its plot along the street, and it is outside the
 * box of the piece. The structure script ({@code tools/structures/village.py}) writes the pieces with these values.
 *
 * @param name          the name of the style
 * @param segment       the length of the piece {@code street_next}
 * @param segmentPlots  the z of the plots of the piece {@code street_next}, on each side
 * @param crossArm      the length of an arm of the piece {@code street_cross}: the crossing is at z = arm to arm + 4
 * @param crossPlots    the z of the plots of the piece {@code street_cross}, on each side
 * @param side          the length of the piece {@code street_side} (no plot)
 * @param home          the home that fits each plot: the box around the homes and the house-workshops of the style
 * @param wide          the wide homes: each one needs a plot that the plan marks for it. The counts are start values:
 *                      the tuning of the village sizes (#788) sets them
 * @param endOverflow   the number of blocks that a wide home can go past the end of its piece when a piece with plots
 *                      comes after it
 */
public record StreetStyle(String name, int segment, List<Integer> segmentPlots, int crossArm, List<Integer> crossPlots,
        int side, Home home, List<Home> wide, int endOverflow) {
    /** The width of a street piece, in blocks. */
    public static final int WIDTH = 5;
    /** The length of the piece {@code street_end}: the end of a street, with no plot. */
    public static final int END = 5;

    /** The pieces of all the cultures. */
    public static final StreetStyle BASE = new StreetStyle("base", 13, List.of(8), 11, List.of(6, 20), 11,
            new Home("house", 9, 9, 0), List.of(new Home("market", 15, 9, 1)), Integer.MAX_VALUE);
    /** The pieces of the maritime villages: the dimensions of the pieces of all the cultures. */
    public static final StreetStyle MARITIME = new StreetStyle("maritime", 13, List.of(8), 11, List.of(6, 20), 11,
            new Home("house", 9, 9, 0), List.of(new Home("market", 15, 9, 1)), Integer.MAX_VALUE);
    /** The Sunreach pieces: the house-workshops are 9 x 13, the courtyard houses 13 x 13. */
    public static final StreetStyle SUNREACH = new StreetStyle("sunreach", 13, List.of(8), 15, List.of(8, 26), 15,
            new Home("house", 9, 13, 0),
            List.of(new Home("market", 15, 9, 1), new Home("courtyard_house", 13, 13, 3)), Integer.MAX_VALUE);
    /**
     * The Valeholm pieces: the long houses are 9 x 17 (the market of 9 x 15 fits), the side long houses 17 x 10. A side
     * long house on the last plot of a segment goes 4 blocks past the end of the segment: only a street end can come
     * after it.
     */
    public static final StreetStyle VALEHOLM = new StreetStyle("valeholm", 21, List.of(6, 16), 19, List.of(10, 32),
            19, new Home("long_house", 9, 17, 0), List.of(new Home("side_long_house", 17, 10, 2)), 0);

    /** The styles of the village structure. */
    public static final List<StreetStyle> ALL = List.of(BASE, MARITIME, SUNREACH, VALEHOLM);

    public StreetStyle {
        segmentPlots = List.copyOf(segmentPlots);
        crossPlots = List.copyOf(crossPlots);
        wide = List.copyOf(wide);
    }

    /** The length of the piece {@code street_cross}: 2 arms and the crossing. */
    public int cross() {
        return 2 * crossArm + WIDTH;
    }

    /**
     * A home of the style.
     *
     * @param name     the name of the home
     * @param frontage the width along the street, in blocks
     * @param depth    the depth from the street, in blocks
     * @param count    for a wide home: the number of plots that the plan marks for it
     */
    public record Home(String name, int frontage, int depth, int count) {
    }
}
