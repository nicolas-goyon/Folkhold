package io.github.nicolasgoyon.folkhold.balance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws a chain between the two minecarts of each link that {@link ClientCartLinks} knows, as a leash: two crossed
 * strips with a small sag, between the interpolated positions of the minecarts. The links of a minecart that the
 * client does not have are removed.
 */
public final class ChainRenderer {
    /** The height of the chain above the bottom of a minecart, in blocks. */
    private static final double HEIGHT = 0.45;
    /** The sag at the middle of the chain, in blocks. */
    private static final float SAG = 0.12F;
    /** The half width of a strip, in blocks. */
    private static final float HALF_WIDTH = 0.03F;
    /** The number of segments of a strip. */
    private static final int SEGMENTS = 16;
    /** The links are invisible farther than this distance from the camera, in blocks. */
    private static final double MAX_RENDER_DISTANCE = 96.0;

    private ChainRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ClientCartLinks.clear();
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        ClientCartLinks.forEach((frontId, backId) -> {
            Entity front = level.getEntity(frontId);
            Entity back = level.getEntity(backId);
            if (!(front instanceof AbstractMinecart) || !(back instanceof AbstractMinecart)
                    || front.isRemoved() || back.isRemoved()) {
                return false;
            }
            Vec3 start = front.getPosition(partialTick).add(0.0, HEIGHT, 0.0);
            Vec3 end = back.getPosition(partialTick).add(0.0, HEIGHT, 0.0);
            if (start.distanceToSqr(camera) < MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                renderChain(level, pose, buffers, start.subtract(camera), end.subtract(start), start, end);
            }
            return true;
        });
        buffers.endBatch(RenderType.leash());
    }

    private static void renderChain(ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 origin,
                                    Vec3 span, Vec3 start, Vec3 end) {
        float dx = (float) span.x;
        float dy = (float) span.y;
        float dz = (float) span.z;
        float horizontal = Mth.sqrt(dx * dx + dz * dz);
        // The side direction of the flat strip. A vertical chain uses the X axis.
        float sideX = horizontal > 1.0E-4F ? -dz / horizontal * HALF_WIDTH : HALF_WIDTH;
        float sideZ = horizontal > 1.0E-4F ? dx / horizontal * HALF_WIDTH : 0.0F;
        int startLight = LevelRenderer.getLightColor(level, BlockPos.containing(start));
        int endLight = LevelRenderer.getLightColor(level, BlockPos.containing(end));
        pose.pushPose();
        pose.translate(origin.x, origin.y, origin.z);
        Matrix4f matrix = pose.last().pose();
        // Each call of getBuffer starts a new strip.
        VertexConsumer flat = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= SEGMENTS; i++) {
            addPair(flat, matrix, dx, dy, dz, i, sideX, 0.0F, sideZ, startLight, endLight);
        }
        VertexConsumer upright = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= SEGMENTS; i++) {
            addPair(upright, matrix, dx, dy, dz, i, 0.0F, HALF_WIDTH, 0.0F, startLight, endLight);
        }
        pose.popPose();
    }

    private static void addPair(VertexConsumer buffer, Matrix4f matrix, float dx, float dy, float dz, int index,
                                float offsetX, float offsetY, float offsetZ, int startLight, int endLight) {
        float f = (float) index / SEGMENTS;
        float x = dx * f;
        float y = dy * f - SAG * 4.0F * f * (1.0F - f);
        float z = dz * f;
        int block = (int) Mth.lerp(f, LightTexture.block(startLight), LightTexture.block(endLight));
        int sky = (int) Mth.lerp(f, LightTexture.sky(startLight), LightTexture.sky(endLight));
        int light = LightTexture.pack(block, sky);
        // Light and dark links alternate, as the links of a chain.
        float shade = index % 2 == 0 ? 0.45F : 0.3F;
        buffer.addVertex(matrix, x - offsetX, y - offsetY, z - offsetZ).setColor(shade, shade, shade + 0.03F, 1.0F)
                .setLight(light);
        buffer.addVertex(matrix, x + offsetX, y + offsetY, z + offsetZ).setColor(shade, shade, shade + 0.03F, 1.0F)
                .setLight(light);
    }
}
