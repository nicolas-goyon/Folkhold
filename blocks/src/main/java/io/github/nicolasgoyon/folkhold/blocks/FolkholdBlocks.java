package io.github.nicolasgoyon.folkhold.blocks;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.BrazierWater;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeatures;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Blocks mod: the library of the Folkhold blocks.
 *
 * <p>The library contains the blocks: shape, block states, placement of the parts, light, model, item, loot table and
 * names. Each block also has the behavior that a player expects from a similar vanilla block, with no other Folkhold
 * mod. For example, a player lights and puts out a brazier as a campfire (see {@link BrazierBlock}).
 *
 * <p>The library has no game logic. The other Folkhold mods add the features: they register a {@link BlockFeature}
 * for a block (see {@link BlockFeatures}).
 */
@Mod(FolkholdBlocks.MOD_ID)
public final class FolkholdBlocks {
    public static final String MOD_ID = "folkhold_blocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdBlocks(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(BrazierWater::onProjectileImpact);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
