package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a faction unit with the vanilla humanoid model, one of the vanilla player skins and its armor.
 *
 * <p>The skin depends on the id of the unit, so units of one village do not all look the same.
 */
public class FactionUnitRenderer<T extends FactionUnit> extends HumanoidMobRenderer<T, HumanoidModel<T>> {

    private static final ResourceLocation[] SKINS = {
            skin("steve"), skin("alex"), skin("ari"), skin("efe"), skin("kai"),
            skin("makena"), skin("noor"), skin("sunny"), skin("zuri"),
    };

    public FactionUnitRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + name + ".png");
    }

    @Override
    public void render(T unit, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        HumanoidModel.ArmPose mainPose = mainHandPose(unit, unit.getMainHandItem());
        HumanoidModel.ArmPose offPose = offHandPose(unit, unit.getOffhandItem());
        if (unit.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.leftArmPose = mainPose;
            model.rightArmPose = offPose;
        }
        super.render(unit, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static HumanoidModel.ArmPose mainHandPose(FactionUnit unit, ItemStack mainHand) {
        if (mainHand.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (mainHand.getItem() instanceof BowItem && unit.isAggressive()) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    private static HumanoidModel.ArmPose offHandPose(FactionUnit unit, ItemStack offHand) {
        if (offHand.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        return unit.isBlocking() ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(T unit) {
        return SKINS[Math.floorMod(unit.getUUID().hashCode(), SKINS.length)];
    }
}
