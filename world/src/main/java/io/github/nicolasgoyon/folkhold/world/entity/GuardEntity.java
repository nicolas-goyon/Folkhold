package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.GuardHurtByTargetGoal;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Guard: basic defender of a faction village. It carries an iron sword and a shield.
 *
 * <p>The attributes come from {@link WorldConfig#GUARD} when the guard spawns.
 *
 * <p>Behavior:
 * <ul>
 *   <li>The guard has a home position. By day, it patrols in the patrol radius around the home position.
 *       At night, it stays near the home position.</li>
 *   <li>It attacks monsters in range, except creepers. It attacks any entity that attacks it, and alerts
 *       the other guards in the alert radius.</li>
 *   <li>It never chases a target that is farther than the chase radius from the home position.</li>
 *   <li>It raises its shield when the target is not in melee reach.</li>
 * </ul>
 */
public class GuardEntity extends PathfinderMob {

    /** Color of the tabard (dyed leather chestplate). The faction color replaces it in J2. */
    public static final int DEFAULT_TABARD_COLOR = 0x3C5A9A;

    /** Patrol radius at night, in blocks. */
    private static final int NIGHT_RADIUS = 4;
    /** Distance at which the guard lowers the shield to attack. */
    private static final double MELEE_REACH = 3.0;
    private static final String HOME_TAG = "HomePos";

    @Nullable
    private BlockPos homePos;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /** Default attributes. {@link #applyConfiguredAttributes(UnitStats)} replaces them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new GuardHurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper) && canTargetFromHome(target)));
    }

    /** The home position. The patrol and the chase limit use it. */
    public BlockPos getHomePos() {
        return homePos != null ? homePos : blockPosition();
    }

    public void setHomePos(BlockPos pos) {
        homePos = pos.immutable();
    }

    /**
     * Tells if the target is in the chase area: not farther than the chase radius from the home position.
     *
     * @param target the possible target
     * @return true if the guard can chase this target
     */
    public boolean canTargetFromHome(LivingEntity target) {
        double chase = WorldConfig.CHASE_RADIUS.get();
        return target.distanceToSqr(getHomePos().getCenter()) <= chase * chase;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        int radius = level().isNight() ? NIGHT_RADIUS : WorldConfig.PATROL_RADIUS.get();
        restrictTo(getHomePos(), radius);

        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !canTargetFromHome(target))) {
            setTarget(null);
            target = null;
        }
        updateShield(target);
    }

    private void updateShield(@Nullable LivingEntity target) {
        if (!getOffhandItem().is(Items.SHIELD)) {
            return;
        }
        boolean raise = target != null && distanceTo(target) > MELEE_REACH;
        if (raise && !isUsingItem()) {
            startUsingItem(InteractionHand.OFF_HAND);
        } else if (!raise && isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) {
            stopUsingItem();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.put(HOME_TAG, NbtUtils.writeBlockPos(homePos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, HOME_TAG).ifPresent(this::setHomePos);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (homePos == null) {
            setHomePos(blockPosition());
        }
        applyConfiguredAttributes(WorldConfig.GUARD);
        populateDefaultEquipmentSlots(getRandom(), difficulty);
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
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemSlot(EquipmentSlot.CHEST, tabard(DEFAULT_TABARD_COLOR));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    private static ItemStack tabard(int color) {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Guards do not pick up items. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }
}
