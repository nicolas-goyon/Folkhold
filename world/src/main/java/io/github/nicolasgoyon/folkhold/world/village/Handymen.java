package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;

/**
 * The handymen of a faction village: its villagers with the handyman profession (see {@link VillageFolk}). They light
 * the braziers of the village in the evening and put them out in the morning (see {@link VillageBraziers}).
 *
 * <ul>
 *   <li>The village heart gives the work at each check (see {@link #update}): the braziers to light or to put
 *       out.</li>
 *   <li>A handyman takes the nearest brazier that no other handyman took. It walks to a position next to the
 *       brazier, looks at the brazier, plays the animation of a use (see {@link AnimatedVillagers#use}), then the
 *       brazier changes.</li>
 *   <li>A handyman works when it is awake and calm: not in bed, not in the rest time of the villagers, not in a panic
 *       or a raid (see {@link HandymanWorker#isAvailable}). Thus the handymen light the braziers from
 *       {@link VillageBraziers#EVENING} to the rest time, and put them out from the end of the rest time to
 *       {@link VillageBraziers#MORNING_END}.</li>
 *   <li>A handyman that cannot go to a brazier stops. No handyman tries this brazier again in the same work, and the
 *       village heart changes it at the end of the period.</li>
 * </ul>
 *
 * <p>The work is not saved: after a load, the village heart gives it again.
 */
public final class Handymen {
    private final Map<UUID, HandymanWorker> workers = new HashMap<>();
    /** The braziers to change. */
    private final List<BlockPos> tasks = new ArrayList<>();
    /** The braziers that a handyman took, with the id of the handyman. */
    private final Map<BlockPos, UUID> claims = new HashMap<>();
    /** The braziers that a handyman could not reach during the current work. */
    private final Set<BlockPos> unreachable = new HashSet<>();
    /** The state of the braziers after the work: true to light them. Empty when there is no work. */
    private Optional<Boolean> lit = Optional.empty();

    /**
     * True if a village has handymen: they put out the braziers in the morning.
     *
     * @param level the level
     * @param heart the position of the village heart
     * @return true if the loaded entities of the level contain a handyman of the village
     */
    public static boolean exist(ServerLevel level, BlockPos heart) {
        return !VillageFolk.of(level, heart, ModProfessions.HANDYMAN.get()).isEmpty();
    }

    /**
     * Gives the work to the handymen of a village. The village heart calls it at each check.
     *
     * @param level    the level
     * @param heart    the position of the village heart
     * @param braziers the braziers of the village
     * @param lit      true to light the braziers, false to put them out, empty for no work
     */
    public void update(ServerLevel level, BlockPos heart, List<BlockPos> braziers, Optional<Boolean> lit) {
        if (!lit.equals(this.lit)) {
            // A new work: the handymen try all the braziers again.
            unreachable.clear();
            this.lit = lit;
        }
        tasks.clear();
        lit.ifPresent(on -> {
            for (BlockPos brazier : braziers) {
                if (!unreachable.contains(brazier) && ReadyChunks.isReady(level, brazier)
                        && VillageBraziers.needsChange(level.getBlockState(brazier), on)) {
                    tasks.add(brazier);
                }
            }
        });
        claims.keySet().retainAll(new HashSet<>(tasks));
        Set<UUID> available = new HashSet<>();
        if (!tasks.isEmpty()) {
            for (Villager villager : VillageFolk.of(level, heart, ModProfessions.HANDYMAN.get())) {
                if (HandymanWorker.isAvailable(villager)) {
                    available.add(villager.getUUID());
                    workers.computeIfAbsent(villager.getUUID(), HandymanWorker::new);
                }
            }
        }
        for (HandymanWorker worker : List.copyOf(workers.values())) {
            if (!available.contains(worker.id()) && !worker.isWorking()) {
                remove(level, worker);
            }
        }
    }

    /**
     * One tick of work of the handymen.
     *
     * @param level the level
     */
    public void tick(ServerLevel level) {
        for (HandymanWorker worker : List.copyOf(workers.values())) {
            if (level.getEntity(worker.id()) instanceof Villager villager && villager.isAlive()) {
                worker.tick(level, this, villager);
            } else {
                claims.values().removeIf(worker.id()::equals);
                workers.remove(worker.id());
            }
        }
    }

    /**
     * Stops the work: the handymen do the other activities of the villagers again.
     *
     * @param level the level
     */
    public void stop(ServerLevel level) {
        for (HandymanWorker worker : List.copyOf(workers.values())) {
            remove(level, worker);
        }
        tasks.clear();
        lit = Optional.empty();
    }

    private void remove(ServerLevel level, HandymanWorker worker) {
        if (level.getEntity(worker.id()) instanceof Villager villager) {
            worker.release(villager);
        }
        claims.values().removeIf(worker.id()::equals);
        workers.remove(worker.id());
    }

    /** The nearest brazier that no other handyman took, for a handyman. The handyman takes it. */
    @Nullable
    BlockPos claim(HandymanWorker worker, Villager villager) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos brazier : tasks) {
            double distance = brazier.distToCenterSqr(villager.position());
            if (!claims.containsKey(brazier) && distance < bestDistance) {
                best = brazier;
                bestDistance = distance;
            }
        }
        if (best != null) {
            claims.put(best, worker.id());
        }
        return best;
    }

    /** True if a handyman holds a brazier that the work must still change. */
    boolean holds(HandymanWorker worker, BlockPos brazier) {
        return worker.id().equals(claims.get(brazier));
    }

    /** No handyman tries this brazier again during the current work. */
    void markUnreachable(BlockPos brazier) {
        unreachable.add(brazier);
        tasks.remove(brazier);
        claims.remove(brazier);
    }

    /**
     * Changes a brazier of the work, with a sound. The handyman releases the brazier.
     *
     * @return true if the brazier changed
     */
    boolean apply(ServerLevel level, BlockPos brazier) {
        tasks.remove(brazier);
        claims.remove(brazier);
        if (lit.isEmpty() || !ReadyChunks.isReady(level, brazier) || !VillageBraziers.set(level, brazier, lit.get())) {
            return false;
        }
        if (lit.get()) {
            level.playSound(null, brazier, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
                    level.random.nextFloat() * 0.4F + 0.8F);
        } else {
            level.playSound(null, brazier, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F,
                    level.random.nextFloat() * 0.4F + 0.8F);
            level.sendParticles(ParticleTypes.SMOKE, brazier.getX() + 0.5, brazier.getY() + 1.4,
                    brazier.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
        }
        return true;
    }
}
