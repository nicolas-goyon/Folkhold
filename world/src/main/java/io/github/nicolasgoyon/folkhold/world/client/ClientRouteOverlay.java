package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.world.group.RoadNetwork;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the route of the journal in the world ({@link ClientRouteView}), as a line a little above the ground near the
 * player.
 *
 * <ul>
 *   <li>The color of the line shows the surface: gold on a road, green on land, cyan in a ford, blue on deep water.
 *       A coarse route is grey.</li>
 *   <li>On a road segment, the line is on the road floor, also on a bridge or in a tunnel. On the other segments, the
 *       line is on the terrain.</li>
 *   <li>The client reads the heights only in the loaded chunks, in a radius of {@link #RADIUS} blocks around the
 *       player. It calculates the line again when the route changes, when the player moves, and at an interval.</li>
 * </ul>
 */
public final class ClientRouteOverlay {
    /** The radius around the player where the line shows, in blocks. */
    private static final int RADIUS = 128;
    /** The distance between two points of the line, in blocks. */
    private static final double SAMPLE = 2.0;
    /** The height of the line above the floor, in blocks. */
    private static final float LIFT = 0.08F;
    /** The distance that the player moves before the client calculates the line again, in blocks. */
    private static final double MOVE = 8.0;
    /** The interval between two calculations of the line, in ticks: the chunks near the player load. */
    private static final int INTERVAL = 40;

    /** A segment of the line, in world coordinates, with its color as ARGB. */
    private record Segment(float ax, float ay, float az, float bx, float by, float bz, int color) {
    }

    private static List<Segment> segments = List.of();
    private static int builtVersion = -1;
    private static long builtTick;
    private static Vec3 builtCenter = Vec3.ZERO;

    private ClientRouteOverlay() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        RoutePayload payload = ClientRouteView.route();
        if (!ClientRouteView.inWorld() || level == null || player == null || payload == null
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Optional<Route> route = payload.route();
        if (route.isEmpty()) {
            return;
        }
        long tick = level.getGameTime();
        if (builtVersion != ClientRouteView.version() || tick - builtTick >= INTERVAL || tick < builtTick
                || builtCenter.distanceToSqr(player.position()) > MOVE * MOVE) {
            segments = build(level, route.get(), payload.detailed(), player.getX(), player.getZ());
            builtVersion = ClientRouteView.version();
            builtTick = tick;
            builtCenter = player.position();
        }
        if (segments.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose last = pose.last();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Segment segment : segments) {
            float dx = segment.bx - segment.ax;
            float dy = segment.by - segment.ay;
            float dz = segment.bz - segment.az;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-4F) {
                continue;
            }
            lines.addVertex(last, segment.ax, segment.ay, segment.az).setColor(segment.color)
                    .setNormal(last, dx / length, dy / length, dz / length);
            lines.addVertex(last, segment.bx, segment.by, segment.bz).setColor(segment.color)
                    .setNormal(last, dx / length, dy / length, dz / length);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** The segments of the line near a position. A point in a chunk that is not loaded cuts the line. */
    private static List<Segment> build(ClientLevel level, Route route, boolean detailed, double x, double z) {
        List<Segment> result = new ArrayList<>();
        double length = route.length();
        float[] previous = null;
        for (double progress = 0; progress <= length + SAMPLE - 1.0E-6; progress += SAMPLE) {
            double at = Math.min(progress, length);
            Route.Position position = route.positionAt(at);
            int bx = (int) Math.floor(position.x());
            int bz = (int) Math.floor(position.z());
            if (Math.abs(bx - x) > RADIUS || Math.abs(bz - z) > RADIUS || !level.hasChunk(bx >> 4, bz >> 4)) {
                previous = null;
                continue;
            }
            Route.Surface surface = route.surfaceAt(at);
            float[] point = {(float) position.x(), floorY(level, surface, bx, bz) + LIFT, (float) position.z()};
            if (previous != null) {
                result.add(new Segment(previous[0], previous[1], previous[2], point[0], point[1], point[2],
                        color(surface, detailed)));
            }
            previous = point;
        }
        return result;
    }

    /** The Y of the floor at a point of the route: the road floor on a road segment, else the terrain. */
    private static int floorY(ClientLevel level, Route.Surface surface, int x, int z) {
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surface != Route.Surface.ROAD) {
            return terrain;
        }
        int road = RoadNetwork.roadFloorAt(level, x, z, level.getMinBuildHeight(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        return road == RouteFinder.NO_ROAD ? terrain : road + 1;
    }

    /**
     * The color of a segment in the world, as ARGB.
     *
     * @param surface  the surface of the segment
     * @param detailed false for a coarse route: grey
     * @return the color
     */
    static int color(Route.Surface surface, boolean detailed) {
        if (!detailed) {
            return 0xFFCCCCCC;
        }
        return switch (surface) {
            case ROAD -> 0xFFFFD37F;
            case LAND -> 0xFF9AE680;
            case FORD -> 0xFF73D9FF;
            case WATER -> 0xFF598CFF;
        };
    }

    /** Forgets the line. Called when the player leaves a world or a server. */
    public static void clear() {
        segments = List.of();
        builtVersion = -1;
        builtTick = 0;
        builtCenter = Vec3.ZERO;
    }
}
