package io.github.nicolasgoyon.folkhold.events.client;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of the events mod. The game loads this class only on the client.
 */
@Mod(value = FolkholdEvents.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdEventsClient {

    public FolkholdEventsClient(IEventBus modEventBus, ModContainer modContainer) {
        // Config screen in the mod list. Server values are editable only in a world.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CameraShake::onClientTick);
        NeoForge.EVENT_BUS.addListener(CameraShake::onCameraAngles);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> CameraShake.clear());
        // A new player entity: a change of dimension or a respawn. The shake of the old place stops.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> CameraShake.clear());
    }
}
