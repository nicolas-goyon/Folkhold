package io.github.nicolasgoyon.folkhold.world.construction;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.LevelView;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Makes the targets of the construction plans, and the view of the level for a plan:
 * <ul>
 *   <li>from a box of the level: the blocks of the box as they are now;</li>
 *   <li>from a structure template at a position: the blocks of the template, turned and mirrored as the settings tell.
 *       A jigsaw block becomes its final state, as in a village. A structure void block is not part of the target.</li>
 * </ul>
 */
public final class ConstructionTargets {
    private ConstructionTargets() {
    }

    /**
     * The blocks of a box of the level, as a target.
     *
     * @param level the level
     * @param from  a corner of the box
     * @param to    the opposite corner of the box
     * @return the target
     */
    public static Target fromLevel(Level level, BlockPos from, BlockPos to) {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            blocks.put(BlockKinds.toBuildPos(pos), describeAt(level, pos.immutable()));
        }
        return new Target(blocks);
    }

    /**
     * A block of a structure template, in the coordinates of the template.
     *
     * @param pos   the position of the block in the template
     * @param state the state of the block, with no turn and no mirror. A jigsaw block has its final state.
     * @param nbt   the data of the block entity in the template, or empty
     */
    public record TemplateBlock(BlockPos pos, BlockState state, Optional<CompoundTag> nbt) {
    }

    /**
     * The blocks of a structure template, in the coordinates of the template, with no turn and no mirror. A jigsaw
     * block becomes its final state, as in a village. The list has no structure void block.
     *
     * @param level    the level: it gives the blocks
     * @param template the template
     * @return the blocks, air included
     */
    public static List<TemplateBlock> templateBlocks(Level level, StructureTemplate template) {
        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        CompoundTag saved = template.save(new CompoundTag());
        ListTag paletteTag = saved.contains("palettes", Tag.TAG_LIST)
                ? saved.getList("palettes", Tag.TAG_LIST).getList(0) : saved.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        ListTag blockList = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag entry = blockList.getCompound(i);
            ListTag local = entry.getList("pos", Tag.TAG_INT);
            BlockState state = palette.get(entry.getInt("state"));
            Optional<CompoundTag> nbt = entry.contains("nbt", Tag.TAG_COMPOUND)
                    ? Optional.of(entry.getCompound("nbt")) : Optional.empty();
            if (state.is(Blocks.JIGSAW)) {
                state = jigsawFinalState(blockLookup, nbt);
                nbt = Optional.empty();
            }
            if (!state.is(Blocks.STRUCTURE_VOID)) {
                blocks.add(new TemplateBlock(new BlockPos(local.getInt(0), local.getInt(1), local.getInt(2)), state,
                        nbt));
            }
        }
        return blocks;
    }

    /**
     * The blocks of a structure template, as a target.
     *
     * @param level    the level of the construction: it gives the blocks and the block entities
     * @param template the template
     * @param origin   the position of the origin of the template
     * @param settings the rotation and the mirror of the template
     * @return the target
     */
    public static Target fromTemplate(Level level, StructureTemplate template, BlockPos origin,
            StructurePlaceSettings settings) {
        HolderLookup.Provider registries = level.registryAccess();
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (TemplateBlock block : templateBlocks(level, template)) {
            BlockPos pos = StructureTemplate.calculateRelativePosition(settings, block.pos()).offset(origin);
            BlockState state = block.state().mirror(settings.getMirror()).rotate(level, pos, settings.getRotation());
            Optional<String> data = block.nbt().filter(tag -> state.is(BlockKinds.KEEP_DATA))
                    .flatMap(tag -> normalData(pos, state, tag, registries));
            blocks.put(BlockKinds.toBuildPos(pos), BlockKinds.describe(pos, state, data));
        }
        return new Target(blocks);
    }

    /** The state of a jigsaw block after the generation: its final state, or air. */
    private static BlockState jigsawFinalState(HolderLookup<Block> blockLookup, Optional<CompoundTag> nbt) {
        String text = nbt.map(tag -> tag.getString("final_state")).orElse("minecraft:air");
        try {
            return BlockStateParser.parseForBlock(blockLookup, text, true).blockState();
        } catch (CommandSyntaxException e) {
            FolkholdWorld.LOGGER.warn("Construction: unknown final state of a jigsaw block: {}", text);
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * The data of a block entity of a template, in the form of the data of the level. The block entity loads the data,
     * and saves it again.
     */
    private static Optional<String> normalData(BlockPos pos, BlockState state, CompoundTag nbt,
            HolderLookup.Provider registries) {
        BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, nbt, registries);
        return blockEntity == null ? Optional.empty()
                : Optional.of(blockEntity.saveWithoutMetadata(registries).toString());
    }

    /**
     * A view of the blocks of the level for a plan. The view keeps each block that it reads: the level must not change
     * during the plan.
     */
    public static LevelView view(Level level) {
        Map<BuildPos, BuildBlock> cache = new HashMap<>();
        return pos -> cache.computeIfAbsent(pos, key -> describeAt(level, BlockKinds.toBlockPos(key)));
    }

    /** The block of the level at a position, for a plan. */
    public static BuildBlock describeAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return BlockKinds.describe(pos, state, dataAt(level, pos, state));
    }

    /** The data of the block entity at a position, for a block of {@link BlockKinds#KEEP_DATA}. */
    static Optional<String> dataAt(Level level, BlockPos pos, BlockState state) {
        if (!state.is(BlockKinds.KEEP_DATA)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? Optional.empty()
                : Optional.of(blockEntity.saveWithoutMetadata(level.registryAccess()).toString());
    }
}
