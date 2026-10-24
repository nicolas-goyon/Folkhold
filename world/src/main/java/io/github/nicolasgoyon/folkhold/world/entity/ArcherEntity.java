package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.CrossbowAttackGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.PostRangedAttackGoal;
import io.github.nicolasgoyon.folkhold.world.registry.ModSounds.UnitVoice;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;

/**
 * Archer: ranged defender of a faction village. It carries a bow or a crossbow (see the equipment sets in
 * {@code garrison_equipment.json}).
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>It shoots arrows at monsters in range and at attackers.</li>
 *   <li>When the archer has a post (a tower or a wall), it stays there: it shoots from the post and does not go to
 *       a target (see {@link PostRangedAttackGoal}). An archer with no post, for example in a caravan, goes to its
 *       target as a vanilla ranged mob.</li>
 *   <li>With a crossbow, the archer charges the crossbow, aims, and shoots a bolt (see {@link CrossbowAttackGoal}).
 *       The bolt is faster than an arrow of the bow, so it does more damage.</li>
 *   <li>The arrow damage comes from the "attackDamage" value of the archer config.</li>
 * </ul>
 */
public class ArcherEntity extends FactionUnit implements RangedAttackMob {
    private static final int ATTACK_INTERVAL = 30;
    private static final float ATTACK_RADIUS = 16.0F;
    /** The speed of a bolt of the crossbow. An arrow of the bow has the speed 1.6. */
    private static final float CROSSBOW_VELOCITY = 2.0F;
    /** True while the archer charges its crossbow. The clients use it for the animation. */
    private static final EntityDataAccessor<Boolean> CHARGING_CROSSBOW =
            SynchedEntityData.defineId(ArcherEntity.class, EntityDataSerializers.BOOLEAN);

    public ArcherEntity(EntityType<? extends ArcherEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING_CROSSBOW, false);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.ARCHER;
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new PostRangedAttackGoal(this, ATTACK_INTERVAL, ATTACK_RADIUS) {
            @Override
            public boolean canUse() {
                return holdsBow() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new CrossbowAttackGoal(this, true, ATTACK_RADIUS, 1.0));
        goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0, ATTACK_INTERVAL, ATTACK_RADIUS) {
            @Override
            public boolean canUse() {
                return getPostPos() == null && holdsBow() && super.canUse();
            }

            // The goal stops when the archer has no target. Else the vanilla goal can shoot until its path ends.
            @Override
            public boolean canContinueToUse() {
                return getPostPos() == null && holdsBow() && getTarget() != null && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new CrossbowAttackGoal(this, false, ATTACK_RADIUS, 1.0));
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
        // The renderer uses the aggressive flag for the bow pose and the aim of the crossbow.
        setAggressive(target != null);
    }

    /** True if the archer holds a bow in its main hand. */
    public boolean holdsBow() {
        return getMainHandItem().getItem() instanceof BowItem;
    }

    /** True if the archer holds a crossbow in its main hand. */
    public boolean holdsCrossbow() {
        return getMainHandItem().getItem() instanceof CrossbowItem;
    }

    /** True if the crossbow in the main hand holds a bolt. */
    public boolean isCrossbowCharged() {
        return CrossbowItem.isCharged(getMainHandItem());
    }

    @Override
    public boolean isChargingCrossbow() {
        return entityData.get(CHARGING_CROSSBOW);
    }

    /**
     * Charges the crossbow in the main hand, one tick. The archer uses the crossbow for the charge time of the
     * crossbow (25 ticks, less with Quick Charge), then the crossbow holds a bolt.
     */
    public void chargeCrossbow() {
        ItemStack crossbow = getMainHandItem();
        if (!isUsingItem()) {
            startUsingItem(InteractionHand.MAIN_HAND);
            entityData.set(CHARGING_CROSSBOW, true);
            return;
        }
        if (getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, this)) {
            stopUsingItem();
            entityData.set(CHARGING_CROSSBOW, false);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F,
                    1.0F / (getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    /** Stops a charge of the crossbow that is not complete. */
    public void stopChargingCrossbow() {
        if (isChargingCrossbow()) {
            stopUsingItem();
            entityData.set(CHARGING_CROSSBOW, false);
        }
    }

    /**
     * Shoots the bolt of the charged crossbow at a target. After the shot, the crossbow is empty, and the clients
     * show the recoil ({@link #CROSSBOW_SHOT}).
     *
     * @param target the target
     */
    public void shootCrossbow(LivingEntity target) {
        ItemStack crossbow = getMainHandItem();
        AbstractArrow bolt = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), 1.0F, crossbow);
        bolt.setBaseDamage(getAttributeValue(Attributes.ATTACK_DAMAGE));
        bolt.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
        aim(bolt, target, CROSSBOW_VELOCITY, 0.1);
        playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        playVoice(UnitVoice.ATTACK);
        level().addFreshEntity(bolt);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        triggerAnim(HOLD_CONTROLLER, CROSSBOW_SHOT);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item == Items.BOW));
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, velocity, bow);
        arrow.setBaseDamage(getAttributeValue(Attributes.ATTACK_DAMAGE));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        aim(arrow, target, 1.6F, 0.2);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        playVoice(UnitVoice.ATTACK);
        level().addFreshEntity(arrow);
    }

    /**
     * Shoots an arrow at a target, a little above it for the fall of the arrow.
     *
     * @param arrow    the arrow
     * @param target   the target
     * @param velocity the speed of the arrow
     * @param lift     the extra height of the aim for each block of horizontal distance
     */
    private void aim(AbstractArrow arrow, LivingEntity target, float velocity, double lift) {
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * lift, dz, velocity, 14 - level().getDifficulty().getId() * 4);
    }

    @Override
    public GarrisonRole role() {
        return GarrisonRole.ARCHER;
    }
}
