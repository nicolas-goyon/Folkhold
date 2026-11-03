package io.github.nicolasgoyon.folkhold.balance.client;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
        NeoForge.EVENT_BUS.addListener(ChainRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientCartLinks.clear());
    }
}
