package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the templates of the job buildings ({@code village/job_<id>}, written by
 * {@code tools/structures/village.py}): each template has the job sign of its job, the construction plan of each
 * template has no warning, and the scan of the heart counts the job sign of each building.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobBuildingTemplateGameTests {
    private static final int ARENA = 32;
    /** The jobs of the fields and the gardens. The id of a job is the name of its job file. */
    private static final List<String> JOBS = List.of("farmer", "gardener", "gardener_jungle", "forager",
            "beekeeper");
    /** The origin of the template of each job of {@link #JOBS}: 3 buildings north of the heart, 2 south of it. */
    private static final List<BlockPos> ORIGINS = List.of(new BlockPos(1, 1, 1), new BlockPos(11, 1, 1),
            new BlockPos(21, 1, 1), new BlockPos(6, 1, 21), new BlockPos(16, 1, 21));
    private static final BlockPos HEART = new BlockPos(16, 1, 15);
    /** The folders of the pieces of the village: the styles of the cultures and the maritime pieces. */
    private static final List<String> STYLES = List.of("", "valeholm/", "sunreach/", "maritime/");
    /** The folders of the biome variants in a style. The base variant has no folder. */
    private static final List<String> VARIANTS = List.of("", "plains/", "birch/", "savanna/", "desert/", "swamp/");

    private JobBuildingTemplateGameTests() {
    }

    /** Each variant of each job building has one job sign, with the job id and an icon. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "job_building_templates")
    public static void eachVariantHasItsJobSign(GameTestHelper helper) {
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        List<String> problems = new ArrayList<>();
        for (String job : JOBS) {
            if (!EconomyData.jobs().containsKey("folkhold_world:" + job)) {
                problems.add("no job file for " + job);
            }
            for (String style : STYLES) {
                for (String variant : VARIANTS) {
                    ResourceLocation id = piece(style + variant, job);
                    Optional<StructureTemplate> template = templates.get(id);
                    if (template.isEmpty()) {
                        problems.add("no template " + id);
                        continue;
                    }
                    List<StructureTemplate.StructureBlockInfo> signs = template.get().filterBlocks(BlockPos.ZERO,
                            new StructurePlaceSettings(), ModBlocks.JOB_SIGN.get());
                    boolean valid = signs.size() == 1 && signs.get(0).nbt() != null
                            && job.equals(signs.get(0).nbt().getString(JobSignBlockEntity.JOB))
                            && !signs.get(0).nbt().getString(JobSignBlockEntity.ICON).isEmpty();
                    if (!valid) {
                        problems.add(id + " has " + signs.size() + " job signs");
                    }
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Problems of the job building templates: " + problems);
        helper.succeed();
    }

    /**
     * The 5 job buildings of the base variant: the plan of each template has no warning. After the placement of the
     * templates, each job sign has its job and its icon item, and the scan of the heart counts 1 building for each job.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan", skyAccess = true)
    public static void heartCountsTheJobBuildings(GameTestHelper helper) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getStructureManager();
        List<BoundingBox> boxes = new ArrayList<>();
        Map<String, Integer> expected = new HashMap<>();
        try {
            for (int i = 0; i < JOBS.size(); i++) {
                String job = JOBS.get(i);
                StructureTemplate template = templates.get(piece("", job)).orElseThrow();
                BlockPos origin = helper.absolutePos(ORIGINS.get(i));
                Plan plan = ConstructionPlanner.plan(ConstructionTargets.fromTemplate(level, template, origin,
                        new StructurePlaceSettings()), ConstructionTargets.view(level), Reach.DEFAULT);
                helper.assertTrue(plan.warnings().isEmpty(), "The plan of " + job + " must have no warnings: "
                        + plan.warnings());
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .addProcessor(JigsawReplacementProcessor.INSTANCE);
                template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                boxes.add(template.getBoundingBox(settings, origin));
                checkSign(helper, template, origin, job);
                expected.put("folkhold_world:" + job, 1);
            }
            helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
            VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
            heart.ensureSettlement();
            helper.assertTrue(heart.getLayout().scan(level, heart.getBlockPos()), "The scan area must be ready");
            heart.recordJobBuildings();
            Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
            helper.assertTrue(settlement.jobBuildings().equals(Optional.of(expected)),
                    "Expected 1 building for each job: " + settlement.jobBuildings());
        } finally {
            helper.setBlock(HEART, Blocks.AIR);
            // The buildings are higher than the arena: a later test at this place must not find them.
            boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos ->
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
        }
        helper.succeed();
    }

    private static ResourceLocation piece(String folder, String job) {
        return ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/" + folder + "job_" + job);
    }

    /** The placed template has one job sign, with the job id and an icon item that exists. */
    private static void checkSign(GameTestHelper helper, StructureTemplate template, BlockPos origin, String job) {
        List<StructureTemplate.StructureBlockInfo> signs = template.filterBlocks(origin, new StructurePlaceSettings(),
                ModBlocks.JOB_SIGN.get());
        helper.assertTrue(signs.size() == 1, "The building of " + job + " must have 1 job sign: " + signs.size());
        if (!(helper.getLevel().getBlockEntity(signs.get(0).pos()) instanceof JobSignBlockEntity sign)) {
            helper.fail("No job sign entity in the building of " + job);
            return;
        }
        helper.assertTrue(job.equals(sign.job()), "The job sign must have the job " + job + ": " + sign.job());
        helper.assertTrue(sign.icon().filter(BuiltInRegistries.ITEM::containsKey).isPresent()
                && !sign.iconStack().isEmpty(), "The job sign of " + job + " must have an icon item: " + sign.icon());
    }
}
