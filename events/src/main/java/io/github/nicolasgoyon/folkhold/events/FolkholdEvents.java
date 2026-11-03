package io.github.nicolasgoyon.folkhold.events;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.events.command.EventsCommands;
import io.github.nicolasgoyon.folkhold.events.common.ActiveEvents;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import io.github.nicolasgoyon.folkhold.events.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.events.registry.ModSounds;
import io.github.nicolasgoyon.folkhold.events.storm.Storms;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Events mod.
 */
@Mod(FolkholdEvents.MOD_ID)
public final class FolkholdEvents {
    public static final String MOD_ID = "folkhold_events";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdEvents(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, EventsConfig.SPEC);
        ModSounds.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(Earthquakes::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Storms::onLevelTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ActiveEvents.clearAll());
        NeoForge.EVENT_BUS.addListener(EventsCommands::onRegisterCommands);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
