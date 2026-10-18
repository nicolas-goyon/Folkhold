package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types of the world mod. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FolkholdWorld.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VillageHeartBlockEntity>> VILLAGE_HEART =
            BLOCK_ENTITY_TYPES.register("village_heart",
                    () -> BlockEntityType.Builder.of(VillageHeartBlockEntity::new, ModBlocks.VILLAGE_HEART.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankChestBlockEntity>> BANK_CHEST =
            BLOCK_ENTITY_TYPES.register("bank_chest",
                    () -> BlockEntityType.Builder.of(BankChestBlockEntity::new, ModBlocks.BANK_CHEST.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
