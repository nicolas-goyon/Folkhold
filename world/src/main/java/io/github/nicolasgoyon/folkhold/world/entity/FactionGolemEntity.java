package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModSounds;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Faction golem: the heavy unit of the large villages. It has the model and the animations of the vanilla iron golem,
 * with an armband in the color of its faction.
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>It guards an anchor: a post inside a gate of its village, or its home near the village heart. It goes back to
 *       its anchor after a fight.</li>
 *   <li>It attacks the monsters and the hostile players near its anchor, with the rules of the guards. It chases a
 *       target only in a small radius around its anchor ({@link WorldConfig#GOLEM_CHASE_RADIUS}).</li>
 *   <li>Its attack throws the target up, as the attack of the vanilla iron golem.</li>
 *   <li>It is very resistant and slow. It does not drown, does not run away and does not talk.</li>
 * </ul>
 */
public class FactionGolemEntity extends FactionUnit {
    private static final EntityDataAccessor<Integer> DATA_ARMBAND_COLOR =
            SynchedEntityData.defineId(FactionGolemEntity.class, EntityDataSerializers.INT);
    private static final String ARMBAND_COLOR_TAG = "ArmbandColor";
    /** The entity event of an attack: the client starts the attack animation. */
    private static final byte ATTACK_EVENT = 4;
    /** The duration of the attack animation, in ticks. */
    public static final int ATTACK_ANIMATION_TICKS = 10;
    /** The upward speed of a target that the golem hits, for a target with no knockback resistance. */
    private static final double THROW_UP_SPEED = 0.4;
    /** Above this horizontal speed (squared), the golem shows dust under its feet. */
    private static final double DUST_SPEED_SQR = 2.5E-7;

    private int attackAnimationTick;

    public FactionGolemEntity(EntityType<? extends FactionGolemEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /**
     * Tells if a golem fits at a position: a block with a collision shape is below the position, and no block is in
     * the space of the golem.
     *
     * @param level the level
     * @param pos   the position of the feet of the golem
     * @return true if a golem can stand at the position
     */
    public static boolean fitsAt(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.noCollision(ModEntities.FACTION_GOLEM.get().getSpawnAABB(pos.getX() + 0.5, pos.getY(),
                        pos.getZ() + 0.5));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARMBAND_COLOR, DEFAULT_TABARD_COLOR);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.GOLEM;
    }

    @Override
    public GarrisonRole role() {
        return GarrisonRole.GOLEM;
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
    }

    /** The chase area of a golem is around its anchor: its post at a gate, or its home. */
    @Override
    protected BlockPos chaseCenter() {
        return getAnchorPos();
    }

    @Override
    protected double chaseRadius() {
        return WorldConfig.GOLEM_CHASE_RADIUS.get();
    }

    @Override
    public boolean canSpeak() {
        return false;
    }

    /** A golem does not run away: the death of the captain does not rout it. */
    @Override
    public void rout(long ticks) {
    }

    @Override
    public void applyConfiguredAttributes(UnitStats stats) {
        super.applyConfiguredAttributes(stats);
        AttributeInstance knockback = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(WorldConfig.GOLEM_KNOCKBACK_RESISTANCE.get());
        }
    }

    /**
     * A golem has no equipment (the role {@code golem} of the equipment sets is empty). The armband takes the color of
     * the faction of the golem.
     */
    @Override
    public void equip(String set) {
        super.equip(set);
        entityData.set(DATA_ARMBAND_COLOR, factionColor());
    }

    /** The color of the armband: the color of the faction of the golem. The server sends it to the clients. */
    public int getArmbandColor() {
        return entityData.get(DATA_ARMBAND_COLOR);
    }

    /** The time left in the attack animation, in ticks. The value is 0 when the golem does not attack. */
    public int getAttackAnimationTick() {
        return attackAnimationTick;
    }

    /** The cracks of the golem, from its health, as the cracks of the vanilla iron golem. */
    public Crackiness.Level getCrackiness() {
        return Crackiness.GOLEM.byFraction(getHealth() / getMaxHealth());
    }

    /**
     * Hits the target. The damage is between half and one and a half of the attack damage. The target goes up, less
     * with a knockback resistance.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        attackAnimationTick = ATTACK_ANIMATION_TICKS;
        level().broadcastEntityEvent(this, ATTACK_EVENT);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        float amount = (int) damage > 0 ? damage / 2.0F + getRandom().nextInt((int) damage) : damage;
        DamageSource source = damageSources().mobAttack(this);
        boolean hurt = target.hurt(source, amount);
        if (hurt) {
            double resistance = target instanceof LivingEntity living
                    ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    : 0.0;
            target.setDeltaMovement(target.getDeltaMovement()
                    .add(0.0, THROW_UP_SPEED * Math.max(0.0, 1.0 - resistance), 0.0));
            if (level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
            }
        }
        playSound(ModSounds.FACTION_GOLEM_ATTACK.get(), 1.0F, 1.0F);
        return hurt;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == ATTACK_EVENT) {
            attackAnimationTick = ATTACK_ANIMATION_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (attackAnimationTick > 0) {
            attackAnimationTick--;
        }
    }

    /** The golem makes a sound when its cracks change, as the vanilla iron golem. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Crackiness.Level before = getCrackiness();
        boolean hurt = super.hurt(source, amount);
        if (hurt && getCrackiness() != before) {
            playSound(ModSounds.FACTION_GOLEM_DAMAGE.get(), 1.0F, 1.0F);
        }
        return hurt;
    }

    /** The golem does not drown. */
    @Override
    protected int decreaseAirSupply(int air) {
        return air;
    }

    /** The golem shows dust under its feet when it walks, as the vanilla iron golem. */
    @Override
    public boolean canSpawnSprintParticle() {
        return getDeltaMovement().horizontalDistanceSqr() > DUST_SPEED_SQR && getRandom().nextInt(5) == 0;
    }

    /** The golem makes no ambient sound. */
    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.FACTION_GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FACTION_GOLEM_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(ARMBAND_COLOR_TAG, getArmbandColor());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(ARMBAND_COLOR_TAG)) {
            entityData.set(DATA_ARMBAND_COLOR, tag.getInt(ARMBAND_COLOR_TAG));
        }
    }
}
