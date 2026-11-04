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
 * Draws a locomotive as the vanilla furnace minecart, with a color on the block in the minecart: copper for the
 * furnace locomotive, steel for the heavy locomotive. The item has the icon of the vanilla furnace minecart in the
 * same color.
 */
public final class LocomotiveRenderer<T extends Locomotive> extends MinecartRenderer<T> {
    /** The color of the furnace locomotive, as ARGB. */
    static final int FURNACE_COLOR = 0xFFD98C66;
    /** The color of the heavy locomotive, as ARGB. */
    static final int HEAVY_COLOR = 0xFF8C9AB3;
    /** The sides of the block, and null for the quads that are not on a side. */
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST};

    private final float red;
    private final float green;
    private final float blue;

    public LocomotiveRenderer(EntityRendererProvider.Context context, int color) {
        super(context, ModelLayers.FURNACE_MINECART);
        red = (color >> 16 & 0xFF) / 255.0F;
        green = (color >> 8 & 0xFF) / 255.0F;
        blue = (color & 0xFF) / 255.0F;
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
                consumer.putBulkData(poseStack.last(), quad, red, green, blue, 1.0F, packedLight,
                        OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
