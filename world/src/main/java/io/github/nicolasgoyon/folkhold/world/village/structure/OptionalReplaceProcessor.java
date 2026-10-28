package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Replaces blocks of a template by blocks of another mod, when the other mod is present. A replacement names the
 * output block by its id: with no block of this id, the block of the template stays. Thus a template can use the
 * blocks of an optional mod, for example the weathered blocks of Immersive Weathering, and keep vanilla blocks with no
 * mod.
 *
 * <p>The output block gets the values of the properties of the input block that it also has (for example the facing of
 * a door). Type {@code folkhold_world:optional_replace}. Field: {@code replacements}, a list of objects with an
 * {@code input} block and an {@code output} block id.
 */
public final class OptionalReplaceProcessor extends StructureProcessor {
    /**
     * A replacement.
     *
     * @param input  the block of the template
     * @param output the id of the block of the other mod
     */
    public record Replacement(Block input, ResourceLocation output) {
        public static final Codec<Replacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("input").forGetter(Replacement::input),
                ResourceLocation.CODEC.fieldOf("output").forGetter(Replacement::output))
                .apply(instance, Replacement::new));
    }

    public static final MapCodec<OptionalReplaceProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Replacement.CODEC.listOf().fieldOf("replacements").forGetter(OptionalReplaceProcessor::replacements))
            .apply(instance, OptionalReplaceProcessor::new));

    private final List<Replacement> replacements;

    public OptionalReplaceProcessor(List<Replacement> replacements) {
        this.replacements = List.copyOf(replacements);
    }

    /** The replacements. */
    public List<Replacement> replacements() {
        return replacements;
    }

    @Override
    @Nullable
    public StructureBlockInfo process(LevelReader level, BlockPos offset, BlockPos pos,
            StructureBlockInfo original, StructureBlockInfo current, StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        for (Replacement replacement : replacements) {
            if (current.state().is(replacement.input())) {
                Optional<Block> output = BuiltInRegistries.BLOCK.getOptional(replacement.output());
                if (output.isPresent()) {
                    return new StructureBlockInfo(current.pos(), copyProperties(current.state(),
                            output.get().defaultBlockState()), current.nbt());
                }
                return current;
            }
        }
        return current;
    }

    /** The output state with the values of the properties of the input state that it also has. */
    static BlockState copyProperties(BlockState input, BlockState output) {
        BlockState result = output;
        for (Property<?> property : input.getProperties()) {
            result = copy(input, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState input, BlockState output,
            Property<T> property) {
        for (Property<?> candidate : output.getProperties()) {
            if (candidate.getName().equals(property.getName())
                    && candidate.getValueClass() == property.getValueClass()) {
                @SuppressWarnings("unchecked")
                Property<T> same = (Property<T>) candidate;
                T value = input.getValue(property);
                if (same.getPossibleValues().contains(value)) {
                    return output.setValue(same, value);
                }
            }
        }
        return output;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.OPTIONAL_REPLACE.get();
    }
}
