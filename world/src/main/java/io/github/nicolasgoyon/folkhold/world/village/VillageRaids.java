package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MarchOnVillageGoal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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
 *   <li>A raid is at a faction village when a loaded village heart is {@link WorldConfig#RAID_HEART_RADIUS} blocks
 *       or less from the center of the raid.</li>
 *   <li>Spawn: the raiders of a new wave of such a raid spawn at a position of {@link RaidSpawns}: on natural ground,
 *       on land, under the open sky, not in a hole or a ravine, with a path to the village. All the raiders of a wave
 *       spawn at the same position. When no position is good, the raiders keep the vanilla position.</li>
 *   <li>March: each raider gets a {@link MarchOnVillageGoal}. The goal works only in a raid at a faction village. The
 *       raider walks to the approach of the nearest gate, then through the gate to the heart. It crosses the
 *       rivers.</li>
 *   <li>Waves: a raid of level 4 or 5 at a faction village gets more raiders. Each wave of the raid gets
 *       {@link #extraRavagers} ravagers more than the vanilla wave (see {@link #reinforce}). The vanilla raids at the
 *       other villages do not change.</li>
 *   <li>Rewards: only the players who help the village get the effect Hero of the Village after a victory (see
 *       {@link RaidHelpers}).</li>
 * </ul>
 *
 * <p>The numbers of these rules are in the section {@code raid} of {@link WorldConfig}.
 */
public final class VillageRaids {
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

    /** The waves that have their extra raiders, by raid. */
    private static final Map<RaidKey, Integer> REINFORCED_WAVES = new HashMap<>();

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
        return VillageHearts.findNearest(level, raid.getCenter(), WorldConfig.RAID_HEART_RADIUS.get());
    }

    /**
     * True when the village of a raid holds: the raid ends in a victory, and the village did not fall during the raid
     * ({@link RaidHelpers#isFallen}). The village of a raid at a vanilla village holds after a victory. Read the
     * result when the raid ends (victory or loss), not when it stops: the record of a raid stops with the raid, and
     * after that, this method gives the vanilla result.
     *
     * @param level the level of the raid
     * @param raid  the raid
     * @return true when the village holds
     */
    public static boolean holds(ServerLevel level, Raid raid) {
        return raid.isVictory() && !RaidHelpers.isFallen(level, raid);
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
     * position of the wave. A raid at a faction village records its helpers ({@link RaidHelpers}).
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
        if (raid == null) {
            return;
        }
        Optional<BlockPos> heart = heartOf(raid, level);
        if (heart.isEmpty()) {
            return;
        }
        RaidHelpers.track(level, raid);
        if (event.loadedFromDisk()) {
            return;
        }
        BlockPos vanillaPos = raider.blockPosition().below();
        waveSpawn(level, raid, raider.getWave(), heart.get()).ifPresent(pos ->
                raider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, raider.getYRot(), raider.getXRot()));
        reinforce(level, raid, raider.getWave(), vanillaPos);
    }

    /**
     * The ravagers that each wave of a raid at a faction village gets more than the vanilla wave. At the difficulty
     * Normal, a raid of level 2 to 5 has the same 6 vanilla waves: only the chance of enchanted weapons changes. These
     * ravagers make the raids of levels 4 and 5 stronger than the raids of levels 2 and 3 (balance targets in
     * {@code docs/design.md}).
     *
     * @param raidLevel the level of the raid (the Raid Omen level, 0 to 5)
     * @return the number of extra ravagers: {@link WorldConfig#RAID_EXTRA_RAVAGERS_LEVEL_4} at level 4,
     *         {@link WorldConfig#RAID_EXTRA_RAVAGERS_LEVEL_5} at level 5, else 0
     */
    public static int extraRavagers(int raidLevel) {
        return switch (raidLevel) {
            case 4 -> WorldConfig.RAID_EXTRA_RAVAGERS_LEVEL_4.get();
            case 5 -> WorldConfig.RAID_EXTRA_RAVAGERS_LEVEL_5.get();
            default -> 0;
        };
    }

    /**
     * Adds the extra ravagers of {@link #extraRavagers} to a new wave of a raid at a faction village. The first raider
     * of the wave adds them. The extra ravagers join the wave as the vanilla raiders: the wave ends when they are dead.
     * They spawn at the spawn position of the wave.
     */
    private static void reinforce(ServerLevel level, Raid raid, int wave, BlockPos vanillaPos) {
        RaidKey key = new RaidKey(level.dimension(), raid.getId());
        Integer done = REINFORCED_WAVES.get(key);
        if (done != null && done >= wave) {
            return;
        }
        if (REINFORCED_WAVES.size() >= MAX_RAIDS) {
            REINFORCED_WAVES.clear();
        }
        REINFORCED_WAVES.put(key, wave);
        for (int i = 0; i < extraRavagers(raid.getRaidOmenLevel()); i++) {
            Raider ravager = EntityType.RAVAGER.create(level);
            if (ravager == null) {
                return;
            }
            raid.joinRaid(wave, ravager, vanillaPos, false);
        }
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
        REINFORCED_WAVES.clear();
    }
}
