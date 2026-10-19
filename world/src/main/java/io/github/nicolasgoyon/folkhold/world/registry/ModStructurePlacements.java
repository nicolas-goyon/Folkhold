package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.FixedChunkPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure placement types of the world mod.
 */
public final class ModStructurePlacements {
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, FolkholdWorld.MOD_ID);

    /** One start chunk ({@link FixedChunkPlacement}). */
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<FixedChunkPlacement>>
            FIXED_CHUNK = PLACEMENTS.<StructurePlacementType<FixedChunkPlacement>>register("fixed_chunk",
                    () -> () -> FixedChunkPlacement.CODEC);

    private ModStructurePlacements() {
    }

    public static void register(IEventBus modEventBus) {
        PLACEMENTS.register(modEventBus);
    }
}
