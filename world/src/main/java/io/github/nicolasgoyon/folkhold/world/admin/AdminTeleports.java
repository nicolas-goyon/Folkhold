package io.github.nicolasgoyon.folkhold.world.admin;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions.Result;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * The teleports of the admin screen and of the operator commands. The server thread does not wait for the chunks of
 * the target (see {@link AreaWaits}).
 *
 * <p>The target of a teleport comes from the blocks of an area, for example a safe position near a village heart.
 * When the area is ready, the server checks that the player is in the same dimension as at the request. Then the
 * server finds the target in the ready chunks and teleports the player. If the target is not in the world now (for
 * example a removed settlement), the request stops with a message.
 *
 * <p>All the methods run on the server thread.
 */
public final class AdminTeleports {
    /** The maximum time from the request to the teleport, in seconds. */
    public static final int TIMEOUT_SECONDS = AreaWaits.TIMEOUT_SECONDS;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "screen.folkhold_world.admin.teleport.";
    private static final AreaWaits WAITS = AreaWaits.TELEPORTS;

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
        ResourceKey<Level> dimension = player.level().dimension();
        return WAITS.request(player, level, area, timeoutSeconds, current -> current.level().dimension()
                .equals(dimension) ? teleport(current, level, target, done) : refused("dimension"));
    }

    /**
     * True if the player has a request that waits for its chunks.
     *
     * @param player the player
     * @return true if a request of the player waits
     */
    public static boolean isPending(ServerPlayer player) {
        return WAITS.isPending(player);
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

    private static Result refused(String key) {
        return new Result(Component.translatable(KEY + key), false);
    }
}
