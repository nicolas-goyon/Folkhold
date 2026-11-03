package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.world.admin.AdminActions.Result;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The teleports of the admin screen and of the operator commands. The server thread does not wait for the chunks of
 * the target.
 *
 * <p>The target of a teleport comes from the blocks of an area, for example a safe position near a village heart.
 * When all the chunks of the area are ready ({@link ReadyChunks}), the teleport occurs at once. Else:
 * <ol>
 *   <li>The request adds a chunk ticket to each chunk of the area and asks for the futures of these chunks. The player
 *       gets the message {@code teleport.loading}. The server thread does not wait.</li>
 *   <li>When all the futures are complete, the server thread checks the player again: the player is online, is in the
 *       same dimension as at the request, and is an operator. Then the server finds the target in the ready chunks
 *       and teleports the player.</li>
 *   <li>If the futures are not complete after {@link #TIMEOUT_TICKS}, the request stops with a message.</li>
 * </ol>
 *
 * <p>A player has a maximum of one request. A new request of the player cancels the request before it. A request
 * releases its tickets in all cases: teleport, time limit, error, logout of the player, and stop of the server.
 *
 * <p>All the methods run on the server thread.
 */
public final class AdminTeleports {
    /** The maximum time from the request to the teleport, in ticks. */
    public static final int TIMEOUT_TICKS = 30 * SharedConstants.TICKS_PER_SECOND;

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
            List<ChunkPos> chunks, Function<ServerLevel, BlockPos> target, Component done, int timeoutTicks,
            int deadline) {
    }

    /**
     * Teleports a player to a target in an area, with the default time limit. See {@link #request(ServerPlayer,
     * ServerLevel, Area, Function, Component, int)}.
     */
    public static Result request(ServerPlayer player, ServerLevel level, Area area,
            Function<ServerLevel, BlockPos> target, Component done) {
        return request(player, level, area, target, done, TIMEOUT_TICKS);
    }

    /**
     * Teleports a player to a target in an area. If the chunks of the area are ready, the teleport occurs at once.
     * Else the request waits for the chunks with no wait on the server thread, and the player gets the result later
     * (see {@link AdminActions#sendResult}). This request cancels the request of the player before it.
     *
     * @param player       the player
     * @param level        the level of the target
     * @param area         the area with all the blocks that the target function reads
     * @param target       finds the position of the feet of the player in the ready chunks of the area
     * @param done         the message after the teleport
     * @param timeoutTicks the maximum time from the request to the teleport, in ticks
     * @return the result for the player now: the teleport, or the message {@code teleport.loading}
     */
    public static Result request(ServerPlayer player, ServerLevel level, Area area,
            Function<ServerLevel, BlockPos> target, Component done, int timeoutTicks) {
        cancel(player.getUUID());
        if (area.isReady(level)) {
            teleport(player, level, target.apply(level));
            return new Result(done, true);
        }
        MinecraftServer server = level.getServer();
        Request request = new Request(nextId++, player.getUUID(), player.level().dimension(), level, area,
                area.chunks(), target, done, timeoutTicks, server.getTickCount() + timeoutTicks);
        REQUESTS.put(request.player(), request);
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
        for (ChunkPos chunk : request.chunks()) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, request.id());
            futures.add(chunkFuture(level, chunk));
        }
        // The completion reports its result to the server thread: the future is not needed.
        var unused = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    if (!server.isStopped()) {
                        server.execute(() -> complete(request, error == null && futures.stream()
                                .map(future -> future.getNow(null))
                                .allMatch(result -> result != null && result.isSuccess())));
                    }
                });
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

    /**
     * The future of a chunk at the full status. {@code ServerChunkCache.getChunkFuture} waits for the chunk when
     * the caller is the server thread. A background thread calls it, and the method gives the future at once.
     */
    private static CompletableFuture<ChunkResult<ChunkAccess>> chunkFuture(ServerLevel level, ChunkPos chunk) {
        CompletableFuture<ChunkResult<ChunkAccess>> future = new CompletableFuture<>();
        Util.backgroundExecutor().execute(() -> {
            // The completion copies the result to the returned future: the future of whenComplete is not needed.
            var unused = level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(result);
                        }
                    });
        });
        return future;
    }

    /** Teleports the player when the futures of the request are complete. */
    private static void complete(Request request, boolean loaded) {
        MinecraftServer server = request.level().getServer();
        if (!server.isSameThread() || REQUESTS.get(request.player()) != request) {
            // The request stopped before: logout, time limit, new request or stop of the server.
            return;
        }
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(request.player());
            if (player == null) {
                return;
            }
            if (server.getTickCount() >= request.deadline()) {
                AdminActions.sendResult(player, timeout(request));
            } else if (!loaded || !request.area().isReady(request.level())) {
                AdminActions.sendResult(player, refused("error"));
            } else if (!AdminActions.isAllowed(player)) {
                AdminActions.sendResult(player, new Result(Component.translatable(
                        "screen.folkhold_world.admin.denied"), false));
            } else if (!player.level().dimension().equals(request.dimension())) {
                AdminActions.sendResult(player, refused("dimension"));
            } else {
                teleport(player, request.level(), request.target().apply(request.level()));
                AdminActions.sendResult(player, new Result(request.done(), true));
            }
        } finally {
            release(request);
        }
    }

    /** Stops the requests after their time limit. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (REQUESTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Request request : List.copyOf(REQUESTS.values())) {
            if (server.getTickCount() >= request.deadline()) {
                release(request);
                ServerPlayer player = server.getPlayerList().getPlayer(request.player());
                if (player != null) {
                    AdminActions.sendResult(player, timeout(request));
                }
            }
        }
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

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos feet) {
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, player.getYRot(),
                player.getXRot());
    }

    private static Result timeout(Request request) {
        return new Result(Component.translatable(KEY + "timeout",
                request.timeoutTicks() / SharedConstants.TICKS_PER_SECOND), false);
    }

    private static Result refused(String key) {
        return new Result(Component.translatable(KEY + key), false);
    }
}
