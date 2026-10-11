package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a guard with the vanilla humanoid model, a vanilla player skin and its armor.
 */
public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, HumanoidModel<GuardEntity>> {

    private static final ResourceLocation SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public void render(GuardEntity guard, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        HumanoidModel.ArmPose mainPose = guard.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.EMPTY
                : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose offPose = offHandPose(guard, guard.getOffhandItem());
        if (guard.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.leftArmPose = mainPose;
            model.rightArmPose = offPose;
        }
        super.render(guard, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static HumanoidModel.ArmPose offHandPose(GuardEntity guard, ItemStack offHand) {
        if (offHand.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        return guard.isBlocking() ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(GuardEntity guard) {
        return SKIN;
    }
}
