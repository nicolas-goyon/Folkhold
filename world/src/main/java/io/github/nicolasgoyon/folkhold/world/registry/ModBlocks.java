package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlock;
import io.github.nicolasgoyon.folkhold.world.block.BuildingSignBlock;
import io.github.nicolasgoyon.folkhold.world.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
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

    /** Marker of an archer post in the village structure. The archer stands on top of it. */
    public static final DeferredBlock<Block> WATCH_POST = BLOCKS.registerSimpleBlock("watch_post",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD));

    /** Chest of the village bank. It resists explosions, as obsidian. */
    public static final DeferredBlock<BankChestBlock> BANK_CHEST = BLOCKS.register("bank_chest",
            () -> new BankChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(10.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /** Wall of the village bank. It resists explosions, as obsidian. */
    public static final DeferredBlock<Block> BANK_WALL = BLOCKS.registerSimpleBlock("bank_wall",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(4.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));

    /** The building signs, one block for each kind. */
    public static final Map<BuildingSignKind, DeferredBlock<BuildingSignBlock>> BUILDING_SIGNS = buildingSigns();

    private ModBlocks() {
    }

    private static Map<BuildingSignKind, DeferredBlock<BuildingSignBlock>> buildingSigns() {
        Map<BuildingSignKind, DeferredBlock<BuildingSignBlock>> signs = new EnumMap<>(BuildingSignKind.class);
        for (BuildingSignKind kind : BuildingSignKind.values()) {
            signs.put(kind, BLOCKS.register(kind.blockId(), () -> new BuildingSignBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())));
        }
        return Collections.unmodifiableMap(signs);
    }

    /** The building sign of a kind. */
    public static BuildingSignBlock buildingSign(BuildingSignKind kind) {
        return BUILDING_SIGNS.get(kind).get();
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
