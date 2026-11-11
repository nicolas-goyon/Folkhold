package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for all the templates of the village structure (see {@link VillagePieces}), written by
 * {@code tools/structures/village.py}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageTemplateGameTests {
    /** The number of variants of the village structure: 4 styles in 6 biome variants each. */
    private static final int VARIANTS = JobBuildingTemplateGameTests.STYLES.size()
            * JobBuildingTemplateGameTests.VARIANTS.size();
    /** The number of jobs with a house-workshop piece in each variant. */
    private static final int JOBS = 34;
    /** The number of the other rigid pieces of each variant: the plaza, the bank, the market, the tower, the gate. */
    private static final int OTHER_PIECES = 5;
    /**
     * The number of the street pieces of each variant: {@code street}, {@code street_bank}, {@code street_jobs} and
     * {@code street_end}.
     */
    private static final int STREET_PIECES = 4;
    /** The number of the pieces that the plan test checks: all the rigid pieces of all the variants. */
    private static final int PLAN_PIECES = (JOBS + OTHER_PIECES) * VARIANTS + HomeTemplateGameTests.HOME_PIECES;
    /** The number of the pieces that the plan test does not check: the streets of all the variants. */
    private static final int SKIPPED_PIECES = STREET_PIECES * VARIANTS;
    /** The size of the floor of the plan test, in blocks: a long house (17 blocks deep) and a margin around it. */
    private static final int PLAN_FLOOR = 24;
    /** The origin of the template of the plan test: the floor goes around the piece on each side. */
    private static final BlockPos PLAN_ORIGIN = new BlockPos(3, 1, 3);

    private VillageTemplateGameTests() {
    }

    /**
     * Builders can place each block of each piece that they build or repair: the construction plan of the piece on a
     * flat floor has no warning. A block with no position to stand for a builder gives the warning {@code NO_STAND}.
     *
     * <ul>
     *   <li>The builders build the pieces of the pools of the growth (see
     *       {@link io.github.nicolasgoyon.folkhold.world.village.VillageGrowth#pools}): the house-workshops, the
     *       buildings of the levels and the market.</li>
     *   <li>The builders repair each rigid piece of the generation (see
     *       {@link io.github.nicolasgoyon.folkhold.world.village.VillageReference}): the plaza, the bank, the homes,
     *       the house-workshops, the market, the towers and the gates.</li>
     *   <li>The test does not check the streets: their projection is {@code TERRAIN_MATCHING}. Their blocks go to the
     *       height of the terrain, thus the template does not give the blocks of the street. The builders never build a
     *       street piece, and the repairs fill only the holes of the surface of the streets (see
     *       {@link io.github.nicolasgoyon.folkhold.world.village.StreetBlocks}). The log gives the skipped
     *       pieces.</li>
     * </ul>
     *
     * <p>The test checks exactly {@link #PLAN_PIECES} pieces and skips exactly {@link #SKIPPED_PIECES} pieces: a
     * missing template or a new piece fails it.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "village_templates", skyAccess = true)
    public static void eachVillagePlanHasNoWarning(GameTestHelper helper) {
        long start = System.nanoTime();
        TestSupport.buildFloor(helper, PLAN_FLOOR);
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, VillagePieces.Piece> pieces = VillagePieces.all(level, problems);
        BlockPos origin = helper.absolutePos(PLAN_ORIGIN);
        int room = PLAN_FLOOR - 2 * PLAN_ORIGIN.getX();
        List<ResourceLocation> skipped = new ArrayList<>();
        int checked = 0;
        for (VillagePieces.Piece piece : pieces.values()) {
            if (piece.projection() == StructureTemplatePool.Projection.TERRAIN_MATCHING) {
                skipped.add(piece.template());
                continue;
            }
            Optional<StructureTemplate> template = level.getStructureManager().get(piece.template());
            if (template.isEmpty()) {
                problems.add("no template " + piece.template());
                continue;
            }
            Vec3i size = template.get().getSize();
            if (size.getX() > room || size.getZ() > room) {
                problems.add(piece.template() + " is larger than the floor: " + size);
                continue;
            }
            checked++;
            Plan plan = ConstructionPlanner.plan(ConstructionTargets.fromTemplate(level, template.get(), origin,
                    new StructurePlaceSettings()), ConstructionTargets.view(level), Reach.DEFAULT);
            if (!plan.warnings().isEmpty()) {
                problems.add(piece.template() + ": " + plan.warnings().stream().map(warning -> warning.problem()
                        + " " + relative(warning.pos(), origin)).toList());
            }
        }
        FolkholdWorld.LOGGER.info("Plan test of the village pieces: {} pieces checked in {} ms, {} street pieces"
                + " skipped (the builders do not build them): {}", checked, (System.nanoTime() - start) / 1_000_000L,
                skipped.size(), skipped);
        helper.assertTrue(problems.isEmpty(), "Plans of village pieces with warnings: " + problems);
        helper.assertTrue(checked == PLAN_PIECES, "Expected " + PLAN_PIECES + " pieces to check: " + checked);
        helper.assertTrue(skipped.size() == SKIPPED_PIECES, "Expected " + SKIPPED_PIECES + " street pieces to skip: "
                + skipped.size());
        helper.succeed();
    }

    /** The position of a block of the plan, relative to the origin of the template. */
    private static String relative(BuildPos pos, BlockPos origin) {
        return (pos.x() - origin.getX()) + ", " + (pos.y() - origin.getY()) + ", " + (pos.z() - origin.getZ());
    }
}
