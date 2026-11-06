package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
 * A hit or a kill from outside the raid area does not count. Only the damage source of a player counts, as in vanilla:
 * the projectiles of the player count, a tamed wolf and the fire do not.
 *
 * <p>Fall: the village of a raid falls when the raid starts and the heart is loaded with no living unit in its
 * garrison, when its settlement is ruins, or when its heart is broken. The check stops at the end of the raid (victory
 * or loss). The fall stays until the raid stops, also when units come back. The
 * village holds when the raid ends in a victory and the village did not fall ({@link VillageRaids#holds}). A broken
 * heart is a loaded position with no heart block entity: a heart that is loaded but not registered yet is not broken.
 *
 * <p>End: at the first server tick after the end of the raid, {@link RaidEffects} changes the settlement and the
 * reputation of the helpers, one time for each raid.
 *
 * <p>Reward: the vanilla raid gives the effect Hero of the Village to its heroes when it ends in a victory, with the
 * level of the raid. For a raid at a faction village, the heroes are the helpers only, and no player when the village
 * falls. Vanilla adds each player who kills a raider to the heroes: this class removes the players who do not help.
 * Thus a player gets the reward one time, and a player who does not help gets no reward, also in the raid area.
 *
 * <p>The helpers of a raid stay in memory until the raid stops (vanilla stops a raid 30 seconds after its victory or
 * its loss). A server restart during a raid clears them. The map keeps {@link #MAX_RAIDS} raids at most: when it is
 * full of active raids, a new raid gets no record and keeps the vanilla heroes.
 */
public final class RaidHelpers {
    /** The radius of the raid area, in blocks: vanilla finds the raid of a position at 96 blocks or less. */
    public static final double RAID_AREA_RADIUS = 96.0;
    /** The helpers stay in memory for this count of raids, at most. */
    private static final int MAX_RAIDS = 64;

    private static final Map<RaidKey, Tracked> RAIDS = new HashMap<>();
    /** True after the log of a full map, until the map has room again. */
    private static boolean warnedFull;

    private record RaidKey(ResourceKey<Level> dimension, int id) {
    }

    /** The help of one player during one raid. */
    private static final class Help {
        private int kills;
        private double damage;
    }

    /** The help of the players during one raid, and the fall of its village. */
    private static final class Tracked {
        private final Raid raid;
        private final Map<UUID, Help> help = new HashMap<>();
        private boolean fallen;
        private boolean heartBroken;
        /** True after {@link RaidEffects#apply} for this raid. */
        private boolean settled;
        /** The settlement of the village, after the first check of the fall. */
        @Nullable
        private String villageId;
        /** The heart of the village, after the first check of the fall. */
        @Nullable
        private BlockPos heartPos;

        private Tracked(Raid raid) {
            this.raid = raid;
        }
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
        Help help = tracked == null ? null : tracked.help.get(player);
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
        return tracked.help.entrySet().stream().filter(entry -> helps(entry.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * True when the village of a raid at a faction village falls (see the class comment).
     *
     * @param level the level of the raid
     * @param raid  the raid
     * @return true when the village falls; false when the raid is not at a faction village
     */
    public static boolean isFallen(ServerLevel level, Raid raid) {
        Tracked tracked = RAIDS.get(key(level, raid));
        return tracked != null && tracked.raid == raid && tracked.fallen;
    }

    /**
     * True after the effects of the end of a raid at a faction village (see {@link RaidEffects}).
     *
     * @param level the level of the raid
     * @param raid  the raid
     * @return true when the effects are applied; false when the raid is not at a faction village
     */
    public static boolean isSettled(ServerLevel level, Raid raid) {
        Tracked tracked = RAIDS.get(key(level, raid));
        return tracked != null && tracked.raid == raid && tracked.settled;
    }

    /**
     * Checks the fall of the village of a raid now. The server tick does this check at each tick.
     *
     * @param level the level of the raid
     * @param raid  the raid
     * @return true when the village falls; false when the raid is not at a faction village
     */
    public static boolean updateFall(ServerLevel level, Raid raid) {
        Tracked tracked = RAIDS.get(key(level, raid));
        return tracked != null && tracked.raid == raid && checkFall(level, tracked);
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

    /** The record of a raid, new or known. Null when the map is full of active raids. */
    @Nullable
    private static Tracked tracked(ServerLevel level, Raid raid) {
        RaidKey key = key(level, raid);
        Tracked tracked = RAIDS.get(key);
        if (tracked == null || tracked.raid != raid) {
            if (tracked == null && RAIDS.size() >= MAX_RAIDS) {
                RAIDS.values().removeIf(old -> old.raid.isStopped()
                        || level.getServer().getLevel(old.raid.getLevel().dimension()) != old.raid.getLevel());
                if (RAIDS.size() >= MAX_RAIDS) {
                    if (!warnedFull) {
                        warnedFull = true;
                        FolkholdWorld.LOGGER.warn("{} raids at faction villages are active: the raid {} gets no "
                                + "record of its helpers", MAX_RAIDS, raid.getId());
                    }
                    return null;
                }
            }
            warnedFull = false;
            tracked = new Tracked(raid);
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
        if (tracked == null || tracked.raid != raid) {
            if (VillageRaids.heartOf(raid, level).isEmpty()) {
                return;
            }
            tracked = tracked(level, raid);
            if (tracked == null) {
                return;
            }
        }
        Help help = tracked.help.computeIfAbsent(player.getUUID(), id -> new Help());
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
            if (tracked.raid.getLevel() == level) {
                checkFall(level, tracked);
                syncHeroes(level, tracked);
            }
        }
    }

    /**
     * Records the fall of the villages, applies the effects of the raids that end, and removes the helpers of the
     * raids that stop. Vanilla sets a victory 40 ticks after the death of the last raider: a fall before this death
     * stops the reward.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RAIDS.isEmpty()) {
            return;
        }
        for (Tracked tracked : RAIDS.values()) {
            if (!(tracked.raid.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            if (tracked.raid.isOver()) {
                if (!tracked.settled) {
                    tracked.settled = true;
                    RaidEffects.apply(level, tracked.raid, tracked.villageId, tracked.heartBroken,
                            helpers(level, tracked.raid));
                }
            } else if (!tracked.fallen && checkFall(level, tracked)) {
                syncHeroes(level, tracked);
            }
        }
        RAIDS.values().removeIf(tracked -> tracked.raid.isStopped());
    }

    /**
     * Sets the fall of the village of a raid, before the end of the raid: the raid started, and the village has no
     * living unit, is ruins, or has a broken heart (a loaded position with no heart).
     */
    private static boolean checkFall(ServerLevel level, Tracked tracked) {
        if (tracked.fallen || !tracked.raid.isStarted() || tracked.raid.isOver()) {
            return tracked.fallen;
        }
        Optional<BlockPos> pos = VillageRaids.heartOf(tracked.raid, level);
        if (pos.isPresent() && level.isLoaded(pos.get())
                && level.getBlockEntity(pos.get()) instanceof VillageHeartBlockEntity heart) {
            tracked.heartPos = pos.get();
            tracked.villageId = heart.getVillageId().toString();
            tracked.fallen = heart.getGarrison().aliveCount() == 0 || isRuins(level, heart.getVillageId().toString());
        } else if (tracked.heartPos != null && level.isLoaded(tracked.heartPos)
                && !(level.getBlockEntity(tracked.heartPos) instanceof VillageHeartBlockEntity)) {
            // The heart of the village is broken: the village is ruins, or becomes ruins when its captain dies. A heart
            // in a chunk that loads again can be missing from the registry for some ticks: it is not broken.
            tracked.fallen = true;
            tracked.heartBroken = true;
        }
        return tracked.fallen;
    }

    private static boolean isRuins(ServerLevel level, String villageId) {
        return SettlementData.get(level.getServer()).registry().byId(villageId)
                .filter(settlement -> settlement.state() == SettlementState.RUINS).isPresent();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        RAIDS.clear();
        warnedFull = false;
    }

    /**
     * Sets the heroes of a raid to its helpers, or to no player when the village falls: the vanilla victory gives the
     * reward to the heroes only.
     */
    private static void syncHeroes(ServerLevel level, Tracked tracked) {
        Set<UUID> heroes = tracked.raid.heroesOfTheVillage;
        heroes.clear();
        if (!tracked.fallen) {
            heroes.addAll(helpers(level, tracked.raid));
        }
    }

    private static boolean helps(Help help) {
        return helps(help.kills, help.damage, WorldConfig.RAID_REWARD_MIN_KILLS.get(),
                WorldConfig.RAID_REWARD_MIN_DAMAGE.get());
    }

    private static RaidKey key(ServerLevel level, Raid raid) {
        return new RaidKey(level.dimension(), raid.getId());
    }
}
