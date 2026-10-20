package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost.Ghost;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost.Phase;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the ghost of {@link ConstructionGhost} in the level, after the translucent blocks:
 * <ul>
 *   <li>the blocks of the ghost that are not in place: translucent, a little blue, with full light;</li>
 *   <li>an outline for a block with no block model, for example a chest or a bed;</li>
 *   <li>the box of the structure: blue while the ghost follows the view, gold when it is locked, green during the
 *       construction;</li>
 *   <li>a red outline for each block of the level that the construction removes.</li>
 * </ul>
 */
public final class ConstructionGhostRenderer {
    /** The opacity of the blocks of the ghost, from 0 to 255. */
    private static final int ALPHA = 150;
    private static final float TINT_RED = 0.75F;
    private static final float TINT_GREEN = 0.9F;
    private static final double OUTLINE_INSET = 0.1;

    private ConstructionGhostRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Ghost ghost = ConstructionGhost.current();
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ghost == null
                || minecraft.level != ghost.level) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        VertexConsumer blocks = new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()), ALPHA);
        RandomSource random = RandomSource.create();
        List<BlockPos> positions = ghost.positions;
        List<BlockState> states = ghost.placedStates;
        boolean[] outlined = new boolean[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState state = states.get(i);
            if (ghost.level.getBlockState(pos).is(state.getBlock()) || !frustum.isVisible(new AABB(pos))) {
                continue;
            }
            if (state.getRenderShape() != RenderShape.MODEL) {
                outlined[i] = true;
                continue;
            }
            BakedModel model = dispatcher.getBlockModel(state);
            int color = minecraft.getBlockColors().getColor(state, ghost.level, pos, 0);
            float red = color == -1 ? 1.0F : FastColor.ARGB32.red(color) / 255.0F;
            float green = color == -1 ? 1.0F : FastColor.ARGB32.green(color) / 255.0F;
            float blue = color == -1 ? 1.0F : FastColor.ARGB32.blue(color) / 255.0F;
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            random.setSeed(42L);
            for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().renderModel(pose.last(), blocks, state, model, red * TINT_RED,
                        green * TINT_GREEN, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, type);
            }
            pose.popPose();
        }
        buffers.endBatch(RenderType.translucent());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (int i = 0; i < positions.size(); i++) {
            if (outlined[i]) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(positions.get(i)).deflate(OUTLINE_INSET), 0.8F,
                        0.9F, 1.0F, 1.0F);
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

    /**
     * A vertex consumer that gives the same opacity to all the vertices of a block model. The block renderer writes
     * opaque vertices.
     */
    private static final class GhostVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        GhostVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int unused) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay,
                int packedLight, float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, FastColor.ARGB32.color(alpha, color), u, v, packedOverlay, packedLight,
                    normalX, normalY, normalZ);
        }
    }
}
