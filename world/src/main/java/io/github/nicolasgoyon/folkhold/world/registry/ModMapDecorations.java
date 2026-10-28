package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The map decoration types of the world mod. The icon of a type is the sprite
 * {@code textures/map/decorations/<name>.png} of the world mod.
 */
public final class ModMapDecorations {
    private static final DeferredRegister<MapDecorationType> TYPES =
            DeferredRegister.create(Registries.MAP_DECORATION_TYPE, FolkholdWorld.MOD_ID);

    /** The waypoint of a village on a vanilla map (see {@link io.github.nicolasgoyon.folkhold.world.journal.MapWaypoints}). */
    public static final DeferredHolder<MapDecorationType, MapDecorationType> VILLAGE = TYPES.register("village",
            () -> new MapDecorationType(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village"), true,
                    -1, false, false));

    private ModMapDecorations() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
