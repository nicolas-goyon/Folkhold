package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.RiderEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * The melee attack of a rider. On its horse, the rider charges at a gallop ({@link #CHARGE_SPEED}). On foot, it
 * attacks as a guard. A rider has one goal for each case: the goal runs only when the rider is on a horse, or only
 * when it is on foot.
 */
public class RiderMeleeGoal extends MeleeAttackGoal {
    /** The speed modifier of the horse during a charge. */
    public static final double CHARGE_SPEED = 1.5;
    /** The speed modifier of a rider on foot. */
    private static final double FOOT_SPEED = 1.0;

    private final RiderEntity rider;
    private final boolean mounted;

    /**
     * Makes the goal.
     *
     * @param rider   the rider
     * @param mounted true for the goal of the rider on its horse, false for the goal of the rider on foot
     */
    public RiderMeleeGoal(RiderEntity rider, boolean mounted) {
        super(rider, mounted ? CHARGE_SPEED : FOOT_SPEED, true);
        this.rider = rider;
        this.mounted = mounted;
    }

    @Override
    public boolean canUse() {
        return rider.isPassenger() == mounted && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return rider.isPassenger() == mounted && super.canContinueToUse();
    }
}
