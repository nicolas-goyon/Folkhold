package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Random walk in the anchor area of the unit (see {@link FactionUnit#getAnchorPos()}).
 * A unit with a post does not walk. A unit that watches a gate at night walks on the heart side of its watch
 * position only, so it does not go through the gate.
 */
public class AnchorStrollGoal extends WaterAvoidingRandomStrollGoal {
    private static final int ATTEMPTS = 10;

    private final FactionUnit unit;

    public AnchorStrollGoal(FactionUnit unit, double speed) {
        super(unit, speed);
        this.unit = unit;
    }

    @Override
    public boolean canUse() {
        return unit.getPostPos() == null && super.canUse();
    }

    @Override
    @Nullable
    protected Vec3 getPosition() {
        int radius = Math.max(2, unit.getAnchorRadius());
        for (int i = 0; i < ATTEMPTS; i++) {
            Vec3 pos = LandRandomPos.getPos(unit, radius, 4);
            if (pos != null && accepts(unit, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * True if a unit can walk to a position: the position is in the anchor area of the unit. When the unit watches
     * a gate at night, the position is not farther from the village heart than the watch position.
     *
     * @param unit the unit
     * @param pos  the position
     * @return true if the unit can walk to the position
     */
    public static boolean accepts(FactionUnit unit, Vec3 pos) {
        Vec3 anchor = Vec3.atBottomCenterOf(unit.getAnchorPos());
        if (pos.distanceTo(anchor) > Math.max(2, unit.getAnchorRadius())) {
            return false;
        }
        BlockPos village = unit.getVillagePos();
        boolean watch = unit.getPostPos() == null && unit.getNightPos() != null && unit.level().isNight()
                && village != null;
        if (!watch) {
            return true;
        }
        Vec3 heart = Vec3.atBottomCenterOf(village);
        return pos.distanceToSqr(heart) <= anchor.distanceToSqr(heart);
    }
}
