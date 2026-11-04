package io.github.nicolasgoyon.folkhold.world.admin;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions.Result;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The teleports of the admin screen and of the operator commands. The server thread does not wait for the chunks of
 * the target.
 *
 * <p>The target of a teleport comes from the blocks of an area, for example a safe position near a village heart.
 * When all the chunks of the area are ready ({@link ReadyChunks}), the teleport occurs at once. Else:
 * <ol>
 *   <li>The request adds a chunk ticket at the full status to each chunk of the area. The ticket alone starts the
 *       load or the generation of the chunk. The player gets the message {@code teleport.loading}. The server thread
 *       does not wait.</li>
 *   <li>At the end of each server tick, {@link #onServerTick} checks the area of each request. When the area is
 *       ready, the server checks the player again: the player is online, is an operator, and is in the same
 *       dimension as at the request. Then the server finds the target in the ready chunks and teleports the player.
 *       If the target is not in the world now (for example a removed settlement), the request stops with a
 *       message.</li>
 *   <li>If the area is not ready after {@link #TIMEOUT_SECONDS}, the request stops with a message. The time
 *       limit is in real time, not in ticks.</li>
 * </ol>
 *
 * <p>A player has a maximum of one request. A new request of the player cancels the request before it. A request
 * releases its tickets in all cases: teleport, time limit, error, logout of the player, and stop of the server.
 *
 * <p>All the methods run on the server thread.
 */
public final class AdminTeleports {
    /** The maximum time from the request to the teleport, in seconds. */
    public static final int TIMEOUT_SECONDS = 30;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "screen.folkhold_world.admin.teleport.";
    /** The ticket that keeps the chunks of a request at the full status. The value is the id of the request. */
    private static final TicketType<Long> TICKET = TicketType.create("folkhold_world_admin_teleport", Long::compare);
    /** The distance of the ticket: 0 gives the full status to the chunk of the ticket only. */
    private static final int TICKET_DISTANCE = 0;

    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static long nextId;

    private AdminTeleports() {
    }

    /**
     * An area of blocks in the horizontal plane.
     *
     * @param minX the lowest block X
     * @param minZ the lowest block Z
     * @param maxX the highest block X
     * @param maxZ the highest block Z
     */
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        /**
         * The square area around a position.
         *
         * @param x      block X of the center
         * @param z      block Z of the center
         * @param radius the distance from the center to each side, in blocks
         * @return the area
         */
        public static Area around(int x, int z, int radius) {
            return new Area(x - radius, z - radius, x + radius, z + radius);
        }

        /** The chunks with a part of the area. */
        public List<ChunkPos> chunks() {
            List<ChunkPos> chunks = new ArrayList<>();
            for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX);
                    chunkX++) {
                for (int chunkZ = SectionPos.blockToSectionCoord(minZ);
                        chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                    chunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
            return chunks;
        }

        boolean isReady(ServerLevel level) {
            return ReadyChunks.isAreaReady(level, minX, minZ, maxX, maxZ);
        }
    }

    /** A request that waits for its chunks. */
    private record Request(long id, UUID player, ResourceKey<Level> dimension, ServerLevel level, Area area,
            List<ChunkPos> chunks, Function<ServerLevel, Optional<BlockPos>> target, Component done,
            int timeoutSeconds, long deadline) {
    }

    /**
     * Teleports a player to a target in an area, with the default time limit. See {@link #request(ServerPlayer,
     * ServerLevel, Area, Function, Component, int)}.
     */
    public static Result request(ServerPlayer player, ServerLevel level, Area area,
            Function<ServerLevel, Optional<BlockPos>> target, Component done) {
        return request(player, level, area, target, done, TIMEOUT_SECONDS);
    }

    /**
     * Teleports a player to a target in an area. If the chunks of the area are ready, the teleport occurs at once.
     * Else the request waits for the chunks with no wait on the server thread, and the player gets the result later
     * (see {@link AdminActions#sendResult}). This request cancels the request of the player before it.
     *
     * <p>The target function reads the current state of the target, for example the settlement by its id. It gives
     * an empty result when the target is not in the world now.
     *
     * @param player         the player
     * @param level          the level of the target
     * @param area           the area with all the blocks that the target function reads
     * @param target         finds the position of the feet of the player in the ready chunks of the area, or empty
     * @param done           the message after the teleport
     * @param timeoutSeconds the maximum time from the request to the teleport, in seconds
     * @return the result for the player now: the teleport, or the message {@code teleport.loading}
     */
    public static Result request(ServerPlayer player, ServerLevel level, Area area,
            Function<ServerLevel, Optional<BlockPos>> target, Component done, int timeoutSeconds) {
        cancel(player.getUUID());
        if (area.isReady(level)) {
            return teleport(player, level, target, done);
        }
        Request request = new Request(nextId++, player.getUUID(), player.level().dimension(), level, area,
                area.chunks(), target, done, timeoutSeconds, Util.getMillis() + timeoutSeconds * 1000L);
        REQUESTS.put(request.player(), request);
        for (ChunkPos chunk : request.chunks()) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, request.id());
        }
        return new Result(Component.translatable(KEY + "loading"), true);
    }

    /**
     * True if the player has a request that waits for its chunks.
     *
     * @param player the player
     * @return true if a request of the player waits
     */
    public static boolean isPending(ServerPlayer player) {
        return REQUESTS.containsKey(player.getUUID());
    }

    /**
     * The surface at a position: the feet of the player are on the top block, leaves excluded. The chunk of the
     * position must be ready.
     *
     * @param level the level
     * @param x     block X
     * @param z     block Z
     * @return the position of the feet of the player
     */
    public static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Completes the requests with a ready area, and stops the requests after their time limit. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (REQUESTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Request request : List.copyOf(REQUESTS.values())) {
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

    /** The result of a request with a ready area: the teleport, or the reason of the refusal. */
    private static Result complete(Request request, ServerPlayer player) {
        if (!AdminActions.isAllowed(player)) {
            return new Result(Component.translatable("screen.folkhold_world.admin.denied"), false);
        }
        if (!player.level().dimension().equals(request.dimension())) {
            return refused("dimension");
        }
        return teleport(player, request.level(), request.target(), request.done());
    }

    /** Stops the request of a player who logs out. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity().getUUID());
    }

    /** Stops all requests and releases their tickets. */
    public static void onServerStopping(ServerStoppingEvent event) {
        List.copyOf(REQUESTS.values()).forEach(AdminTeleports::release);
    }

    private static void cancel(UUID player) {
        Request request = REQUESTS.get(player);
        if (request != null) {
            release(request);
        }
    }

    /** Removes the request and its tickets. */
    private static void release(Request request) {
        REQUESTS.remove(request.player(), request);
        for (ChunkPos chunk : request.chunks()) {
            request.level().getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_DISTANCE, request.id());
        }
    }

    /** Teleports the player to the target. The chunks of the area must be ready. */
    private static Result teleport(ServerPlayer player, ServerLevel level,
            Function<ServerLevel, Optional<BlockPos>> target, Component done) {
        try {
            Optional<BlockPos> feet = target.apply(level);
            if (feet.isEmpty()) {
                return refused("gone");
            }
            BlockPos pos = feet.get();
            player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(),
                    player.getXRot());
            return new Result(done, true);
        } catch (RuntimeException e) {
            LOGGER.error("The admin teleport of {} failed", player.getGameProfile().getName(), e);
            return refused("error");
        }
    }

    private static Result timeout(Request request) {
        return new Result(Component.translatable(KEY + "timeout",
                request.timeoutSeconds()), false);
    }

    private static Result refused(String key) {
        return new Result(Component.translatable(KEY + key), false);
    }
}
