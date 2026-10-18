package io.github.nicolasgoyon.folkhold.blocks;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeatures;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Blocks mod: the library of the Folkhold blocks.
 *
 * <p>The library contains the blocks with no game logic: shape, block states, placement of the parts, light, model,
 * item, loot table and names. The other Folkhold mods add the features: they register a {@link BlockFeature} for a
 * block (see {@link BlockFeatures}).
 */
@Mod(FolkholdBlocks.MOD_ID)
public final class FolkholdBlocks {
    public static final String MOD_ID = "folkhold_blocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdBlocks(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
