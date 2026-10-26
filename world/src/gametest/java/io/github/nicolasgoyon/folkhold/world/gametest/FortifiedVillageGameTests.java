package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village structure of the faction villages ({@code folkhold_world:village}): the pieces of the
 * pools in a closed wall. The tests generate the structure in chunks far from the other tests, on the flat terrain of
 * the GameTest world.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FortifiedVillageGameTests {
    /** The first candidate chunk of the village: far from the spawn and from the areas of the other tests. */
    private static final int FIRST_CHUNK = 6000;
    /** The distance between 2 candidate chunks, in chunks. */
    private static final int CANDIDATE_STEP = 12;
    private static final int CANDIDATES = 8;
    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/tower");
    private static final ResourceLocation GATE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/gate");
    /** The templates of the plots: the houses and the bank. */
    private static final Set<ResourceLocation> HOUSES = Set.of(
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_cottage"),
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_barracks"),
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_forge"),
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_storehouse"),
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/bank"));
    /** The walk on the ground falls this number of blocks at most. */
    private static final int MAX_FALL = 8;
    /** A walk of fewer positions did not start correctly. */
    private static final int MIN_WALK = 1000;

    private FortifiedVillageGameTests() {
    }

    /**
     * A generated village has its heart on the plaza, a tower at each corner, a gate on each side and a palisade with
     * no gap. From the heart, a walk on the ground does not go out while the gates stay closed. With the gates open,
     * the walk goes out.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_wall")
    public static void generatedVillageHasAClosedWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = generate(helper, level);
        VillageReference reference = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        BoundingBox area = reference.area().orElseThrow();

        helper.assertTrue(level.getBlockState(village.heart()).is(ModBlocks.VILLAGE_HEART.get()),
                "The heart must be in the middle of the plaza: " + village.heart());
        long towers = count(reference, TOWER);
        long gates = count(reference, GATE);
        helper.assertTrue(towers >= 4, "A tower must be at each corner: " + towers);
        helper.assertTrue(gates == 4, "A gate must be on each side: " + gates);
        helper.assertTrue(village.start().getPieces().stream().anyMatch(PalisadePiece.class::isInstance),
                "The wall must have a palisade");
        helper.assertTrue(village.start().getPieces().stream().anyMatch(VillagePathPiece.class::isInstance),
                "A path must go from a gate to the streets");

        Walk closed = walk(level, village.heart(), area);
        helper.assertTrue(closed.positions() >= MIN_WALK, "The walk must go through the village: "
                + closed.positions() + " positions");
        helper.assertTrue(closed.exit().isEmpty(), "The walk must not go out of the palisade "
                + area + ": out at " + closed.exit().orElse(null));

        List<BlockPos> fenceGates = blocksIn(level, area, state -> state.getBlock() instanceof FenceGateBlock);
        helper.assertTrue(fenceGates.size() == 4 * 3, "Each gate must have 3 fence gates: " + fenceGates.size());
        for (BlockPos gate : fenceGates) {
            level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
        }
        Walk open = walk(level, village.heart(), area);
        helper.assertTrue(open.exit().isPresent(), "With the gates open, the walk must go out");
        BlockPos exit = open.exit().get();
        helper.assertTrue(fenceGates.stream().anyMatch(gate -> gate.distManhattan(exit.atY(gate.getY())) <= 2),
                "The walk must go out through a gate: " + exit);
        helper.succeed();
    }

    /**
     * The heart of a generated village scans the area of the structure. It finds a post in each tower and the fence
     * gates of the 4 gates. The reference of the repairs has the palisade, and it matches the blocks after the
     * generation: no block is damaged. A missing log of the palisade is damaged.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_heart")
    public static void heartScansTheStructureAndRepairsThePalisade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = generate(helper, level);
        VillageReference reference = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        helper.assertTrue(level.getBlockEntity(village.heart()) instanceof VillageHeartBlockEntity,
                "The heart must have its block entity");
        VillageHeartBlockEntity heart = (VillageHeartBlockEntity) level.getBlockEntity(village.heart());
        heart.setRepairReference(reference);

        helper.assertTrue(heart.getLayout().scan(level, heart.scanArea(level)), "The heart must scan the village");
        VillageLayout layout = heart.getLayout();
        long towers = count(reference, TOWER);
        helper.assertTrue(layout.posts().size() == towers, "Each tower must have a post: " + layout.posts().size()
                + " posts, " + towers + " towers");
        helper.assertTrue(layout.gates().size() == 4 * 3, "The scan must find the 4 gates: " + layout.gates());
        helper.assertFalse(layout.bells().isEmpty(), "The scan must find the bell of the plaza");
        helper.assertTrue(layout.questBoards().size() == 1, "The scan must find the quest board");

        Set<BlockPos> damaged = VillageRepairs.damaged(level, heart, reference);
        helper.assertTrue(damaged.isEmpty(), "No block must be damaged after the generation: "
                + damaged.stream().limit(10).toList());
        VillageReference.Palisade palisade = reference.palisades().get(0);
        BlockPos log = palisade.start().above(PalisadeHeights.LOGS);
        helper.assertTrue(level.getBlockState(log).is(palisade.log().getBlock()), "A log of the palisade at " + log);
        level.setBlock(log, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(VillageRepairs.damaged(level, heart, reference).contains(log),
                "A missing log of the palisade must be damaged: " + log);
        helper.succeed();
    }

    /**
     * The plots of the generated villages: a plot with a house has a junction, a free plot has none. The wall keeps
     * the space of a home on each free plot: the space is inside the area of the village, and has no palisade.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_plots")
    public static void wallKeepsTheSpaceOfTheFreePlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(Settlements.VILLAGE_STRUCTURE);
        StructureTemplatePool homes = ((FortifiedVillageStructure) structure).homes().value();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int free = 0;
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK + CANDIDATE_STEP);
            StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            VillageReference reference = VillageReference.of(level, start.getPieces()).orElseThrow();
            BoundingBox area = reference.area().orElseThrow();
            long houses = reference.pieces().stream().filter(piece -> HOUSES.contains(piece.template())).count();
            long built = reference.plots().stream().filter(VillagePlots.Plot::built).count();
            helper.assertTrue(built == houses, "Each house must have its plot: " + houses + " houses, " + built
                    + " built plots in " + chunk);
            for (VillagePlots.Plot plot : reference.plots()) {
                if (plot.built()) {
                    continue;
                }
                free++;
                BoundingBox space = VillagePlots.space(level.getStructureManager(), homes, plot).orElseThrow();
                helper.assertTrue(space.minX() > area.minX() && space.maxX() < area.maxX()
                        && space.minZ() > area.minZ() && space.maxZ() < area.maxZ(),
                        "The space of a free plot must be inside the palisade: " + space + " in " + area);
                boolean palisade = reference.palisadeBlocks().keySet().stream()
                        .anyMatch(pos -> pos.getX() >= space.minX() && pos.getX() <= space.maxX()
                                && pos.getZ() >= space.minZ() && pos.getZ() <= space.maxZ());
                helper.assertFalse(palisade, "The space of a free plot must have no palisade: " + space);
            }
        }
        helper.assertTrue(free > 0, "The villages of the test must have a free plot");
        helper.succeed();
    }

    /** A generated village and the position of its heart. */
    private record Village(StructureStart start, BlockPos heart) {
    }

    /** The result of a walk: the number of positions, and a position out of the palisade, if any. */
    private record Walk(int positions, Optional<BlockPos> exit) {
    }

    /**
     * Generates a village and places it in the level. The village goes to the first candidate chunk where no other
     * structure is: the GameTest world generates the vanilla villages.
     */
    private static Village generate(GameTestHelper helper, ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(Settlements.VILLAGE_STRUCTURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK);
            StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            BoundingBox box = start.getBoundingBox();
            if (hasOtherStructures(level, box)) {
                continue;
            }
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                            new BoundingBox(x << 4, level.getMinBuildHeight(), z << 4, (x << 4) + 15,
                                    level.getMaxBuildHeight(), (z << 4) + 15),
                            new ChunkPos(x, z));
                }
            }
            BoundingBox plaza = start.getPieces().get(0).getBoundingBox();
            BlockPos heart = new BlockPos((plaza.minX() + plaza.maxX()) / 2, plaza.minY() + 1,
                    (plaza.minZ() + plaza.maxZ()) / 2);
            return new Village(start, heart);
        }
        throw new IllegalStateException("No candidate chunk is free of other structures");
    }

    /** True if a chunk of a box has a structure, or a part of one. The method generates the chunks. */
    private static boolean hasOtherStructures(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (!level.getChunk(x, z).getAllReferences().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The number of template pieces of a reference with a template. */
    private static long count(VillageReference reference, ResourceLocation template) {
        return reference.pieces().stream().filter(piece -> piece.template().equals(template)).count();
    }

    /** The positions of the blocks of an area that pass a test. */
    private static List<BlockPos> blocksIn(ServerLevel level, BoundingBox area, Predicate<BlockState> test) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(),
                area.maxZ())) {
            if (test.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /**
     * A walk on the ground from a position next to the heart. A step goes to a block side by side: 1 block up at
     * most, or down by a fall of {@link #MAX_FALL} blocks at most. The walk does not go on stairs: the stairs of the
     * towers go to the top of the palisade. A walk on the ground does not use them. The walk stops at the first
     * position out of the area.
     */
    private static Walk walk(ServerLevel level, BlockPos heart, BoundingBox area) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos start = heart.relative(direction);
            if (canStand(level, start) && seen.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (pos.getX() < area.minX() || pos.getX() > area.maxX() || pos.getZ() < area.minZ()
                    || pos.getZ() > area.maxZ()) {
                return new Walk(seen.size(), Optional.of(pos));
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                step(level, pos, pos.relative(direction)).filter(seen::add).ifPresent(queue::add);
            }
        }
        return new Walk(seen.size(), Optional.empty());
    }

    /** The position after a step from a position to the column of a block side by side, if the step can occur. */
    private static Optional<BlockPos> step(ServerLevel level, BlockPos from, BlockPos side) {
        BlockPos up = side.above();
        if (isFree(level, from.above(2)) && canStand(level, up)) {
            return Optional.of(up);
        }
        for (int fall = 0; fall <= MAX_FALL; fall++) {
            BlockPos target = side.below(fall);
            if (!isFree(level, target) || !isFree(level, target.above())) {
                return Optional.empty();
            }
            if (canStand(level, target)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    /** True if a mob can stand with its feet at a position: 2 free blocks on a support that is not a stair. */
    private static boolean canStand(ServerLevel level, BlockPos feet) {
        if (!isFree(level, feet) || !isFree(level, feet.above())) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState support = level.getBlockState(below);
        VoxelShape shape = support.getCollisionShape(level, below);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0 && !(support.getBlock() instanceof StairBlock);
    }

    /**
     * True if a mob can be in a position: the block has no collision shape. Also, the block below it is not higher
     * than 1 block: a fence or a fence gate is 1.5 blocks high.
     */
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        BlockPos below = pos.below();
        VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1.0;
    }
}
