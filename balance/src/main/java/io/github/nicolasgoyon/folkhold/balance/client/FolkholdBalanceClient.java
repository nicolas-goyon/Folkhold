package io.github.nicolasgoyon.folkhold.balance.client;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of the balance mod. The game loads this class only on the client.
 */
@Mod(value = FolkholdBalance.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdBalanceClient {

    public FolkholdBalanceClient(IEventBus modEventBus, ModContainer modContainer) {
        // Config screen in the mod list. Server values are editable only in a world.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(ModEntities.FURNACE_LOCOMOTIVE.get(),
                    context -> new LocomotiveRenderer<>(context, LocomotiveRenderer.FURNACE_COLOR));
            event.registerEntityRenderer(ModEntities.HEAVY_LOCOMOTIVE.get(),
                    context -> new LocomotiveRenderer<>(context, LocomotiveRenderer.HEAVY_COLOR));
            event.registerEntityRenderer(ModEntities.DOUBLE_CHEST_MINECART.get(), DoubleChestMinecartRenderer::new);
        });
        // The color of each locomotive on the icon of the vanilla furnace minecart, and of the double chest minecart.
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> {
            event.register((stack, layer) -> LocomotiveRenderer.FURNACE_COLOR, ModItems.FURNACE_LOCOMOTIVE.get());
            event.register((stack, layer) -> LocomotiveRenderer.HEAVY_COLOR, ModItems.HEAVY_LOCOMOTIVE.get());
            event.register((stack, layer) -> DoubleChestMinecartRenderer.ICON_COLOR,
                    ModItems.DOUBLE_CHEST_MINECART.get());
        });
        NeoForge.EVENT_BUS.addListener(ChainRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientCartLinks.clear());
    }
}
