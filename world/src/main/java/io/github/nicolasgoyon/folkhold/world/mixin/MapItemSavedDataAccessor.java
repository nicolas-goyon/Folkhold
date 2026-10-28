package io.github.nicolasgoyon.folkhold.world.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The data of a vanilla map with any center: the game makes only maps with a center on its grid (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.MapMoves}).
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    /** The map tracks the position of its holders. */
    @Accessor("trackingPosition")
    boolean folkhold$trackingPosition();

    /** The map tracks its holders also far from its area. */
    @Accessor("unlimitedTracking")
    boolean folkhold$unlimitedTracking();

    /** The constructor of the data, with the center of the map. */
    @Invoker("<init>")
    // Mixin replaces the body of the method.
    @SuppressWarnings("DoNotCallSuggester")
    static MapItemSavedData folkhold$create(int x, int z, byte scale, boolean trackingPosition,
            boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        throw new AssertionError("Mixin replaces this method");
    }
}
