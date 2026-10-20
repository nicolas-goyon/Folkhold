package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionHorseEntity;
import io.github.nicolasgoyon.folkhold.world.entity.RiderEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A rider on foot goes back to its horse and rides it again, for example after a swim. The goal runs when the rider has
 * no target, and its horse is alive, free and in {@link #MAX_DISTANCE}.
 */
public class MountHorseGoal extends Goal {
    /** The rider gets on its horse at this distance or nearer, in blocks. */
    private static final double MOUNT_DISTANCE = 2.5;
    /** The rider looks for its horse at this distance or nearer, in blocks. */
    private static final double MAX_DISTANCE = 24.0;
    private static final int REPATH_TICKS = 20;
    /** The goal stops after this time, in ticks. */
    private static final int MAX_TICKS = 400;

    private final RiderEntity rider;
    @Nullable
    private FactionHorseEntity horse;
    private int repathTicks;
    private int ticks;

    public MountHorseGoal(RiderEntity rider) {
        this.rider = rider;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean canMount(@Nullable FactionHorseEntity candidate) {
        return candidate != null && candidate.isAlive() && !candidate.isVehicle()
                && rider.distanceTo(candidate) <= MAX_DISTANCE;
    }

    @Override
    public boolean canUse() {
        if (rider.isPassenger() || rider.getTarget() != null || rider.isRouted()) {
            return false;
        }
        horse = rider.getHorse();
        return canMount(horse);
    }

    @Override
    public boolean canContinueToUse() {
        return !rider.isPassenger() && rider.getTarget() == null && ticks < MAX_TICKS && canMount(horse);
    }

    @Override
    public void start() {
        repathTicks = 0;
        ticks = 0;
    }

    @Override
    public void stop() {
        horse = null;
        if (!rider.isPassenger()) {
            rider.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (horse == null) {
            return;
        }
        ticks++;
        if (rider.distanceTo(horse) <= MOUNT_DISTANCE) {
            rider.getNavigation().stop();
            rider.startRiding(horse, true);
            return;
        }
        if (--repathTicks <= 0) {
            repathTicks = REPATH_TICKS;
            rider.getNavigation().moveTo(horse, 1.0);
        }
    }
}
