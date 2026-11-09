package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the templates of the homes of the villages: the pieces of the pools {@code homes} and
 * {@code houses} of each style and each biome variant, written by {@code tools/structures/village.py}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeTemplateGameTests {
    /** The pools of the homes in the folder of a style and a variant. */
    private static final List<String> HOME_POOLS = List.of("homes", "houses");
    /**
     * The number of home pieces: 4 homes of all the cultures, 6 Valeholm homes, 5 Sunreach homes and 6 maritime
     * homes, in each of the 6 variants.
     */
    private static final int HOME_PIECES = (4 + 6 + 5 + 6) * 6;
    /** The number of Valeholm home pieces: 6 homes in each of the 6 variants. */
    private static final int VALEHOLM_HOME_PIECES = 6 * 6;
    /** The size of the floor of the plan test, in blocks: a long house (17 blocks deep) and a margin around it. */
    private static final int PLAN_FLOOR = 24;
    /** The origin of the template of the plan test: the floor goes around the piece on each side. */
    private static final BlockPos PLAN_ORIGIN = new BlockPos(3, 1, 3);

    private HomeTemplateGameTests() {
    }

    /**
     * In each home piece of the pools of each style and each variant, a villager can go from the front of the piece to
     * each work block and to each bed: a walk from the front reaches a position next to the block (see
     * {@link TemplateReach#unreachable}). The test checks exactly {@link #HOME_PIECES} pieces: a missing template
     * fails it.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "home_templates")
    public static void eachHomeWorkBlockAndBedCanBeReached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<ResourceLocation> pieces = homePieces(level, JobBuildingTemplateGameTests.STYLES);
        List<String> problems = new ArrayList<>();
        for (ResourceLocation id : pieces) {
            Optional<StructureTemplate> template = level.getStructureManager().get(id);
            if (template.isEmpty()) {
                problems.add("no template " + id);
                continue;
            }
            List<String> blocks = TemplateReach.unreachable(level, template.get());
            if (!blocks.isEmpty()) {
                problems.add(id + ": " + blocks);
            }
        }
        helper.assertTrue(pieces.size() == HOME_PIECES, "Expected " + HOME_PIECES + " home pieces to check: "
                + pieces.size());
        helper.assertTrue(problems.isEmpty(), "Work blocks and beds that a villager cannot reach from the front: "
                + problems);
        helper.succeed();
    }

    /**
     * Builders can place each block of each Valeholm home piece of the pools in each variant: the construction plan
     * of the piece on a flat floor has no warning. A block with no position to stand for a builder gives the warning
     * {@code NO_STAND}. The test checks exactly {@link #VALEHOLM_HOME_PIECES} pieces: a missing template fails it.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "home_templates", skyAccess = true)
    public static void eachValeholmHomePlanHasNoWarning(GameTestHelper helper) {
        TestSupport.buildFloor(helper, PLAN_FLOOR);
        ServerLevel level = helper.getLevel();
        Set<ResourceLocation> pieces = homePieces(level, List.of("valeholm/"));
        BlockPos origin = helper.absolutePos(PLAN_ORIGIN);
        List<String> problems = new ArrayList<>();
        for (ResourceLocation id : pieces) {
            Optional<StructureTemplate> template = level.getStructureManager().get(id);
            if (template.isEmpty()) {
                problems.add("no template " + id);
                continue;
            }
            Plan plan = ConstructionPlanner.plan(ConstructionTargets.fromTemplate(level, template.get(), origin,
                    new StructurePlaceSettings()), ConstructionTargets.view(level), Reach.DEFAULT);
            if (!plan.warnings().isEmpty()) {
                problems.add(id + ": " + plan.warnings().stream().map(warning -> warning.problem() + " "
                        + relative(warning.pos(), origin)).toList());
            }
        }
        helper.assertTrue(pieces.size() == VALEHOLM_HOME_PIECES, "Expected " + VALEHOLM_HOME_PIECES
                + " Valeholm home pieces to check: " + pieces.size());
        helper.assertTrue(problems.isEmpty(), "Plans of Valeholm home pieces with warnings: " + problems);
        helper.succeed();
    }

    /**
     * The templates of the pieces of the pools {@link #HOME_POOLS} of the given styles in each variant of
     * {@link JobBuildingTemplateGameTests#VARIANTS}.
     */
    private static Set<ResourceLocation> homePieces(ServerLevel level, List<String> styles) {
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Set<ResourceLocation> pieces = new TreeSet<>();
        for (String style : styles) {
            for (String variant : JobBuildingTemplateGameTests.VARIANTS) {
                for (String name : HOME_POOLS) {
                    ResourceKey<StructureTemplatePool> key = ResourceKey.create(Registries.TEMPLATE_POOL,
                            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                                    "village/" + style + variant + name));
                    for (StructurePoolElement element : pools.getOrThrow(key)
                            .getShuffledTemplates(RandomSource.create(0L))) {
                        VillageReference.templateOf(level, element).ifPresent(pieces::add);
                    }
                }
            }
        }
        return pieces;
    }

    /** The position of a block of the plan, relative to the origin of the template. */
    private static String relative(BuildPos pos, BlockPos origin) {
        return (pos.x() - origin.getX()) + ", " + (pos.y() - origin.getY()) + ", " + (pos.z() - origin.getZ());
    }
}
