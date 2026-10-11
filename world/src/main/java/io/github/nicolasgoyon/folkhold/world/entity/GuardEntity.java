package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitHurtByTargetGoal;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.level.Level;

/**
 * Guard: basic defender of a faction village. It carries an iron sword and a shield.
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>By day, the guard patrols in the patrol radius around its home position.
 *       At night, it watches a gate of its village (if the village heart gives it a night watch position),
 *       or it stays near its home position.</li>
 *   <li>It attacks monsters in range, except creepers. It attacks any entity that attacks it, and alerts
 *       the other units in the alert radius and in its village.</li>
 *   <li>It never chases a target that is farther than the chase radius from the home position.</li>
 *   <li>It raises its shield when the target is not in melee reach.</li>
 * </ul>
 */
public class GuardEntity extends FactionUnit {

    /** Distance at which the guard lowers the shield to attack. */
    private static final double MELEE_REACH = 3.0;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.GUARD;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new UnitHurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper) && canTargetFromHome(target)));
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
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
    protected void equip() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemSlot(EquipmentSlot.CHEST, tabard(DEFAULT_TABARD_COLOR));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }
}
