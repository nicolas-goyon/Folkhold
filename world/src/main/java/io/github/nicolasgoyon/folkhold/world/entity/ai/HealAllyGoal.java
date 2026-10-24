package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.HealerEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Moves a healer to the wounded unit of its village with the lowest share of health, then heals it with a bandage
 * ({@link HealerEntity#bandage}).
 *
 * <ul>
 *   <li>The goal starts when the heal cooldown is over, the healer has a bandage, and a unit of the village in the
 *       search radius is wounded.</li>
 *   <li>The goal stops when the patient is healed, dead or too far, or when the path to it fails for a long
 *       time.</li>
 * </ul>
 */
public class HealAllyGoal extends Goal {
    /** The healer puts the bandage at this distance from the patient or nearer, in blocks. */
    private static final double REACH = 2.5;
    /** The goal stops when the patient is farther than this distance, in blocks. */
    private static final double MAX_DISTANCE = 24.0;
    private static final int REPATH_TICKS = 10;
    /** The goal stops after this time with no heal, in ticks. */
    private static final int MAX_TICKS = 400;

    private final HealerEntity healer;
    @Nullable
    private FactionUnit patient;
    private int repathTicks;
    private int ticks;

    public HealAllyGoal(HealerEntity healer) {
        this.healer = healer;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!healer.canHeal()) {
            return false;
        }
        patient = healer.findPatient().orElse(null);
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && healer.isPatient(patient) && healer.canHeal() && ticks < MAX_TICKS
                && healer.distanceTo(patient) <= MAX_DISTANCE;
    }

    @Override
    public void start() {
        repathTicks = 0;
        ticks = 0;
    }

    @Override
    public void stop() {
        patient = null;
        healer.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (patient == null) {
            return;
        }
        ticks++;
        healer.getLookControl().setLookAt(patient, 30.0F, healer.getMaxHeadXRot());
        if (healer.distanceTo(patient) <= REACH) {
            healer.getNavigation().stop();
            healer.bandage(patient);
            return;
        }
        if (--repathTicks <= 0) {
            repathTicks = REPATH_TICKS;
            healer.getNavigation().moveTo(patient, 1.1);
        }
    }
}
