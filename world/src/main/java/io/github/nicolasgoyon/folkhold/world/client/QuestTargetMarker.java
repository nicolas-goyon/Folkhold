package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * A gold marker above the merchant of an escort of the player, so the player finds the caravan to protect. The
 * marker moves up and down a little, and shows through the blocks.
 */
public final class QuestTargetMarker {
    private static final String MARK = "▼";
    private static final int COLOR = 0xFFFFD37F;
    private static final float SCALE = 0.05F;
    /** The height of the marker above the head, in blocks: above the name of the merchant. */
    private static final double ABOVE_HEAD = 0.95;

    private QuestTargetMarker() {
    }

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!ClientQuestLog.isTarget(entity.getId())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        PoseStack pose = event.getPoseStack();
        float bob = Mth.sin((entity.tickCount + event.getPartialTick()) / 6.0F) * 0.08F;
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + ABOVE_HEAD + bob, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        font.drawInBatch(MARK, -font.width(MARK) / 2.0F, 0.0F, COLOR, false, pose.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
