package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
        Village village = village(helper, List.of("job_farmer", "house_cottage"), level, slotJobs, true);
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

    private static Village village(GameTestHelper helper, List<String> names, int level) {
        return village(helper, names, level, List.of(), false);
    }

    /** Places the pieces, the heart and its settlement, and scans the village. */
    private static Village village(GameTestHelper helper, List<String> names, int level, List<String> slotJobs,
            boolean legacy) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel serverLevel = helper.getLevel();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<BoundingBox> boxes = new ArrayList<>();
        List<VillageReference.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                    "village/" + names.get(i));
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
        settlement.economy().setPopulation(POPULATION);
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
