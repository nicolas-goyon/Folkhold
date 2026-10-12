package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Random walk in the anchor area of the unit (see {@link FactionUnit#getAnchorPos()}).
 * A unit with a post does not walk.
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
        Vec3 anchor = Vec3.atBottomCenterOf(unit.getAnchorPos());
        int radius = Math.max(2, unit.getAnchorRadius());
        for (int i = 0; i < ATTEMPTS; i++) {
            Vec3 pos = LandRandomPos.getPos(unit, radius, 4);
            if (pos != null && pos.distanceTo(anchor) <= radius) {
                return pos;
            }
        }
        return null;
    }
}
