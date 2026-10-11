package io.github.nicolasgoyon.folkhold.world;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold World mod.
 */
@Mod(FolkholdWorld.MOD_ID)
public final class FolkholdWorld {
    public static final String MOD_ID = "folkhold_world";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdWorld(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
