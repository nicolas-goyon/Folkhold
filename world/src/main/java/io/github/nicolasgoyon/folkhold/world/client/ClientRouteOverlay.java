package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.client.ClientRouteView.LineMode;
import io.github.nicolasgoyon.folkhold.world.group.RouteWalker;
import io.github.nicolasgoyon.folkhold.world.group.RouteWalker.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the route of the journal in the world ({@link ClientRouteView}): a yellow line above the blocks that the
 * caravans walk on, near the player.
 *
 * <ul>
 *   <li>The line follows a walk on the blocks ({@link RouteWalker}): one block at a time, with valid steps only. It
 *       does not go through a wall, a ceiling or the ground. The client cuts the route into parts of
 *       {@link #PART} blocks and calculates the walk of {@link #PARTS_PER_FRAME} parts at most in each frame. The
 *       walk of a part starts at the end of the walk of the part before it.</li>
 *   <li>When the search finds no walk in the corridor of a part, the line follows the floors of the columns of the
 *       route ({@link RouteWalker#followColumns}). This line is paler.</li>
 *   <li>Two modes ({@link LineMode}): precise (a point on each block of the walk) and approximate (a point every
 *       {@link #APPROXIMATE_STEP} blocks of the walk, and at each change of height).</li>
 *   <li>A change of height is a vertical step at the edge of the higher block, a little above the lower block: the
 *       line does not go into the ground.</li>
 *   <li>The line is a flat strip above the blocks and a low vertical strip, so the player sees it from above and from
 *       the side. A coarse route (the detailed route is not ready) is pale and translucent.</li>
 *   <li>The client reads the blocks only in the loaded chunks, in a radius of {@link #RADIUS} blocks around the
 *       player. It calculates the walks again when the route changes, and every {@link #REFRESH_TICKS} ticks (the
 *       blocks can change).</li>
 * </ul>
 */
public final class ClientRouteOverlay {
    /** The radius around the player where the line shows, in blocks. */
    private static final int RADIUS = 128;
    /** The length of a part of the route, in blocks. */
    static final double PART = 24.0;
    /** The maximum number of parts that the client walks in one frame. */
    static final int PARTS_PER_FRAME = 1;
    /** The distance between two points of the approximate line, in blocks of the walk. */
    static final int APPROXIMATE_STEP = 3;
    /** The height of the bottom of the line above the floor, in blocks. */
    private static final float LIFT = 0.08F;
    /** The distance of a vertical step from the edge of the higher block, above the lower block. */
    private static final float STEP_OFFSET = 0.06F;
    /** The width of the flat strip, in blocks. */
    private static final float WIDTH = 0.3F;
    /** The height of the vertical strip, in blocks. */
    private static final float HEIGHT = 0.12F;
    /** The interval between two calculations of all the walks, in ticks. */
    private static final int REFRESH_TICKS = 200;
    /** The colors of the line, as ARGB: detailed route, coarse route, line of the columns. */
    private static final int DETAILED_COLOR = 0xF0FFD21F;
    private static final int COARSE_COLOR = 0x80FFF0A0;
    private static final int COLUMNS_COLOR = 0x90FFE070;

    /**
     * A part of the line from a point to another point. The direction (dirX, dirZ) is the horizontal direction of the
     * route, for the width of the strip. A vertical part (the same X and Z) is a step.
     */
    private record Segment(float ax, float ay, float az, float bx, float by, float bz, float dirX, float dirZ,
            boolean columns) {
    }

    /** The walk of a part of the route. Columns: true if the search found no walk. */
    private record PartWalk(List<Step> steps, boolean columns) {
    }

    private static final Map<Integer, PartWalk> WALKS = new HashMap<>();
    private static List<Segment> segments = List.of();
    private static int builtVersion = -1;
    private static long walkedTick;
    private static boolean dirty;
    /** True while the client calculates the walks again for the same route: the old line stays until the end. */
    private static boolean refreshing;

    private ClientRouteOverlay() {
    }

    /** The number of parts of the line near the player. 0 if the line does not show. */
    public static int segmentCount() {
        return ClientRouteView.inWorld() ? segments.size() : 0;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        Optional<Route> route = ClientRouteView.shownRoute();
        if (!ClientRouteView.inWorld() || level == null || player == null || route.isEmpty()
                || level.dimension() != Level.OVERWORLD) {
            segments = List.of();
            return;
        }
        update(level, route.get(), player);
        if (segments.isEmpty()) {
            return;
        }
        int routeColor = ClientRouteView.isDetailed() ? DETAILED_COLOR : COARSE_COLOR;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (Segment segment : segments) {
            int color = segment.columns ? COLUMNS_COLOR : routeColor;
            float px = -segment.dirZ * WIDTH / 2;
            float pz = segment.dirX * WIDTH / 2;
            // The flat strip above the blocks. For a step, the same quad is a vertical face across the route.
            quad(quads, matrix, color, segment.ax + px, segment.ay, segment.az + pz, segment.ax - px, segment.ay,
                    segment.az - pz, segment.bx - px, segment.by, segment.bz - pz, segment.bx + px, segment.by,
                    segment.bz + pz);
            if (segment.ax != segment.bx || segment.az != segment.bz) {
                // The low vertical strip on the center of the flat strip.
                quad(quads, matrix, color, segment.ax, segment.ay, segment.az, segment.ax, segment.ay + HEIGHT,
                        segment.az, segment.bx, segment.by + HEIGHT, segment.bz, segment.bx, segment.by, segment.bz);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        pose.popPose();
    }

    /** Calculates the walks of some parts near the player, and the segments when the walks change. */
    private static void update(ClientLevel level, Route route, LocalPlayer player) {
        long tick = level.getGameTime();
        boolean changed = builtVersion != ClientRouteView.version();
        if (changed || tick - walkedTick >= REFRESH_TICKS || tick < walkedTick) {
            WALKS.clear();
            refreshing = !changed;
            builtVersion = ClientRouteView.version();
            walkedTick = tick;
            dirty = true;
        }
        int parts = (int) Math.ceil(route.length() / PART);
        Set<Integer> near = new HashSet<>();
        for (int k = 0; k < parts; k++) {
            if (isNear(route, k, player.getX(), player.getZ())) {
                near.add(k);
            }
        }
        RouteWalker walker = new RouteWalker(level);
        int walked = 0;
        for (int k = 0; k < parts && walked < PARTS_PER_FRAME; k++) {
            if (!near.contains(k) || WALKS.containsKey(k)) {
                continue;
            }
            WALKS.put(k, walkPart(level, walker, route, k));
            walked++;
            dirty = true;
        }
        if (WALKS.keySet().removeIf(k -> !near.contains(k))) {
            dirty = true;
        }
        boolean complete = WALKS.keySet().containsAll(near);
        if (dirty && (complete || !refreshing)) {
            segments = segments(parts);
            dirty = false;
            refreshing = refreshing && !complete;
        }
    }

    /** True if a point of a part of the route is in the radius of the line. */
    private static boolean isNear(Route route, int part, double x, double z) {
        for (double progress = part * PART; progress <= (part + 1) * PART; progress += PART / 4) {
            Route.Position position = route.positionAt(progress);
            if (Math.abs(position.x() - x) <= RADIUS && Math.abs(position.z() - z) <= RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** The walk of a part, from the end of the walk of the part before it, or from the floor of its first column. */
    private static PartWalk walkPart(ClientLevel level, RouteWalker walker, Route route, int part) {
        Route path = route.between(part * PART, (part + 1) * PART);
        PartWalk before = WALKS.get(part - 1);
        Step start = before != null && !before.steps().isEmpty()
                ? before.steps().get(before.steps().size() - 1) : null;
        if (start == null) {
            Route.Point first = path.points().get(0);
            if (!level.hasChunk(first.x() >> 4, first.z() >> 4)) {
                return new PartWalk(walker.followColumns(path, null), true);
            }
            start = walker.highestStandable(first.x(), first.z(),
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING, first.x(), first.z()),
                    level.getMinBuildHeight() + 1);
        }
        if (start != null) {
            List<Step> steps = walker.walk(start, path);
            if (!steps.isEmpty()) {
                return new PartWalk(steps, false);
            }
        }
        return new PartWalk(walker.followColumns(path, start), true);
    }

    /** The segments of all the walks, in the order of the parts. */
    private static List<Segment> segments(int parts) {
        List<Segment> result = new ArrayList<>();
        boolean precise = ClientRouteView.lineMode() == LineMode.PRECISE;
        Step previous = null;
        for (int k = 0; k < parts; k++) {
            PartWalk walk = WALKS.get(k);
            if (walk == null) {
                previous = null;
                continue;
            }
            List<Step> steps = precise ? walk.steps() : simplify(walk.steps());
            for (Step step : steps) {
                if (previous != null && !step.equals(previous)) {
                    addLink(result, previous, step, walk.columns());
                }
                previous = step;
            }
        }
        return result;
    }

    /**
     * The points of the approximate line: one point every {@link #APPROXIMATE_STEP} steps of the walk, the two ends,
     * and the two steps of each change of height.
     */
    static List<Step> simplify(List<Step> steps) {
        List<Step> kept = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            boolean change = (i > 0 && steps.get(i - 1).floor() != steps.get(i).floor())
                    || (i < steps.size() - 1 && steps.get(i + 1).floor() != steps.get(i).floor());
            if (i == 0 || i == steps.size() - 1 || change || i % APPROXIMATE_STEP == 0) {
                kept.add(steps.get(i));
            }
        }
        return kept;
    }

    /**
     * Adds the line from one point to the next one. At the same height: a flat strip. At different heights: a flat
     * strip to the edge between the two blocks, a vertical step a little above the lower block, and a flat strip to
     * the next point.
     */
    private static void addLink(List<Segment> result, Step from, Step to, boolean columns) {
        float ax = from.x() + 0.5F;
        float az = from.z() + 0.5F;
        float bx = to.x() + 0.5F;
        float bz = to.z() + 0.5F;
        float dx = bx - ax;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        float dirX = dx / length;
        float dirZ = dz / length;
        float ay = (float) from.floor() + LIFT;
        float by = (float) to.floor() + LIFT;
        if (ay == by) {
            result.add(new Segment(ax, ay, az, bx, by, bz, dirX, dirZ, columns));
            return;
        }
        // The edge is at half of the link. The step moves a little toward the lower point, above its block.
        float toward = by < ay ? STEP_OFFSET : -STEP_OFFSET;
        float edgeX = ax + dirX * (length / 2 + toward);
        float edgeZ = az + dirZ * (length / 2 + toward);
        result.add(new Segment(ax, ay, az, edgeX, ay, edgeZ, dirX, dirZ, columns));
        result.add(new Segment(edgeX, ay, edgeZ, edgeX, by, edgeZ, dirX, dirZ, columns));
        result.add(new Segment(edgeX, by, edgeZ, bx, by, bz, dirX, dirZ, columns));
    }

    private static void quad(VertexConsumer quads, Matrix4f matrix, int color, float x0, float y0, float z0,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        quads.addVertex(matrix, x0, y0, z0).setColor(color);
        quads.addVertex(matrix, x1, y1, z1).setColor(color);
        quads.addVertex(matrix, x2, y2, z2).setColor(color);
        quads.addVertex(matrix, x3, y3, z3).setColor(color);
    }

    /** Forgets the line. Called when the player leaves a world or a server. */
    public static void clear() {
        WALKS.clear();
        segments = List.of();
        builtVersion = -1;
        walkedTick = 0;
        dirty = false;
        refreshing = false;
    }
}
