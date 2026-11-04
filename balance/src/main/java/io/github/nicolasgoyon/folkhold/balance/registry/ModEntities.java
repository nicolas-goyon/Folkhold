package io.github.nicolasgoyon.folkhold.balance.registry;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.DoubleChestMinecart;
import io.github.nicolasgoyon.folkhold.balance.rail.FurnaceLocomotive;
import io.github.nicolasgoyon.folkhold.balance.rail.HeavyLocomotive;
import io.github.nicolasgoyon.folkhold.balance.rail.ShulkerMinecart;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
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

    /** The heavy locomotive. Same size as a vanilla minecart. */
    public static final DeferredHolder<EntityType<?>, EntityType<HeavyLocomotive>> HEAVY_LOCOMOTIVE =
            ENTITY_TYPES.register("heavy_locomotive", () -> EntityType.Builder
                    .<HeavyLocomotive>of(HeavyLocomotive::new, MobCategory.MISC)
                    .sized(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .clientTrackingRange(8)
                    .build("heavy_locomotive"));

    /** The double chest minecart. Same size as a vanilla minecart. */
    public static final DeferredHolder<EntityType<?>, EntityType<DoubleChestMinecart>> DOUBLE_CHEST_MINECART =
            ENTITY_TYPES.register("double_chest_minecart", () -> EntityType.Builder
                    .<DoubleChestMinecart>of(DoubleChestMinecart::new, MobCategory.MISC)
                    .sized(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .clientTrackingRange(8)
                    .build("double_chest_minecart"));
    /** The shulker box minecart. Same size as a vanilla minecart. */
    public static final DeferredHolder<EntityType<?>, EntityType<ShulkerMinecart>> SHULKER_BOX_MINECART =
            ENTITY_TYPES.register("shulker_box_minecart", () -> EntityType.Builder
                    .<ShulkerMinecart>of(ShulkerMinecart::new, MobCategory.MISC)
                    .sized(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .clientTrackingRange(8)
                    .build("shulker_box_minecart"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::onRegisterCapabilities);
    }

    /** The item handlers of the minecarts with a container, as NeoForge gives them to the vanilla chest minecart. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        for (EntityType<? extends AbstractMinecartContainer> type
                : List.of(DOUBLE_CHEST_MINECART.get(), SHULKER_BOX_MINECART.get())) {
            event.registerEntity(Capabilities.ItemHandler.ENTITY, type, (cart, context) -> new InvWrapper(cart));
            event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, type, (cart, side) -> new InvWrapper(cart));
        }
    }
}
