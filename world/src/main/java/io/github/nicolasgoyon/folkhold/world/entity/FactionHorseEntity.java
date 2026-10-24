package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitNavigation;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * The horse of a rider ({@link RiderEntity}): a vanilla horse with a saddle and a leather horse armor in the color of
 * the faction of the rider.
 *
 * <ul>
 *   <li>The rider controls the horse, as a vanilla mob controls the horse that it rides. The horse finds its paths as a
 *       faction unit, so it goes through the closed gates of the village (see {@link UnitNavigation}).</li>
 *   <li>A player cannot ride the horse, feed it, tame it, lure it, put it on a lead or open its inventory.</li>
 *   <li>The units of the village of the horse do not hurt it. An attack on the horse is an attack on its rider: the
 *       rider and its village answer the attacker.</li>
 *   <li>The horse drops no items.</li>
 *   <li>When its rider is dead or missing for {@link #RIDERLESS_TICKS}, the horse disappears.</li>
 * </ul>
 */
public class FactionHorseEntity extends Horse {
    private static final String RIDER_TAG = "Rider";
    private static final String VILLAGE_TAG = "VillagePos";
    private static final String RIDERLESS_TAG = "RiderlessSince";
    /** A horse with no rider for this time disappears, in ticks. */
    public static final long RIDERLESS_TICKS = 30 * 20;
    /** The horse checks its rider at this interval, in ticks. */
    private static final int CHECK_INTERVAL = 20;
    /** The follow range of the horse, in blocks. */
    private static final double PATH_RANGE = 48.0;

    @Nullable
    private UUID riderId;
    @Nullable
    private BlockPos villagePos;
    /** The game time when the horse lost its rider, or -1 when the horse has its rider. */
    private long riderlessSince = -1;

    public FactionHorseEntity(EntityType<? extends Horse> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /**
     * Default attributes. {@link #prepareFor(RiderEntity)} sets the health and the speed from the config. The follow
     * range sets the number of nodes of a path search: the horse needs long paths around the walls of the village.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.FOLLOW_RANGE, PATH_RANGE);
    }

    /** Ground navigation of the faction units: the horse of a rider goes through the gates of the village. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new UnitNavigation(this, level);
    }

    /** The horse swims, but a player cannot lure it with food. */
    @Override
    protected void addBehaviourGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /**
     * Prepares the horse for a rider: the rider and the village of the horse, its health and its speed from the config,
     * a saddle, and a leather horse armor in the color of the faction of the rider.
     *
     * @param rider the rider of the horse
     */
    public void prepareFor(RiderEntity rider) {
        riderId = rider.getUUID();
        villagePos = rider.getVillagePos();
        riderlessSince = -1;
        setTamed(true);
        setBase(Attributes.MAX_HEALTH, WorldConfig.RIDER_HORSE_HEALTH.get());
        setBase(Attributes.MOVEMENT_SPEED, WorldConfig.RIDER_HORSE_SPEED.get());
        setHealth(getMaxHealth());
        equipSaddle(new ItemStack(Items.SADDLE), null);
        ItemStack armor = new ItemStack(Items.LEATHER_HORSE_ARMOR);
        armor.set(DataComponents.DYED_COLOR, new DyedItemColor(rider.factionColor(), false));
        setItemSlot(EquipmentSlot.BODY, armor);
        setDropChance(EquipmentSlot.BODY, 0.0F);
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** The id of the rider of the horse, or null. */
    @Nullable
    public UUID getRiderId() {
        return riderId;
    }

    /**
     * The position of the village heart of the horse: the village of its rider when the rider is loaded, else the
     * village of the rider at the last save. Null if the horse has no village.
     */
    @Nullable
    public BlockPos getVillagePos() {
        RiderEntity rider = getRider();
        if (rider != null) {
            villagePos = rider.getVillagePos();
        }
        return villagePos;
    }

    /** The rider of the horse, if it is loaded and alive. */
    @Nullable
    public RiderEntity getRider() {
        if (riderId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(riderId) instanceof RiderEntity rider && rider.isAlive() ? rider : null;
    }

    /** A player cannot ride the horse, feed it, tame it, put armor on it or open its inventory. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return false;
    }

    /** The horse drops neither its saddle nor its armor. */
    @Override
    protected void dropEquipment() {
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    /** An attack on the horse is an attack on its rider: the rider answers the attacker, and alerts its village. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (hurt && attacker instanceof LivingEntity living) {
            RiderEntity rider = getRider();
            if (rider != null && living != rider) {
                rider.setLastHurtByMob(living);
            }
        }
        return hurt;
    }

    /**
     * The rider of a dead horse gets off at once, and gets a new horse after the refill delay of the garrison.
     */
    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        RiderEntity rider = getRider();
        ejectPassengers();
        if (rider != null) {
            rider.onHorseLost(level().getGameTime());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % CHECK_INTERVAL == 0) {
            checkRider(level().getGameTime());
        }
    }

    /**
     * Counts the time with no rider. The rider can be away from the horse for a time, for example after a swim. A
     * horse with a dead or missing rider for {@link #RIDERLESS_TICKS} disappears.
     *
     * @param now the current game time
     */
    public void checkRider(long now) {
        if (getRider() != null) {
            riderlessSince = -1;
            return;
        }
        if (riderlessSince < 0) {
            riderlessSince = now;
        } else if (now - riderlessSince >= RIDERLESS_TICKS) {
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 12, 0.5,
                        0.5, 0.5, 0.02);
            }
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (riderId != null) {
            tag.putUUID(RIDER_TAG, riderId);
        }
        if (villagePos != null) {
            tag.put(VILLAGE_TAG, NbtUtils.writeBlockPos(villagePos));
        }
        tag.putLong(RIDERLESS_TAG, riderlessSince);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        riderId = tag.hasUUID(RIDER_TAG) ? tag.getUUID(RIDER_TAG) : null;
        villagePos = NbtUtils.readBlockPos(tag, VILLAGE_TAG).orElse(null);
        riderlessSince = tag.contains(RIDERLESS_TAG) ? tag.getLong(RIDERLESS_TAG) : -1;
    }
}
