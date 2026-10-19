package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MountHorseGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.RiderMeleeGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.RiderPatrolGoal;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;

/**
 * Rider: a mounted unit of the garrison. It rides a faction horse ({@link FactionHorseEntity}) and carries a sword.
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>On its horse, it patrols on a ring around its village, outside the walls ({@link WorldConfig#RIDER_PATROL_RADIUS}).
 *       The ring has {@link #PATROL_POINTS} waypoints. The gates of the village open for it.</li>
 *   <li>It charges the threats near the village at a gallop, with the rules of the guards, then it goes back to its
 *       patrol.</li>
 *   <li>When its horse dies, it fights on foot, as a guard. It gets a new horse after the refill delay of the garrison
 *       ({@link WorldConfig#GARRISON_REFILL_DELAY_SECONDS}), while its village is calm. A new rider gets its horse at
 *       once.</li>
 *   <li>When it is on foot and its horse is alive and near, it goes back to its horse.</li>
 *   <li>It keeps the id of its horse. As for the units of a garrison, a horse in a chunk that is not loaded is still
 *       alive: the rider gets a new horse only when its horse dies, or when the horse is missing for
 *       {@link #MISSING_HORSE_TICKS} while the chunk of its last known position is loaded.</li>
 * </ul>
 */
public class RiderEntity extends FactionUnit {
    /** The number of waypoints of the patrol ring. */
    public static final int PATROL_POINTS = 8;
    /** A horse that is missing for 5 minutes in a loaded chunk is lost. */
    static final long MISSING_HORSE_TICKS = 5 * 60 * 20;
    /** The rider checks its horse at this interval, in ticks. */
    private static final int HORSE_CHECK_INTERVAL = 20;
    /** The value of {@link #horseLostSince} for a rider that never had a horse. */
    private static final long NEVER = Long.MIN_VALUE;
    private static final String HORSE_TAG = "Horse";
    private static final String HORSE_POS_TAG = "HorsePos";
    private static final String HORSE_SEEN_TAG = "HorseSeen";
    private static final String HORSE_LOST_TAG = "HorseLostSince";
    private static final RawAnimation RIDE = RawAnimation.begin().thenLoop("move.ride");
    private static final RawAnimation RIDE_CHARGE = RawAnimation.begin().thenLoop("move.ride_charge");

    @Nullable
    private UUID horseId;
    @Nullable
    private BlockPos horseLastPos;
    private long horseLastSeen;
    /** The game time when the rider lost its horse, or {@link #NEVER}. */
    private long horseLostSince = NEVER;
    /** The index of the current waypoint of the patrol, or -1 before the first patrol. Not saved. */
    private int patrolIndex = -1;
    /** True while the patrol goal runs. Not saved. */
    private boolean patrolling;

    public RiderEntity(EntityType<? extends RiderEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.RIDER;
    }

    @Override
    public GarrisonRole role() {
        return GarrisonRole.RIDER;
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new RiderMeleeGoal(this, true));
        goalSelector.addGoal(2, new RiderMeleeGoal(this, false));
        goalSelector.addGoal(3, new MountHorseGoal(this));
        goalSelector.addGoal(3, new RiderPatrolGoal(this));
    }

    /**
     * On a horse, the rider sits: the riding pose, or the charge pose with the sword forward when it attacks. On foot,
     * it walks as the other units.
     */
    @Override
    protected AnimationController<FactionUnit> movementController() {
        return new AnimationController<>(this, "movement", 4, state -> {
            if (isPassenger()) {
                return state.setAndContinue(isAggressive() ? RIDE_CHARGE : RIDE);
            }
            return state.setAndContinue(state.isMoving() ? DefaultAnimations.WALK : DefaultAnimations.IDLE);
        });
    }

    /** The horse of the rider, if it is loaded and alive. */
    @Nullable
    public FactionHorseEntity getHorse() {
        if (horseId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(horseId) instanceof FactionHorseEntity horse && horse.isAlive() ? horse : null;
    }

    /** The id of the horse of the rider, or null when the rider has no horse. */
    @Nullable
    public UUID getHorseId() {
        return horseId;
    }

    /** True if the village of the rider is calm. A rider with no village is calm. */
    public boolean isVillageCalm() {
        return getVillageHeart().map(heart -> heart.getAlertState() == AlertState.CALM).orElse(true);
    }

    /**
     * Records the loss of the horse: the rider gets a new horse after the refill delay.
     *
     * @param now the current game time
     */
    public void onHorseLost(long now) {
        horseId = null;
        horseLastPos = null;
        horseLostSince = now;
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
        // A new rider looks for a place for its horse on each tick of its first second.
        boolean newRider = horseLostSince == NEVER && horseId == null && tickCount < HORSE_CHECK_INTERVAL;
        if (newRider || tickCount % HORSE_CHECK_INTERVAL == 0) {
            updateHorse(level().getGameTime());
        }
    }

    /**
     * Follows the horse of the rider, and gives a new horse to a rider with no horse (see the class description).
     *
     * @param now the current game time
     */
    public void updateHorse(long now) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (getVehicle() instanceof FactionHorseEntity horse) {
            horseId = horse.getUUID();
            horseLastPos = horse.blockPosition();
            horseLastSeen = now;
            return;
        }
        if (horseId != null) {
            Entity horse = serverLevel.getEntity(horseId);
            if (horse != null && horse.isAlive()) {
                horseLastPos = horse.blockPosition();
                horseLastSeen = now;
            } else if (horse != null || ((horseLastPos == null || serverLevel.isLoaded(horseLastPos))
                    && now - horseLastSeen > MISSING_HORSE_TICKS)) {
                onHorseLost(now);
            }
            return;
        }
        long refillDelay = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        if (horseLostSince == NEVER || (now - horseLostSince >= refillDelay && isVillageCalm())) {
            spawnHorse(serverLevel, now);
        }
    }

    /** Gives a new horse to the rider, at a free position next to it, and puts the rider on it. */
    private void spawnHorse(ServerLevel serverLevel, long now) {
        BlockPos pos = horseSpawnPos();
        if (pos == null) {
            return;
        }
        FactionHorseEntity horse = ModEntities.FACTION_HORSE.get().create(serverLevel);
        if (horse == null) {
            return;
        }
        horse.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, getYRot(), 0.0F);
        EventHooks.finalizeMobSpawn(horse, serverLevel, serverLevel.getCurrentDifficultyAt(pos),
                MobSpawnType.MOB_SUMMONED, null);
        horse.prepareFor(this);
        serverLevel.addFreshEntity(horse);
        startRiding(horse, true);
        horseId = horse.getUUID();
        horseLastPos = horse.blockPosition();
        horseLastSeen = now;
    }

    /** The position of the rider, or a position next to it, where a horse fits. Null if there is none. */
    @Nullable
    private BlockPos horseSpawnPos() {
        EntityType<FactionHorseEntity> type = ModEntities.FACTION_HORSE.get();
        BlockPos origin = blockPosition();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (fitsAt(type, level(), pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The current waypoint of the patrol: a point of the patrol ring around the village heart (around the home for a
     * rider with no village), on the ground. A point in a chunk that is not ready, or in a fluid, is not a waypoint:
     * the patrol goes to the next point.
     *
     * @return the waypoint, or null if the ring has no waypoint
     */
    @Nullable
    public BlockPos patrolWaypoint() {
        if (patrolIndex < 0) {
            patrolIndex = Math.floorMod(getUUID().hashCode(), PATROL_POINTS);
        }
        BlockPos center = getVillagePos() != null ? getVillagePos() : getHomePos();
        int radius = WorldConfig.RIDER_PATROL_RADIUS.get();
        for (int tries = 0; tries < PATROL_POINTS; tries++) {
            float angle = (float) (2.0 * Math.PI * patrolIndex / PATROL_POINTS);
            int x = center.getX() + Math.round(Mth.cos(angle) * radius);
            int z = center.getZ() + Math.round(Mth.sin(angle) * radius);
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (ReadyChunks.isReady(level(), column)) {
                BlockPos ground = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (level().getFluidState(ground).isEmpty() && level().getFluidState(ground.below()).isEmpty()) {
                    return ground;
                }
            }
            nextPatrolWaypoint();
        }
        return null;
    }

    /** The patrol goes on to the next waypoint of the ring. */
    public void nextPatrolWaypoint() {
        patrolIndex = (Math.max(patrolIndex, 0) + 1) % PATROL_POINTS;
    }

    /** True while the rider patrols on its horse (see {@link RiderPatrolGoal}). */
    public boolean isPatrolling() {
        return patrolling;
    }

    /** The patrol goal sets this value when it starts and when it stops. */
    public void setPatrolling(boolean patrolling) {
        this.patrolling = patrolling;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (horseId != null) {
            tag.putUUID(HORSE_TAG, horseId);
        }
        if (horseLastPos != null) {
            tag.put(HORSE_POS_TAG, NbtUtils.writeBlockPos(horseLastPos));
        }
        tag.putLong(HORSE_SEEN_TAG, horseLastSeen);
        tag.putLong(HORSE_LOST_TAG, horseLostSince);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        horseId = tag.hasUUID(HORSE_TAG) ? tag.getUUID(HORSE_TAG) : null;
        horseLastPos = NbtUtils.readBlockPos(tag, HORSE_POS_TAG).orElse(null);
        horseLastSeen = tag.getLong(HORSE_SEEN_TAG);
        horseLostSince = tag.contains(HORSE_LOST_TAG) ? tag.getLong(HORSE_LOST_TAG) : NEVER;
    }
}
