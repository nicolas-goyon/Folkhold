package io.github.nicolasgoyon.folkhold.core.sim.village;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Locale;

/**
 * The size of the village structure of a tier (see {@link VillageSizeTable}).
 *
 * <p>The number of plots comes from one of two rules:
 * <ul>
 *   <li>A plot target: {@code plots} is the number of plots, with the spare plots.</li>
 *   <li>The beds rule: {@code plots} is 0 and {@code bedsPerPlot} is more than 0. The homes hold the maximum
 *       population of the top level of the tier, minus the places of the hall. Each home is one plot with
 *       {@code bedsPerPlot} beds. Then the spare plots come in addition (see {@link #plots(int, int)}).</li>
 * </ul>
 *
 * @param tier               the id of the tier
 * @param plots              the plot target with the spare plots, or 0 for the beds rule
 * @param bedsPerPlot        the beds of a home for the beds rule, or 0 for a plot target
 * @param spareShare         the share of spare plots: the plots that stay empty, from {@link #MIN_SPARE} to
 *                           {@link #MAX_SPARE} of the plots that the village needs
 * @param maxRadius          the maximum distance in blocks from the center of the village to the edge of a plot
 * @param rings              the number of rings outside the first wall
 * @param wall               the type of the first wall
 * @param minTerritoryRadius the minimum radius of the territory, in chunks
 */
public record VillageSize(String tier, int plots, int bedsPerPlot, double spareShare, int maxRadius, int rings,
        Wall wall, int minTerritoryRadius) {
    /** The smallest share of spare plots. */
    public static final double MIN_SPARE = 0.10;
    /** The largest share of spare plots. */
    public static final double MAX_SPARE = 0.20;

    /** The type of the first wall of a village. */
    public enum Wall implements SerializedName {
        /** No wall. */
        NONE,
        /** A palisade with towers and gates. */
        PALISADE;

        @Override
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Makes a village size.
     *
     * @throws IllegalArgumentException if a value is not valid. The message gives the tier and the value.
     */
    public VillageSize {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("A village size needs the id of its tier");
        }
        if (wall == null) {
            throw new IllegalArgumentException(error(tier, "needs a wall type"));
        }
        if (plots < 0 || bedsPerPlot < 0 || (plots > 0) == (bedsPerPlot > 0)) {
            throw new IllegalArgumentException(error(tier, "needs a plot target (plots) or the beds rule "
                    + "(beds_per_plot), not the two: plots " + plots + ", beds_per_plot " + bedsPerPlot));
        }
        if (!(spareShare >= MIN_SPARE && spareShare <= MAX_SPARE)) {
            throw new IllegalArgumentException(error(tier, "needs a spare share from " + MIN_SPARE + " to "
                    + MAX_SPARE + ", not " + spareShare));
        }
        if (maxRadius < 1 || rings < 0 || minTerritoryRadius < 1) {
            throw new IllegalArgumentException(error(tier, "needs a max radius and a min territory radius of 1 "
                    + "or more and 0 rings or more: max_radius " + maxRadius + ", rings " + rings
                    + ", min_territory_radius " + minTerritoryRadius));
        }
        if (maxRadius > minTerritoryRadius * 16) {
            throw new IllegalArgumentException(error(tier, "has a max radius of " + maxRadius
                    + " blocks outside its min territory radius of " + minTerritoryRadius + " chunks"));
        }
    }

    private static String error(String tier, String text) {
        return "The village size of the tier " + tier + " " + text;
    }

    /**
     * The number of plots of a village of the tier, with the spare plots.
     *
     * @param maxPopulation the maximum population of the top level of the tier
     * @param hallPlaces    the places of the hall (the builders and the handymen)
     * @return the plot target, or the plots of the beds rule
     */
    public int plots(int maxPopulation, int hallPlaces) {
        if (plots > 0) {
            return plots;
        }
        int homes = Math.max(1, Math.ceilDiv(Math.max(0, maxPopulation - hallPlaces), bedsPerPlot));
        return (int) Math.ceil(homes * (1.0 + spareShare));
    }

    /**
     * The number of spare plots among a number of plots.
     *
     * @param plots the number of plots, with the spare plots
     * @return the number of spare plots
     */
    public int sparePlots(int plots) {
        return (int) Math.round(plots * spareShare / (1.0 + spareShare));
    }
}
