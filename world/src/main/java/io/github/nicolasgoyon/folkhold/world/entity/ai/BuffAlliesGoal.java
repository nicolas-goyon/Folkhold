package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.HealerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Gives a short buff to the units near a healer during an alert of its village ({@link HealerEntity#buffAllies}). The
 * goal starts when the buff cooldown is over and the healer has a bandage. It acts at once and does not move the
 * healer.
 */
public class BuffAlliesGoal extends Goal {
    private final HealerEntity healer;

    public BuffAlliesGoal(HealerEntity healer) {
        this.healer = healer;
    }

    @Override
    public boolean canUse() {
        return healer.canBuff() && healer.isVillageAlerted();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        healer.buffAllies();
    }
}
