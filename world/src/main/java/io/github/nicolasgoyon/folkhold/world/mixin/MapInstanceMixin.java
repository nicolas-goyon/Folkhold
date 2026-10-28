package io.github.nicolasgoyon.folkhold.world.mixin;

import io.github.nicolasgoyon.folkhold.world.client.map.VanillaMapOverlay;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "updateTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void folkhold$drawTerritories(CallbackInfo info) {
        if (texture.getPixels() != null) {
            VanillaMapOverlay.draw(data, (x, z, abgr) -> texture.getPixels().setPixelRGBA(x, z, abgr));
        }
    }
}
