package io.github.nicolasgoyon.folkhold.world.client.renderer;

import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import javax.annotation.Nullable;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/**
 * The model of the faction golem: the parts and the animations of the vanilla iron golem.
 *
 * <p>The vanilla model {@link IronGolemModel} sets the angles of the parts: the head, the legs, and the arms for the
 * walk and for the attack. It reads the attack time from a vanilla iron golem. This model gives it a pose golem: a
 * vanilla iron golem that is never in the level, with the attack time of the faction golem. The two models use the
 * same parts.
 */
public class FactionGolemModel extends HierarchicalModel<FactionGolemEntity> {
    /** The pose golem of the current client level, or null. */
    @Nullable
    private static PoseGolem pose;

    private final ModelPart root;
    private final ModelPart leftArm;
    private final IronGolemModel<IronGolem> vanilla;

    /**
     * Makes the model.
     *
     * @param root the parts of the vanilla iron golem model ({@code ModelLayers.IRON_GOLEM})
     */
    public FactionGolemModel(ModelPart root) {
        this.root = root;
        this.leftArm = root.getChild("left_arm");
        this.vanilla = new IronGolemModel<>(root);
    }

    /** Forgets the pose golem. The client calls this method when the player leaves a world: no level stays in use. */
    public static void forgetLevel() {
        pose = null;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** The left arm. The armband layer puts the armband on it. */
    public ModelPart leftArm() {
        return leftArm;
    }

    @Override
    public void prepareMobModel(FactionGolemEntity golem, float limbSwing, float limbSwingAmount, float partialTick) {
        vanilla.prepareMobModel(pose(golem), limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(FactionGolemEntity golem, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        vanilla.setupAnim(pose(golem), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    /** The pose golem, with the attack time of the faction golem. */
    private static IronGolem pose(FactionGolemEntity golem) {
        PoseGolem current = pose;
        if (current == null || current.level() != golem.level()) {
            current = new PoseGolem(golem.level());
            pose = current;
        }
        current.attackTick = golem.getAttackAnimationTick();
        return current;
    }

    /**
     * A vanilla iron golem that gives an attack time to the vanilla model. It is never in the level, and it never holds
     * a flower.
     */
    private static final class PoseGolem extends IronGolem {
        private int attackTick;

        PoseGolem(Level level) {
            super(EntityType.IRON_GOLEM, level);
        }

        @Override
        public int getAttackAnimationTick() {
            return attackTick;
        }

        @Override
        public int getOfferFlowerTick() {
            return 0;
        }
    }
}
