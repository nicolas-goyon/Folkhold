package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
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
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the templates of the job buildings ({@code village/job_<id>}, written by
 * {@code tools/structures/village.py}): each template is a house-workshop with the job sign of its job, the house
 * sign and {@link #HOME_BEDS} beds or more. The construction plan of each template has no warning, the fences have the
 * shape that the game gives them, and the scan of the heart counts the job sign, the home and the beds of each
 * building. The templates have no entities.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobBuildingTemplateGameTests {
    private static final int ARENA = 32;
    /** The minimum number of beds of a house-workshop: each bed gives a place to an inhabitant. */
    private static final int HOME_BEDS = 2;
    /** The jobs of the fields and the gardens. The id of a job is the name of its job file. */
    private static final List<String> FIELDS = List.of("farmer", "gardener", "gardener_jungle", "forager",
            "beekeeper");
    /** The jobs of the food and the animals. */
    private static final List<String> FOOD = List.of("baker", "pastry_cook", "rancher", "butcher", "fisher");
    /** The jobs of the wood: 2 groups of 5, one group for each scan test. */
    private static final List<String> WOOD = List.of("lumberjack", "lumberjack_acacia", "lumberjack_birch",
            "lumberjack_cherry", "lumberjack_dark_oak", "lumberjack_jungle", "lumberjack_mangrove",
            "lumberjack_spruce", "sawyer", "sawyer_spruce");
    /** The jobs of the stone, the earth and the metal. */
    private static final List<String> STONE = List.of("miner", "digger", "mason", "smelter", "gold_smelter");
    /** The jobs of the crafts. The building of the blacksmith is the forge. */
    private static final List<String> CRAFTS = List.of("blacksmith", "tinker", "fletcher", "carpenter", "tanner");
    /** The jobs of the cloth and the paper. */
    private static final List<String> CLOTH = List.of("shepherd", "weaver", "dyer", "papermaker");
    /** The jobs with a building template. */
    private static final List<String> JOBS = List.of(FIELDS, FOOD, WOOD, STONE, CRAFTS, CLOTH).stream()
            .flatMap(List::stream).toList();
    /** The origin of the template of each job of a group: 3 buildings north of the heart, 2 south of it. */
    private static final List<BlockPos> ORIGINS = List.of(new BlockPos(1, 1, 1), new BlockPos(11, 1, 1),
            new BlockPos(21, 1, 1), new BlockPos(6, 1, 21), new BlockPos(16, 1, 21));
    private static final BlockPos HEART = new BlockPos(16, 1, 15);
    /** The folders of the pieces of the village: the styles of the cultures and the maritime pieces. */
    private static final List<String> STYLES = List.of("", "valeholm/", "sunreach/", "maritime/");
    /** The folders of the biome variants in a style. The base variant has no folder. */
    private static final List<String> VARIANTS = List.of("", "plains/", "birch/", "savanna/", "desert/", "swamp/");

    private JobBuildingTemplateGameTests() {
    }

    /**
     * Each variant of each job building has one job sign, with the job id and an icon, and no entities: a template
     * with entities adds them again at each placement. Each job building and the maritime fishing hut are homes: 1
     * house sign and {@link #HOME_BEDS} beds or more. The maritime fishing hut has the job sign of the fisher. The
     * forge of each style has no job sign: it is not a job post.
     */
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
                    checkTemplate(templates, piece(style + variant, "job_" + job), job, problems);
                }
            }
        }
        for (String variant : VARIANTS) {
            checkTemplate(templates, piece("maritime/" + variant, "house_fishing_hut"), "fisher", problems);
            for (String style : STYLES) {
                ResourceLocation forge = piece(style + variant, "house_forge");
                if (templates.get(forge).map(template -> template.filterBlocks(BlockPos.ZERO,
                        new StructurePlaceSettings(), ModBlocks.JOB_SIGN.get()).size()).orElse(-1) != 0) {
                    problems.add(forge + " must exist with no job sign");
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Problems of the job building templates: " + problems);
        helper.succeed();
    }

    /**
     * The template is a home with 1 job sign with the job and an icon, 1 house sign, {@link #HOME_BEDS} beds or more,
     * and no entities.
     */
    private static void checkTemplate(StructureTemplateManager templates, ResourceLocation id, String job,
            List<String> problems) {
        Optional<StructureTemplate> template = templates.get(id);
        if (template.isEmpty()) {
            problems.add("no template " + id);
            return;
        }
        List<StructureTemplate.StructureBlockInfo> signs = template.get().filterBlocks(BlockPos.ZERO,
                new StructurePlaceSettings(), ModBlocks.JOB_SIGN.get());
        boolean valid = signs.size() == 1 && signs.get(0).nbt() != null
                && job.equals(signs.get(0).nbt().getString(JobSignBlockEntity.JOB))
                && !signs.get(0).nbt().getString(JobSignBlockEntity.ICON).isEmpty();
        if (!valid) {
            problems.add(id + " has " + signs.size() + " job signs");
        }
        if (!template.get().save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND).isEmpty()) {
            problems.add(id + " has entities");
        }
        int houseSigns = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                ModBlocks.buildingSign(BuildingSignKind.HOUSE)).size();
        int beds = beds(template.get());
        if (houseSigns != 1 || beds < HOME_BEDS) {
            problems.add(id + " has " + houseSigns + " house signs and " + beds + " beds");
        }
    }

    /** The number of beds of a template: the head parts of the beds of all the colors. */
    private static int beds(StructureTemplate template) {
        return (int) BuiltInRegistries.BLOCK.stream().filter(BedBlock.class::isInstance)
                .flatMap(bed -> template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), bed).stream())
                .filter(info -> info.state().getValue(BedBlock.PART) == BedPart.HEAD)
                .count();
    }

    /** The 5 job buildings of the fields and the gardens: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan", skyAccess = true)
    public static void heartCountsTheJobBuildings(GameTestHelper helper) {
        checkScan(helper, FIELDS);
    }

    /** The 5 job buildings of the food and the animals: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_food", skyAccess = true)
    public static void heartCountsTheFoodJobBuildings(GameTestHelper helper) {
        checkScan(helper, FOOD);
    }

    /** The first 5 job buildings of the wood: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_wood", skyAccess = true)
    public static void heartCountsTheWoodJobBuildings(GameTestHelper helper) {
        checkScan(helper, WOOD.subList(0, 5));
    }

    /** The last 5 job buildings of the wood: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_wood_2", skyAccess = true)
    public static void heartCountsTheOtherWoodJobBuildings(GameTestHelper helper) {
        checkScan(helper, WOOD.subList(5, 10));
    }

    /** The 5 job buildings of the stone, the earth and the metal: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_stone", skyAccess = true)
    public static void heartCountsTheStoneJobBuildings(GameTestHelper helper) {
        checkScan(helper, STONE);
    }

    /** The 5 job buildings of the crafts: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_crafts", skyAccess = true)
    public static void heartCountsTheCraftJobBuildings(GameTestHelper helper) {
        checkScan(helper, CRAFTS);
    }

    /** The 4 job buildings of the cloth and the paper: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_cloth", skyAccess = true)
    public static void heartCountsTheClothJobBuildings(GameTestHelper helper) {
        checkScan(helper, CLOTH);
    }

    /**
     * A forge has no beds: it is not a home. A job sign in a forge is not a job post, and a forge with no job sign is
     * not a post too.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_forge", skyAccess = true)
    public static void forgeWithNoBedsIsNotAPost(GameTestHelper helper) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().getOrCreate(piece("", "house_forge"));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<BoundingBox> boxes = new ArrayList<>();
        List<VillageReference.Piece> pieces = new ArrayList<>();
        try {
            for (BlockPos relative : List.of(ORIGINS.get(0), ORIGINS.get(1))) {
                BlockPos origin = helper.absolutePos(relative);
                template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                boxes.add(template.getBoundingBox(settings, origin));
                pieces.add(new VillageReference.Piece(piece("", "house_forge"), origin, Rotation.NONE));
            }
            // The first forge also has a job sign, next to the west porch post.
            BlockPos sign = ORIGINS.get(0).offset(1, 1, 0);
            helper.setBlock(sign, ModBlocks.JOB_SIGN.get());
            JobSignBlockEntity jobSign = helper.getBlockEntity(sign);
            jobSign.setJob("blacksmith", ResourceLocation.withDefaultNamespace("anvil"));
            helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
            VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
            heart.ensureSettlement();
            heart.setRepairReference(new VillageReference(pieces));
            helper.assertTrue(heart.getLayout().scan(level, heart.getBlockPos()), "The scan area must be ready");
            heart.recordJobPosts();
            Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
            helper.assertTrue(heart.getLayout().buildings().getOrDefault("forge", 0) == 2,
                    "The scan must count 2 forges: " + heart.getLayout().buildings());
            helper.assertTrue(heart.getLayout().jobSigns().size() == 1,
                    "The scan must find the job sign: " + heart.getLayout().jobSigns());
            helper.assertTrue(settlement.jobPosts().equals(Optional.of(List.of())),
                    "A forge with no beds must not be a job post: " + settlement.jobPosts());
        } finally {
            helper.setBlock(HEART, Blocks.AIR);
            boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos ->
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
        }
        helper.succeed();
    }

    /**
     * The 5 job buildings of a group in the base variant: the plan of each template has no warning. After the
     * placement of the templates, each job sign has its job and its icon item, each fence has the shape of its
     * neighbors, and the scan of the heart finds 1 job post for each building with the beds of the building, 1 home
     * (house sign) for each building and the beds of the templates as the home beds of the village.
     */
    private static void checkScan(GameTestHelper helper, List<String> jobs) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getStructureManager();
        List<BoundingBox> boxes = new ArrayList<>();
        List<VillageReference.Piece> pieces = new ArrayList<>();
        Map<String, Integer> expected = new HashMap<>();
        int beds = 0;
        try {
            for (int i = 0; i < jobs.size(); i++) {
                String job = jobs.get(i);
                StructureTemplate template = templates.get(piece("", "job_" + job)).orElseThrow();
                BlockPos origin = helper.absolutePos(ORIGINS.get(i));
                Plan plan = ConstructionPlanner.plan(ConstructionTargets.fromTemplate(level, template, origin,
                        new StructurePlaceSettings()), ConstructionTargets.view(level), Reach.DEFAULT);
                helper.assertTrue(plan.warnings().isEmpty(), "The plan of " + job + " must have no warnings: "
                        + plan.warnings());
                // As a piece of the village structure: the placement does not change the shapes of the blocks.
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
                template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                boxes.add(template.getBoundingBox(settings, origin));
                pieces.add(new VillageReference.Piece(piece("", "job_" + job), origin, Rotation.NONE));
                checkSign(helper, template, origin, job);
                beds += beds(template);
                expected.put("folkhold_world:" + job, beds(template));
            }
            checkFences(helper, boxes);
            helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
            VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
            heart.ensureSettlement();
            heart.setRepairReference(new VillageReference(pieces));
            helper.assertTrue(heart.getLayout().scan(level, heart.getBlockPos()), "The scan area must be ready");
            heart.recordJobPosts();
            Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
            Map<String, Integer> posts = new HashMap<>();
            settlement.jobPosts().orElse(List.of()).forEach(post -> posts.put(post.jobId(), post.homeBeds()));
            helper.assertTrue(posts.equals(expected) && settlement.jobPosts().orElseThrow().size() == jobs.size(),
                    "Expected 1 job post with the beds of its building for each job: " + settlement.jobPosts());
            int homes = heart.getLayout().buildings().getOrDefault(BuildingSignKind.HOUSE.serializedName(), 0);
            helper.assertTrue(homes == jobs.size(), "Expected 1 home for each job building: " + homes);
            heart.recordBeds();
            helper.assertTrue(beds >= HOME_BEDS * jobs.size() && settlement.beds() == beds,
                    "Expected the " + beds + " beds of the job buildings as home beds: " + settlement.beds());
        } finally {
            helper.setBlock(HEART, Blocks.AIR);
            // The buildings are higher than the arena: a later test at this place must not find them.
            boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos ->
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
        }
        helper.succeed();
    }

    /**
     * The template gives each fence the shape that the game gives it from its neighbors: a fence connects to the
     * fences and to the solid blocks of the building, not to the plants.
     */
    static void checkFences(GameTestHelper helper, List<BoundingBox> boxes) {
        ServerLevel level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        int fences = 0;
        for (BoundingBox box : boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                    box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FenceBlock)) {
                    continue;
                }
                fences++;
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                if (!shaped.equals(state)) {
                    wrong.add(helper.relativePos(pos) + " " + state + " -> " + shaped);
                }
            }
        }
        helper.assertTrue(fences > 0, "The buildings must have fences");
        helper.assertTrue(wrong.isEmpty(), "Fences with a wrong shape: " + wrong);
    }

    private static ResourceLocation piece(String folder, String name) {
        return ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/" + folder + name);
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
