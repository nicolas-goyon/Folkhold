package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks of the world mod. */
public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FolkholdWorld.MOD_ID);

    /** Hard to break, as obsidian: the player must want to destroy the village. */
    public static final DeferredBlock<VillageHeartBlock> VILLAGE_HEART = BLOCKS.register("village_heart",
            () -> new VillageHeartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 7)));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
