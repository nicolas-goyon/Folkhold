package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the first house-workshops of a generated village: the plots of the street to the south of the
 * plaza use the pools {@code jobs_food} and {@code jobs_start} (see {@code first_jobs} in
 * {@code tools/structures/village.py}).
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstJobsGameTests {
    /** The first candidate chunk of the villages: far from the areas of the other tests. */
    private static final int FIRST_CHUNK = 12000;
    /** The distance between 2 candidate chunks, in chunks. */
    private static final int CANDIDATE_STEP = 12;
    private static final int CANDIDATES = 4;
    /** The house-workshops of the street to the south of the plaza in the base style. */
    private static final int BASE_WORKSHOPS = 2;

    private FirstJobsGameTests() {
    }

    /**
     * Each generated village of the base style has {@link #BASE_WORKSHOPS} house-workshops, and one of them is the
     * house-workshop of a food job. Its job sign names a job with a minimum level of 1 that trades. No other piece of
     * the village has a job sign.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_village")
    public static void generatedVillageHasAFoodHouseWorkshop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        // The base style with no variants: the biome of the GameTest world does not select another style.
        FortifiedVillageStructure base = new FortifiedVillageStructure(new Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                pool(pools, "village/plaza"), structure.size(), structure.maxDistanceFromCenter(), structure.wall(),
                pool(pools, "village/homes"), List.of());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK);
            StructureStart start = base.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            List<String> ids = jobsOf(level, start);
            helper.assertTrue(ids.size() == BASE_WORKSHOPS, "The village in " + chunk + " must have "
                    + BASE_WORKSHOPS + " house-workshops: " + ids);
            List<Job> jobs = ids.stream().map(EconomyData.jobs()::get).filter(job -> job != null
                    && job.minLevel() == 1 && trades(job)).toList();
            helper.assertTrue(jobs.size() == ids.size(), "Each job must have a minimum level of 1 and trade: " + ids);
            helper.assertTrue(jobs.stream().anyMatch(job -> job.producesFood(EconomyData.catalog())),
                    "The village in " + chunk + " must have a food job: " + ids);
        }
        helper.succeed();
    }

    /**
     * The pools {@code jobs_food} and {@code jobs_start} of each variant have only house-workshops of jobs with a
     * minimum level of 1 that trade and that are valid in each biome of the variant. The jobs of the pool
     * {@code jobs_food} make food.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_pools")
    public static void firstJobPoolsHaveValidJobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        List<FortifiedVillageStructure.Variant> variants = new ArrayList<>(structure.variants());
        variants.add(new FortifiedVillageStructure.Variant(HolderSet.direct(),
                structure.startPool(), structure.wall(), structure.homes(), Optional.empty()));
        List<String> problems = new ArrayList<>();
        for (FortifiedVillageStructure.Variant variant : variants) {
            String start = variant.startPool().unwrapKey().orElseThrow().location().getPath();
            String folder = start.substring(0, start.lastIndexOf('/') + 1);
            for (String name : List.of("jobs_food", "jobs_start")) {
                List<StructurePoolElement> elements = pool(pools, folder + name).value()
                        .getShuffledTemplates(RandomSource.create(0L));
                if (elements.isEmpty()) {
                    problems.add(folder + name + " is empty");
                }
                for (StructurePoolElement element : elements) {
                    String template = VillageReference.templateOf(level, element).map(ResourceLocation::getPath)
                            .orElse("");
                    String id = FolkholdWorld.MOD_ID + ":" + template.substring(template.lastIndexOf('/') + 1)
                            .replaceFirst("^job_", "");
                    Job job = EconomyData.jobs().get(id);
                    boolean food = name.equals("jobs_food");
                    if (job == null || job.minLevel() != 1 || !trades(job)
                            || (food && !job.producesFood(EconomyData.catalog()))
                            || !variant.biomes().stream().allMatch(biome -> validIn(job, biome))) {
                        problems.add(folder + name + ": " + template);
                    }
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Jobs that are not valid in the first job pools: " + problems);
        helper.succeed();
    }

    /** The job ids of the job signs of the pieces of a village. */
    private static List<String> jobsOf(ServerLevel level, StructureStart start) {
        List<String> jobs = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            Optional<StructureTemplate> template = VillageReference.templateOf(level, pool.getElement())
                    .flatMap(level.getStructureManager()::get);
            if (template.isEmpty()) {
                continue;
            }
            for (StructureTemplate.StructureBlockInfo sign : template.get().filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), ModBlocks.JOB_SIGN.get())) {
                jobs.add(FolkholdWorld.MOD_ID + ":" + sign.nbt().getString(JobSignBlockEntity.JOB));
            }
        }
        return jobs;
    }

    /** True if the workers of a job trade: the job file has a profession. */
    private static boolean trades(Job job) {
        return EconomyData.profession(job.id()).isPresent();
    }

    /** True if a job is valid in a biome: the job has no biome list, or the list has the biome or one of its tags. */
    private static boolean validIn(Job job, Holder<Biome> biome) {
        return job.biomes().isEmpty() || job.biomes().stream().anyMatch(entry -> entry.startsWith("#")
                ? biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(entry.substring(1))))
                : biome.is(ResourceLocation.parse(entry)));
    }

    private static Holder<StructureTemplatePool> pool(Registry<StructureTemplatePool> pools, String path) {
        return pools.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL,
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path)));
    }
}
