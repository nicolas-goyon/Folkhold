package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.AnchorStrollGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.PostRangedAttackGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.ReturnToAnchorGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitHurtByTargetGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UsePassageGoal;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Archer: ranged defender of a faction village. It carries a bow.
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>It shoots arrows at monsters in range and at attackers.</li>
 *   <li>When the archer has a post (a tower or a wall), it stays there: it shoots from the post and does not go to
 *       a target (see {@link PostRangedAttackGoal}). An archer with no post, for example in a caravan, goes to its
 *       target as a vanilla ranged mob.</li>
 *   <li>The arrow damage comes from the "attackDamage" value of the archer config.</li>
 * </ul>
 */
public class ArcherEntity extends FactionUnit implements RangedAttackMob {
    private static final int ATTACK_INTERVAL = 30;
    private static final float ATTACK_RADIUS = 16.0F;

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
    protected UnitStats stats() {
        return WorldConfig.ARCHER;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new UsePassageGoal(this));
        goalSelector.addGoal(2, new PostRangedAttackGoal(this, ATTACK_INTERVAL, ATTACK_RADIUS));
        goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0, ATTACK_INTERVAL, ATTACK_RADIUS) {
            @Override
            public boolean canUse() {
                return getPostPos() == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getPostPos() == null && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new ReturnToAnchorGoal(this, 1.0));
        goalSelector.addGoal(5, new AnchorStrollGoal(this, 0.6));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new UnitHurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> target instanceof Player player && canTargetFromHome(player) && isHostileTo(player)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper) && canTargetFromHome(target)));
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
        // The renderer uses the aggressive flag for the bow pose.
        setAggressive(target != null);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, velocity, bow);
        arrow.setBaseDamage(getAttributeValue(Attributes.ATTACK_DAMAGE));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, 14 - level().getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    protected void equip() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        setItemSlot(EquipmentSlot.CHEST, tabard(factionColor()));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
    }
}
