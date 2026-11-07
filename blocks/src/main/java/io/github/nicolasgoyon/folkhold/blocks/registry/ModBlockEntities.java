package io.github.nicolasgoyon.folkhold.blocks.registry;

import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The block entity types of the library. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FolkholdBlocks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JobSignBlockEntity>> JOB_SIGN =
            BLOCK_ENTITY_TYPES.register("job_sign",
                    () -> BlockEntityType.Builder.of(JobSignBlockEntity::new, ModBlocks.JOB_SIGN.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
