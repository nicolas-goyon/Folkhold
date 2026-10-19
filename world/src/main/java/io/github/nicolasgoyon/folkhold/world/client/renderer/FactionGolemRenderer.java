package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders a faction golem as a vanilla iron golem, with the vanilla texture, the vanilla cracks and an armband in the
 * color of its faction. The game provides the vanilla texture: the mod refers to it by its id.
 */
public class FactionGolemRenderer extends MobRenderer<FactionGolemEntity, FactionGolemModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    /** The shadow radius of the vanilla iron golem. */
    private static final float SHADOW_RADIUS = 0.7F;
    /** The body rolls from side to side with the steps, by this angle at most, in degrees. */
    private static final float SWAY_DEGREES = 6.5F;
    /** One period of the roll, in units of the walk position: one step of each leg. */
    private static final float SWAY_PERIOD = 13.0F;
    /** The offset of the roll in the walk position, so the body rolls toward the leg on the ground. */
    private static final float SWAY_PHASE = 6.0F;
    /** Below this walk speed, the body does not roll. */
    private static final float MIN_SWAY_SPEED = 0.01F;

    public FactionGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new FactionGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), SHADOW_RADIUS);
        addLayer(new GolemCracksLayer(this));
        addLayer(new GolemArmbandLayer(this, context.bakeLayer(GolemArmbandLayer.LAYER)));
    }

    @Override
    public ResourceLocation getTextureLocation(FactionGolemEntity golem) {
        return TEXTURE;
    }

    /** When the golem walks, its body rolls from side to side with the steps, as the body of the vanilla iron golem. */
    @Override
    protected void setupRotations(FactionGolemEntity golem, PoseStack poseStack, float bob, float yBodyRot,
            float partialTick, float scale) {
        super.setupRotations(golem, poseStack, bob, yBodyRot, partialTick, scale);
        if (golem.walkAnimation.speed() >= MIN_SWAY_SPEED) {
            float position = golem.walkAnimation.position(partialTick) + SWAY_PHASE;
            poseStack.mulPose(Axis.ZP.rotationDegrees(SWAY_DEGREES * Mth.triangleWave(position, SWAY_PERIOD)));
        }
    }
}
