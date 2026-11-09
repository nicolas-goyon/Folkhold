package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.construction.Walk;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.BlockKinds;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The reach check of the village pieces: a villager can walk from the front of a piece to each work block and to each
 * bed of the piece (see {@link #unreachable}).
 */
final class TemplateReach {
    /** The name of the jigsaw connector at the front of a home or a job building: the street is in front of it. */
    private static final String HOUSE_FRONT = FolkholdWorld.MOD_ID + ":house_front";
    /**
     * The work blocks: the job sites of the vanilla professions, the containers and the work tables. A cauldron and an
     * anvil of each kind are work blocks too (see {@link #isWorkBlock}).
     */
    private static final Set<Block> WORK_BLOCKS = Set.of(Blocks.BARREL, Blocks.CHEST, Blocks.TRAPPED_CHEST,
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE, Blocks.LOOM, Blocks.LECTERN, Blocks.FLETCHING_TABLE, Blocks.STONECUTTER,
            Blocks.COMPOSTER, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND);

    private TemplateReach() {
    }

    /**
     * The work blocks and the bed heads of a template that a walk from the front of the template does not reach.
     *
     * <p>The front is the side of the jigsaw connector {@code house_front}: the street is in front of it. The walk
     * starts at each position of the front row: the positions at the level of the connector, on the side of the
     * template that the connector faces, with the feet and the head free. The street gives the floor of the front row.
     *
     * <p>A position is a position to stand when the block at the feet and the block at the head let a villager walk
     * through them, and a villager walks on the block below (see {@link BlockKinds#describe}). A villager cannot stand in
     * the space of a work block or a bed, but it can stand on top of it. From a position, the walk goes to a position to
     * stand 1 block to the north, south, east or west, at the same level or 1 block higher or lower. A step 1 block
     * higher also needs a free block 2 blocks above the start position: the head of the villager goes through it. A
     * block is reached when a reached position is next to it, to the north, south, east or west, with the feet or the
     * head at the level of the block.
     *
     * @param level    the level, for the block registry
     * @param template the template of the piece
     * @return the unreached blocks as {@code <block> <x>, <y>, <z>}, sorted, or a problem when the template has not
     *         exactly 1 connector {@code house_front}
     */
    static List<String> unreachable(ServerLevel level, StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> fronts = template.filterBlocks(BlockPos.ZERO,
                new StructurePlaceSettings(), Blocks.JIGSAW).stream()
                .filter(info -> info.nbt() != null
                        && HOUSE_FRONT.equals(info.nbt().getString(JigsawBlockEntity.NAME)))
                .toList();
        if (fronts.size() != 1) {
            return List.of(fronts.size() + " connectors " + HOUSE_FRONT);
        }
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (ConstructionTargets.TemplateBlock block : ConstructionTargets.templateBlocks(level, template)) {
            states.put(block.pos(), block.state());
        }
        Vec3i size = template.getSize();
        Deque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> reached = new HashSet<>();
        for (BlockPos start : frontRow(fronts.get(0), size)) {
            if (free(states, start) && free(states, start.above()) && reached.add(start)) {
                open.add(start);
            }
        }
        while (!open.isEmpty()) {
            BlockPos pos = open.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(side).above(dy);
                    if (next.getX() >= 0 && next.getX() < size.getX() && next.getZ() >= 0 && next.getZ() < size.getZ()
                            && (dy <= 0 || free(states, pos.above(2))) && standable(states, next)
                            && reached.add(next)) {
                        open.add(next);
                    }
                }
            }
        }
        List<String> blocks = new ArrayList<>();
        states.forEach((pos, state) -> {
            boolean bedHead = state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
            if ((isWorkBlock(state) || bedHead) && Direction.Plane.HORIZONTAL.stream().map(pos::relative)
                    .noneMatch(next -> reached.contains(next) || reached.contains(next.below()))) {
                blocks.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " " + pos.toShortString());
            }
        });
        blocks.sort(null);
        return blocks;
    }

    /**
     * The front row of a template: the positions at the level of the connector, on the side of the template that the
     * connector faces.
     */
    private static List<BlockPos> frontRow(StructureTemplate.StructureBlockInfo front, Vec3i size) {
        Direction facing = JigsawBlock.getFrontFacing(front.state());
        BlockPos pos = front.pos();
        List<BlockPos> row = new ArrayList<>();
        if (facing.getAxis() == Direction.Axis.Z) {
            int z = facing == Direction.NORTH ? 0 : size.getZ() - 1;
            for (int x = 0; x < size.getX(); x++) {
                row.add(new BlockPos(x, pos.getY(), z));
            }
        } else {
            int x = facing == Direction.WEST ? 0 : size.getX() - 1;
            for (int z = 0; z < size.getZ(); z++) {
                row.add(new BlockPos(x, pos.getY(), z));
            }
        }
        return row;
    }

    /** A villager can stand at the position: the feet and the head are free, and the block below carries it. */
    private static boolean standable(Map<BlockPos, BlockState> states, BlockPos pos) {
        return free(states, pos) && free(states, pos.above()) && walk(states, pos.below()) == Walk.ON;
    }

    /**
     * A villager walks through the block at the position. The space of a work block or a bed is never free: a villager
     * cannot stand in it, but it can stand on top of it (see {@link #standable}).
     */
    private static boolean free(Map<BlockPos, BlockState> states, BlockPos pos) {
        BlockState state = states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        return !isWorkBlock(state) && !(state.getBlock() instanceof BedBlock) && walk(states, pos) == Walk.THROUGH;
    }

    private static Walk walk(Map<BlockPos, BlockState> states, BlockPos pos) {
        return BlockKinds.describe(pos, states.getOrDefault(pos, Blocks.AIR.defaultBlockState()), Optional.empty())
                .walk();
    }

    private static boolean isWorkBlock(BlockState state) {
        return WORK_BLOCKS.contains(state.getBlock()) || state.getBlock() instanceof AbstractCauldronBlock
                || state.getBlock() instanceof AnvilBlock;
    }
}
