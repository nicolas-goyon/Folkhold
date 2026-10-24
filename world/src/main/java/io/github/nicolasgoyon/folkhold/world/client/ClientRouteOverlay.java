package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.client.ClientRouteView.LineMode;
import io.github.nicolasgoyon.folkhold.world.group.RoadNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the route of the journal in the world ({@link ClientRouteView}): a yellow line above the blocks that the
 * caravans walk on, near the player.
 *
 * <ul>
 *   <li>The line is a flat strip above the blocks and a low vertical strip, so the player sees it from above and from
 *       the side.</li>
 *   <li>Two modes ({@link LineMode}):
 *     <ul>
 *       <li>Approximate: a smooth line with a point every {@link #APPROXIMATE_STEP} blocks.</li>
 *       <li>Precise: a flat strip on the top of each block of the route, and a vertical step between two blocks at
 *           different heights.</li>
 *     </ul>
 *   </li>
 *   <li>The floor of a block is a block that the caravans can stand on, with free space above it. A column can have
 *       more than one floor: a gate under a lintel, a tree, a tunnel. The line takes the highest floor that is not
 *       more than {@link #MAX_RISE} blocks above the floor of the block before it. Thus the line goes under a lintel
 *       or a tree. On a road segment, the line takes a floor of road blocks if the column has one.</li>
 *   <li>A coarse route (the detailed route is not ready) is pale and translucent.</li>
 *   <li>The client reads the blocks only in the loaded chunks, in a radius of {@link #RADIUS} blocks around the
 *       player. It calculates the line again when the route or the mode changes, when the player moves, and at an
 *       interval.</li>
 * </ul>
 */
public final class ClientRouteOverlay {
    /** The radius around the player where the line shows, in blocks. */
    private static final int RADIUS = 128;
    /** The distance between two samples of the route, in blocks: smaller than a block, so no block is missed. */
    private static final double SAMPLE = 0.25;
    /** The distance between two points of the approximate line, in blocks. */
    static final double APPROXIMATE_STEP = 3.0;
    /** The height of the bottom of the line above the floor, in blocks. */
    private static final float LIFT = 0.08F;
    /** The width of the flat strip, in blocks. */
    private static final float WIDTH = 0.3F;
    /** The height of the vertical strip, in blocks. */
    private static final float HEIGHT = 0.12F;
    /** The free space above a floor, in blocks: the height of a merchant. */
    private static final int HEADROOM = 2;
    /** The largest climb from one floor to the next floor of the line, in blocks. */
    static final float MAX_RISE = 1.6F;
    /** The distance that the player moves before the client calculates the line again, in blocks. */
    private static final double MOVE = 8.0;
    /** The interval between two calculations of the line, in ticks: the chunks near the player load. */
    private static final int INTERVAL = 40;
    /** The color of the line of a detailed route, as ARGB. */
    private static final int DETAILED_COLOR = 0xF0FFD21F;
    /** The color of the line of a coarse route, as ARGB. */
    private static final int COARSE_COLOR = 0x80FFF0A0;

    /**
     * A part of the line from a point to another point. The direction (dirX, dirZ) is the horizontal direction of the
     * route, for the width of the strip. A vertical part (the same X and Z) is a step.
     */
    private record Segment(float ax, float ay, float az, float bx, float by, float bz, float dirX, float dirZ) {
    }

    private static List<Segment> segments = List.of();
    private static int builtVersion = -1;
    private static long builtTick;
    private static Vec3 builtCenter = Vec3.ZERO;

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
        long tick = level.getGameTime();
        if (builtVersion != ClientRouteView.version() || tick - builtTick >= INTERVAL || tick < builtTick
                || builtCenter.distanceToSqr(player.position()) > MOVE * MOVE) {
            segments = ClientRouteView.lineMode() == LineMode.PRECISE
                    ? buildPrecise(level, route.get(), player.getX(), player.getZ())
                    : buildApproximate(level, route.get(), player.getX(), player.getZ());
            builtVersion = ClientRouteView.version();
            builtTick = tick;
            builtCenter = player.position();
        }
        if (segments.isEmpty()) {
            return;
        }
        int color = ClientRouteView.isDetailed() ? DETAILED_COLOR : COARSE_COLOR;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (Segment segment : segments) {
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

    private static void quad(VertexConsumer quads, Matrix4f matrix, int color, float x0, float y0, float z0,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        quads.addVertex(matrix, x0, y0, z0).setColor(color);
        quads.addVertex(matrix, x1, y1, z1).setColor(color);
        quads.addVertex(matrix, x2, y2, z2).setColor(color);
        quads.addVertex(matrix, x3, y3, z3).setColor(color);
    }

    /**
     * The precise line near a position: for each pair of blocks that follow each other on the route, a flat strip to
     * the middle of the two blocks, a step, and a flat strip to the center of the second block. A block in a chunk
     * that is not loaded cuts the line.
     */
    private static List<Segment> buildPrecise(ClientLevel level, Route route, double x, double z) {
        List<Segment> result = new ArrayList<>();
        double length = route.length();
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        float[] previous = null;
        for (double progress = 0; progress <= length + SAMPLE - 1.0E-6; progress += SAMPLE) {
            double at = Math.min(progress, length);
            Route.Position position = route.positionAt(at);
            int bx = (int) Math.floor(position.x());
            int bz = (int) Math.floor(position.z());
            if (bx == lastX && bz == lastZ) {
                continue;
            }
            lastX = bx;
            lastZ = bz;
            if (!isNear(level, bx, bz, x, z)) {
                previous = null;
                continue;
            }
            float floor = floorY(level, route.surfaceAt(at), bx, bz, previous == null ? Float.NaN : previous[1]);
            float[] point = {bx + 0.5F, floor, bz + 0.5F};
            if (previous != null) {
                float dx = point[0] - previous[0];
                float dz = point[2] - previous[2];
                float length2d = (float) Math.sqrt(dx * dx + dz * dz);
                float dirX = dx / length2d;
                float dirZ = dz / length2d;
                float midX = (previous[0] + point[0]) / 2;
                float midZ = (previous[2] + point[2]) / 2;
                float lowY = previous[1] + LIFT;
                float highY = point[1] + LIFT;
                result.add(new Segment(previous[0], lowY, previous[2], midX, lowY, midZ, dirX, dirZ));
                if (lowY != highY) {
                    result.add(new Segment(midX, lowY, midZ, midX, highY, midZ, dirX, dirZ));
                }
                result.add(new Segment(midX, highY, midZ, point[0], highY, point[2], dirX, dirZ));
            }
            previous = point;
        }
        return result;
    }

    /**
     * The approximate line near a position: a straight strip between points of the route every
     * {@link #APPROXIMATE_STEP} blocks, on the floor. A point in a chunk that is not loaded cuts the line.
     */
    private static List<Segment> buildApproximate(ClientLevel level, Route route, double x, double z) {
        List<Segment> result = new ArrayList<>();
        double length = route.length();
        float[] previous = null;
        for (double progress = 0; progress <= length + APPROXIMATE_STEP - 1.0E-6; progress += APPROXIMATE_STEP) {
            double at = Math.min(progress, length);
            Route.Position position = route.positionAt(at);
            int bx = (int) Math.floor(position.x());
            int bz = (int) Math.floor(position.z());
            if (!isNear(level, bx, bz, x, z)) {
                previous = null;
                continue;
            }
            float floor = floorY(level, route.surfaceAt(at), bx, bz, previous == null ? Float.NaN : previous[3]);
            float[] point = {(float) position.x(), floor + LIFT, (float) position.z(), floor};
            if (previous != null) {
                float dx = point[0] - previous[0];
                float dz = point[2] - previous[2];
                float length2d = (float) Math.sqrt(dx * dx + dz * dz);
                if (length2d > 1.0E-4F) {
                    result.add(new Segment(previous[0], previous[1], previous[2], point[0], point[1], point[2],
                            dx / length2d, dz / length2d));
                }
            }
            previous = point;
        }
        return result;
    }

    /** True if a block is in the radius of the line and in a loaded chunk. */
    private static boolean isNear(ClientLevel level, int bx, int bz, double x, double z) {
        return Math.abs(bx + 0.5 - x) <= RADIUS && Math.abs(bz + 0.5 - z) <= RADIUS
                && level.hasChunk(bx >> 4, bz >> 4);
    }

    /**
     * The Y of the floor of a block of the route (see the class description). The client reads the blocks: it does
     * not use the heightmaps that the server does not send.
     *
     * @param level     the level
     * @param surface   the surface of the route at the block
     * @param x         block X
     * @param z         block Z
     * @param reference the floor of the block before it on the line, or NaN for the first block
     * @return the Y of the top of the floor
     */
    private static float floorY(ClientLevel level, Route.Surface surface, int x, int z, float reference) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        List<Float> floors = new ArrayList<>();
        List<Float> roads = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        int free = 0;
        for (int y = top - 1; y > level.getMinBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                if (free >= HEADROOM - 1) {
                    floors.add(y + state.getFluidState().getHeight(level, pos));
                }
                free = 0;
                continue;
            }
            if (isPassable(level, pos, state)) {
                free++;
                continue;
            }
            if (free >= HEADROOM || y == top - 1) {
                float floor = y + topOf(level, pos, state);
                floors.add(floor);
                if (state.is(RoadNetwork.ROAD)) {
                    roads.add(floor);
                }
            }
            free = 0;
        }
        List<Float> candidates = surface == Route.Surface.ROAD && !roads.isEmpty() ? roads : floors;
        if (candidates.isEmpty()) {
            return top;
        }
        return choose(candidates, reference);
    }

    /**
     * Selects a floor in a column: the highest floor that is not more than {@link #MAX_RISE} blocks above the floor
     * before it. If all the floors are higher, the lowest of them.
     *
     * @param floors    the floors of the column, from the highest to the lowest
     * @param reference the floor before it, or NaN: the highest floor
     * @return the selected floor
     */
    static float choose(List<Float> floors, float reference) {
        if (Float.isNaN(reference)) {
            return floors.get(0);
        }
        for (float floor : floors) {
            if (floor <= reference + MAX_RISE) {
                return floor;
            }
        }
        return floors.get(floors.size() - 1);
    }

    /** True for a block that the caravans walk through: leaves, a fence gate, a door, or a block with no collision. */
    private static boolean isPassable(ClientLevel level, BlockPos pos, BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS)
                || state.getCollisionShape(level, pos).isEmpty();
    }

    /** The top of the collision shape of a block, from 0 to 1. A block with no collision gives 1. */
    private static float topOf(ClientLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? 1.0F : (float) Math.min(1.0, shape.max(Direction.Axis.Y));
    }

    /** Forgets the line. Called when the player leaves a world or a server. */
    public static void clear() {
        segments = List.of();
        builtVersion = -1;
        builtTick = 0;
        builtCenter = Vec3.ZERO;
    }
}
