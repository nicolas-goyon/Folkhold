package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Base class of the faction units (guard, archer, captain).
 *
 * <p>A unit has:
 * <ul>
 *   <li>a <b>home position</b>: center of the patrol and of the chase limit;</li>
 *   <li>a <b>village</b>: the position of its village heart, or none. The unit reports threats to the heart,
 *       and the heart alerts all units of the village;</li>
 *   <li>a <b>post</b> (optional): a tower or wall position. A unit with a post stays at its post.</li>
 * </ul>
 *
 * <p>The attributes come from the config of the unit type when the unit spawns.
 */
public abstract class FactionUnit extends PathfinderMob {
    /** Color of the tabard (dyed leather chestplate). The faction color replaces it in J2. */
    public static final int DEFAULT_TABARD_COLOR = 0x3C5A9A;

    /** Patrol radius at night, in blocks. */
    private static final int NIGHT_RADIUS = 4;
    private static final String HOME_TAG = "HomePos";
    private static final String VILLAGE_TAG = "VillagePos";
    private static final String POST_TAG = "PostPos";
    private static final String ROUTED_TAG = "RoutedUntil";
    /** Speed of a routed unit that runs back home. */
    private static final double ROUT_SPEED = 1.3;
    /** Radius around the post while the village is not calm, in blocks. */
    private static final int POST_RADIUS = 1;

    @Nullable
    private BlockPos homePos;
    @Nullable
    private BlockPos villagePos;
    @Nullable
    private BlockPos postPos;
    /** Game time until which the unit is routed (runs away and does not fight). */
    private long routedUntil;

    protected FactionUnit(EntityType<? extends FactionUnit> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /** The configured values of this unit type. */
    protected abstract UnitStats stats();

    /** Puts the equipment of this unit type in the equipment slots. */
    protected abstract void equip();

    /** The home position. The patrol and the chase limit use it. */
    public BlockPos getHomePos() {
        return homePos != null ? homePos : blockPosition();
    }

    public void setHomePos(BlockPos pos) {
        homePos = pos.immutable();
    }

    /** The position of the village heart, or null if the unit has no village. */
    @Nullable
    public BlockPos getVillagePos() {
        return villagePos;
    }

    public void setVillagePos(@Nullable BlockPos pos) {
        villagePos = pos == null ? null : pos.immutable();
    }

    /** The post of the unit (a tower or a wall position), or null. A unit with a post stays there. */
    @Nullable
    public BlockPos getPostPos() {
        return postPos;
    }

    public void setPostPos(@Nullable BlockPos pos) {
        postPos = pos == null ? null : pos.immutable();
    }

    /** True if the unit is routed: it runs back home and does not fight. */
    public boolean isRouted() {
        return level().getGameTime() < routedUntil;
    }

    /**
     * Routes the unit: it drops its target, runs back home and does not fight for the given time.
     *
     * @param ticks the duration in game ticks
     */
    public void rout(long ticks) {
        routedUntil = level().getGameTime() + ticks;
        super.setTarget(null);
    }

    /** A routed unit does not accept a target. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && isRouted()) {
            return;
        }
        super.setTarget(target);
    }

    /** True if the unit has a village and the village is not calm. */
    public boolean isVillageAlerted() {
        return getVillageHeart().map(heart -> heart.getAlertState() != AlertState.CALM).orElse(false);
    }

    /** The heart of the village of this unit, if the unit has a village and the heart is loaded. */
    public Optional<VillageHeartBlockEntity> getVillageHeart() {
        if (villagePos == null || !level().isLoaded(villagePos)) {
            return Optional.empty();
        }
        return level().getBlockEntity(villagePos) instanceof VillageHeartBlockEntity heart
                ? Optional.of(heart)
                : Optional.empty();
    }

    /**
     * Tells if the target is in the chase area: not farther than the chase radius from the home position.
     *
     * @param target the possible target
     * @return true if the unit can chase this target
     */
    public boolean canTargetFromHome(LivingEntity target) {
        double chase = WorldConfig.CHASE_RADIUS.get();
        return target.distanceToSqr(getHomePos().getCenter()) <= chase * chase;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (postPos != null) {
            restrictTo(postPos, POST_RADIUS);
        } else {
            int radius = level().isNight() ? NIGHT_RADIUS : WorldConfig.PATROL_RADIUS.get();
            restrictTo(getHomePos(), radius);
        }

        if (isRouted()) {
            if (getTarget() != null) {
                super.setTarget(null);
            }
            if (distanceToSqr(getHomePos().getCenter()) > 9.0 && getNavigation().isDone()) {
                BlockPos home = getHomePos();
                getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, ROUT_SPEED);
            }
            unitServerAiStep(null);
            return;
        }

        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !canTargetFromHome(target))) {
            setTarget(null);
            target = null;
        }
        if (target != null) {
            getVillageHeart().ifPresent(heart -> heart.reportThreat(null));
        }
        unitServerAiStep(target);
    }

    /**
     * Called on each server AI step, after the common logic.
     *
     * @param target the current target, or null
     */
    protected void unitServerAiStep(@Nullable LivingEntity target) {
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (homePos == null) {
            setHomePos(blockPosition());
        }
        if (villagePos == null) {
            VillageHearts.findNearest(level.getLevel(), blockPosition(), WorldConfig.VILLAGE_RADIUS.get())
                    .ifPresent(this::setVillagePos);
        }
        applyConfiguredAttributes(stats());
        equip();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
        return data;
    }

    /**
     * Sets the base attributes from the config and fills the health.
     *
     * @param stats the configured values of this unit type
     */
    public void applyConfiguredAttributes(UnitStats stats) {
        setBase(Attributes.MAX_HEALTH, stats.health());
        setBase(Attributes.ATTACK_DAMAGE, stats.attackDamage());
        setBase(Attributes.ARMOR, stats.armor());
        setBase(Attributes.MOVEMENT_SPEED, stats.movementSpeed());
        setBase(Attributes.FOLLOW_RANGE, stats.followRange());
        setHealth(getMaxHealth());
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!level().isClientSide()) {
            getVillageHeart().ifPresent(heart -> heart.onUnitDied(getUUID()));
        }
    }

    /** A leather chestplate in the given color. Units wear it as a tabard. */
    protected static ItemStack tabard(int color) {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Units do not pick up items. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.put(HOME_TAG, NbtUtils.writeBlockPos(homePos));
        }
        if (villagePos != null) {
            tag.put(VILLAGE_TAG, NbtUtils.writeBlockPos(villagePos));
        }
        if (postPos != null) {
            tag.put(POST_TAG, NbtUtils.writeBlockPos(postPos));
        }
        tag.putLong(ROUTED_TAG, routedUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, HOME_TAG).ifPresent(this::setHomePos);
        NbtUtils.readBlockPos(tag, VILLAGE_TAG).ifPresent(this::setVillagePos);
        NbtUtils.readBlockPos(tag, POST_TAG).ifPresent(this::setPostPos);
        routedUntil = tag.getLong(ROUTED_TAG);
    }
}
