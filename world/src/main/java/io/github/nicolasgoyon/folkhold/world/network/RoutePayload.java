package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 */
public record RoutePayload(String fromId, String toId, Status status, boolean detailed, List<Route.Point> points,
        List<Route.Surface> surfaces) implements CustomPacketPayload {

    public static final Type<RoutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "route"));

    /** The state of a route. */
    public enum Status {
        /** The route is ready: the caravans take it. */
        READY,
        /** The server calculates the route. The payload can have the coarse route. The journal asks again later. */
        PENDING,
        /** The two villages have no route: the caravans do not go from one to the other. */
        NONE,
        /** The server does not show the route: a village is not in the journal of the player, or is not known. */
        REFUSED
    }

    /**
     * Makes a payload with a route.
     *
     * @param fromId   the settlement id of the start
     * @param toId     the settlement id of the end
     * @param status   the state of the route
     * @param detailed true for the detailed route
     * @param route    the route
     * @return the payload
     */
    public static RoutePayload of(String fromId, String toId, Status status, boolean detailed, Route route) {
        return new RoutePayload(fromId, toId, status, detailed, route.points(), route.surfaces());
    }

    /**
     * Makes a payload with no route.
     *
     * @param fromId the settlement id of the start
     * @param toId   the settlement id of the end
     * @param status the state of the route
     * @return the payload
     */
    public static RoutePayload empty(String fromId, String toId, Status status) {
        return new RoutePayload(fromId, toId, status, false, List.of(), List.of());
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
                return new RoutePayload(fromId, toId, status, detailed, points, surfaces);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
