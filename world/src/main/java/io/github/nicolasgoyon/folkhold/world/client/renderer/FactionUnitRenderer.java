package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/**
 * Renders a faction unit with GeckoLib: animated humanoid model, vanilla armor and held items.
 */
public class FactionUnitRenderer<T extends FactionUnit> extends GeoEntityRenderer<T> {
    private static final String HEAD = "armorHead";
    private static final String BODY = "armorBody";
    private static final String WAIST = "armorWaist";
    private static final String RIGHT_ARM = "armorRightArm";
    private static final String LEFT_ARM = "armorLeftArm";
    private static final String RIGHT_LEG = "armorRightLeg";
    private static final String LEFT_LEG = "armorLeftLeg";
    private static final String RIGHT_BOOT = "armorRightBoot";
    private static final String LEFT_BOOT = "armorLeftBoot";
    private static final Set<String> ARMOR_BONES =
            Set.of(HEAD, BODY, WAIST, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, RIGHT_BOOT, LEFT_BOOT);
    private static final String RIGHT_ITEM = "rightItem";
    private static final String LEFT_ITEM = "leftItem";

    public FactionUnitRenderer(EntityRendererProvider.Context context) {
        super(context, new FactionUnitModel<>());
        this.shadowRadius = 0.5F;
        addRenderLayer(new UnitArmorLayer<>(this));
        addRenderLayer(new UnitItemLayer<>(this));
    }

    /**
     * Vanilla armor on the armor bones. The cubes of the armor bones only give the size: they are hidden.
     * GeckoLib scales each armor part by the size of the bone cube divided by the size of the vanilla part, so each
     * bone cube has the size of the vanilla part (head 8 × 8 × 8, body 8 × 12 × 4, arms and legs 4 × 12 × 4).
     */
    private static final class UnitArmorLayer<T extends FactionUnit> extends ItemArmorGeoLayer<T> {
        UnitArmorLayer(FactionUnitRenderer<T> renderer) {
            super(renderer);
        }

        @Override
        public void preRender(PoseStack poseStack, T unit, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight,
                int packedOverlay) {
            super.preRender(poseStack, unit, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight,
                    packedOverlay);
            for (String name : ARMOR_BONES) {
                bakedModel.getBone(name).ifPresent(bone -> bone.setHidden(true));
            }
        }

        @Nullable
        @Override
        protected ItemStack getArmorItemForBone(GeoBone bone, T unit) {
            return switch (bone.getName()) {
                case HEAD -> helmetStack;
                case BODY, RIGHT_ARM, LEFT_ARM -> chestplateStack;
                case WAIST, RIGHT_LEG, LEFT_LEG -> leggingsStack;
                case RIGHT_BOOT, LEFT_BOOT -> bootsStack;
                default -> null;
            };
        }

        @Override
        protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, T unit) {
            return switch (bone.getName()) {
                case HEAD -> EquipmentSlot.HEAD;
                case WAIST, RIGHT_LEG, LEFT_LEG -> EquipmentSlot.LEGS;
                case RIGHT_BOOT, LEFT_BOOT -> EquipmentSlot.FEET;
                default -> EquipmentSlot.CHEST;
            };
        }

        @Override
        protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack, T unit,
                HumanoidModel<?> baseModel) {
            return switch (bone.getName()) {
                case HEAD -> baseModel.head;
                case RIGHT_ARM -> baseModel.rightArm;
                case LEFT_ARM -> baseModel.leftArm;
                case RIGHT_LEG, RIGHT_BOOT -> baseModel.rightLeg;
                case LEFT_LEG, LEFT_BOOT -> baseModel.leftLeg;
                default -> baseModel.body;
            };
        }
    }

    /**
     * Main hand item in the right hand, off hand item (shield) in the left hand, and a head item that is not
     * armor (for example the banner of a captain) on the head.
     *
     * <p>The transforms are the transforms of the vanilla layers, in the frame of GeckoLib. The vanilla model frame
     * is the GeckoLib frame turned by 180° around z. The item bones are at the vanilla hand positions (10 pixels
     * below the shoulder, 2 pixels to the front).
     */
    private static final class UnitItemLayer<T extends FactionUnit> extends BlockAndItemGeoLayer<T> {
        UnitItemLayer(FactionUnitRenderer<T> renderer) {
            super(renderer);
        }

        @Nullable
        @Override
        protected ItemStack getStackForBone(GeoBone bone, T unit) {
            return switch (bone.getName()) {
                case RIGHT_ITEM -> unit.getMainHandItem().isEmpty() ? null : unit.getMainHandItem();
                case LEFT_ITEM -> unit.getOffhandItem().isEmpty() ? null : unit.getOffhandItem();
                case HEAD -> {
                    ItemStack head = unit.getItemBySlot(EquipmentSlot.HEAD);
                    yield head.isEmpty() || head.getItem() instanceof ArmorItem ? null : head;
                }
                default -> null;
            };
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T unit) {
            return switch (bone.getName()) {
                case LEFT_ITEM -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                case HEAD -> ItemDisplayContext.HEAD;
                default -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            };
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T unit,
                MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            if (bone.getName().equals(HEAD)) {
                // Vanilla custom head layer: 4 pixels above the neck, scale 0.625.
                poseStack.translate(0.0, 0.25, 0.0);
                poseStack.scale(0.625F, 0.625F, 0.625F);
            } else {
                // Vanilla item in hand layer: the item points to the front of the hand.
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            // The left hand flag mirrors the item transform, as for the vanilla left hand.
            Minecraft.getInstance().getItemRenderer().renderStatic(unit, stack,
                    getTransformTypeForStack(bone, stack, unit), bone.getName().equals(LEFT_ITEM), poseStack,
                    bufferSource, unit.level(), packedLight, packedOverlay, unit.getId());
        }
    }
}
