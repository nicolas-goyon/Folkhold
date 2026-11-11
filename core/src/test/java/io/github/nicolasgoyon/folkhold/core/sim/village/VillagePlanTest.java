package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle.Home;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Kind;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Piece;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Start;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize.Wall;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class VillagePlanTest {
    private static final int PLAZA = 15;
    private static final List<Start> STARTS = Start.around(PLAZA);
    private static boolean good(Box box) {
        return true;
    }

    /** About 1 plot in 6 has bad terrain. */
    private static boolean someBad(Box box) {
        return Math.floorMod(box.minX() * 31 + box.minZ() * 17, 6) != 0;
    }

    private static VillagePlan plan(VillageSize size, StreetStyle style, Predicate<Box> terrain, long seed) {
        return VillagePlan.plan(size, size.plots(0, 0), style, PLAZA, STARTS, terrain, seed);
    }

    /** Each tier and style: the plot target, the spare share, no overlap, inside the radius, the 4 axis streets. */
    @Test
    void eachTierAndStyleGetsItsPlots() {
        List<Predicate<Box>> terrains = List.of(VillagePlanTest::good, VillagePlanTest::someBad);
        for (VillageSize size : VillageSizeTable.DEFAULT.sizes()) {
            for (StreetStyle style : StreetStyle.ALL) {
                for (long seed = 0; seed < 4; seed++) {
                    for (Predicate<Box> terrain : terrains) {
                        VillagePlan plan = plan(size, style, terrain, seed);
                        String name = size.tier() + " " + style.name() + " seed " + seed;
                        checkLayout(plan, style, name);
                        assertTrue(plan.radius() <= size.maxRadius(), name);
                        assertEquals(size.plots(0, 0), plan.plots().size() + plan.missingPlots(), name);
                        assertTrue(plan.plots().stream().allMatch(plot -> plot.good() || plot.spare()), name);
                        assertEquals(0, plan.missingPlots(), name);
                        assertEquals(0, plan.missingHomes(), name);
                        long spare = plan.plots().stream().filter(Plot::spare).count();
                        double share = (double) spare / (plan.plots().size() - spare);
                        assertTrue(share >= VillageSize.MIN_SPARE && share <= VillageSize.MAX_SPARE, name);
                        assertEquals(size.sparePlots(size.plots(0, 0)), spare, name);
                    }
                }
            }
        }
    }

    private static void checkLayout(VillagePlan plan, StreetStyle style, String name) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(-7, -7, 7, 7));
        plan.pieces().forEach(piece -> boxes.add(piece.box()));
        plan.plots().forEach(plot -> boxes.add(plot.area()));
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).overlaps(boxes.get(j)), name + ": " + boxes.get(i) + " " + boxes.get(j));
            }
        }
        // The area of a plot holds the largest home of the plot, centered on the plot.
        for (Plot plot : plan.plots()) {
            for (Home home : plot.homes()) {
                int along = plot.facing().dx() == 0 ? plot.area().maxX() - plot.area().minX()
                        : plot.area().maxZ() - plot.area().minZ();
                int depth = plot.facing().dx() == 0 ? plot.area().maxZ() - plot.area().minZ()
                        : plot.area().maxX() - plot.area().minX();
                assertTrue(along + 1 >= home.frontage() && depth + 1 >= home.depth(), name);
            }
            assertTrue(plot.area().contains(plot.x() + plot.facing().dx(), plot.z() + plot.facing().dz()), name);
        }
        for (Start start : STARTS) {
            assertTrue(plan.pieces().stream().anyMatch(p -> p.parent() == -1 && p.x() == start.x()
                    && p.z() == start.z() && p.dir() == start.dir()), name + " axis " + start.dir());
        }
        // The rule of the end of a piece: a wide home goes past the end only when a street end is after it.
        for (Plot plot : plan.plots().stream().filter(Plot::wide).toList()) {
            Piece piece = plan.pieces().get(plot.piece());
            if (style.endOverflow() == 0 && overflows(plot, piece)) {
                assertTrue(plan.pieces().stream().noneMatch(p -> p.parent() == plot.piece() && p.dir() == piece.dir()
                        && p.kind() != Kind.END), name);
            }
        }
    }

    /** True when the area of the plot goes past the end of its piece. */
    private static boolean overflows(Plot plot, Piece piece) {
        int endX = piece.x() + piece.dir().dx() * (piece.length() - 1);
        int endZ = piece.z() + piece.dir().dz() * (piece.length() - 1);
        Box area = plot.area();
        return switch (piece.dir()) {
            case NORTH -> area.minZ() < endZ;
            case SOUTH -> area.maxZ() > endZ;
            case WEST -> area.minX() < endX;
            case EAST -> area.maxX() > endX;
        };
    }

    /** Each tier and style marks plots for its wide homes. */
    @Test
    void wideHomesGetTheirPlots() {
        for (VillageSize size : VillageSizeTable.DEFAULT.sizes()) {
            for (StreetStyle style : StreetStyle.ALL) {
                VillagePlan plan = plan(size, style, VillagePlanTest::good, 1);
                assertTrue(plan.missingWide().isEmpty(), size.tier() + " " + style.name() + " " + plan.missingWide());
                int wide = style.wide().stream().mapToInt(Home::count).sum();
                assertEquals(wide, plan.plots().stream().filter(Plot::wide).count());
                assertTrue(plan.plots().stream().filter(Plot::wide).noneMatch(Plot::spare));
            }
        }
    }

    /** The same seed gives the same plan. */
    @Test
    void sameSeedGivesTheSamePlan() {
        VillageSize size = VillageSizeTable.DEFAULT.size("capital").orElseThrow();
        for (StreetStyle style : StreetStyle.ALL) {
            assertEquals(plan(size, style, VillagePlanTest::someBad, 42),
                    plan(size, style, VillagePlanTest::someBad, 42));
        }
        assertNotEquals(plan(size, StreetStyle.BASE, VillagePlanTest::good, 1).pieces(),
                plan(size, StreetStyle.BASE, VillagePlanTest::good, 2).pieces());
    }

    /** The bad plots are spare plots first, then the farthest plots. */
    @Test
    void sparePlotsAreTheBadPlotsThenTheFarthestPlots() {
        VillageSize size = VillageSizeTable.DEFAULT.size("village").orElseThrow();
        VillagePlan plan = plan(size, StreetStyle.BASE, VillagePlanTest::good, 3);
        double home = plan.plots().stream().filter(p -> !p.spare()).mapToDouble(VillagePlanTest::distance).max()
                .orElseThrow();
        assertTrue(plan.plots().stream().filter(Plot::spare).allMatch(p -> distance(p) >= home));
        // A bad plot near the plaza: it is a spare plot.
        Predicate<Box> badNorth = box -> !box.overlaps(new Box(-30, -30, 30, -8));
        VillagePlan bad = plan(size, StreetStyle.BASE, badNorth, 3);
        assertTrue(bad.plots().stream().filter(p -> !p.good()).allMatch(Plot::spare));
        assertTrue(bad.plots().stream().anyMatch(p -> !p.good()));
    }

    private static double distance(Plot plot) {
        return Math.hypot(plot.x(), plot.z());
    }

    /** A radius that is too small gives a plan with a shortfall, not an error. */
    @Test
    void smallRadiusGivesAShortfall() {
        VillageSize small = new VillageSize("capital", 66, 0, 0.15, 40, 0, Wall.PALISADE, 3);
        for (StreetStyle style : StreetStyle.ALL) {
            VillagePlan plan = plan(small, style, VillagePlanTest::good, 0);
            assertTrue(plan.missingPlots() > 0, style.name());
            assertEquals(66, plan.plots().size() + plan.missingPlots());
            assertTrue(plan.radius() <= 40);
        }
    }

    /** Writes a map of one plan for each tier and style to build/village-plans.txt, for a review. */
    @Test
    void writeMaps() throws IOException {
        StringBuilder text = new StringBuilder();
        for (VillageSize size : VillageSizeTable.DEFAULT.sizes()) {
            for (StreetStyle style : StreetStyle.ALL) {
                VillagePlan plan = plan(size, style, VillagePlanTest::someBad, 1);
                long spare = plan.plots().stream().filter(Plot::spare).count();
                text.append(String.format(Locale.ROOT, "%s %s: %d plots of %d, %d spare, %d wide, radius %.1f of %d"
                        + ", %d pieces%n", size.tier(), style.name(), plan.plots().size(), plan.target(), spare,
                        plan.plots().stream().filter(Plot::wide).count(), plan.radius(), size.maxRadius(),
                        plan.pieces().size()));
                int scale = size.maxRadius() <= 64 ? 2 : size.maxRadius() <= 128 ? 4 : 5;
                text.append(VillagePlanMap.map(plan, PLAZA, size.maxRadius(), scale)).append('\n');
            }
        }
        Path out = Path.of("build", "village-plans.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, text.toString());
    }
}
