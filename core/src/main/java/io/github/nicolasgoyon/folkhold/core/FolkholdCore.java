package io.github.nicolasgoyon.folkhold.core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Core mod.
 */
@Mod(FolkholdCore.MOD_ID)
public final class FolkholdCore {
    public static final String MOD_ID = "folkhold_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdCore(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
