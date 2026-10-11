package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.phys.AABB;

/**
 * Targets the attacker of the unit. Then:
 * <ul>
 *   <li>the other units in the alert radius also target the attacker;</li>
 *   <li>the unit reports the attack to its village heart, which alerts all units of the village.</li>
 * </ul>
 */
public class UnitHurtByTargetGoal extends HurtByTargetGoal {
    private final FactionUnit unit;

    public UnitHurtByTargetGoal(FactionUnit unit) {
        super(unit, FactionUnit.class);
        this.unit = unit;
    }

    @Override
    protected void alertOthers() {
        LivingEntity attacker = unit.getLastHurtByMob();
        if (attacker == null) {
            return;
        }
        double radius = WorldConfig.ALERT_RADIUS.get();
        AABB area = unit.getBoundingBox().inflate(radius, 8.0, radius);
        for (FactionUnit other : unit.level().getEntitiesOfClass(FactionUnit.class, area)) {
            if (other != unit && other.getTarget() == null && other.canTargetFromHome(attacker)) {
                other.setTarget(attacker);
            }
        }
        unit.getVillageHeart().ifPresent(heart -> heart.reportThreat(attacker));
    }
}
