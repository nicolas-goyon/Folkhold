package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.BuffAlliesGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.HealAllyGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.StayNearCaptainGoal;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Healer: the field medic of a garrison. It does not attack.
 *
 * <p>Behavior (see also {@link FactionUnit}):
 * <ul>
 *   <li>It heals a wounded unit of its village with a bandage: it walks to the unit, then gives it back some health
 *       ({@link WorldConfig#HEALER_HEAL_AMOUNT}). The heal has a long cooldown.</li>
 *   <li>During an alert, it stays near the captain of its village, behind the fighting units. It gives a short buff
 *       (regeneration and resistance) to the units near it, with a longer cooldown.</li>
 *   <li>Each heal and each buff uses one bandage of its supply. The supply refills while its village is calm.</li>
 *   <li>It runs away from the monsters and from the hostile players that come near it.</li>
 * </ul>
 */
public class HealerEntity extends FactionUnit {
    private static final String SUPPLY_TAG = "Supply";
    private static final String LAST_HEAL_TAG = "LastHeal";
    private static final String LAST_BUFF_TAG = "LastBuff";
    private static final String LAST_REFILL_TAG = "LastRefill";
    /** The healer runs away from a threat at this distance or nearer, in blocks. */
    private static final float AVOID_DISTANCE = 5.0F;
    /** The buff reaches the units at this distance from the healer or nearer, in blocks. */
    private static final double BUFF_RADIUS = 6.0;

    private int supply = -1;
    private long lastHeal = Long.MIN_VALUE / 2;
    private long lastBuff = Long.MIN_VALUE / 2;
    /** Start of the refill delay, or -1 before the first refill check. */
    private long lastRefill = -1;

    public HealerEntity(EntityType<? extends HealerEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.HEALER;
    }

    @Override
    public GarrisonRole role() {
        return GarrisonRole.HEALER;
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, AVOID_DISTANCE, 1.0, 1.3,
                this::isThreat));
        goalSelector.addGoal(3, new HealAllyGoal(this));
        goalSelector.addGoal(3, new BuffAlliesGoal(this));
        goalSelector.addGoal(3, new StayNearCaptainGoal(this));
    }

    /** A monster, or a player that the healer must attack (the healer runs away instead). */
    private boolean isThreat(LivingEntity entity) {
        return entity instanceof Enemy || (entity instanceof Player player && isHostileTo(player));
    }

    /** The number of bandages of the healer. */
    public int getSupply() {
        if (supply < 0) {
            supply = WorldConfig.HEALER_SUPPLY.get();
        }
        return supply;
    }

    /** Sets the number of bandages. The refill delay starts again. */
    public void setSupply(int supply) {
        this.supply = Math.max(0, Math.min(supply, WorldConfig.HEALER_SUPPLY.get()));
        lastRefill = level().getGameTime();
    }

    /** True if the heal cooldown is over and the healer has a bandage. */
    public boolean canHeal() {
        long cooldown = WorldConfig.HEALER_HEAL_COOLDOWN_SECONDS.get() * 20L;
        return getSupply() > 0 && level().getGameTime() - lastHeal >= cooldown;
    }

    /** True if the buff cooldown is over and the healer has a bandage. */
    public boolean canBuff() {
        long cooldown = WorldConfig.HEALER_BUFF_COOLDOWN_SECONDS.get() * 20L;
        return getSupply() > 0 && level().getGameTime() - lastBuff >= cooldown;
    }

    /** True if the village of the healer is not calm. */
    public boolean isVillageAlerted() {
        return getVillageHeart().map(heart -> heart.getAlertState() != AlertState.CALM).orElse(false);
    }

    /**
     * The loaded units of the village of the healer that are alive, the healer excluded. A healer with no village
     * has no allies.
     *
     * @param radius the distance from the healer, in blocks
     * @return the units
     */
    public List<FactionUnit> alliesNear(double radius) {
        if (getVillagePos() == null) {
            return List.of();
        }
        return level().getEntitiesOfClass(FactionUnit.class, getBoundingBox().inflate(radius),
                unit -> unit != this && unit.isAlive() && getVillagePos().equals(unit.getVillagePos()));
    }

    /**
     * The wounded unit of the village with the lowest share of health, in the search radius. A unit is wounded below
     * the share {@link WorldConfig#HEALER_WOUNDED_SHARE} of its maximum health. A bandage does not repair a faction
     * golem, so a golem is never a patient.
     */
    public Optional<FactionUnit> findPatient() {
        return alliesNear(WorldConfig.HEALER_SEARCH_RADIUS.get()).stream()
                .filter(this::isPatient)
                .min(Comparator.comparingDouble(unit -> unit.getHealth() / unit.getMaxHealth()));
    }

    /** True if a unit is a patient: alive, wounded, and not a faction golem. */
    public boolean isPatient(FactionUnit unit) {
        return unit.isAlive() && !(unit instanceof FactionGolemEntity)
                && unit.getHealth() < unit.getMaxHealth() * WorldConfig.HEALER_WOUNDED_SHARE.get();
    }

    /**
     * Heals a unit with a bandage: the unit gets back {@link WorldConfig#HEALER_HEAL_AMOUNT} health. The heal uses one
     * bandage and starts the heal cooldown.
     *
     * @param patient the unit
     */
    public void bandage(FactionUnit patient) {
        patient.heal(WorldConfig.HEALER_HEAL_AMOUNT.get().floatValue());
        supply = getSupply() - 1;
        lastHeal = level().getGameTime();
        swing(InteractionHand.MAIN_HAND);
        level().playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight(),
                    patient.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    /**
     * Gives regeneration and resistance to the units of the village near the healer. The buff uses one bandage and
     * starts the buff cooldown.
     *
     * @return the number of units that got the buff
     */
    public int buffAllies() {
        List<FactionUnit> allies = alliesNear(BUFF_RADIUS);
        if (allies.isEmpty()) {
            return 0;
        }
        int ticks = WorldConfig.HEALER_BUFF_SECONDS.get() * 20;
        for (FactionUnit unit : allies) {
            unit.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0));
            unit.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        }
        supply = getSupply() - 1;
        lastBuff = level().getGameTime();
        swing(InteractionHand.MAIN_HAND);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 1.0F,
                1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(), 12, BUFF_RADIUS / 3,
                    0.5, BUFF_RADIUS / 3, 0.0);
        }
        return allies.size();
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
        refillSupply(level().getGameTime());
    }

    /**
     * While the village is calm, the healer gets back one bandage at each refill delay
     * ({@link WorldConfig#HEALER_SUPPLY_REFILL_SECONDS}). During an alert, or with a full supply, the refill delay
     * starts again.
     *
     * @param now the current game time
     */
    public void refillSupply(long now) {
        if (lastRefill < 0 || getSupply() >= WorldConfig.HEALER_SUPPLY.get() || isVillageAlerted()) {
            lastRefill = now;
            return;
        }
        if (now - lastRefill >= WorldConfig.HEALER_SUPPLY_REFILL_SECONDS.get() * 20L) {
            supply = getSupply() + 1;
            lastRefill = now;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(SUPPLY_TAG, getSupply());
        tag.putLong(LAST_HEAL_TAG, lastHeal);
        tag.putLong(LAST_BUFF_TAG, lastBuff);
        tag.putLong(LAST_REFILL_TAG, lastRefill);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(SUPPLY_TAG)) {
            supply = tag.getInt(SUPPLY_TAG);
        }
        lastHeal = tag.getLong(LAST_HEAL_TAG);
        lastBuff = tag.getLong(LAST_BUFF_TAG);
        lastRefill = tag.contains(LAST_REFILL_TAG) ? tag.getLong(LAST_REFILL_TAG) : -1;
    }
}
