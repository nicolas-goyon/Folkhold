package io.github.nicolasgoyon.folkhold.balance.registry;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.FurnaceLocomotive;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types of the balance mod. */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FolkholdBalance.MOD_ID);

    /** The furnace locomotive. Same size as a vanilla minecart. */
    public static final DeferredHolder<EntityType<?>, EntityType<FurnaceLocomotive>> FURNACE_LOCOMOTIVE =
            ENTITY_TYPES.register("furnace_locomotive", () -> EntityType.Builder
                    .<FurnaceLocomotive>of(FurnaceLocomotive::new, MobCategory.MISC)
                    .sized(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .clientTrackingRange(8)
                    .build("furnace_locomotive"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
