package io.github.nicolasgoyon.folkhold.world.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.nicolasgoyon.folkhold.world.journal.SpyglassMaps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The update of a vanilla map in the hand of a player (see {@link SpyglassMaps}): a larger radius with a spyglass, and
 * only the chunks that are loaded.
 */
@Mixin(MapItem.class)
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class MapItemMixin {
    /** The radius of the update: the first constant 128 of the method, in {@code 128 / scale}. */
    @ModifyExpressionValue(method = "update", at = @At(value = "CONSTANT", args = "intValue=128", ordinal = 0))
    private int folkhold$spyglassRadius(int radius, @Local(argsOnly = true) Entity viewer) {
        return SpyglassMaps.radius(radius, viewer);
    }

    /** A chunk that is not loaded is empty for the update: the update does not load or generate it. */
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk folkhold$loadedChunk(Level level, int x, int z, Operation<LevelChunk> original) {
        if (level instanceof ServerLevel server && server.getChunkSource().getChunkNow(x, z) == null) {
            return new EmptyLevelChunk(level, new ChunkPos(x, z),
                    level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        }
        return original.call(level, x, z);
    }
}
