package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the route of the caravans between two villages of the journal (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.RouteViews}).
 *
 * @param fromId   the settlement id of the start
 * @param toId     the settlement id of the end
 * @param status   the state of the route
 * @param detailed true if the route is the detailed route, false for the coarse route
 * @param points   the points of the route, from the start to the end. Empty if the server has no route to show.
 * @param surfaces the surface of each segment: one less than the points
 * @param progress the progress of the calculation on the way, or null with no calculation
 * @param relief   the relief of the map, or null if the server did not calculate it
 * @param more     true if the server calculates more data for the route: the journal asks again later
 */
public record RoutePayload(String fromId, String toId, Status status, boolean detailed, List<Route.Point> points,
        List<Route.Surface> surfaces, @Nullable Progress progress, @Nullable Relief relief, boolean more)
        implements CustomPacketPayload {

    public static final Type<RoutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "route"));

    /** The state of a route. */
    public enum Status {
        /** The route is ready: the caravans take it. */
        READY,
        /** The server calculates the route. The payload can have the coarse route. */
        PENDING,
        /** The two villages have no route: the caravans do not go from one to the other. */
        NONE,
        /** The server does not show the route: a village is not in the journal of the player, or is not known. */
        REFUSED
    }

    /**
     * The progress of the calculation of a route.
     *
     * @param detail   true for the detailed route, false for the coarse route
     * @param ahead    the number of calculations before this one: 0 when the calculation runs
     * @param examined the number of cells that the search examined
     * @param budget   the maximum number of cells of one search
     * @param percent  the part of the direct distance that the search covered, from 0 to 100
     */
    public record Progress(boolean detail, int ahead, int examined, int budget, int percent) {
    }

    /**
     * The relief of the map: the floor height and the water depth of a grid of cells. Cell (i, j) is at block X
     * {@code minX + i * cell} and block Z {@code minZ + j * cell}.
     */
    public static final class Relief {
        private final int minX;
        private final int minZ;
        private final int cell;
        private final int width;
        private final int height;
        private final short[] floor;
        private final byte[] water;

        /**
         * Makes a relief.
         *
         * @param minX   block X of the first column of cells
         * @param minZ   block Z of the first row of cells
         * @param cell   the size of a cell, in blocks
         * @param width  the number of columns
         * @param height the number of rows
         * @param floor  the floor height of each cell, row after row
         * @param water  the water depth of each cell, row after row
         */
        public Relief(int minX, int minZ, int cell, int width, int height, short[] floor, byte[] water) {
            if (width < 1 || height < 1 || cell < 1 || floor.length != width * height
                    || water.length != width * height) {
                throw new IllegalArgumentException("A relief needs one floor and one water value for each cell");
            }
            this.minX = minX;
            this.minZ = minZ;
            this.cell = cell;
            this.width = width;
            this.height = height;
            this.floor = floor.clone();
            this.water = water.clone();
        }

        public int minX() {
            return minX;
        }

        public int minZ() {
            return minZ;
        }

        public int cell() {
            return cell;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** The floor height of cell (i, j). */
        public int floor(int i, int j) {
            return floor[j * width + i];
        }

        /** The water depth of cell (i, j), in blocks. */
        public int water(int i, int j) {
            return water[j * width + i];
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(minX);
            buf.writeVarInt(minZ);
            buf.writeVarInt(cell);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            for (short value : floor) {
                buf.writeShort(value);
            }
            buf.writeBytes(water);
        }

        static Relief read(RegistryFriendlyByteBuf buf) {
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int cell = buf.readVarInt();
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            short[] floor = new short[width * height];
            for (int k = 0; k < floor.length; k++) {
                floor[k] = buf.readShort();
            }
            byte[] water = new byte[width * height];
            buf.readBytes(water);
            return new Relief(minX, minZ, cell, width, height, floor, water);
        }
    }

    /**
     * Makes a payload with a route.
     *
     * @param fromId   the settlement id of the start
     * @param toId     the settlement id of the end
     * @param status   the state of the route
     * @param detailed true for the detailed route
     * @param route    the route
     * @return the payload, with no progress, no relief and no more data
     */
    public static RoutePayload of(String fromId, String toId, Status status, boolean detailed, Route route) {
        return new RoutePayload(fromId, toId, status, detailed, route.points(), route.surfaces(), null, null, false);
    }

    /**
     * Makes a payload with no route.
     *
     * @param fromId the settlement id of the start
     * @param toId   the settlement id of the end
     * @param status the state of the route
     * @return the payload, with no progress, no relief and no more data
     */
    public static RoutePayload empty(String fromId, String toId, Status status) {
        return new RoutePayload(fromId, toId, status, false, List.of(), List.of(), null, null, false);
    }

    /** The same payload with a progress, a relief and the flag "more". */
    public RoutePayload with(@Nullable Progress newProgress, @Nullable Relief newRelief, boolean newMore) {
        return new RoutePayload(fromId, toId, status, detailed, points, surfaces, newProgress, newRelief, newMore);
    }

    /** The route of the payload, or an empty value if the payload has no route. */
    public Optional<Route> route() {
        return points.size() >= 2 && surfaces.size() == points.size() - 1
                ? Optional.of(new Route(points, surfaces)) : Optional.empty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RoutePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.fromId);
                buf.writeUtf(payload.toId);
                buf.writeEnum(payload.status);
                buf.writeBoolean(payload.detailed);
                buf.writeVarInt(payload.points.size());
                for (Route.Point point : payload.points) {
                    buf.writeVarInt(point.x());
                    buf.writeVarInt(point.z());
                }
                buf.writeVarInt(payload.surfaces.size());
                for (Route.Surface surface : payload.surfaces) {
                    buf.writeByte(surface.code());
                }
                buf.writeBoolean(payload.progress != null);
                if (payload.progress != null) {
                    buf.writeBoolean(payload.progress.detail());
                    buf.writeVarInt(payload.progress.ahead());
                    buf.writeVarInt(payload.progress.examined());
                    buf.writeVarInt(payload.progress.budget());
                    buf.writeVarInt(payload.progress.percent());
                }
                buf.writeBoolean(payload.relief != null);
                if (payload.relief != null) {
                    payload.relief.write(buf);
                }
                buf.writeBoolean(payload.more);
            },
            buf -> {
                String fromId = buf.readUtf();
                String toId = buf.readUtf();
                Status status = buf.readEnum(Status.class);
                boolean detailed = buf.readBoolean();
                int pointCount = buf.readVarInt();
                List<Route.Point> points = new ArrayList<>(pointCount);
                for (int i = 0; i < pointCount; i++) {
                    points.add(new Route.Point(buf.readVarInt(), buf.readVarInt()));
                }
                int surfaceCount = buf.readVarInt();
                List<Route.Surface> surfaces = new ArrayList<>(surfaceCount);
                for (int i = 0; i < surfaceCount; i++) {
                    surfaces.add(Route.Surface.fromCode(buf.readByte()));
                }
                Progress progress = buf.readBoolean() ? new Progress(buf.readBoolean(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) : null;
                Relief relief = buf.readBoolean() ? Relief.read(buf) : null;
                boolean more = buf.readBoolean();
                return new RoutePayload(fromId, toId, status, detailed, points, surfaces, progress, relief, more);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
