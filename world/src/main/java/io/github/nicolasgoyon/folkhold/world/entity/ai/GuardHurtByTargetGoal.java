package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.phys.AABB;

/**
 * Targets the attacker of the guard. Other guards in the alert radius also target the attacker.
 */
public class GuardHurtByTargetGoal extends HurtByTargetGoal {
    private final GuardEntity guard;

    public GuardHurtByTargetGoal(GuardEntity guard) {
        super(guard, GuardEntity.class);
        this.guard = guard;
    }

    @Override
    protected void alertOthers() {
        LivingEntity attacker = guard.getLastHurtByMob();
        if (attacker == null) {
            return;
        }
        double radius = WorldConfig.ALERT_RADIUS.get();
        AABB area = guard.getBoundingBox().inflate(radius, 8.0, radius);
        for (GuardEntity other : guard.level().getEntitiesOfClass(GuardEntity.class, area)) {
            if (other != guard && other.getTarget() == null && other.canTargetFromHome(attacker)) {
                other.setTarget(attacker);
            }
        }
    }
}
