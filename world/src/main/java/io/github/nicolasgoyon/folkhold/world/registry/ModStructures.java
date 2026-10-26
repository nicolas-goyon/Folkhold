package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.FoundationProcessor;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.PierPiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.TerrainPiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure types, structure piece types and structure processor types of the world mod.
 */
public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, FolkholdWorld.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, FolkholdWorld.MOD_ID);
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, FolkholdWorld.MOD_ID);

    /** The faction village: the pieces of the jigsaw pools in a closed wall ({@link FortifiedVillageStructure}). */
    public static final DeferredHolder<StructureType<?>, StructureType<FortifiedVillageStructure>> FORTIFIED_VILLAGE =
            TYPES.register("fortified_village", () -> () -> FortifiedVillageStructure.CODEC);

    /** A segment of the palisade of a village ({@link PalisadePiece}). */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PALISADE =
            PIECES.register("palisade", () -> PalisadePiece::new);

    /** A path from a gate of a village to its streets ({@link VillagePathPiece}). */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> VILLAGE_PATH =
            PIECES.register("village_path", () -> (StructurePieceType.ContextlessType) VillagePathPiece::new);

    /** A pier from a water gate of a village ({@link PierPiece}). */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIER =
            PIECES.register("pier", () -> PierPiece::new);

    /** A piece with no blocks that changes the shape of the terrain ({@link TerrainPiece}). */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> TERRAIN =
            PIECES.register("terrain", () -> (StructurePieceType.ContextlessType) TerrainPiece::new);

    /** A foundation under a structure template ({@link FoundationProcessor}). */
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<FoundationProcessor>>
            FOUNDATION = PROCESSORS.register("foundation", () -> () -> FoundationProcessor.CODEC);

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        PIECES.register(modEventBus);
        PROCESSORS.register(modEventBus);
    }
}
