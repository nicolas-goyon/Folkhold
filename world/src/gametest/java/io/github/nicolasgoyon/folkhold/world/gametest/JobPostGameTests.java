package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHomes;
import io.github.nicolasgoyon.folkhold.world.village.VillageLife;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.schedule.VillageSchedule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the job posts (see {@link JobPosts}): the heart scans the village pieces, and each home with a job
 * sign is one job post. The tests place the pieces of the base style as the pieces of a village structure.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobPostGameTests {
    private static final int ARENA = 32;
    private static final BlockPos HEART = new BlockPos(16, 1, 15);
    /** The origins of the pieces, side by side north of the heart. */
    private static final List<BlockPos> ORIGINS = List.of(new BlockPos(1, 1, 1), new BlockPos(11, 1, 1),
            new BlockPos(21, 1, 1));
    private static final String FARMER = "folkhold_world:farmer";
    private static final String MASON = "folkhold_world:mason";
    private static final int POPULATION = 20;
    private static final String SCREEN = "screen.folkhold_world.village_heart.";
    /** The share of the maximum population at the start of a village (see {@code Economies.initialize}). */
    private static final double START_SHARE = 0.6;

    private JobPostGameTests() {
    }

    private record Village(VillageHeartBlockEntity heart, Settlement settlement, List<BoundingBox> boxes) {
    }

    /**
     * A village with 2 house-workshops (farmer, mason) and 1 cottage has 2 job posts. After the scan, its jobs are the
     * farmer and the mason, also with more job slots in its level.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_scan", skyAccess = true)
    public static void villageWithTwoHouseWorkshopsHasTheirJobs(GameTestHelper helper) {
        Village village = village(helper, List.of("job_farmer", "job_mason", "house_cottage"), 5);
        try {
            List<JobPosts.Post> posts = village.settlement().jobPosts().orElseThrow();
            List<String> postJobs = posts.stream().map(JobPosts.Post::jobId).sorted().toList();
            helper.assertTrue(postJobs.equals(List.of(FARMER, MASON)),
                    "Expected the posts of the farmer and the mason: " + posts);
            List<String> jobs = Economies.activeJobIds(village.settlement()).stream().sorted().toList();
            helper.assertTrue(jobs.equals(List.of(FARMER, MASON)), "Expected the jobs farmer and mason: " + jobs);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A legacy village (its save has no list of posts) keeps the job slots of its level after the scan: its post takes
     * one job slot, and the other job slots stay. The same village with no mark has only the job of its post.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_legacy", skyAccess = true)
    public static void legacyVillageKeepsItsJobs(GameTestHelper helper) {
        int level = 3;
        int slots = LevelTables.get().traits(level).jobSlots();
        List<String> slotJobs = List.of("folkhold_world:baker", "folkhold_world:miner", "folkhold_world:fisher",
                "folkhold_world:baker", "folkhold_world:miner").subList(0, slots);
        Village village = village(helper, "", List.of("job_farmer", "house_cottage"), level, POPULATION, slotJobs,
                true, "minecraft:plains");
        try {
            List<String> jobs = Economies.activeJobIds(village.settlement());
            List<String> expected = new ArrayList<>(List.of(FARMER));
            expected.addAll(slotJobs.subList(0, slots - 1));
            helper.assertTrue(jobs.equals(expected), "Expected the post and " + (slots - 1) + " legacy jobs: " + jobs);
            village.settlement().setLegacyJobs(false);
            List<String> newJobs = Economies.activeJobIds(village.settlement());
            helper.assertTrue(newJobs.equals(List.of(FARMER)), "A new village must have the job of its post: "
                    + newJobs);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A new village of level 1 at its start population (2 inhabitants of the homes) has its food post active, also
     * when the scan finds the sign of an other job that trades first. The mason comes first in the scan order.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_start", skyAccess = true)
    public static void newVillageHasItsFoodPostAtItsStartPopulation(GameTestHelper helper) {
        startStaff(helper);
        Village village = village(helper, "", List.of("job_mason", "job_farmer"), 1, startPopulation(), List.of(),
                false, "minecraft:plains");
        try {
            List<String> jobs = Economies.activeJobIds(village.settlement());
            helper.assertTrue(Economies.homeInhabitants(village.settlement()) == 2,
                    "Expected 2 inhabitants of the homes: " + Economies.homeInhabitants(village.settlement()));
            helper.assertTrue(village.settlement().jobPosts().orElseThrow().get(0).jobId().equals(FARMER),
                    "The food post must be the first post: " + village.settlement().jobPosts());
            helper.assertTrue(jobs.equals(List.of(FARMER)), "Expected the active food job: " + jobs);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A maritime village of level 1 in a biome that is not a fisher biome: its fishing hut is a post with a job that
     * is not valid. The hut takes no inhabitant, and the farmer post of the village is active at the start population.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_maritime", skyAccess = true)
    public static void postThatIsNotValidLeavesItsInhabitantsToTheNextPost(GameTestHelper helper) {
        startStaff(helper);
        Village village = village(helper, "maritime/", List.of("house_fishing_hut", "job_farmer"), 1,
                startPopulation(), List.of(), false, "minecraft:plains");
        try {
            List<String> postJobs = village.settlement().jobPosts().orElseThrow().stream()
                    .map(JobPosts.Post::jobId).sorted().toList();
            helper.assertTrue(postJobs.equals(List.of(FARMER, "folkhold_world:fisher")),
                    "Expected the posts of the farmer and the fisher: " + postJobs);
            List<String> jobs = Economies.activeJobIds(village.settlement());
            helper.assertTrue(jobs.equals(List.of(FARMER)), "Expected the active farmer job only: " + jobs);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A village of level 5 with the house-workshop of the blacksmith (minimum level 5) loses a level: the post keeps
     * its job and its sign, and it is inactive. At level 5 again, the post is active.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_level", skyAccess = true)
    public static void levelLossKeepsTheJobOfAPost(GameTestHelper helper) {
        String blacksmith = "folkhold_world:blacksmith";
        Village village = village(helper, List.of("job_blacksmith"), 5);
        try {
            village.heart().renewJobPosts();
            helper.assertTrue(Economies.activeJobIds(village.settlement()).equals(List.of(blacksmith)),
                    "The blacksmith must be active at level 5: " + Economies.activeJobIds(village.settlement()));
            village.settlement().setProgress(4, 0);
            village.heart().renewJobPosts();
            JobPosts.Post post = village.settlement().jobPosts().orElseThrow().get(0);
            JobSignBlockEntity sign = (JobSignBlockEntity) helper.getLevel()
                    .getBlockEntity(new BlockPos(post.x(), post.y(), post.z()));
            helper.assertTrue(post.jobId().equals(blacksmith) && sign != null && sign.job().equals("blacksmith"),
                    "The post must keep the blacksmith job and sign at level 4: " + post);
            helper.assertTrue(Economies.activeJobIds(village.settlement()).isEmpty(),
                    "The blacksmith must be inactive at level 4: " + Economies.activeJobIds(village.settlement()));
            village.settlement().setProgress(5, 0);
            helper.assertTrue(Economies.activeJobIds(village.settlement()).equals(List.of(blacksmith)),
                    "The blacksmith must be active at level 5 again: " + Economies.activeJobIds(village.settlement()));
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A fishing hut in a biome that is not a fisher biome: its post gets a valid job at the check of the heart, and
     * its job sign shows the new job and an icon.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_renew", skyAccess = true)
    public static void postWithAJobOfAnOtherBiomeGetsAValidJob(GameTestHelper helper) {
        Village village = village(helper, "maritime/", List.of("house_fishing_hut"), 1, POPULATION, List.of(), false,
                "minecraft:plains");
        try {
            village.heart().renewJobPosts();
            assertValidPostAndSign(helper, village, 1);
            helper.assertFalse(village.settlement().jobPosts().orElseThrow().get(0).jobId()
                    .equals("folkhold_world:fisher"), "The fisher post must get a new job");
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A house-workshop with a second job sign with no job: the sign with a job is the post, and the sign with no job
     * stays empty. The item of the broken job sign keeps the job.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_two_signs", skyAccess = true)
    public static void signWithAJobComesBeforeAnEmptySignInAHome(GameTestHelper helper) {
        Village village = village(helper, List.of("job_farmer"), 3);
        try {
            BoundingBox box = village.boxes().get(0);
            BlockPos blank = BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                    .filter(pos -> pos.getY() > box.minY() && helper.getLevel().isEmptyBlock(pos)).findFirst()
                    .orElseThrow();
            helper.getLevel().setBlock(blank, ModBlocks.JOB_SIGN.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(village.heart().getLayout().scan(helper.getLevel(), village.heart().getBlockPos()),
                    "The scan area must be ready");
            village.heart().recordJobPosts();
            village.heart().renewJobPosts();
            List<JobPosts.Post> posts = village.settlement().jobPosts().orElseThrow();
            helper.assertTrue(posts.size() == 1 && posts.get(0).jobId().equals(FARMER),
                    "Expected the farmer post only: " + posts);
            JobSignBlockEntity blankSign = (JobSignBlockEntity) helper.getLevel().getBlockEntity(blank);
            helper.assertTrue(blankSign != null && blankSign.job().isEmpty(), "The second sign must stay empty");
            BlockPos signPos = new BlockPos(posts.get(0).x(), posts.get(0).y(), posts.get(0).z());
            List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(signPos), helper.getLevel(),
                    signPos, helper.getLevel().getBlockEntity(signPos));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getOrDefault(DataComponents.BLOCK_ENTITY_DATA,
                    CustomData.EMPTY).copyTag().getString(JobSignBlockEntity.JOB).equals("farmer"),
                    "The item of the job sign must keep the job: " + drops);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A job sign with no job in a cottage (for example a sign that a player places) is a post: the heart chooses its
     * job and writes it on the sign. A job sign with no job out of the homes is not a post and stays empty.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_empty_sign", skyAccess = true)
    public static void emptyJobSignInAHomeGetsAJob(GameTestHelper helper) {
        Village village = village(helper, List.of("house_cottage"), 3);
        try {
            helper.assertTrue(village.settlement().jobPosts().orElseThrow().isEmpty(), "A cottage has no post");
            BoundingBox box = village.boxes().get(0);
            BlockPos inside = BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                    .filter(pos -> pos.getY() > box.minY() && helper.getLevel().isEmptyBlock(pos)).findFirst()
                    .orElseThrow();
            BlockPos outside = helper.absolutePos(new BlockPos(16, 2, 25));
            helper.getLevel().setBlock(inside, ModBlocks.JOB_SIGN.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.getLevel().setBlock(outside, ModBlocks.JOB_SIGN.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(village.heart().getLayout().scan(helper.getLevel(), village.heart().getBlockPos()),
                    "The scan area must be ready");
            village.heart().recordJobPosts();
            village.heart().renewJobPosts();
            assertValidPostAndSign(helper, village, 1);
            JobSignBlockEntity sign = (JobSignBlockEntity) helper.getLevel().getBlockEntity(outside);
            helper.assertTrue(sign != null && sign.job().isEmpty(), "A sign out of the homes must stay empty");
            helper.getLevel().setBlock(outside, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A village with the house-workshop of the farmer (2 beds) and a cottage (3 beds), with 5 inhabitants of the homes:
     * the 2 inhabitants of the workshop are farmers and their {@code HOME} is a bed of the workshop. The 3 inhabitants
     * with no post sleep in the cottage. An inhabitant in excess gets no bed, and a vanilla villager that holds a bed of
     * the workshop loses it.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_households", skyAccess = true)
    public static void workersOfAPostSleepInItsHome(GameTestHelper helper) {
        startStaff(helper);
        Village village = village(helper, List.of("job_farmer", "house_cottage"), 3);
        List<Villager> villagers = new ArrayList<>();
        TestSupport.atTestEnd(helper, () -> villagers.forEach(Entity::discard));
        try {
            ServerLevel level = helper.getLevel();
            BlockPos heartPos = village.heart().getBlockPos();
            helper.assertTrue(Economies.homeInhabitants(village.settlement()) == 5,
                    "Expected 5 inhabitants of the homes: " + Economies.homeInhabitants(village.settlement()));
            List<VillagerProfession> professions = VillageFolk.professions(village.settlement(),
                    VillageFolk.inhabitantCount(village.settlement()));
            professions.forEach(profession -> villagers.add(VillageFolk.spawn(level, heartPos, profession)));
            Villager extra = VillageFolk.spawn(level, heartPos, ModProfessions.INHABITANT.get());
            villagers.add(extra);
            BlockPos workshopBed = village.heart().getLayout().beds().stream()
                    .filter(village.boxes().get(0)::isInside).findFirst().orElseThrow();
            Villager stranger = EntityType.VILLAGER.create(level);
            stranger.moveTo(Vec3.atBottomCenterOf(heartPos.above()));
            stranger.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), workshopBed));
            level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(workshopBed),
                    workshopBed, 1);
            level.addFreshEntity(stranger);
            villagers.add(stranger);

            helper.assertTrue(village.heart().claimHomeBeds() == 5, "Expected 5 villagers with a new bed");
            VillagerProfession farmer = VillageFolk.professionOf(FARMER);
            List<BlockPos> farmerBeds = homes(level, villagers, farmer);
            helper.assertTrue(farmerBeds.size() == 2 && farmerBeds.stream().distinct().count() == 2
                    && farmerBeds.stream().allMatch(village.boxes().get(0)::isInside),
                    "The 2 farmers must sleep in the workshop: " + farmerBeds);
            helper.assertTrue(farmerBeds.stream().noneMatch(bed -> hasFreeTicket(level, bed)),
                    "The farmers must hold the tickets of their beds");
            helper.assertTrue(level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), workshopBed, 1,
                    PoiManager.Occupancy.IS_OCCUPIED).filter(record -> record.getPos().equals(workshopBed)).count() == 1,
                    "The bed of the vanilla villager must have 1 used ticket");
            List<BlockPos> otherBeds = homes(level, villagers, ModProfessions.INHABITANT.get());
            helper.assertTrue(otherBeds.size() == 3 && otherBeds.stream().distinct().count() == 3
                    && otherBeds.stream().allMatch(village.boxes().get(1)::isInside),
                    "The 3 inhabitants with no post must sleep in the cottage: " + otherBeds);
            helper.assertTrue(VillageHomes.home(level, stranger).isEmpty(),
                    "The vanilla villager must lose the bed of the workshop");
            helper.assertTrue(village.heart().claimHomeBeds() == 0, "The villagers must keep their beds");
            helper.assertTrue(Merchants.traders(village.settlement()).equals(List.of(farmer)),
                    "Only the farmers must trade: " + Merchants.traders(village.settlement()));
            helper.assertTrue(villagers.stream().filter(villager -> villager.getVillagerData().getProfession()
                    == ModProfessions.INHABITANT.get()).noneMatch(villager -> Merchants.isTrader(level, villager)),
                    "The inhabitants with no post must not trade");

            // A farmer that is not loaded keeps its bed when an other bed of the workshop is free.
            List<Villager> farmers = villagers.stream()
                    .filter(villager -> villager.getVillagerData().getProfession() == farmer).toList();
            BlockPos kept = VillageHomes.home(level, farmers.get(0)).orElseThrow();
            BlockPos free = VillageHomes.home(level, farmers.get(1)).orElseThrow();
            farmers.forEach(Entity::discard);
            level.getPoiManager().release(free);
            Villager newcomer = VillageFolk.spawn(level, heartPos, farmer);
            villagers.add(newcomer);
            village.heart().claimHomeBeds();
            helper.assertTrue(VillageHomes.home(level, newcomer).filter(free::equals).isPresent(),
                    "The new farmer must take the free bed " + free + ", not " + kept + ": "
                            + VillageHomes.home(level, newcomer));
        } finally {
            villagers.forEach(Entity::discard);
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A legacy village: the household of its post does the job of the post, and its legacy job slots go to the
     * inhabitants of the cottage. With no list of posts, the heart gives no bed: the vanilla rules apply.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_legacy_households", skyAccess = true)
    public static void legacyVillageGivesItsJobSlotsToTheOtherHomes(GameTestHelper helper) {
        startStaff(helper);
        Village village = village(helper, "", List.of("job_farmer", "house_cottage"), 3, POPULATION,
                List.of("folkhold_world:baker", "folkhold_world:miner", "folkhold_world:fisher"), true,
                "minecraft:plains");
        List<Villager> villagers = new ArrayList<>();
        TestSupport.atTestEnd(helper, () -> villagers.forEach(Entity::discard));
        try {
            ServerLevel level = helper.getLevel();
            BlockPos heartPos = village.heart().getBlockPos();
            VillageFolk.professions(village.settlement(), VillageFolk.inhabitantCount(village.settlement()))
                    .forEach(profession -> villagers.add(VillageFolk.spawn(level, heartPos, profession)));
            village.heart().claimHomeBeds();
            List<BlockPos> farmerBeds = homes(level, villagers, VillageFolk.professionOf(FARMER));
            helper.assertTrue(farmerBeds.size() == 2 && farmerBeds.stream().allMatch(village.boxes().get(0)::isInside),
                    "The 2 farmers must sleep in the workshop: " + farmerBeds);
            long cottageWorkers = villagers.stream().filter(villager -> VillageHomes.home(level, villager)
                    .filter(village.boxes().get(1)::isInside).isPresent())
                    .filter(villager -> villager.getVillagerData().getProfession() != ModProfessions.INHABITANT.get())
                    .count();
            helper.assertTrue(cottageWorkers == 2, "Expected 2 workers of a legacy job slot in the cottage, found "
                    + cottageWorkers);

            villagers.forEach(Entity::discard);
            villagers.clear();
            village.settlement().setJobPosts(Optional.empty());
            List<VillageFolk.Seat> seats = VillageFolk.seats(village.settlement(),
                    VillageFolk.inhabitantCount(village.settlement()));
            helper.assertTrue(seats.stream().allMatch(seat -> seat.post() == VillageFolk.NO_HOME),
                    "A village with no list of posts gives no home: " + seats);
            seats.forEach(seat -> villagers.add(VillageFolk.spawn(level, heartPos, seat.profession())));
            helper.assertTrue(village.heart().claimHomeBeds() == 0
                    && villagers.stream().allMatch(villager -> VillageHomes.home(level, villager).isEmpty()),
                    "A village with no list of posts must give no bed");
        } finally {
            villagers.forEach(Entity::discard);
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * The jobs section of the heart screen: the active jobs, one row for each post in the order of the posts (job,
     * state, workers and beds), the homes with no post, and the row for no place for a new home. The economy section
     * starts with the market rows: a village of level 3 is a hamlet.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_screen", skyAccess = true)
    public static void heartScreenShowsThePostsAndTheCottages(GameTestHelper helper) {
        startStaff(helper);
        Village village = village(helper, "", List.of("job_farmer", "job_mason", "house_cottage"), 3, 5, List.of(),
                false, "minecraft:plains");
        try {
            helper.assertTrue(Economies.homeInhabitants(village.settlement()) == 2,
                    "Expected 2 inhabitants of the homes: " + Economies.homeInhabitants(village.settlement()));
            village.heart().setHomeNoSpace(true);
            List<Row> rows = VillageLife.jobsSection(village.heart(), village.settlement()).rows();
            helper.assertTrue(key(rows.get(0).label()).equals(SCREEN + "active_jobs")
                    && rows.get(0).value().getString().equals("1"), "Expected 1 active job: " + rows.get(0));
            assertRow(helper, rows, "job.folkhold_world.farmer", SCREEN + "post.active", 2, 2);
            assertRow(helper, rows, "job.folkhold_world.mason", SCREEN + "post.empty", 0, 2);
            Row cottages = row(helper, rows, SCREEN + "cottages");
            helper.assertTrue(cottages.value().getString().equals("1"), "Expected 1 home with no post: " + cottages);
            helper.assertTrue(key(row(helper, rows, SCREEN + "new_home").value()).equals(SCREEN + "new_home.no_space"),
                    "Expected the row for no place for a new home");
            helper.assertTrue(rows.size() == 5, "Expected 5 rows: " + rows);

            village.heart().setHomeNoSpace(false);
            List<Row> after = VillageLife.jobsSection(village.heart(), village.settlement()).rows();
            helper.assertTrue(after.stream().noneMatch(row -> key(row.label()).equals(SCREEN + "new_home")),
                    "With a place for a new home, the row must not show");

            List<Row> market = VillageLife.marketRows(village.heart(), village.settlement());
            helper.assertTrue(market.size() == 3 && key(market.get(0).value()).equals(SCREEN + "market.hamlet"),
                    "A village of level 3 must be a hamlet: " + market);
            int days = VillageSchedule.daysToMarketDay(helper.getLevel(), village.settlement());
            helper.assertTrue(key(dayOf(market.get(2).value())).equals(SCREEN + (days == 0 ? "market_day.today"
                    : days == 1 ? "market_day.tomorrow" : "market_day.days")), "Wrong market day: " + market.get(2));
            String hours = key(market.get(1).value());
            helper.assertTrue(hours.equals(SCREEN + (VillageSchedule.isMarketOpen(helper.getLevel())
                    ? "market_hours.open" : "market_hours.closed")), "Wrong market hours: " + hours);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * The jobs section of a legacy village: the active jobs count the legacy job slots, a post with a job that is not
     * valid shows it, and a row gives the legacy job slots and their jobs.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_screen_legacy", skyAccess = true)
    public static void heartScreenShowsTheLegacyJobs(GameTestHelper helper) {
        int level = 3;
        List<String> slotJobs = List.of("folkhold_world:baker", "folkhold_world:miner", "folkhold_world:fisher");
        Village village = village(helper, "", List.of("job_farmer", "job_mason", "house_cottage"), level, POPULATION,
                slotJobs, true, "minecraft:plains");
        try {
            List<JobPosts.Post> posts = new ArrayList<>(village.settlement().jobPosts().orElseThrow());
            int mason = posts.indexOf(posts.stream().filter(post -> post.jobId().equals(MASON)).findFirst()
                    .orElseThrow());
            JobPosts.Post old = posts.get(mason);
            posts.set(mason, new JobPosts.Post("folkhold_world:blacksmith", old.x(), old.y(), old.z(),
                    old.homeBeds()));
            village.settlement().setJobPosts(Optional.of(posts));
            int legacy = Math.min(slotJobs.size(),
                    JobPosts.legacySlots(LevelTables.get().traits(level).jobSlots(), 1));
            helper.assertTrue(legacy > 0, "The test needs legacy job slots");

            List<Row> rows = VillageLife.jobsSection(village.heart(), village.settlement()).rows();
            Object[] active = args(rows.get(0).value());
            helper.assertTrue(key(rows.get(0).value()).equals(SCREEN + "active_jobs.legacy")
                    && active[0].equals(1 + legacy) && active[1].equals(legacy),
                    "Expected the active jobs with the legacy job slots: " + rows.get(0));
            assertRow(helper, rows, "job.folkhold_world.farmer", SCREEN + "post.active", 2, 2);
            Row blacksmith = row(helper, rows, "job.folkhold_world.blacksmith");
            helper.assertTrue(key(blacksmith.value()).equals(SCREEN + "post.not_valid"),
                    "The blacksmith post at level 3 must be not valid: " + blacksmith);
            Row legacyRow = row(helper, rows, SCREEN + "legacy_jobs");
            helper.assertTrue(args(legacyRow.value())[0].equals(legacy)
                    && legacyRow.value().getString().contains(VillageLife.jobName(slotJobs.get(0)).getString()),
                    "Expected the legacy job slots and their jobs: " + legacyRow.value().getString());
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * The market rows at fixed day times, with the default schedule (market from 11:00 to 14:00, one market day in 7
     * days): a village of level 5 with no free plot for its market shows "no free plot". Before the first scan after a
     * load, the state is "not checked yet". The test sets the day time and puts it back in the same tick.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_screen_market", skyAccess = true)
    public static void heartScreenShowsTheMarketAtFixedTimes(GameTestHelper helper) {
        Village village = village(helper, List.of("job_farmer", "job_mason", "house_cottage"), 5);
        ServerLevel level = helper.getLevel();
        long oldTime = level.getDayTime();
        try {
            DaySchedule schedule = VillageSchedule.schedule();
            helper.assertTrue(schedule.marketStart() == 5000 && schedule.marketEnd() == 8000
                    && schedule.marketDayPeriod() == 7, "The test needs the default schedule: " + schedule);
            village.heart().setMarketNoSpace(true);
            long base = 700;
            long marketDay = base;
            while (true) {
                level.setDayTime(marketDay * DaySchedule.DAY_TICKS);
                if (VillageSchedule.daysToMarketDay(level, village.settlement()) == 0) {
                    break;
                }
                marketDay++;
                helper.assertTrue(marketDay < base + 7, "No market day in 7 days");
            }

            level.setDayTime(marketDay * DaySchedule.DAY_TICKS + 6000);
            List<Row> noon = VillageLife.marketRows(village.heart(), village.settlement());
            helper.assertTrue(key(noon.get(0).value()).equals(SCREEN + "market.no_space"),
                    "Expected no free plot for the market: " + noon.get(0));
            assertText(helper, noon.get(1), SCREEN + "market_hours.open", "11:00", "14:00");
            assertMarketDay(helper, noon.get(2), SCREEN + "market_day.today", 0);

            level.setDayTime((marketDay + 6) * DaySchedule.DAY_TICKS + 1000);
            List<Row> morning = VillageLife.marketRows(village.heart(), village.settlement());
            assertText(helper, morning.get(1), SCREEN + "market_hours.closed", "11:00", "14:00");
            assertMarketDay(helper, morning.get(2), SCREEN + "market_day.tomorrow", 1);

            level.setDayTime((marketDay + 3) * DaySchedule.DAY_TICKS + 9000);
            List<Row> afternoon = VillageLife.marketRows(village.heart(), village.settlement());
            assertText(helper, afternoon.get(1), SCREEN + "market_hours.closed", "11:00", "14:00");
            assertMarketDay(helper, afternoon.get(2), SCREEN + "market_day.days", 4);

            village.heart().getLayout().invalidate();
            List<Row> unknown = VillageLife.marketRows(village.heart(), village.settlement());
            helper.assertTrue(key(unknown.get(0).value()).equals(SCREEN + "market.unknown"),
                    "Before the first scan, the market must be not checked yet: " + unknown.get(0));
        } finally {
            level.setDayTime(oldTime);
            village.heart().setMarketNoSpace(false);
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A blank job sign in a home is a post with no job: its row shows "No job", not an empty name.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "job_posts_screen_blank", skyAccess = true)
    public static void heartScreenShowsAPostWithNoJob(GameTestHelper helper) {
        Village village = village(helper, List.of("job_farmer", "house_cottage"), 3);
        try {
            List<JobPosts.Post> posts = new ArrayList<>(village.settlement().jobPosts().orElseThrow());
            JobPosts.Post old = posts.get(0);
            posts.set(0, new JobPosts.Post("", old.x(), old.y(), old.z(), old.homeBeds()));
            village.settlement().setJobPosts(Optional.of(posts));
            List<Row> rows = VillageLife.jobsSection(village.heart(), village.settlement()).rows();
            Row blank = row(helper, rows, SCREEN + "post.no_job");
            helper.assertTrue(key(blank.value()).equals(SCREEN + "post.not_valid"),
                    "A post with no job is not valid: " + blank);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /** The text of a row value has the key and the arguments. */
    private static void assertText(GameTestHelper helper, Row row, String expectedKey, Object... expectedArgs) {
        helper.assertTrue(key(row.value()).equals(expectedKey)
                && Arrays.equals(args(row.value()), expectedArgs),
                "Expected " + expectedKey + " " + Arrays.toString(expectedArgs) + ": " + row.value());
    }

    /**
     * The market day row has the text of the days and the extra offers of the default schedule (2 offers for each
     * worker).
     */
    private static void assertMarketDay(GameTestHelper helper, Row row, String expectedKey, int days) {
        int offers = VillageSchedule.schedule().marketDayOffers();
        helper.assertTrue(offers == 2 && key(row.value()).equals(SCREEN + "market_day.offers")
                && args(row.value())[1].equals(offers), "Expected 2 extra offers: " + row.value());
        Component day = dayOf(row.value());
        helper.assertTrue(key(day).equals(expectedKey) && Arrays.equals(args(day), new Object[] {days}),
                "Expected " + expectedKey + " " + days + ": " + row.value());
    }

    /** The text of the days in the value of the market day row, with no extra offers. */
    private static Component dayOf(Component value) {
        String key = key(value);
        return key.equals(SCREEN + "market_day.offers") || key.equals(SCREEN + "market_day.offer")
                ? (Component) args(value)[0] : value;
    }

    /** The row with a label key. */
    private static Row row(GameTestHelper helper, List<Row> rows, String labelKey) {
        Optional<Row> found = rows.stream().filter(row -> key(row.label()).equals(labelKey)).findFirst();
        helper.assertTrue(found.isPresent(), "No row " + labelKey + ": " + rows);
        return found.orElseThrow();
    }

    /** The row of a post has the text of its state, its workers and the beds of its home. */
    private static void assertRow(GameTestHelper helper, List<Row> rows, String job, String state, int workers,
            int beds) {
        Row row = row(helper, rows, job);
        Object[] args = args(row.value());
        helper.assertTrue(key(row.value()).equals(state) && args[0].equals(workers) && args[1].equals(beds),
                "Expected " + state + " " + workers + "/" + beds + " for " + job + ": " + row.value());
    }

    /** The translation key of a text, or an empty text. */
    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    /** The arguments of a translated text. */
    private static Object[] args(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
    }

    /** True if the bed has a free ticket. */
    private static boolean hasFreeTicket(ServerLevel level, BlockPos bed) {
        return level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bed, 1,
                PoiManager.Occupancy.HAS_SPACE).anyMatch(record -> record.getPos().equals(bed));
    }

    /** The beds of the villagers with a profession. */
    private static List<BlockPos> homes(ServerLevel level, List<Villager> villagers, VillagerProfession profession) {
        return villagers.stream().filter(villager -> villager.getVillagerData().getProfession() == profession)
                .map(villager -> VillageHomes.home(level, villager)).flatMap(Optional::stream).toList();
    }

    /** The village has the posts, each post has a valid job, and its job sign shows this job and an icon. */
    private static void assertValidPostAndSign(GameTestHelper helper, Village village, int count) {
        List<JobPosts.Post> posts = village.settlement().jobPosts().orElseThrow();
        helper.assertTrue(posts.size() == count, "Expected " + count + " posts: " + posts);
        for (JobPosts.Post post : posts) {
            helper.assertTrue(Economies.isValidJob(helper.getLevel().getServer(), village.settlement(), post.jobId()),
                    "The post must have a valid job: " + post);
            JobSignBlockEntity sign = (JobSignBlockEntity) helper.getLevel()
                    .getBlockEntity(new BlockPos(post.x(), post.y(), post.z()));
            helper.assertTrue(sign != null && JobPosts.jobId(sign.job(), FolkholdWorld.MOD_ID).equals(post.jobId())
                    && !sign.iconStack().isEmpty(), "The job sign must show the job of the post: " + post);
        }
    }

    /** The staff of the default configuration: 2 builders and 1 handyman. */
    private static void startStaff(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.BUILDERS, 2);
        TestSupport.setConfigForTest(helper, WorldConfig.HANDYMEN, 1);
    }

    /** The start population of a village of level 1: 60 % of its maximum population, as in the economy. */
    private static int startPopulation() {
        return Math.max(2, (int) Math.round(LevelTables.get().traits(1).maxPopulation() * START_SHARE));
    }

    private static Village village(GameTestHelper helper, List<String> names, int level) {
        return village(helper, "", names, level, POPULATION, List.of(), false, "minecraft:plains");
    }

    /** Places the pieces, the heart and its settlement, and scans the village. */
    private static Village village(GameTestHelper helper, String style, List<String> names, int level,
            int population, List<String> slotJobs, boolean legacy, String biome) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel serverLevel = helper.getLevel();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<BoundingBox> boxes = new ArrayList<>();
        List<VillageReference.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                    "village/" + style + names.get(i));
            StructureTemplate template = serverLevel.getStructureManager().get(id).orElseThrow();
            BlockPos origin = helper.absolutePos(ORIGINS.get(i));
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.getRandom(), Block.UPDATE_CLIENTS);
            boxes.add(template.getBoundingBox(settings, origin));
            pieces.add(new VillageReference.Piece(id, origin, Rotation.NONE));
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.setRepairReference(new VillageReference(pieces));
        Settlement settlement = Settlements.of(serverLevel.getServer(), heart).orElseThrow();
        settlement.setProgress(level, 0);
        settlement.setBiome(biome);
        settlement.economy().setPopulation(population);
        settlement.economy().setJobs(slotJobs);
        settlement.setLegacyJobs(legacy);
        settlement.setJobPosts(Optional.empty());
        helper.assertTrue(heart.getLayout().scan(serverLevel, heart.getBlockPos()), "The scan area must be ready");
        heart.recordBeds();
        heart.recordJobPosts();
        return new Village(heart, settlement, boxes);
    }

    /** Removes the heart and the pieces: a later test at this place must not find them. */
    private static void end(GameTestHelper helper, Village village) {
        village.settlement().setLegacyJobs(false);
        helper.setBlock(HEART, Blocks.AIR);
        village.boxes().forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos -> helper.getLevel()
                .setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
    }
}
