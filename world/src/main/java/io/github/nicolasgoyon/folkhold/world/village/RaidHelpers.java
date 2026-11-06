package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The players who help a faction village during a raid. A raid is at a faction village as in {@link VillageRaids}.
 *
 * <p>Rule: a player helps when, during the raid (before its victory or its loss), the player is in the raid area
 * ({@link #RAID_AREA_RADIUS} blocks or less from the center of the raid) and hurts the raiders of the raid. The player
 * helps when one of these conditions is true:
 * <ul>
 *   <li>the player kills {@code raidRewards.minKills} raiders or more;</li>
 *   <li>the player gives {@code raidRewards.minDamage} damage or more to the raiders (0: the damage does not count).</li>
 * </ul>
 * A hit or a kill from a position outside the raid area does not count.
 *
 * <p>Reward: the vanilla raid gives the effect Hero of the Village to its heroes when it ends in a victory, with the
 * level of the raid. For a raid at a faction village, the heroes are the helpers only. Vanilla adds each player who
 * kills a raider to the heroes: this class removes the players who do not help. Thus a player gets the reward one
 * time, and a player who does not help gets no reward, also in the raid area.
 *
 * <p>The helpers of a raid stay in memory until the raid stops (vanilla stops a raid 30 seconds after its victory or
 * its loss). A server restart during a raid clears them.
 */
public final class RaidHelpers {
    /** The radius of the raid area, in blocks: vanilla finds the raid of a position at 96 blocks or less. */
    public static final double RAID_AREA_RADIUS = 96.0;
    /** The helpers stay in memory for this count of raids, at most. */
    private static final int MAX_RAIDS = 64;

    private static final Map<RaidKey, Tracked> RAIDS = new HashMap<>();

    private record RaidKey(ResourceKey<Level> dimension, int id) {
    }

    /** The help of one player during one raid. */
    private static final class Help {
        private int kills;
        private double damage;
    }

    private record Tracked(Raid raid, Map<UUID, Help> help) {
    }

    private RaidHelpers() {
    }

    /**
     * True when a player with these results helps.
     *
     * @param kills     the raiders that the player kills in the raid area
     * @param damage    the damage that the player gives to the raiders in the raid area
     * @param minKills  the minimum count of kills (1 or more)
     * @param minDamage the minimum damage, or 0 when the damage does not count
     * @return true when the player helps
     */
    public static boolean helps(int kills, double damage, int minKills, double minDamage) {
        return kills >= minKills || (minDamage > 0 && damage >= minDamage);
    }

    /**
     * True when a player helps the faction village of a raid.
     *
     * @param level  the level of the raid
     * @param raid   the raid
     * @param player the id of the player
     * @return true when the player helps; false when the raid is not at a faction village
     */
    public static boolean helps(ServerLevel level, Raid raid, UUID player) {
        Tracked tracked = RAIDS.get(key(level, raid));
        Help help = tracked == null ? null : tracked.help().get(player);
        return help != null && helps(help);
    }

    /**
     * The players who help the faction village of a raid.
     *
     * @param level the level of the raid
     * @param raid  the raid
     * @return the ids of the helpers; empty when the raid is not at a faction village
     */
    public static Set<UUID> helpers(ServerLevel level, Raid raid) {
        Tracked tracked = RAIDS.get(key(level, raid));
        if (tracked == null) {
            return Set.of();
        }
        return tracked.help().entrySet().stream().filter(entry -> helps(entry.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /** True when an entity is in the raid area: {@link #RAID_AREA_RADIUS} blocks or less from the center. */
    public static boolean inRaidArea(Raid raid, Entity entity) {
        return entity.blockPosition().distSqr(raid.getCenter()) <= RAID_AREA_RADIUS * RAID_AREA_RADIUS;
    }

    /**
     * Starts to record the helpers of a raid at a faction village. The first call sets the heroes of the raid to its
     * helpers (none).
     */
    public static void track(ServerLevel level, Raid raid) {
        tracked(level, raid);
    }

    private static Tracked tracked(ServerLevel level, Raid raid) {
        RaidKey key = key(level, raid);
        Tracked tracked = RAIDS.get(key);
        if (tracked == null || tracked.raid() != raid) {
            if (RAIDS.size() >= MAX_RAIDS) {
                RAIDS.clear();
            }
            tracked = new Tracked(raid, new HashMap<>());
            RAIDS.put(key, tracked);
            syncHeroes(level, tracked);
        }
        return tracked;
    }

    /** Records the damage and the kills of the players against the raiders of a raid at a faction village. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Raider raider) || !(raider.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Raid raid = raider.getCurrentRaid();
        if (raid == null || raid.isOver() || raid.isStopped() || !inRaidArea(raid, player)) {
            return;
        }
        Tracked tracked = RAIDS.get(key(level, raid));
        if (tracked == null || tracked.raid() != raid) {
            if (VillageRaids.heartOf(raid, level).isEmpty()) {
                return;
            }
            tracked = tracked(level, raid);
        }
        Help help = tracked.help().computeIfAbsent(player.getUUID(), id -> new Help());
        boolean before = helps(help);
        help.damage += event.getNewDamage();
        if (raider.isDeadOrDying()) {
            help.kills++;
        }
        if (helps(help) != before) {
            syncHeroes(level, tracked);
        }
    }

    /**
     * Removes the players who do not help from the heroes of the raids at the faction villages. Vanilla adds the
     * killer of a raider to the heroes before this event.
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Raider) || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        for (Tracked tracked : RAIDS.values()) {
            if (tracked.raid().getLevel() == level) {
                syncHeroes(level, tracked);
            }
        }
    }

    /** Removes the helpers of the raids that stop. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!RAIDS.isEmpty()) {
            RAIDS.values().removeIf(tracked -> tracked.raid().isStopped());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        RAIDS.clear();
    }

    /** Sets the heroes of a raid to its helpers: the vanilla victory gives the reward to the heroes only. */
    private static void syncHeroes(ServerLevel level, Tracked tracked) {
        Set<UUID> heroes = tracked.raid().heroesOfTheVillage;
        heroes.clear();
        heroes.addAll(helpers(level, tracked.raid()));
    }

    private static boolean helps(Help help) {
        return helps(help.kills, help.damage, WorldConfig.RAID_REWARD_MIN_KILLS.get(),
                WorldConfig.RAID_REWARD_MIN_DAMAGE.get());
    }

    private static RaidKey key(ServerLevel level, Raid raid) {
        return new RaidKey(level.dimension(), raid.getId());
    }
}
