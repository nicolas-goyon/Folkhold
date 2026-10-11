package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.renderer.FactionUnitRenderer;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client entry point of the world mod. The game loads this class only on the client.
 */
@Mod(value = FolkholdWorld.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdWorldClient {

    public FolkholdWorldClient(IEventBus modEventBus, ModContainer modContainer) {
        // Config screen in the mod list. Server values are editable only in a world.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(FolkholdWorldClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUARD.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHER.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.CAPTAIN.get(), FactionUnitRenderer::new);
    }
}
