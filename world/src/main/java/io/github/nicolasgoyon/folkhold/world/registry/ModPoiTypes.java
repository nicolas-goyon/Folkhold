package io.github.nicolasgoyon.folkhold.world.registry;

import com.google.common.collect.ImmutableSet;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Points of interest of the world mod (spike #640).
 *
 * <p>{@link #JOB_SIGN}: all the states of the job sign. The type has 0 tickets, so no vanilla villager can claim a
 * job sign with {@code AcquirePoi}. Folkhold sets the {@code JOB_SITE} memory of a worker directly.
 */
public final class ModPoiTypes {
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, FolkholdWorld.MOD_ID);

    public static final ResourceKey<PoiType> JOB_SIGN_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "job_sign"));

    public static final DeferredHolder<PoiType, PoiType> JOB_SIGN = POI_TYPES.register("job_sign",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.JOB_SIGN.get().getStateDefinition().getPossibleStates()),
                    0, 1));

    private ModPoiTypes() {
    }

    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
    }
}
