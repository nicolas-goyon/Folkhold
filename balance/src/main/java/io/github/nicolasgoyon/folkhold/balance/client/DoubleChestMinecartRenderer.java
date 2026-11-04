package io.github.nicolasgoyon.folkhold.balance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.folkhold.balance.rail.DoubleChestMinecart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the double chest minecart as the vanilla chest minecart with a second chest on the first chest. Two vanilla
 * chests show the double capacity with no new model or texture. A larger chest is easy to mix up with the vanilla
 * chest minecart. The item has the icon of the vanilla chest minecart in a darker color.
 */
public final class DoubleChestMinecartRenderer extends MinecartRenderer<DoubleChestMinecart> {
    /** The color of the icon on the item, as ARGB: a darker wood. */
    static final int ICON_COLOR = 0xFFB08A60;

    public DoubleChestMinecartRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.CHEST_MINECART);
    }

    @Override
    protected void renderMinecartContents(DoubleChestMinecart cart, float partialTicks, BlockState state,
                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.renderMinecartContents(cart, partialTicks, state, poseStack, buffer, packedLight);
        poseStack.pushPose();
        // The chest model is 14 pixels high.
        poseStack.translate(0.0F, 14.0F / 16.0F, 0.0F);
        super.renderMinecartContents(cart, partialTicks, state, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
