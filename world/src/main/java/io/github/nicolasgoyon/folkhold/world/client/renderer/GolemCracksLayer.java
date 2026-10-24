package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Crackiness;

/**
 * The cracks of a wounded faction golem: the vanilla crack textures of the iron golem, on the golem model. The game
 * provides the textures.
 */
public class GolemCracksLayer extends RenderLayer<FactionGolemEntity, FactionGolemModel> {
    private static final Map<Crackiness.Level, ResourceLocation> TEXTURES = Map.of(
            Crackiness.Level.LOW, cracks("low"),
            Crackiness.Level.MEDIUM, cracks("medium"),
            Crackiness.Level.HIGH, cracks("high"));

    public GolemCracksLayer(RenderLayerParent<FactionGolemEntity, FactionGolemModel> renderer) {
        super(renderer);
    }

    private static ResourceLocation cracks(String level) {
        return ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_" + level
                + ".png");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, FactionGolemEntity golem,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (golem.isInvisible()) {
            return;
        }
        ResourceLocation texture = TEXTURES.get(golem.getCrackiness());
        if (texture != null) {
            renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer, packedLight, golem, -1);
        }
    }
}
