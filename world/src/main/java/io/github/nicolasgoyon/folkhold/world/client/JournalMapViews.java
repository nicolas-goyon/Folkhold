package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.cartofold.api.client.MapFrame;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The views of the map frames of the journal: the keys of the frames, and the box of blocks that a frame shows first.
 *
 * <ul>
 *   <li>The arrow keys move the view by {@link #KEY_MOVE} units of the screen. The keys + and - change the scale by
 *       {@link #ZOOM_STEP}, from {@link #MIN_SCALE} to {@link #MAX_SCALE} (the limits of the frames of
 *       Cartofold).</li>
 *   <li>The tab "Map" shows all the discovered territories and villages ({@link #territoryBox}). The tab "Routes"
 *       shows the two villages, the route and its relief ({@link #routeBox}).</li>
 *   <li>The tab "Routes" builds its widgets again only when the state of the route changes ({@link #routeState}).
 *       The progress of a calculation does not change the state.</li>
 * </ul>
 */
final class JournalMapViews {
    /** The smallest scale, in units of the screen for one block. */
    static final double MIN_SCALE = 1.0 / 8.0;
    /** The largest scale, in units of the screen for one block. */
    static final double MAX_SCALE = 8.0;
    /** The factor of one step of the zoom. */
    static final double ZOOM_STEP = Math.sqrt(2.0);
    /** The move of the view for an arrow key, in units of the screen. */
    static final double KEY_MOVE = 32.0;
    /** The smallest side of the box of a route, in blocks. */
    static final int ROUTE_MIN_SPAN = 64;

    private JournalMapViews() {
    }

    /**
     * The state of the route that the widgets of the tab "Routes" use.
     *
     * @param fromId   the settlement id of the start, or null
     * @param toId     the settlement id of the end, or null
     * @param busy     true while the server calculates the route or its relief: no "Recalculate"
     * @param points   the points of the route to show, or an empty list
     * @param surfaces the surfaces of the route to show, or an empty list
     * @param relief   the position and the size of the relief, or an empty list
     */
    record RouteState(@Nullable String fromId, @Nullable String toId, boolean busy, List<Route.Point> points,
            List<Route.Surface> surfaces, List<Integer> relief) {

        /** The part of the state that the view of the frame shows all of: a change gives a new view. */
        RouteState fit() {
            return new RouteState(fromId, toId, false, points, surfaces, relief);
        }
    }

    /**
     * The state of the route.
     *
     * @param fromId  the settlement id of the start, or null
     * @param toId    the settlement id of the end, or null
     * @param payload the last answer of the server, or null
     * @param route   the route to show, or empty
     * @param relief  the relief of the route, or null
     * @return the state
     */
    static RouteState routeState(@Nullable String fromId, @Nullable String toId, @Nullable RoutePayload payload,
            Optional<Route> route, @Nullable RoutePayload.Relief relief) {
        return new RouteState(fromId, toId, payload == null || payload.more(),
                route.map(Route::points).orElse(List.of()), route.map(Route::surfaces).orElse(List.of()),
                relief == null ? List.of()
                        : List.of(relief.minX(), relief.minZ(), relief.cell(), relief.width(), relief.height()));
    }

    /**
     * The view after a key.
     *
     * @param view    the view now
     * @param keyCode the GLFW code of the key
     * @return the new view, or empty when the key does not change the view
     */
    static Optional<MapFrame.View> onKey(MapFrame.View view, int keyCode) {
        double move = KEY_MOVE / view.scale();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> Optional.of(moved(view, -move, 0));
            case GLFW.GLFW_KEY_RIGHT -> Optional.of(moved(view, move, 0));
            case GLFW.GLFW_KEY_UP -> Optional.of(moved(view, 0, -move));
            case GLFW.GLFW_KEY_DOWN -> Optional.of(moved(view, 0, move));
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> Optional.of(zoom(view, 1));
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> Optional.of(zoom(view, -1));
            default -> Optional.empty();
        };
    }

    private static MapFrame.View moved(MapFrame.View view, double dx, double dz) {
        return new MapFrame.View(view.centerX() + dx, view.centerZ() + dz, view.scale());
    }

    /**
     * The view after a zoom around its center.
     *
     * @param view  the view now
     * @param steps the number of steps: more than 0 shows more details
     * @return the new view
     */
    static MapFrame.View zoom(MapFrame.View view, int steps) {
        double scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, view.scale() * Math.pow(ZOOM_STEP, steps)));
        return new MapFrame.View(view.centerX(), view.centerZ(), scale);
    }

    /**
     * The box of the discovered territories and villages.
     *
     * @param territories the discovered territories
     * @param villages    the villages of the journal
     * @return the minimum X, the minimum Z, the maximum X and the maximum Z, or empty with no territory and no village
     */
    static Optional<double[]> territoryBox(List<TerritoryMapPayload.Village> territories,
            List<JournalPayload.Village> villages) {
        Box box = new Box();
        for (TerritoryMapPayload.Village territory : territories) {
            // The territory is in the disc of its claim: the radius in chunks, and one chunk more for the borders.
            int reach = (territory.claimant().radius() + 1) * 16;
            box.add(territory.claimant().x() - reach, territory.claimant().z() - reach);
            box.add(territory.claimant().x() + reach, territory.claimant().z() + reach);
        }
        for (JournalPayload.Village village : villages) {
            box.add(village.x(), village.z());
            box.add(village.x() + 1.0, village.z() + 1.0);
        }
        return box.empty() ? Optional.empty() : Optional.of(box.bounds());
    }

    /**
     * The box of a route: the two villages, the points of the route and the relief. A side has
     * {@link #ROUTE_MIN_SPAN} blocks or more.
     *
     * @param from   the start
     * @param to     the end
     * @param route  the route, or empty
     * @param relief the relief of the route, or null
     * @return the minimum X, the minimum Z, the maximum X and the maximum Z
     */
    static double[] routeBox(JournalPayload.Village from, JournalPayload.Village to, Optional<Route> route,
            @Nullable RoutePayload.Relief relief) {
        Box box = new Box();
        box.add(from.x(), from.z());
        box.add(to.x(), to.z());
        route.ifPresent(found -> found.points().forEach(point -> box.add(point.x(), point.z())));
        if (relief != null) {
            box.add(relief.minX(), relief.minZ());
            box.add(relief.minX() + (relief.width() - 1) * relief.cell(),
                    relief.minZ() + (relief.height() - 1) * relief.cell());
        }
        double[] bounds = box.bounds();
        double padX = Math.max(0, ROUTE_MIN_SPAN - (bounds[2] - bounds[0])) / 2.0;
        double padZ = Math.max(0, ROUTE_MIN_SPAN - (bounds[3] - bounds[1])) / 2.0;
        return new double[] {bounds[0] - padX, bounds[1] - padZ, bounds[2] + padX, bounds[3] + padZ};
    }

    /** A box of blocks that grows with each point. */
    private static final class Box {
        private double minX = Double.MAX_VALUE;
        private double minZ = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE;
        private double maxZ = -Double.MAX_VALUE;

        void add(double x, double z) {
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }

        boolean empty() {
            return minX > maxX;
        }

        double[] bounds() {
            return new double[] {minX, minZ, maxX, maxZ};
        }
    }
}
