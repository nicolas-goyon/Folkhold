package io.github.nicolasgoyon.folkhold.blocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Shows the icon item of a {@link JobSignBlockEntity} on the field of the plaque, on the two faces. The field is
 * 8 × 8 pixels, in the middle of the plaque, 12 pixels above the ground. Its front is 1 pixel north of the center of
 * the block when the sign faces north (see {@code tools/pixvox/job_sign.py}).
 */
public class JobSignRenderer implements BlockEntityRenderer<JobSignBlockEntity> {
    /** The height of the center of the field, in blocks. */
    private static final float FIELD_Y = 12.0F / 16.0F;
    /** The distance from the center of the block to a face of the field, plus the half thickness of a flat item. */
    private static final float FIELD_DEPTH = 1.0F / 16.0F + 1.0F / 64.0F;
    /** The item of a flat icon has a width of 1 block: the field has a width of 0.5 block. */
    private static final float ICON_SCALE = 0.5F;

    private final ItemRenderer itemRenderer;

    public JobSignRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(JobSignBlockEntity sign, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, int packedOverlay) {
        ItemStack icon = sign.iconStack();
        if (icon.isEmpty()) {
            return;
        }
        Direction facing = sign.getBlockState().getValue(JobSignBlock.FACING);
        int seed = (int) sign.getBlockPos().asLong();
        // The front face, then the back face.
        for (float turn : new float[] {0.0F, 180.0F}) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot() + turn));
            poseStack.translate(0.0F, FIELD_Y, -FIELD_DEPTH);
            poseStack.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
            itemRenderer.renderStatic(icon, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer,
                    sign.getLevel(), seed);
            poseStack.popPose();
        }
    }
}
