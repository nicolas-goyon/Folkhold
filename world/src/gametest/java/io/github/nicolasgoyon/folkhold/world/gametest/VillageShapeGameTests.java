package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the shapes of the fences, the walls and the panes of the village templates (see
 * {@link VillagePieces}), written by {@code tools/structures/village.py}.
 *
 * <p>The village structure places its pieces with a known shape: the placement does not change the shape of a block
 * from the shape of its neighbors. Thus each fence, wall and pane must have in its template the state that the game
 * gives it from its neighbors. A wall with no connections is a post: a chicken goes through the gap between 2 posts.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageShapeGameTests {
    /** The origin of the templates in the test area. */
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    /**
     * The flags of the blocks that the shape test sets: no change of the shape of the block or of its neighbors, as
     * in the generation of a structure.
     */
    private static final int KNOWN_SHAPE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The template of the pen test: the house-workshop of the rancher of all the cultures, in the desert. */
    private static final ResourceLocation DESERT_RANCHER = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/desert/job_rancher");
    /** The pen of the rancher in the template: from the west wall (x = 0) to the east wall and the back wall. */
    private static final int PEN_WEST = 0;
    private static final int PEN_EAST = 8;
    private static final int PEN_BACK = 8;
    /** The speed of the push on a chicken, in blocks per tick. */
    private static final double PUSH = 0.15;
    /** The number of ticks of the push: without a wall, the push takes a chicken out of the pen. */
    private static final int PUSH_TICKS = 40;

    private VillageShapeGameTests() {
    }

    /**
     * Each fence, wall and pane of each village template has the state that the game gives it from its neighbors in
     * the template ({@link Block#updateFromNeighbourShapes}). The test sets the blocks of the template with no shape
     * updates, as the generation does, then compares the state of each fence, wall and pane with the state from its
     * neighbors. A position of the template with no block is air. A jigsaw block is its final state.
     *
     * <p>The message gives the number of templates with a wrong shape for each kind of template (the name of the
     * template, or its prefix {@code house}, {@code job} or {@code street}), the number of wrong blocks for each kind
     * of block, and the first wrong blocks.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "village_templates", skyAccess = true)
    public static void eachVillageShapeIsTheGameShape(GameTestHelper helper) {
        long start = System.nanoTime();
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, VillagePieces.Piece> pieces = VillagePieces.all(level, problems);
        BlockPos origin = helper.absolutePos(ORIGIN);
        Map<String, Integer> wrongTemplates = new TreeMap<>();
        Map<String, Integer> wrongBlocks = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int templates = 0;
        int shapedBlocks = 0;
        for (VillagePieces.Piece piece : pieces.values()) {
            Optional<StructureTemplate> template = level.getStructureManager().get(piece.template());
            if (template.isEmpty()) {
                problems.add("no template " + piece.template());
                continue;
            }
            Map<BlockPos, BlockState> states = new HashMap<>();
            for (ConstructionTargets.TemplateBlock block : ConstructionTargets.templateBlocks(level, template.get())) {
                states.put(block.pos(), block.state());
            }
            List<BlockPos> shaped = states.keySet().stream().filter(pos -> isShaped(states.get(pos))).sorted()
                    .toList();
            if (shaped.isEmpty()) {
                continue;
            }
            templates++;
            shapedBlocks += shaped.size();
            // The blocks that give the shapes: the shaped blocks, their neighbors on the 4 sides and the blocks above.
            Set<BlockPos> placed = new LinkedHashSet<>();
            for (BlockPos pos : shaped) {
                placed.add(pos);
                placed.add(pos.above());
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    placed.add(pos.relative(side));
                }
            }
            placed.removeIf(pos -> !states.containsKey(pos));
            for (BlockPos pos : placed) {
                level.setBlock(origin.offset(pos), states.get(pos), KNOWN_SHAPE);
            }
            int wrong = 0;
            for (BlockPos pos : shaped) {
                BlockState stored = states.get(pos);
                BlockState game = Block.updateFromNeighbourShapes(stored, level, origin.offset(pos));
                if (!game.equals(stored)) {
                    wrong++;
                    wrongBlocks.merge(blockKind(stored), 1, Integer::sum);
                    if (examples.size() < 10) {
                        examples.add(piece.template() + " " + pos.toShortString() + " " + stored + " -> " + game);
                    }
                }
            }
            for (BlockPos pos : placed) {
                level.setBlock(origin.offset(pos), Blocks.AIR.defaultBlockState(), KNOWN_SHAPE);
            }
            if (wrong > 0) {
                wrongTemplates.merge(templateKind(piece.name()), 1, Integer::sum);
            }
        }
        FolkholdWorld.LOGGER.info("Shape test of the village pieces: {} templates with {} fences, walls and panes"
                + " checked in {} ms", templates, shapedBlocks, (System.nanoTime() - start) / 1_000_000L);
        helper.assertTrue(problems.isEmpty(), "Problems in the village pieces: " + problems);
        helper.assertTrue(templates > 0, "The village templates must have fences, walls or panes");
        helper.assertTrue(wrongTemplates.isEmpty(), "Templates with a shape that the game changes, by kind: "
                + wrongTemplates + "; wrong blocks by kind: " + wrongBlocks + "; first wrong blocks: " + examples);
        helper.succeed();
    }

    /**
     * A chicken cannot leave the pen of the desert rancher: the walls of the pen connect, thus they have no gap. The
     * template is placed as a piece of the village structure. Three chickens with no AI stand in the pen next to the
     * west wall, the east wall and the back wall, at the edge between 2 wall blocks: a gap between 2 posts is there.
     * The test moves each chicken to its wall at each tick, then checks that each chicken is in the pen.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 100, batch = "rancher_pen", skyAccess = true)
    public static void chickenCannotLeaveTheDesertRancherPen(GameTestHelper helper) {
        TestSupport.buildFloor(helper, 16);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().getOrCreate(DESERT_RANCHER);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        BlockPos origin = helper.absolutePos(ORIGIN);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), KNOWN_SHAPE);
        BoundingBox box = template.getBoundingBox(settings, origin);
        // The chickens stand on the ground of the pen (y = 0 of the template), with the push to their wall.
        Map<Chicken, Vec3> pushes = new HashMap<>();
        pushes.put(chicken(helper, 1.5, 6.0), new Vec3(-PUSH, 0.0, 0.0));
        pushes.put(chicken(helper, PEN_EAST - 0.5, 6.0), new Vec3(PUSH, 0.0, 0.0));
        pushes.put(chicken(helper, 4.0, PEN_BACK - 0.5), new Vec3(0.0, 0.0, PUSH));
        // A mob with no AI does not move by itself: the push moves the chicken with the collisions of the game.
        helper.onEachTick(() -> pushes.forEach((chicken, push) -> chicken.move(MoverType.SELF, push)));
        helper.startSequence()
                .thenIdle(PUSH_TICKS)
                .thenExecute(() -> {
                    List<String> outside = new ArrayList<>();
                    for (Chicken chicken : pushes.keySet()) {
                        double x = chicken.getX() - origin.getX();
                        double z = chicken.getZ() - origin.getZ();
                        if (x < PEN_WEST + 0.5 || x > PEN_EAST + 0.5 || z > PEN_BACK + 0.5) {
                            outside.add(String.format("(%.2f, %.2f)", x, z));
                        }
                        chicken.discard();
                    }
                    BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                            .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
                    helper.assertTrue(outside.isEmpty(), "Chickens out of the pen of " + DESERT_RANCHER
                            + " (positions relative to the template): " + outside);
                })
                .thenSucceed();
    }

    /** A chicken with no AI on the ground of the pen, at a position of the template (x, z). */
    private static Chicken chicken(GameTestHelper helper, double x, double z) {
        Chicken chicken = helper.spawn(EntityType.CHICKEN, new Vec3(ORIGIN.getX() + x, ORIGIN.getY() + 1.0,
                ORIGIN.getZ() + z));
        chicken.setNoAi(true);
        return chicken;
    }

    /** True for a block with a shape from its neighbors: a fence, a wall or a pane. */
    private static boolean isShaped(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock || block instanceof WallBlock || block instanceof IronBarsBlock;
    }

    private static String blockKind(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock ? "fence" : block instanceof WallBlock ? "wall" : "pane";
    }

    /** The kind of a template: the prefix of its name for the houses, the jobs and the streets, else its name. */
    private static String templateKind(String name) {
        for (String prefix : List.of("house", "job", "street")) {
            if (name.startsWith(prefix)) {
                return prefix;
            }
        }
        return name;
    }
}
