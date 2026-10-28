package io.github.nicolasgoyon.folkhold.world.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.map.VanillaMapOverlay;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the territories on a vanilla map (see {@link VanillaMapOverlay}). The overlay is a texture of its own, drawn
 * over the map and under the decorations: the texture of the map does not change. Thus the overlay also works with
 * the mods that change the texture of the maps, for example the atlas of the maps of ImmediatelyFast.
 *
 * <p>The overlay is made again when the map changes (its colors or its data) and when the territories change.
 */
@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
// Mixin calls the injected methods, and gives them the parameters of the callbacks.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class MapInstanceMixin {
    /** The Z of the overlay: over the map (-0.01) and under the decorations (-0.02). */
    @Unique
    private static final float FOLKHOLD_Z = -0.015F;

    @Shadow
    private MapItemSavedData data;

    /** The id of the map: the server gives the center of the map with this id. */
    @Unique
    private int folkhold$mapId;
    @Unique
    @Nullable
    private DynamicTexture folkhold$overlay;
    @Unique
    @Nullable
    private ResourceLocation folkhold$overlayId;
    @Unique
    private boolean folkhold$dirty = true;
    @Unique
    private boolean folkhold$visible;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void folkhold$keepId(MapRenderer renderer, int id, MapItemSavedData mapData, CallbackInfo info) {
        folkhold$mapId = id;
    }

    @Inject(method = "forceUpload", at = @At("HEAD"))
    private void folkhold$mapChanged(CallbackInfo info) {
        folkhold$dirty = true;
    }

    @Inject(method = "replaceMapData", at = @At("HEAD"))
    private void folkhold$dataChanged(MapItemSavedData mapData, CallbackInfo info) {
        folkhold$dirty = true;
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void folkhold$drawTerritories(PoseStack poseStack, MultiBufferSource bufferSource, boolean active,
            int packedLight, CallbackInfo info) {
        if (folkhold$dirty) {
            folkhold$dirty = false;
            folkhold$paint();
        }
        if (!folkhold$visible || folkhold$overlayId == null) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.text(folkhold$overlayId));
        vertices.addVertex(matrix, 0.0F, 128.0F, FOLKHOLD_Z).setColor(-1).setUv(0.0F, 1.0F).setLight(packedLight);
        vertices.addVertex(matrix, 128.0F, 128.0F, FOLKHOLD_Z).setColor(-1).setUv(1.0F, 1.0F).setLight(packedLight);
        vertices.addVertex(matrix, 128.0F, 0.0F, FOLKHOLD_Z).setColor(-1).setUv(1.0F, 0.0F).setLight(packedLight);
        vertices.addVertex(matrix, 0.0F, 0.0F, FOLKHOLD_Z).setColor(-1).setUv(0.0F, 0.0F).setLight(packedLight);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void folkhold$close(CallbackInfo info) {
        if (folkhold$overlayId != null) {
            Minecraft.getInstance().getTextureManager().release(folkhold$overlayId);
        }
        folkhold$overlay = null;
        folkhold$overlayId = null;
    }

    /** Makes the overlay texture again. */
    @Unique
    private void folkhold$paint() {
        if (folkhold$overlay == null) {
            folkhold$overlay = new DynamicTexture(VanillaMapOverlay.SIZE, VanillaMapOverlay.SIZE, true);
            folkhold$overlayId = Minecraft.getInstance().getTextureManager().register(FolkholdWorld.MOD_ID
                    + "_map_territories/" + folkhold$mapId, folkhold$overlay);
        }
        NativeImage image = folkhold$overlay.getPixels();
        if (image == null) {
            folkhold$visible = false;
            return;
        }
        folkhold$visible = VanillaMapOverlay.paint(folkhold$mapId, data, image::setPixelRGBA);
        folkhold$overlay.upload();
    }
}
