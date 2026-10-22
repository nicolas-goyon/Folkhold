package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MarchOnVillageGoal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The vanilla raids against the faction villages. The vanilla raid stays: these rules change where its raiders spawn
 * and how they march on the village.
 *
 * <ul>
 *   <li>A raid is at a faction village when a loaded village heart is {@link #HEART_RADIUS} blocks or less from the
 *       center of the raid.</li>
 *   <li>Spawn: the raiders of a new wave of such a raid spawn at a position of {@link RaidSpawns}: on natural ground,
 *       on land, under the open sky, not in a hole or a ravine, with a path to the village. All the raiders of a wave
 *       spawn at the same position. When no position is good, the raiders keep the vanilla position.</li>
 *   <li>March: each raider gets a {@link MarchOnVillageGoal}. The goal works only in a raid at a faction village. The
 *       raider walks to the approach of the nearest gate, then through the gate to the heart. It crosses the
 *       rivers.</li>
 * </ul>
 */
public final class VillageRaids {
    /** The maximum distance from the center of a raid to the heart of its village, in blocks. */
    public static final int HEART_RADIUS = 64;
    /** The priority of the march: before the vanilla march of the raid (3). */
    private static final int MARCH_PRIORITY = 2;
    /** The approaches of a village stay in the cache for this time, in ticks. */
    private static final long APPROACH_CACHE_TICKS = 100;
    /** The cache of the spawn positions keeps the raids of this count, at most. */
    private static final int MAX_RAIDS = 64;

    /** The spawn position of the current wave of each raid. */
    private static final Map<RaidKey, WaveSpawn> WAVE_SPAWNS = new HashMap<>();
    /** The approaches of the villages, with the game time of their calculation. */
    private static final Map<HeartKey, CachedApproaches> APPROACHES = new HashMap<>();

    private record RaidKey(ResourceKey<Level> dimension, int id) {
    }

    private record WaveSpawn(int wave, Optional<BlockPos> pos) {
    }

    private record HeartKey(ResourceKey<Level> dimension, BlockPos heart) {
    }

    private record CachedApproaches(long time, List<VillageExits.Approach> approaches) {
    }

    private VillageRaids() {
    }

    /**
     * The heart of the village of a raid.
     *
     * @param raid  the raid
     * @param level the level of the raid
     * @return the position of the loaded heart that is the nearest to the center of the raid, or empty if the raid is
     *         not at a faction village
     */
    public static Optional<BlockPos> heartOf(Raid raid, Level level) {
        return VillageHearts.findNearest(level, raid.getCenter(), HEART_RADIUS);
    }

    /**
     * The approaches of the gates of a village (see {@link VillageExits#approaches}). The method keeps them in a
     * cache for {@link #APPROACH_CACHE_TICKS} ticks.
     *
     * @param level the level
     * @param heart the position of the heart
     * @return the approaches, or an empty list if the heart is not loaded or has no gates
     */
    public static List<VillageExits.Approach> approaches(ServerLevel level, BlockPos heart) {
        HeartKey key = new HeartKey(level.dimension(), heart.immutable());
        CachedApproaches cached = APPROACHES.get(key);
        long now = level.getGameTime();
        if (cached != null && now - cached.time() < APPROACH_CACHE_TICKS) {
            return cached.approaches();
        }
        List<VillageExits.Approach> approaches = level.getBlockEntity(heart) instanceof VillageHeartBlockEntity entity
                ? VillageExits.approaches(level, heart, entity.getLayout().gates())
                : List.of();
        APPROACHES.put(key, new CachedApproaches(now, approaches));
        return approaches;
    }

    /**
     * Gives each raider its march, and moves the raiders of a new wave of a raid at a faction village to the spawn
     * position of the wave.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Raider raider)) {
            return;
        }
        boolean marches = raider.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof MarchOnVillageGoal);
        if (!marches) {
            raider.goalSelector.addGoal(MARCH_PRIORITY, new MarchOnVillageGoal(raider));
        }
        Raid raid = raider.getCurrentRaid();
        if (event.loadedFromDisk() || raid == null) {
            return;
        }
        heartOf(raid, level).flatMap(heart -> waveSpawn(level, raid, raider.getWave(), heart)).ifPresent(pos ->
                raider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, raider.getYRot(), raider.getXRot()));
    }

    /** The spawn position of a wave of a raid. The first raider of the wave calculates it. */
    private static Optional<BlockPos> waveSpawn(ServerLevel level, Raid raid, int wave, BlockPos heart) {
        RaidKey key = new RaidKey(level.dimension(), raid.getId());
        WaveSpawn spawn = WAVE_SPAWNS.get(key);
        if (spawn == null || spawn.wave() != wave) {
            if (WAVE_SPAWNS.size() >= MAX_RAIDS) {
                WAVE_SPAWNS.clear();
            }
            List<BlockPos> points = approaches(level, heart).stream().map(VillageExits.Approach::point).toList();
            spawn = new WaveSpawn(wave, RaidSpawns.find(level, heart, points, level.getRandom()));
            WAVE_SPAWNS.put(key, spawn);
        }
        return spawn.pos();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        WAVE_SPAWNS.clear();
        APPROACHES.clear();
    }
}
