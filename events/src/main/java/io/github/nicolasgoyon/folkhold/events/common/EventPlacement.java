package io.github.nicolasgoyon.folkhold.events.common;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/** Finds the center of a natural event near a player. No chunk loads for this search. */
public final class EventPlacement {
    /** Attempts to find a center in a chunk that ticks near the player. */
    private static final int ATTEMPTS = 8;

    private EventPlacement() {
    }

    /**
     * A center near a random player of a level: a random column at {@code distance} blocks from the player or nearer,
     * in a loaded chunk that ticks. The center is on the top block of the column, with no leaves.
     *
     * @param level    the level
     * @param distance the largest horizontal distance from the player, in blocks
     * @return the center, or empty with no player alive in the level or no column in a chunk that ticks
     */
    public static Optional<BlockPos> nearRandomPlayer(ServerLevel level, int distance) {
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()).toList();
        if (players.isEmpty()) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();
        ServerPlayer player = players.get(random.nextInt(players.size()));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double length = random.nextDouble() * distance;
            int x = Mth.floor(player.getX() + Math.cos(angle) * length);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * length);
            if (BlockRules.isReady(level, new BlockPos(x, player.getBlockY(), z))) {
                return Optional.of(new BlockPos(x,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
            }
        }
        return Optional.empty();
    }
}
