package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost.Ghost;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * The vertex buffers of a construction ghost on the graphics card: one buffer for each section of
 * {@link GhostSections}.
 *
 * <ul>
 *   <li>The vertices of a section are relative to the section. A move of the ghost changes only the offset of the
 *       draw, so the buffers stay.</li>
 *   <li>A section makes its buffer again when the blocks that the ghost shows in the section change (see
 *       {@link Ghost#sectionStamps}), at most {@link #MAX_BUILDS} sections in each frame. A turn or a mirror of the
 *       ghost needs a new mesh.</li>
 *   <li>A face between two blocks of the ghost is not in the buffer: the blocks of the ghost hide it.</li>
 *   <li>The faces are translucent: each section sorts its faces again when the camera moves by more than one block
 *       (at most {@link #MAX_SORTS} sections in each frame), and the far sections are drawn first.</li>
 * </ul>
 */
final class GhostMesh implements AutoCloseable {
    /** The opacity of the blocks of the ghost, from 0 to 255. */
    private static final int ALPHA = 150;
    private static final float TINT_RED = 0.75F;
    private static final float TINT_GREEN = 0.9F;
    private static final int MAX_BUILDS = 4;
    private static final int MAX_SORTS = 4;
    private static final double SORT_DISTANCE_SQR = 1.0;
    private static final int SECTION_SIZE = 16;
    private static final long MODEL_SEED = 42L;

    final Ghost ghost;
    final int shape;
    private final Long2ObjectMap<BlockState> grid = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final ByteBufferBuilder vertexBytes = new ByteBufferBuilder(1 << 18);
    private final ByteBufferBuilder indexBytes = new ByteBufferBuilder(1 << 16);
    private final View view = new View();
    private final RandomSource random = RandomSource.create();

    /** A section of the ghost and its vertex buffer. */
    private static final class Section {
        final BlockPos origin;
        /** The stamp of the blocks in the buffer, or -1 before the first build. */
        int stamp = -1;
        @Nullable
        VertexBuffer buffer;
        @Nullable
        MeshData.SortState sortState;
        /** The camera relative to the origin of the ghost at the last sort of the faces. */
        Vec3 sortedFrom = Vec3.ZERO;
        /** The shown blocks with no block model (for example a chest), relative to the origin of the ghost. */
        List<BlockPos> outlines = List.of();

        Section(BlockPos origin) {
            this.origin = origin;
        }
    }

    GhostMesh(Ghost ghost) {
        this.ghost = ghost;
        this.shape = ghost.shape;
        for (int i = 0; i < ghost.relatives.size(); i++) {
            grid.put(ghost.relatives.get(i).asLong(), ghost.placedStates.get(i));
        }
    }

    /** True if this mesh shows the blocks of the ghost: same ghost, and no turn or mirror since its creation. */
    boolean isFor(Ghost current) {
        return current == ghost && current.shape == shape;
    }

    /**
     * Makes the buffers of the changed sections again, then draws the visible sections.
     *
     * @param modelView  the model view matrix of the level
     * @param projection the projection matrix
     * @param camera     the position of the camera
     * @param frustum    the view frustum
     */
    void render(Matrix4f modelView, Matrix4f projection, Vec3 camera, Frustum frustum) {
        BlockPos origin = ghost.origin;
        Vec3 relativeCamera = camera.subtract(origin.getX(), origin.getY(), origin.getZ());
        int builds = 0;
        for (Long2ObjectMap.Entry<int[]> entry : ghost.sections.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            Section section = sections.computeIfAbsent(key, unused -> new Section(GhostSections.origin(key)));
            int stamp = ghost.sectionStamps.get(key);
            if (section.stamp != stamp && builds < MAX_BUILDS) {
                build(section, entry.getValue(), relativeCamera);
                section.stamp = stamp;
                builds++;
            }
        }
        List<Section> visible = new ArrayList<>();
        for (Section section : sections.values()) {
            if (section.buffer != null && frustum.isVisible(box(section))) {
                visible.add(section);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        visible.sort(Comparator.comparingDouble((Section section) -> distanceSqr(section, relativeCamera))
                .reversed());
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection,
                    Minecraft.getInstance().getWindow());
            shader.apply();
            int sorts = 0;
            for (Section section : visible) {
                VertexBuffer buffer = section.buffer;
                buffer.bind();
                if (sorts < MAX_SORTS && section.sortState != null
                        && section.sortedFrom.distanceToSqr(relativeCamera) > SORT_DISTANCE_SQR) {
                    sort(section, buffer, relativeCamera);
                    sorts++;
                }
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set((float) (origin.getX() + section.origin.getX() - camera.x),
                            (float) (origin.getY() + section.origin.getY() - camera.y),
                            (float) (origin.getZ() + section.origin.getZ() - camera.z));
                    shader.CHUNK_OFFSET.upload();
                }
                buffer.draw();
            }
            if (shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            }
            shader.clear();
        }
        VertexBuffer.unbind();
        type.clearRenderState();
    }

    /** The shown blocks with no block model, relative to the origin of the ghost. */
    List<BlockPos> outlines() {
        List<BlockPos> outlines = new ArrayList<>();
        for (Section section : sections.values()) {
            outlines.addAll(section.outlines);
        }
        return outlines;
    }

    /** Puts the faces of the shown blocks of a section in its buffer. */
    private void build(Section section, int[] indexes, Vec3 relativeCamera) {
        long start = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BufferBuilder builder = new BufferBuilder(vertexBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        VertexConsumer consumer = new GhostVertexConsumer(builder);
        PoseStack pose = new PoseStack();
        List<BlockPos> outlines = new ArrayList<>();
        for (int index : indexes) {
            if (!ghost.shown[index]) {
                continue;
            }
            BlockPos relative = ghost.relatives.get(index);
            BlockState state = ghost.placedStates.get(index);
            if (state.getRenderShape() != RenderShape.MODEL) {
                if (!state.isAir()) {
                    outlines.add(relative);
                }
                continue;
            }
            BakedModel model = dispatcher.getBlockModel(state);
            pose.pushPose();
            pose.translate(relative.getX() - section.origin.getX(), relative.getY() - section.origin.getY(),
                    relative.getZ() - section.origin.getZ());
            random.setSeed(MODEL_SEED);
            for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().tesselateWithoutAO(view, model, state, relative, pose, consumer, true,
                        random, MODEL_SEED, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
            }
            pose.popPose();
        }
        section.outlines = List.copyOf(outlines);
        MeshData mesh = builder.build();
        if (mesh == null) {
            closeBuffer(section);
            return;
        }
        section.sortState = mesh.sortQuads(indexBytes, sorting(section, relativeCamera));
        section.sortedFrom = relativeCamera;
        if (section.buffer == null) {
            section.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        section.buffer.bind();
        section.buffer.upload(mesh);
        VertexBuffer.unbind();
        FolkholdWorld.LOGGER.debug("Construction ghost: section {} built in {} µs", section.origin,
                (System.nanoTime() - start) / 1000L);
    }

    /** Sorts the faces of a section again for a new position of the camera. The buffer of the section is bound. */
    private void sort(Section section, VertexBuffer buffer, Vec3 relativeCamera) {
        ByteBufferBuilder.Result indexes = section.sortState.buildSortedIndexBuffer(indexBytes,
                sorting(section, relativeCamera));
        if (indexes != null) {
            buffer.uploadIndexBuffer(indexes);
        }
        section.sortedFrom = relativeCamera;
    }

    private static VertexSorting sorting(Section section, Vec3 relativeCamera) {
        return VertexSorting.byDistance((float) (relativeCamera.x - section.origin.getX()),
                (float) (relativeCamera.y - section.origin.getY()), (float) (relativeCamera.z - section.origin.getZ()));
    }

    private AABB box(Section section) {
        BlockPos min = ghost.origin.offset(section.origin);
        return new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + SECTION_SIZE, min.getY() + SECTION_SIZE,
                min.getZ() + SECTION_SIZE);
    }

    private static double distanceSqr(Section section, Vec3 relativeCamera) {
        double half = SECTION_SIZE / 2.0;
        return relativeCamera.distanceToSqr(section.origin.getX() + half, section.origin.getY() + half,
                section.origin.getZ() + half);
    }

    private static void closeBuffer(Section section) {
        if (section.buffer != null) {
            section.buffer.close();
            section.buffer = null;
        }
        section.sortState = null;
    }

    @Override
    public void close() {
        for (Section section : sections.values()) {
            closeBuffer(section);
        }
        sections.clear();
        vertexBytes.close();
        indexBytes.close();
    }

    /**
     * The blocks of the ghost for the block renderer, in the coordinates relative to the origin of the ghost. The
     * other positions are air, so a face of the ghost hides only behind another block of the ghost. The light is
     * full and the faces have no shade. The colors of the biomes come from the level.
     */
    private final class View implements BlockAndTintGetter {
        @Override
        public float getShade(Direction direction, boolean shade) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return ghost.level.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer lightType, BlockPos blockPos) {
            return getMaxLightLevel();
        }

        @Override
        public int getRawBrightness(BlockPos blockPos, int amount) {
            return getMaxLightLevel();
        }

        @Override
        public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
            return ghost.level.getBlockTint(blockPos.offset(ghost.origin), colorResolver);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = grid.get(pos.asLong());
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return ghost.level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return ghost.level.getMinBuildHeight();
        }
    }

    /**
     * A vertex consumer that gives the opacity and the blue tint of the ghost to all the vertices of a block model.
     * The block renderer writes opaque vertices.
     */
    private static final class GhostVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        GhostVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int unused) {
            delegate.setColor((int) (red * TINT_RED), (int) (green * TINT_GREEN), blue, ALPHA);
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
            int tinted = FastColor.ARGB32.color(ALPHA, (int) (FastColor.ARGB32.red(color) * TINT_RED),
                    (int) (FastColor.ARGB32.green(color) * TINT_GREEN), FastColor.ARGB32.blue(color));
            delegate.addVertex(x, y, z, tinted, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
        }
    }
}
