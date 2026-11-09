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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
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
    /**
     * The groups of 4 or 5 jobs of the scan tests of the pieces of the styles, by name: all the jobs of
     * {@link #JOBS}. The Valeholm and the Sunreach styles have a piece of their own for each job (NORSE_JOBS and
     * SUNREACH_JOBS in the structure script).
     */
    private static final Map<String, List<String>> STYLE_GROUPS = styleGroups();
    /** The size of a Norse long house of the Valeholm style (x, y, z): it fits each plot of a Norse street. */
    private static final Vec3i LONG_HOUSE = new Vec3i(9, 16, 17);
    /**
     * The size of a Sunreach house-workshop (x, y, z): as wide as a flat house and as deep as a courtyard house of
     * the style.
     */
    private static final Vec3i SUN_WORKSHOP = new Vec3i(9, 7, 13);
    /** The size of a job building of all the cultures (x, y, z). */
    private static final Vec3i JOB_BUILDING = new Vec3i(9, 10, 9);
    /**
     * The styles with house-workshops of their own, by folder. A complete style has a piece of its own for each job
     * of {@link #JOBS}.
     */
    private static final Map<String, StyleJobs> STYLE_JOBS = styleJobs();
    /** The biome variants of the scan tests of the pieces of a style: the base variant, the desert and the swamp. */
    private static final List<String> STYLE_SCAN_VARIANTS = List.of("", "desert/", "swamp/");
    /** The origin of the template of each job of a group: 3 buildings north of the heart, 2 south of it. */
    private static final List<BlockPos> ORIGINS = List.of(new BlockPos(1, 1, 1), new BlockPos(11, 1, 1),
            new BlockPos(21, 1, 1), new BlockPos(6, 1, 21), new BlockPos(16, 1, 21));
    private static final BlockPos HEART = new BlockPos(16, 1, 15);
    /**
     * The heart of the tests of the pieces of a style: between the 2 rows of the buildings (17 blocks deep or less)
     * in the arena {@code arena_huge}.
     */
    private static final BlockPos STYLE_HEART = new BlockPos(16, 1, 19);
    private static final int HUGE_ARENA = 56;
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
     * forge of each style has no job sign: it is not a job post. The groups of the scan tests of each style (see
     * {@link #checkStyleJobs}) give the jobs with a piece of the style, and these pieces have the size of the style.
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
        STYLE_JOBS.forEach((style, jobs) -> checkStyleJobs(templates, style, jobs, problems));
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

    /**
     * The groups of the scan tests of a style have each job one time, and only the jobs of {@link #JOBS}. The groups
     * of a complete style have all the jobs of {@link #JOBS}: a new job cannot miss its scan test. In each variant,
     * the piece of a job of the groups has the size of the style, and the piece of another job has the size of the
     * piece of all the cultures. Thus a job of the groups cannot fall back to the piece of all the cultures, and a new
     * piece of the style cannot miss its scan test.
     */
    private static void checkStyleJobs(StructureTemplateManager templates, String style, StyleJobs jobs,
            List<String> problems) {
        List<String> grouped = jobs.jobs();
        if (grouped.size() != grouped.stream().distinct().count() || !JOBS.containsAll(grouped)) {
            problems.add("The scan groups of " + style + " must have each job one time, from JOBS: " + grouped);
        }
        if (jobs.complete() && !grouped.containsAll(JOBS)) {
            problems.add("The scan groups of " + style + " must have all the jobs: no group for "
                    + JOBS.stream().filter(job -> !grouped.contains(job)).toList());
        }
        for (String job : JOBS) {
            Vec3i size = grouped.contains(job) ? jobs.size() : JOB_BUILDING;
            for (String variant : VARIANTS) {
                ResourceLocation id = piece(style + variant, "job_" + job);
                templates.get(id).filter(template -> !template.getSize().equals(size)).ifPresent(template ->
                        problems.add(id + " must have the size " + size + ": " + template.getSize()));
            }
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
    public static void heartFindsTheJobPosts(GameTestHelper helper) {
        checkScan(helper, FIELDS);
    }

    /**
     * The scan tests of the pieces of the styles: one test for each group of each style of {@link #STYLE_JOBS} in
     * each variant of {@link #STYLE_SCAN_VARIANTS}, in the arena {@code arena_huge} (see {@link #checkScan}). The name
     * of a test is {@code heartfinds_<style>_[<variant>_]<group>}. In the desert, the fences of the Valeholm yards and
     * pens are sandstone walls with no gaps. In the swamp, the pieces have mangrove wood and mud bricks.
     */
    @GameTestGenerator
    public static List<TestFunction> styleScans() {
        List<TestFunction> tests = new ArrayList<>();
        for (Map.Entry<String, StyleJobs> entry : STYLE_JOBS.entrySet()) {
            String style = entry.getKey();
            for (String variant : STYLE_SCAN_VARIANTS) {
                for (Map.Entry<String, List<String>> group : entry.getValue().groups().entrySet()) {
                    String name = (style + variant).replace("/", "_") + group.getKey();
                    tests.add(new TestFunction("job_building_scan_" + name, "jobbuildingtemplategametests.heartfinds_"
                            + name, FolkholdWorld.MOD_ID + ":arena_huge", Rotation.NONE, 40, 0L, true, false, 1, 1,
                            true, helper -> checkScan(helper, style + variant, group.getValue())));
                }
            }
        }
        return tests;
    }

    /**
     * The groups of the scan tests of a style, the size of the pieces of the jobs of these groups, and true when the
     * style has a piece of its own for each job.
     */
    private record StyleJobs(Map<String, List<String>> groups, Vec3i size, boolean complete) {
        List<String> jobs() {
            return groups.values().stream().flatMap(List::stream).toList();
        }
    }

    /** The styles of {@link #STYLE_JOBS}, in the order of their scan tests. */
    private static Map<String, StyleJobs> styleJobs() {
        Map<String, StyleJobs> styles = new LinkedHashMap<>();
        styles.put("valeholm/", new StyleJobs(STYLE_GROUPS, LONG_HOUSE, true));
        styles.put("sunreach/", new StyleJobs(STYLE_GROUPS, SUN_WORKSHOP, true));
        return styles;
    }

    private static Map<String, List<String>> styleGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("fields", FIELDS);
        groups.put("food", FOOD);
        groups.put("wood", WOOD.subList(0, 5));
        groups.put("wood_2", WOOD.subList(5, 10));
        groups.put("stone", STONE);
        groups.put("crafts", CRAFTS);
        groups.put("cloth", CLOTH);
        return groups;
    }

    /** The 5 job buildings of the food and the animals: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_food", skyAccess = true)
    public static void heartFindsTheFoodJobPosts(GameTestHelper helper) {
        checkScan(helper, FOOD);
    }

    /** The first 5 job buildings of the wood: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_wood", skyAccess = true)
    public static void heartFindsTheWoodJobPosts(GameTestHelper helper) {
        checkScan(helper, WOOD.subList(0, 5));
    }

    /** The last 5 job buildings of the wood: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_wood_2", skyAccess = true)
    public static void heartFindsTheOtherWoodJobPosts(GameTestHelper helper) {
        checkScan(helper, WOOD.subList(5, 10));
    }

    /** The 5 job buildings of the stone, the earth and the metal: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_stone", skyAccess = true)
    public static void heartFindsTheStoneJobPosts(GameTestHelper helper) {
        checkScan(helper, STONE);
    }

    /** The 5 job buildings of the crafts: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_crafts", skyAccess = true)
    public static void heartFindsTheCraftJobPosts(GameTestHelper helper) {
        checkScan(helper, CRAFTS);
    }

    /** The 4 job buildings of the cloth and the paper: see {@link #checkScan}. */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_building_scan_cloth", skyAccess = true)
    public static void heartFindsTheClothJobPosts(GameTestHelper helper) {
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

    /** The 5 job buildings of a group in the base variant of the pieces of all the cultures: see the next method. */
    private static void checkScan(GameTestHelper helper, List<String> jobs) {
        checkScan(helper, "", jobs);
    }

    /**
     * The 4 or 5 job buildings of a group in a folder of the pieces (a style and a biome variant): the plan of each
     * template has no warning. After the placement of the templates, each job sign has its job and its icon item, each
     * fence and each wall has the shape of its neighbors, and the scan of the heart finds 1 job post for each building
     * with the beds of the building, 1 home (house sign) for each building and the beds of the templates as the home
     * beds of the village. The pieces of a style of {@link #STYLE_JOBS} have the size of the style: their tests use
     * the arena {@code arena_huge}.
     */
    private static void checkScan(GameTestHelper helper, String folder, List<String> jobs) {
        StyleJobs style = STYLE_JOBS.entrySet().stream().filter(entry -> folder.startsWith(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        BlockPos heartPos = style != null ? STYLE_HEART : HEART;
        TestSupport.buildFloor(helper, style != null ? HUGE_ARENA : ARENA);
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getStructureManager();
        List<BoundingBox> boxes = new ArrayList<>();
        List<VillageReference.Piece> pieces = new ArrayList<>();
        Map<String, Integer> expected = new HashMap<>();
        int beds = 0;
        try {
            for (int i = 0; i < jobs.size(); i++) {
                String job = jobs.get(i);
                StructureTemplate template = templates.get(piece(folder, "job_" + job)).orElseThrow();
                helper.assertTrue(style == null || template.getSize().equals(style.size()), "The piece " + folder
                        + "job_" + job + " must have the size of its style: " + template.getSize());
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
                pieces.add(new VillageReference.Piece(piece(folder, "job_" + job), origin, Rotation.NONE));
                checkSign(helper, template, origin, job);
                beds += beds(template);
                expected.put("folkhold_world:" + job, beds(template));
            }
            checkFences(helper, boxes);
            helper.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get());
            VillageHeartBlockEntity heart = helper.getBlockEntity(heartPos);
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
            helper.setBlock(heartPos, Blocks.AIR);
            // The buildings are higher than the arena: a later test at this place must not find them.
            boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos ->
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
        }
        helper.succeed();
    }

    /**
     * The template gives each fence and each wall the shape that the game gives it from its neighbors: a fence
     * connects to the fences and to the solid blocks of the building, not to the plants. A wall of the desert also has
     * the height of its sides and its post.
     */
    static void checkFences(GameTestHelper helper, List<BoundingBox> boxes) {
        ServerLevel level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        int fences = 0;
        for (BoundingBox box : boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                    box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FenceBlock) && !(state.getBlock() instanceof WallBlock)) {
                    continue;
                }
                fences++;
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                if (!shaped.equals(state)) {
                    wrong.add(helper.relativePos(pos) + " " + state + " -> " + shaped);
                }
            }
        }
        helper.assertTrue(fences > 0, "The buildings must have fences or walls");
        helper.assertTrue(wrong.isEmpty(), "Fences or walls with a wrong shape: " + wrong);
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
