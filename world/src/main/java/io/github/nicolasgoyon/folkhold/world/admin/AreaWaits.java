package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.world.admin.AdminActions.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The requests of an operator action that needs the chunks of an area, for example a teleport or a restore. The
 * server thread does not wait for the chunks.
 *
 * <p>When all the chunks of the area are ready ({@link AdminTeleports.Area#isReady}), the action occurs at once.
 * Else:
 * <ol>
 *   <li>The request adds a chunk ticket at the full status to each chunk of the area. The ticket alone starts the
 *       load or the generation of the chunk. The player gets the message {@code <key>loading}.</li>
 *   <li>At the end of each server tick, {@link #onServerTick} checks the area of each request. When the area is
 *       ready, the server checks the player again: the player is online and is an operator. Then the action
 *       occurs, and the player gets its result.</li>
 *   <li>If the area is not ready after the time limit, the request stops with the message {@code <key>timeout}. The
 *       time limit is in real time, not in ticks. The request checks the time limit before the area.</li>
 * </ol>
 *
 * <p>A player has a maximum of one request for each instance. A new request of the player cancels the request
 * before it. A request releases its tickets in all cases: action, time limit, logout of the player, and stop of the
 * server. The tickets are released before the action.
 *
 * <p>All the methods run on the server thread.
 */
public final class AreaWaits {
    /** The default maximum time from the request to the action, in seconds. */
    public static final int TIMEOUT_SECONDS = 30;

    /** The distance of the ticket: 0 gives the full status to the chunk of the ticket only. */
    private static final int TICKET_DISTANCE = 0;
    private static final List<AreaWaits> ALL = new ArrayList<>();
    private static long nextId;

    /** The ticket that keeps the chunks of a request at the full status. The value is the id of the request. */
    private final TicketType<Long> ticket;
    private final String key;
    private final Map<UUID, Request> requests = new HashMap<>();

    /** A request that waits for its chunks. */
    private record Request(long id, UUID player, ServerLevel level, AdminTeleports.Area area, List<ChunkPos> chunks,
            Function<ServerPlayer, Result> action, int timeoutSeconds, long deadline) {
    }

    /**
     * Makes the requests of one action. Make each instance one time, in a static field.
     *
     * @param ticketName the name of the chunk ticket
     * @param key        the prefix of the keys of the messages {@code loading} and {@code timeout}
     */
    AreaWaits(String ticketName, String key) {
        this.ticket = TicketType.create(ticketName, Long::compare);
        this.key = key;
        ALL.add(this);
    }

    /**
     * Does an action that reads or changes the blocks of an area. If the chunks of the area are ready, the action
     * occurs at once. Else the request waits for the chunks with no wait on the server thread, and the player gets
     * the result later (see {@link AdminActions#sendResult}). This request cancels the request of the player before
     * it.
     *
     * @param player         the player
     * @param level          the level of the area
     * @param area           the area with all the blocks that the action reads or changes
     * @param timeoutSeconds the maximum time from the request to the action, in seconds
     * @param action         does the action in the ready area and gives its result. It reads the current state of
     *                       its target, and it does not throw an exception.
     * @return the result for the player now: the result of the action, or the message {@code <key>loading}
     */
    Result request(ServerPlayer player, ServerLevel level, AdminTeleports.Area area, int timeoutSeconds,
            Function<ServerPlayer, Result> action) {
        cancel(player.getUUID());
        if (area.isReady(level)) {
            return action.apply(player);
        }
        Request request = new Request(nextId++, player.getUUID(), level, area, area.chunks(), action, timeoutSeconds,
                Util.getMillis() + timeoutSeconds * 1000L);
        requests.put(request.player(), request);
        for (ChunkPos chunk : request.chunks()) {
            level.getChunkSource().addRegionTicket(ticket, chunk, TICKET_DISTANCE, request.id());
        }
        return new Result(Component.translatable(key + "loading"), true);
    }

    /**
     * True if the player has a request that waits for its chunks.
     *
     * @param player the player
     * @return true if a request of the player waits
     */
    boolean isPending(ServerPlayer player) {
        return requests.containsKey(player.getUUID());
    }

    /** Does the requests with a ready area, and stops the requests after their time limit. */
    public static void onServerTick(ServerTickEvent.Post event) {
        for (AreaWaits waits : ALL) {
            waits.tick(event.getServer());
        }
    }

    /** Stops the requests of a player who logs out. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (AreaWaits waits : ALL) {
            waits.cancel(event.getEntity().getUUID());
        }
    }

    /** Stops all requests and releases their tickets. */
    public static void onServerStopping(ServerStoppingEvent event) {
        for (AreaWaits waits : ALL) {
            List.copyOf(waits.requests.values()).forEach(waits::release);
        }
    }

    private void tick(MinecraftServer server) {
        if (requests.isEmpty()) {
            return;
        }
        for (Request request : List.copyOf(requests.values())) {
            boolean timeout = Util.getMillis() >= request.deadline();
            if (timeout || request.area().isReady(request.level())) {
                release(request);
                ServerPlayer player = server.getPlayerList().getPlayer(request.player());
                if (player != null) {
                    AdminActions.sendResult(player, timeout ? timeout(request) : complete(request, player));
                }
            }
        }
    }

    /** The result of a request with a ready area: the action, or the refusal for a player who is not an operator. */
    private static Result complete(Request request, ServerPlayer player) {
        if (!AdminActions.isAllowed(player)) {
            return new Result(Component.translatable("screen.folkhold_world.admin.denied"), false);
        }
        return request.action().apply(player);
    }

    private Result timeout(Request request) {
        return new Result(Component.translatable(key + "timeout", request.timeoutSeconds()), false);
    }

    private void cancel(UUID player) {
        Request request = requests.get(player);
        if (request != null) {
            release(request);
        }
    }

    /** Removes the request and its tickets. */
    private void release(Request request) {
        requests.remove(request.player(), request);
        for (ChunkPos chunk : request.chunks()) {
            request.level().getChunkSource().removeRegionTicket(ticket, chunk, TICKET_DISTANCE, request.id());
        }
    }
}
