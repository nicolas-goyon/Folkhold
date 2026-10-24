package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost.Ghost;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost.Phase;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the ghost of {@link ConstructionGhost} in the level, after the translucent blocks:
 * <ul>
 *   <li>the blocks of the ghost that it shows, from the vertex buffers of a {@link GhostMesh}: translucent, a little
 *       blue, with full light;</li>
 *   <li>an outline for a block with no block model, for example a chest or a bed;</li>
 *   <li>the box of the structure: blue while the ghost follows the view, gold when it is locked, green during the
 *       construction;</li>
 *   <li>a red outline for each block of the level that the construction removes.</li>
 * </ul>
 */
public final class ConstructionGhostRenderer {
    private static final double OUTLINE_INSET = 0.1;

    /** The vertex buffers of the current ghost, or null. */
    @Nullable
    private static GhostMesh mesh;

    private ConstructionGhostRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Ghost ghost = ConstructionGhost.current();
        Minecraft minecraft = Minecraft.getInstance();
        if (ghost == null || minecraft.level != ghost.level) {
            release();
            return;
        }
        if (mesh == null || !mesh.isFor(ghost)) {
            release();
            mesh = new GhostMesh(ghost);
        }
        Vec3 camera = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        mesh.render(event.getModelViewMatrix(), event.getProjectionMatrix(), camera, frustum);

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (BlockPos relative : mesh.outlines()) {
            AABB block = new AABB(relative.offset(ghost.origin));
            if (frustum.isVisible(block)) {
                LevelRenderer.renderLineBox(pose, lines, block.deflate(OUTLINE_INSET), 0.8F, 0.9F, 1.0F, 1.0F);
            }
        }
        if (ghost.phase == Phase.PLACING) {
            for (BlockPos obstacle : ghost.obstacles) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(obstacle).deflate(OUTLINE_INSET / 2), 1.0F, 0.3F,
                        0.25F, 1.0F);
            }
        }
        BoundingBox box = ghost.box;
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0, box.maxY() + 1.0,
                box.maxZ() + 1.0);
        float[] boxColor = switch (ghost.phase) {
            case PLACING -> ghost.locked ? new float[] {1.0F, 0.83F, 0.5F} : new float[] {0.45F, 0.8F, 1.0F};
            case WAITING, BUILDING -> new float[] {0.6F, 0.82F, 0.5F};
        };
        LevelRenderer.renderLineBox(pose, lines, area, boxColor[0], boxColor[1], boxColor[2], 1.0F);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** Frees the vertex buffers of the ghost. Called when the ghost changes or goes away. */
    static void release() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
    }
}
