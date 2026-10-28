package io.github.nicolasgoyon.folkhold.world.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nicolasgoyon.folkhold.world.client.map.VanillaMapOverlay;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the territories on the texture of a vanilla map (see {@link VanillaMapOverlay}), after the colors of the map
 * and before the upload of the texture.
 */
@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
// Mixin calls the injected method, and gives it the parameter of the callback.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class MapInstanceMixin {
    @Shadow
    private MapItemSavedData data;

    @Shadow
    @Final
    private DynamicTexture texture;

    /** The id of the map: the server gives the center of the map with this id. */
    @Unique
    private int folkhold$mapId;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void folkhold$keepId(MapRenderer renderer, int id, MapItemSavedData mapData, CallbackInfo info) {
        folkhold$mapId = id;
    }

    @Inject(method = "updateTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void folkhold$drawTerritories(CallbackInfo info) {
        NativeImage image = texture.getPixels();
        if (image != null) {
            VanillaMapOverlay.draw(folkhold$mapId, data, new VanillaMapOverlay.Pixels() {
                @Override
                public int get(int x, int z) {
                    return image.getPixelRGBA(x, z);
                }

                @Override
                public void set(int x, int z, int abgr) {
                    image.setPixelRGBA(x, z, abgr);
                }
            });
        }
    }
}
