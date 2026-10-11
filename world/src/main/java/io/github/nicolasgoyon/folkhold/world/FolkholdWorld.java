package io.github.nicolasgoyon.folkhold.world;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold World mod.
 */
@Mod(FolkholdWorld.MOD_ID)
public final class FolkholdWorld {
    public static final String MOD_ID = "folkhold_world";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdWorld(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, WorldConfig.SPEC);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
