package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.HealerEntity;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * During an alert of its village, moves a healer near the captain of the village, behind the fighting units. The
 * healer starts to move when it is farther than {@link #START_DISTANCE} from the captain, and stops at
 * {@link #STOP_DISTANCE}.
 */
public class StayNearCaptainGoal extends Goal {
    private static final double START_DISTANCE = 8.0;
    private static final double STOP_DISTANCE = 4.0;
    /** The healer looks for the captain at this distance or nearer, in blocks. */
    private static final double SEARCH_RADIUS = 48.0;
    private static final int REPATH_TICKS = 20;

    private final HealerEntity healer;
    private int repathTicks;

    public StayNearCaptainGoal(HealerEntity healer) {
        this.healer = healer;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private Optional<CaptainEntity> captain() {
        return healer.alliesNear(SEARCH_RADIUS).stream()
                .filter(CaptainEntity.class::isInstance)
                .map(CaptainEntity.class::cast)
                .min(Comparator.comparingDouble(healer::distanceTo));
    }

    @Override
    public boolean canUse() {
        return healer.isVillageAlerted()
                && captain().map(captain -> healer.distanceTo(captain) > START_DISTANCE).orElse(false);
    }

    @Override
    public boolean canContinueToUse() {
        return healer.isVillageAlerted()
                && captain().map(captain -> healer.distanceTo(captain) > STOP_DISTANCE).orElse(false);
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        healer.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--repathTicks <= 0) {
            repathTicks = REPATH_TICKS;
            captain().ifPresent(captain -> healer.getNavigation().moveTo(captain, 1.0));
        }
    }
}
