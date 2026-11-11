package io.github.nicolasgoyon.folkhold.core.sim.village;

import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle.Home;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.PlotRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The use of each plot of a village plan at the generation of the village (see {@link VillagePlan}): the buildings of
 * its start level, its job posts and its homes. The calculation reads no world.
 *
 * <ul>
 *   <li>The bank plot holds the bank.</li>
 *   <li>Each building of the level takes the first free plot of the plan that holds it: a wide plot of its wide home,
 *       or a home plot that is not wide for a building that fits the home of the style. The plan gives the plots
 *       nearest to the middle first.</li>
 *   <li>The job posts take the plot of the first food job, then the plots of the first trader jobs, then the free home
 *       plots that are not wide, in the order of the plan. The post 0 is the first food job.</li>
 *   <li>The other plots hold a home, except the spare plots and a wide plot of the market ({@link
 *       VillageBuildings#MARKET}) with no market: they stay free.</li>
 * </ul>
 *
 * <p>The same plan and the same inputs give the same uses.
 */
public final class PlotUses {
    /** The type of the use of a plot. */
    public enum Type {
        /** The bank. */
        BANK,
        /** A building of the level. */
        BUILDING,
        /** A house-workshop: a home with a job post. */
        POST,
        /** A home with no job post. */
        HOME,
        /** A free plot: a spare plot, or the wide plot of the market with no market. */
        FREE
    }

    /**
     * The use of a plot.
     *
     * @param type the type of the use
     * @param kind for a building: its kind, for example {@code storehouse}. Else an empty text.
     * @param post for a job post: the index of the post. Else -1.
     */
    public record Use(Type type, String kind, int post) {
        public Use {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * A building of the level.
     *
     * @param kind the kind of the building, for example {@code storehouse}
     * @param home the name of the home of the style that holds the piece of the building: the home of the style or a
     *             wide home (see {@link StreetStyle})
     */
    public record Building(String kind, String home) {
        public Building {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(home, "home");
        }
    }

    /**
     * The uses of the plots.
     *
     * @param uses    the use of each plot, in the order of the plots of the plan
     * @param missing the kinds of the buildings with no plot
     * @param posts   the number of job posts with a plot
     */
    public record Result(List<Use> uses, List<String> missing, int posts) {
        public Result {
            uses = List.copyOf(uses);
            missing = List.copyOf(missing);
        }
    }

    private PlotUses() {
    }

    /**
     * The style with enough wide plots for the buildings of the level: the count of each wide home is the count of the
     * style, or the number of buildings that need this wide home when it is larger.
     *
     * @param style     the style
     * @param buildings the buildings of the level
     * @return the style for the plan
     */
    public static StreetStyle withWide(StreetStyle style, List<Building> buildings) {
        List<Home> wide = style.wide().stream().map(home -> {
            int needed = (int) buildings.stream().filter(building -> building.home().equals(home.name())).count();
            return needed > home.count() ? new Home(home.name(), home.frontage(), home.depth(), needed) : home;
        }).toList();
        return new StreetStyle(style.name(), style.segment(), style.segmentPlots(), style.crossArm(),
                style.crossPlots(), style.side(), style.home(), wide, style.endOverflow());
    }

    /**
     * Gives a use to each plot of a plan.
     *
     * @param plan      the plan
     * @param buildings the buildings of the level, in their order
     * @param posts     the number of job posts of the level
     * @return the uses
     */
    public static Result assign(VillagePlan plan, List<Building> buildings, int posts) {
        List<Plot> plots = plan.plots();
        Use[] uses = new Use[plots.size()];
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < plots.size(); i++) {
            if (plots.get(i).spare()) {
                uses[i] = new Use(Type.FREE, "", -1);
            } else if (plots.get(i).role() == PlotRole.BANK) {
                uses[i] = new Use(Type.BANK, "", -1);
            }
        }
        for (Building building : buildings) {
            int plot = -1;
            for (int i = 0; i < plots.size() && plot < 0; i++) {
                if (uses[i] == null && plots.get(i).role() == PlotRole.HOME && holds(plots.get(i), building)) {
                    plot = i;
                }
            }
            if (plot < 0) {
                missing.add(building.kind());
            } else {
                uses[plot] = new Use(Type.BUILDING, building.kind(), -1);
            }
        }
        int placed = 0;
        for (PlotRole role : List.of(PlotRole.JOBS_FOOD, PlotRole.JOBS_START, PlotRole.HOME)) {
            for (int i = 0; i < plots.size() && placed < posts; i++) {
                if (uses[i] == null && plots.get(i).role() == role && !plots.get(i).wide()) {
                    uses[i] = new Use(Type.POST, "", placed++);
                }
            }
        }
        for (int i = 0; i < plots.size(); i++) {
            if (uses[i] == null) {
                boolean market = plots.get(i).wide()
                        && plots.get(i).homes().get(1).name().equals(VillageBuildings.MARKET);
                uses[i] = new Use(market ? Type.FREE : Type.HOME, "", -1);
            }
        }
        return new Result(Arrays.asList(uses), missing, placed);
    }

    /**
     * The fewest beds of the next home: with fewer beds, the home plots that stay cannot give the beds that the village
     * needs. The result is 0 (any home) when the pool has no home with beds, and {@code mostBeds} at most: when the
     * home plots cannot give the beds, the next home has the most beds.
     *
     * @param need       the home beds that the village needs and does not have
     * @param homesLeft  the home plots with no piece, the next plot included
     * @param mostBeds   the largest number of beds of a home of the pool
     * @return the fewest beds of the next home, from 0 to {@code mostBeds}
     */
    public static int leastBeds(int need, int homesLeft, int mostBeds) {
        if (need <= 0 || mostBeds <= 0) {
            return 0;
        }
        long rest = (long) Math.max(0, homesLeft - 1) * mostBeds;
        return (int) Math.max(0, Math.min(mostBeds, need - rest));
    }

    /** True if a plot holds a building: a wide plot of its wide home, or a plot that is not wide for the home. */
    private static boolean holds(Plot plot, Building building) {
        return plot.wide() ? plot.homes().get(1).name().equals(building.home())
                : plot.homes().get(0).name().equals(building.home());
    }
}
