package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Captain: leader of the garrison. It fights as a guard, with better values.
 *
 * <ul>
 *   <li><b>Morale aura</b>: the units of its village near the captain get Strength I.</li>
 *   <li><b>General alarm</b>: when the captain has a target, its village goes to {@link AlertState#ASSAULT}.</li>
 *   <li><b>Death</b>: a share of the units of its village is routed (runs back home and does not fight),
 *       and all units of the village get Weakness I for the same time.</li>
 * </ul>
 *
 * <p>It wears the banner of its faction on its head (a blue banner without a faction).
 */
public class CaptainEntity extends GuardEntity {
    /** The captain updates its aura every 2 seconds. The effect lasts 3 seconds. */
    private static final int AURA_INTERVAL = 40;
    private static final int AURA_DURATION = 60;

    public CaptainEntity(EntityType<? extends CaptainEntity> type, Level level) {
        super(type, level);
    }

    /** Default attributes. The config values replace them at spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected UnitStats stats() {
        return WorldConfig.CAPTAIN;
    }

    @Override
    protected void unitServerAiStep(@Nullable LivingEntity target) {
        super.unitServerAiStep(target);
        if (target != null) {
            getVillageHeart().ifPresent(heart -> {
                heart.reportThreat(null);
                heart.setAlertState(AlertState.ASSAULT);
            });
        }
        if (tickCount % AURA_INTERVAL == 0) {
            for (FactionUnit unit : villageUnitsNear(WorldConfig.CAPTAIN_AURA_RADIUS.get())) {
                unit.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, AURA_DURATION, 0, true, true));
            }
        }
    }

    /** The other units of the same village in the radius. Without a village: no units. */
    private List<FactionUnit> villageUnitsNear(double radius) {
        if (getVillagePos() == null) {
            return List.of();
        }
        AABB area = getBoundingBox().inflate(radius, 8.0, radius);
        return level().getEntitiesOfClass(FactionUnit.class, area,
                unit -> unit != this && unit.isAlive() && getVillagePos().equals(unit.getVillagePos()));
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!level().isClientSide()) {
            demoralizeVillage();
        }
        super.die(damageSource);
    }

    /** Routes a share of the units of the village and weakens all of them. */
    private void demoralizeVillage() {
        List<FactionUnit> units = new ArrayList<>(getVillageHeart()
                .map(heart -> heart.getUnits())
                .orElseGet(() -> villageUnitsNear(WorldConfig.VILLAGE_RADIUS.get())));
        units.remove(this);
        if (units.isEmpty()) {
            return;
        }
        long ticks = WorldConfig.CAPTAIN_ROUT_SECONDS.get() * 20L;
        double share = WorldConfig.CAPTAIN_ROUT_SHARE.get();
        int routed = share <= 0.0 ? 0 : Math.max(1, (int) Math.round(units.size() * share));
        Collections.shuffle(units, new Random(getRandom().nextLong()));
        for (int i = 0; i < units.size(); i++) {
            FactionUnit unit = units.get(i);
            unit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) ticks, 0));
            if (i < routed) {
                unit.rout(ticks);
            }
        }
    }

    @Override
    protected void equip() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        ItemStack banner = faction().map(f -> FactionVisuals.banner(level(), f)).orElse(ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.HEAD, banner.isEmpty() ? new ItemStack(Items.BLUE_BANNER) : banner);
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }
}
