package io.github.nicolasgoyon.folkhold.core.sim.village;

import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle.Home;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The plan of the streets and of the plots of a village of a tier. The calculation uses the horizontal plane only
 * (block X and Z), with the middle of the plaza at (0, 0). It reads no world: the terrain comes from a predicate.
 *
 * <ul>
 *   <li>A street goes out of each side of the plaza: the 4 axis streets. The gates and the arms of the wall use them.
 *       The first piece of each axis street is a segment (see {@link Piece}), and its plots for the bank and the first
 *       jobs have a role ({@link PlotRole}).</li>
 *   <li>The plan adds the pieces one by one at the open end of a street that is nearest to the middle: a segment
 *       ({@code street_next}) or a crossroad ({@code street_cross}, weight 1 against 2, from the seed). A side exit of
 *       a crossroad starts with the piece {@code street_side}. A piece goes only where its box, the box of a street end
 *       after each of its exits and its plots are inside the maximum radius and touch no other piece and no other plot.
 *       A plot that does not obey this is not a plot. Each open end gets a street end ({@code street_end}).</li>
 *   <li>The plan stops when it has the plot target, the homes on plots with good terrain and the wide plots. For the
 *       wide plots, it adds 4 pieces at most after it has the plots.</li>
 *   <li>The wide homes of the style take the good plots nearest to the middle (not a plot with a role) where they
 *       touch no piece and no plot of another piece. A wide home can take the place of a home plot of its own piece
 *       (for example the 2 plots on a side of a Valeholm segment) when the plan keeps its target. A wide home goes
 *       past the end of its piece by {@link StreetStyle#endOverflow()} blocks at most, except before a street end.
 *       </li>
 *   <li>The homes take the plots with a role, the wide plots and the good plots nearest to the middle. The spare
 *       plots are the bad plots first, then the farthest plots of the plan. The other plots are not in the plan.</li>
 *   <li>When the radius is too small, the plan has fewer plots or homes: {@link #missingPlots()} and
 *       {@link #missingHomes()} give the shortfall.</li>
 * </ul>
 *
 * <p>The plan tries 8 layouts from the seed and keeps the best: the smallest shortfall, then the fewest missing wide
 * plots, then the smallest radius. The same inputs and the same seed give the same plan.
 *
 * @param pieces       the street pieces
 * @param plots        the plots: the homes, then the spare plots
 * @param target       the plot target, with the spare plots
 * @param missingPlots the plots under the target
 * @param missingHomes the homes under the plots that the village needs
 * @param missingWide  for each wide home of the style, the marks under its count
 * @param radius       the largest distance from the middle to a corner of a piece or of a plot
 */
public record VillagePlan(List<Piece> pieces, List<Plot> plots, int target, int missingPlots, int missingHomes,
        Map<String, Integer> missingWide, double radius) {

    /** A direction on the horizontal plane. */
    public enum Dir {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int dx;
        private final int dz;

        Dir(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        /** The direction 90 degrees counterclockwise (seen from above): the +x side of a piece that goes this way. */
        public Dir counterclockwise() {
            return switch (this) {
                case NORTH -> WEST;
                case EAST -> NORTH;
                case SOUTH -> EAST;
                case WEST -> SOUTH;
            };
        }

        /** The opposite direction. */
        public Dir opposite() {
            return counterclockwise().counterclockwise();
        }

        /** The clockwise quarter turns from a piece that goes south (the template) to a piece that goes this way. */
        public int clockwiseTurns() {
            return switch (this) {
                case SOUTH -> 0;
                case WEST -> 1;
                case NORTH -> 2;
                case EAST -> 3;
            };
        }
    }

    /** The kind of a street piece. */
    public enum Kind {
        /** {@code street_next}: a segment with plots. */
        SEGMENT,
        /** {@code street_cross}: a crossroad with plots and 2 side exits. */
        CROSS,
        /** {@code street_side}: the start of a side street, with no plot. */
        SIDE,
        /** {@code street_end}: the end of a street. */
        END
    }

    /** A box of blocks on the horizontal plane. The minimum and maximum values are in the box. */
    public record Box(int minX, int minZ, int maxX, int maxZ) {
        static Box of(int x1, int z1, int x2, int z2) {
            return new Box(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
        }

        public boolean overlaps(Box other) {
            return minX <= other.maxX && other.minX <= maxX && minZ <= other.maxZ && other.minZ <= maxZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** The distance from (0, 0) to the farthest corner of the box. */
        public double reach() {
            long x = Math.max(Math.abs(minX), Math.abs(maxX));
            long z = Math.max(Math.abs(minZ), Math.abs(maxZ));
            return Math.sqrt((double) (x * x + z * z));
        }

        Box union(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxZ, other.maxZ));
        }
    }

    /**
     * The start of a street: the block in the middle of the start edge of its first piece.
     *
     * @param x   the block X
     * @param z   the block Z
     * @param dir the direction of the street
     */
    public record Start(int x, int z, Dir dir) {
        /** The 4 axis streets of a square plaza with its middle at (0, 0): north, east, south and west. */
        public static List<Start> around(int plazaSize) {
            int out = plazaSize / 2 + 1;
            return List.of(new Start(0, -out, Dir.NORTH), new Start(out, 0, Dir.EAST), new Start(0, out, Dir.SOUTH),
                    new Start(-out, 0, Dir.WEST));
        }
    }

    /**
     * A street piece. The template goes south (+z) from its start connector at (2, 0, 0).
     *
     * <p>The first piece of each axis street is a segment ({@code parent} -1). It takes the place of the first street
     * piece of the current village structure, with the same dimensions: {@code street_bank} for the street to the
     * north, {@code street_jobs} for the street to the south and {@code street} for the streets to the east and to the
     * west (the Norse pieces in the Valeholm style). Its plots have the roles of these pieces (see {@link PlotRole}).
     *
     * @param kind   the kind of the piece
     * @param x      the block X of the start: the middle of the start edge
     * @param z      the block Z of the start
     * @param dir    the direction of the street (see {@link Dir#clockwiseTurns()})
     * @param length the length of the piece along the street
     * @param box    the box of the piece
     * @param parent the index of the piece before this piece, or -1 for the first piece of an axis street
     * @param exit   the exit of the piece before this piece: 0 for the next piece, 1 for the side exit at x = 0 and 2
     *               for the side exit at x = 4 of a crossroad, or -1 for the first piece of an axis street
     */
    public record Piece(Kind kind, int x, int z, Dir dir, int length, Box box, int parent, int exit) {
    }

    /**
     * The role of a plot. The plots of the first pieces of the street to the north and of the street to the south have
     * the roles of the current village structure. They are never wide plots and never spare plots.
     */
    public enum PlotRole {
        /** A home: the pool {@code houses}. */
        HOME,
        /** The bank: the plot at x = 0 of the first plot row of the street to the north (pool {@code bank}). */
        BANK,
        /** The first food job: the plot at x = 0 of the first plot row of the street to the south. */
        JOBS_FOOD,
        /**
         * The first trader job: the plot at x = 4 of the first plot row of the street to the south, and in the
         * Valeholm style also the plot at x = 0 of its second plot row.
         */
        JOBS_START
    }

    /**
     * A plot on the edge of a street piece.
     *
     * @param x      the block X of the plot connector, on the edge of the piece
     * @param z      the block Z of the plot connector
     * @param facing the direction from the street to the home
     * @param area   the box around the homes that fit the plot
     * @param homes  the homes that fit the plot: the home of the style, and the wide home of a marked plot
     * @param role   the role of the plot: the pool of its building
     * @param good   true when the terrain of the plot is good
     * @param spare  true for a spare plot: it stays empty
     * @param piece  the index of the piece of the plot
     */
    public record Plot(int x, int z, Dir facing, Box area, List<Home> homes, PlotRole role, boolean good,
            boolean spare, int piece) {
        /** True when a wide home fits the plot. */
        public boolean wide() {
            return homes.size() > 1;
        }
    }

    public VillagePlan {
        pieces = List.copyOf(pieces);
        plots = List.copyOf(plots);
        missingWide = Map.copyOf(missingWide);
    }

    /**
     * Makes the plan of a village.
     *
     * @param size      the size of the tier: the spare share and the maximum radius
     * @param target    the plot target with the spare plots (see {@link VillageSize#plots(int, int)})
     * @param style     the street pieces and the homes of the style
     * @param plazaSize the size of the square plaza
     * @param starts    the starts of the axis streets
     * @param terrain   true when the terrain of a box is good for a home
     * @param seed      the seed of the choices
     * @return the plan, with its shortfall when the radius is too small
     */
    public static VillagePlan plan(VillageSize size, int target, StreetStyle style, int plazaSize, List<Start> starts,
            Predicate<Box> terrain, long seed) {
        VillagePlan best = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Random random = new Random(seed ^ (attempt * 0x9E3779B97F4A7C15L));
            VillagePlan plan = new Planner(size, target, style, plazaSize, terrain, random).plan(starts);
            if (best == null || BETTER.compare(plan, best) < 0) {
                best = plan;
            }
        }
        return best;
    }

    /** The number of layouts that the plan tries. It keeps the best one. */
    private static final int ATTEMPTS = 8;
    /** The pieces that the plan adds after it has its plots, to get the plots of the wide homes. */
    private static final int WIDE_PIECES = 4;
    /** The best plan: the fewest missing plots and homes, then the fewest missing wide plots, then the smallest. */
    private static final Comparator<VillagePlan> BETTER = Comparator
            .comparingInt((VillagePlan plan) -> plan.missingPlots() + plan.missingHomes())
            .thenComparingInt(plan -> plan.missingWide().values().stream().mapToInt(Integer::intValue).sum())
            .thenComparingDouble(VillagePlan::radius);

    /** A plot before the choice of the homes. */
    private record Slot(int x, int z, Dir facing, int piece, int along, boolean east, Box area, PlotRole role,
            boolean good) {
        /** True for a plot of a building of the first pieces: it is never wide and never spare. */
        boolean reserved() {
            return role != PlotRole.HOME;
        }

        long distance() {
            return (long) x * x + (long) z * z;
        }
    }

    /** An open end of a street: the place of the next piece, with the box of its street end. */
    private record Tail(Start start, boolean side, int parent, int exit, Box end) {
    }

    private static final class Planner {
        private final int target;
        private final int needed;
        private final StreetStyle style;
        private final Predicate<Box> terrain;
        private final Random random;
        private final long maxRadius;
        private final Box plaza;
        private final List<Piece> pieces = new ArrayList<>();
        private final List<Slot> slots = new ArrayList<>();
        private final List<Tail> tails = new ArrayList<>();
        /** The ends where no piece fits: they keep their street end. */
        private final List<Tail> closed = new ArrayList<>();

        Planner(VillageSize size, int target, StreetStyle style, int plazaSize, Predicate<Box> terrain, Random random) {
            this.target = target;
            this.needed = target - size.sparePlots(target);
            this.style = style;
            this.terrain = terrain;
            this.random = random;
            this.maxRadius = size.maxRadius();
            int half = plazaSize / 2;
            this.plaza = new Box(-half, -half, plazaSize - 1 - half, plazaSize - 1 - half);
        }

        VillagePlan plan(List<Start> starts) {
            // The first pieces of the streets to the north and to the south come first: their plots have roles.
            List<Start> ordered = new ArrayList<>(starts);
            ordered.sort(Comparator.comparing(start -> start.dir() != Dir.NORTH && start.dir() != Dir.SOUTH));
            for (Start start : ordered) {
                Tail tail = new Tail(start, false, -1, -1, box(start, StreetStyle.END));
                if (fits(tail, Kind.SEGMENT)) {
                    add(tail, Kind.SEGMENT);
                } else {
                    closed.add(tail);
                }
            }
            int widePieces = 0;
            while (!tails.isEmpty()) {
                if (enough()) {
                    Choice choice = choose();
                    if (choice.missingWide().isEmpty() || widePieces++ >= WIDE_PIECES) {
                        break;
                    }
                }
                Tail tail = tails.stream().min(Comparator.comparingLong(t -> distance(t.start()))).orElseThrow();
                tails.remove(tail);
                List<Kind> kinds =
                        new ArrayList<>(tail.side() ? List.of(Kind.SIDE) : List.of(Kind.SEGMENT, Kind.CROSS));
                kinds.removeIf(kind -> !fits(tail, kind));
                if (kinds.isEmpty()) {
                    closed.add(tail);
                } else if (tail.side()) {
                    // A side street starts with its piece with no plot and a segment after it.
                    add(tail, Kind.SIDE);
                    Tail next = tails.remove(tails.size() - 1);
                    add(next, Kind.SEGMENT);
                } else {
                    add(tail, kinds.size() > 1 && random.nextInt(3) == 0 ? Kind.CROSS : kinds.get(0));
                }
            }
            tails.addAll(closed);
            closed.clear();
            Choice choice = choose();
            for (Tail tail : tails) {
                pieces.add(new Piece(Kind.END, tail.start().x(), tail.start().z(), tail.start().dir(), StreetStyle.END,
                        tail.end(), tail.parent(), tail.exit()));
            }
            double radius = 0;
            for (Piece piece : pieces) {
                radius = Math.max(radius, piece.box().reach());
            }
            for (Plot plot : choice.plots()) {
                radius = Math.max(radius, plot.area().reach());
            }
            return new VillagePlan(pieces, choice.plots(), target, choice.missingPlots(), choice.missingHomes(),
                    choice.missingWide(), radius);
        }

        private boolean enough() {
            return slots.size() >= target && slots.stream().filter(s -> s.good() || s.reserved()).count() >= needed;
        }

        private static long distance(Start start) {
            return (long) start.x() * start.x() + (long) start.z() * start.z();
        }

        private int length(Kind kind) {
            return switch (kind) {
                case SEGMENT -> style.segment();
                case CROSS -> style.cross();
                case SIDE -> style.side();
                case END -> StreetStyle.END;
            };
        }

        /** The block of a piece at (across, along) in the coordinates of the template. */
        private static int[] at(Start start, int across, int along) {
            Dir side = start.dir().counterclockwise();
            return new int[] {start.x() + (across - 2) * side.dx() + along * start.dir().dx(),
                start.z() + (across - 2) * side.dz() + along * start.dir().dz()};
        }

        private static Box box(Start start, int length) {
            return boxOf(start, 0, 0, StreetStyle.WIDTH - 1, length - 1);
        }

        private static Box boxOf(Start start, int across1, int along1, int across2, int along2) {
            int[] a = at(start, across1, along1);
            int[] b = at(start, across2, along2);
            return Box.of(a[0], a[1], b[0], b[1]);
        }

        /** The starts of the exits of a piece: the next piece, then the side exits of a crossroad. */
        private List<Start> exits(Start start, Kind kind) {
            int length = length(kind);
            int[] next = at(start, 2, length);
            List<Start> exits = new ArrayList<>(List.of(new Start(next[0], next[1], start.dir())));
            if (kind == Kind.CROSS) {
                int middle = style.crossArm() + 2;
                int[] west = at(start, -1, middle);
                int[] east = at(start, StreetStyle.WIDTH, middle);
                exits.add(new Start(west[0], west[1], start.dir().counterclockwise().opposite()));
                exits.add(new Start(east[0], east[1], start.dir().counterclockwise()));
            }
            return exits;
        }

        private boolean inside(Box box) {
            return box.reach() <= maxRadius;
        }

        /** True when the box is inside the radius and touches no piece, no plot and no street end. */
        private boolean free(Box box) {
            if (!inside(box) || box.overlaps(plaza)) {
                return false;
            }
            return pieces.stream().noneMatch(p -> p.box().overlaps(box))
                    && slots.stream().noneMatch(s -> s.area().overlaps(box))
                    && tails.stream().noneMatch(t -> t.end().overlaps(box))
                    && closed.stream().noneMatch(t -> t.end().overlaps(box));
        }

        private boolean fits(Tail tail, Kind kind) {
            Start start = tail.start();
            if (kind == Kind.SIDE) {
                // The segment after the side piece must also fit.
                Start next = exits(start, kind).get(0);
                Box side = box(start, length(kind));
                return free(side) && fits(new Tail(next, false, -1, 0, side), Kind.SEGMENT);
            }
            if (!free(box(start, length(kind)))) {
                return false;
            }
            return exits(start, kind).stream().allMatch(exit -> free(box(exit, StreetStyle.END)));
        }

        private void add(Tail tail, Kind kind) {
            Start start = tail.start();
            int index = pieces.size();
            pieces.add(new Piece(kind, start.x(), start.z(), start.dir(), length(kind), box(start, length(kind)),
                    tail.parent(), tail.exit()));
            List<Start> exits = exits(start, kind);
            for (int i = 0; i < exits.size(); i++) {
                Start exit = exits.get(i);
                tails.add(new Tail(exit, kind == Kind.CROSS && i > 0, index, i, box(exit, StreetStyle.END)));
            }
            List<Integer> plotZ = kind == Kind.SEGMENT ? style.segmentPlots()
                    : kind == Kind.CROSS ? style.crossPlots() : List.of();
            for (int row = 0; row < plotZ.size(); row++) {
                int along = plotZ.get(row);
                for (boolean east : new boolean[] {false, true}) {
                    Box area = area(start, along, east, style.home());
                    if (free(area)) {
                        int[] connector = at(start, east ? StreetStyle.WIDTH - 1 : 0, along);
                        Dir side = start.dir().counterclockwise();
                        Dir facing = east ? side : side.opposite();
                        PlotRole role = tail.parent() == -1 ? role(start.dir(), row, east) : PlotRole.HOME;
                        slots.add(new Slot(connector[0], connector[1], facing, index, along, east, area, role,
                                terrain.test(area)));
                    }
                }
            }
        }

        /** The role of a plot of the first piece of an axis street, from its row (0 near the plaza) and its side. */
        private static PlotRole role(Dir street, int row, boolean east) {
            if (street == Dir.NORTH && row == 0 && !east) {
                return PlotRole.BANK;
            }
            if (street == Dir.SOUTH && row == 0) {
                return east ? PlotRole.JOBS_START : PlotRole.JOBS_FOOD;
            }
            return street == Dir.SOUTH && row == 1 && !east ? PlotRole.JOBS_START : PlotRole.HOME;
        }

        /** The box of a home on a plot: centered on the plot along the street, outside the box of the piece. */
        private static Box area(Start start, int along, boolean east, Home home) {
            int half = home.frontage() / 2;
            int near = east ? StreetStyle.WIDTH : -1;
            int far = east ? StreetStyle.WIDTH - 1 + home.depth() : -home.depth();
            return boxOf(start, near, along - half, far, along + home.frontage() - 1 - half);
        }

        /**
         * Chooses the plots. First the wide homes take the good plots nearest to the middle, but not a plot with a
         * role. A wide home can take the place of other home plots of its piece when the plan keeps its target without
         * them. Then the plots with a role, the wide plots and the good plots nearest to the middle are the homes. The
         * spare plots are the bad plots first, then the farthest plots of the plan.
         */
        private Choice choose() {
            List<Slot> order = new ArrayList<>(slots);
            order.sort(Comparator.comparingLong(Slot::distance));
            Map<Slot, Home> wideOf = new LinkedHashMap<>();
            Map<Slot, Box> areas = new HashMap<>();
            Map<String, Integer> missingWide = new LinkedHashMap<>();
            for (Home wide : style.wide()) {
                int marked = 0;
                for (Slot slot : List.copyOf(order)) {
                    if (marked >= wide.count()) {
                        break;
                    }
                    if (!order.contains(slot) || !slot.good() || slot.reserved() || wideOf.containsKey(slot)) {
                        continue;
                    }
                    Box area = wideArea(slot, wide);
                    if (area == null) {
                        continue;
                    }
                    List<Slot> lost = order.stream().filter(other -> other != slot
                            && areas.getOrDefault(other, other.area()).overlaps(area)).toList();
                    boolean keepsTarget = order.size() - lost.size() >= target
                            && order.stream().filter(o -> (o.good() || o.reserved()) && !lost.contains(o)).count()
                                    >= needed;
                    if (lost.stream()
                                    .allMatch(o -> o.piece() == slot.piece() && !o.reserved() && !wideOf.containsKey(o))
                            && (lost.isEmpty() || keepsTarget)) {
                        order.removeAll(lost);
                        wideOf.put(slot, wide);
                        areas.put(slot, area);
                        marked++;
                    }
                }
                if (marked < wide.count()) {
                    missingWide.put(wide.name(), wide.count() - marked);
                }
            }
            int count = Math.min(target, order.size());
            List<Slot> homes = new ArrayList<>(order.stream().filter(Slot::reserved).toList());
            homes.addAll(wideOf.keySet());
            for (Slot slot : order) {
                if (homes.size() < Math.min(needed, count) && slot.good() && !homes.contains(slot)) {
                    homes.add(slot);
                }
            }
            List<Slot> rest = new ArrayList<>(order);
            rest.removeAll(homes);
            rest.sort(Comparator.comparing(Slot::good));
            List<Plot> plots = new ArrayList<>();
            for (Slot slot : homes) {
                plots.add(plot(slot, wideOf.get(slot), areas, false));
            }
            for (Slot slot : rest.subList(0, Math.max(0, count - homes.size()))) {
                plots.add(plot(slot, null, areas, true));
            }
            return new Choice(plots, target - count, Math.max(0, needed - homes.size()), missingWide);
        }

        private Plot plot(Slot slot, Home wide, Map<Slot, Box> areas, boolean spare) {
            List<Home> homes = wide == null ? List.of(style.home()) : List.of(style.home(), wide);
            return new Plot(slot.x(), slot.z(), slot.facing(), areas.getOrDefault(slot, slot.area()), homes,
                    slot.role(), slot.good(), spare, slot.piece());
        }

        /**
         * The box around the home of the style and a wide home on a plot, or null when the wide home does not fit: it
         * goes out of the radius, on bad terrain, on a piece or a street end, or too far past the end of its piece.
         */
        private Box wideArea(Slot slot, Home wide) {
            Piece piece = pieces.get(slot.piece());
            Box area = area(new Start(piece.x(), piece.z(), piece.dir()), slot.along(), slot.east(), wide)
                    .union(slot.area());
            int overflow = slot.along() + wide.frontage() - 1 - wide.frontage() / 2 - (piece.length() - 1);
            boolean next = pieces.stream().anyMatch(p -> p.parent() == slot.piece() && p.exit() == 0);
            if (!inside(area) || area.overlaps(plaza) || !terrain.test(area) || (overflow > style.endOverflow() && next)
                    || pieces.stream().anyMatch(p -> p.box().overlaps(area))
                    || tails.stream().anyMatch(t -> t.end().overlaps(area))
                    || closed.stream().anyMatch(t -> t.end().overlaps(area))) {
                return null;
            }
            return area;
        }
    }

    /** The plots of a plan, with the shortfall. */
    private record Choice(List<Plot> plots, int missingPlots, int missingHomes, Map<String, Integer> missingWide) {
    }
}
