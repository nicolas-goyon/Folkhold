package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * The armband of a faction golem: a band of cloth around the upper part of its left arm, in the color of its faction.
 *
 * <p>The layer puts the band at the position and the angle of the left arm of the model, after the animations, so the
 * band moves with the arm. The texture is gray: the color of the faction tints it.
 */
public class GolemArmbandLayer extends RenderLayer<FactionGolemEntity, FactionGolemModel> {
    /** The model layer of the band. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "faction_golem"), "armband");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "textures/entity/faction_golem/armband.png");
    private static final String BAND = "band";

    private final ModelPart band;

    /**
     * Makes the layer.
     *
     * @param renderer the golem renderer
     * @param root     the parts of {@link #LAYER}
     */
    public GolemArmbandLayer(RenderLayerParent<FactionGolemEntity, FactionGolemModel> renderer, ModelPart root) {
        super(renderer);
        this.band = root.getChild(BAND);
    }

    /**
     * The band, around the left arm of the vanilla iron golem model. Around the pivot of the shoulder, the arm goes
     * from x 9 to 13, from y -2.5 to 27.5 and from z -3 to 3. The band covers y 3.5 to 6.5 of the arm, and it is a
     * little larger than the arm. The texture of the band is 32 × 16 pixels.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(BAND, CubeListBuilder.create().texOffs(0, 0)
                .addBox(9.0F, 3.5F, -3.0F, 4.0F, 3.0F, 6.0F, new CubeDeformation(0.4F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, FactionGolemEntity golem,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (golem.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().root().translateAndRotate(poseStack);
        getParentModel().leftArm().translateAndRotate(poseStack);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        band.render(poseStack, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(golem, 0.0F),
                FastColor.ARGB32.opaque(golem.getArmbandColor()));
        poseStack.popPose();
    }
}
