package io.github.nicolasgoyon.folkhold.balance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a locomotive as the vanilla furnace minecart, with a copper color on the block in the minecart.
 */
public final class LocomotiveRenderer<T extends Locomotive> extends MinecartRenderer<T> {
    private static final float RED = 0.85F;
    private static final float GREEN = 0.55F;
    private static final float BLUE = 0.4F;
    /** The same color for the item, as ARGB. */
    static final int ITEM_COLOR = 0xFFD98C66;
    /** The sides of the block, and null for the quads that are not on a side. */
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST};

    public LocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.FURNACE_MINECART);
    }

    @Override
    protected void renderMinecartContents(T cart, float partialTicks, BlockState state, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        VertexConsumer consumer = buffer.getBuffer(Sheets.solidBlockSheet());
        RandomSource random = RandomSource.create(42L);
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null)) {
                consumer.putBulkData(poseStack.last(), quad, RED, GREEN, BLUE, 1.0F, packedLight,
                        OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
