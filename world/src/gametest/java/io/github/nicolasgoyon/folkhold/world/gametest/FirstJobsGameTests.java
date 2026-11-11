package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.TemplateSigns;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
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
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the house-workshops of a generated village: the village has the job posts of its start level (see
 * {@link FortifiedVillageStructure}). The pools {@code jobs_food} and {@code jobs_start} (see {@code first_jobs} in
 * {@code tools/structures/village.py}) have valid jobs.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstJobsGameTests {
    /** The first candidate chunk of the villages: far from the areas of the other tests. */
    private static final int FIRST_CHUNK = 12000;
    /** The distance between 2 candidate chunks, in chunks. */
    private static final int CANDIDATE_STEP = 12;
    private static final int CANDIDATES = 8;

    private FirstJobsGameTests() {
    }

    /** The villages of the base style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_village")
    public static void generatedVillageHasAFoodHouseWorkshop(GameTestHelper helper) {
        checkStyle(helper, "", 0);
    }

    /** The villages of the Sunreach style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_village_sunreach")
    public static void sunreachVillageHasAFoodHouseWorkshop(GameTestHelper helper) {
        checkStyle(helper, "sunreach/", 1);
    }

    /** The villages of the Valeholm style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_village_valeholm")
    public static void valeholmVillageHasAFoodHouseWorkshop(GameTestHelper helper) {
        checkStyle(helper, "valeholm/", 2);
    }

    /** The maritime villages: see {@link #checkStyle}. The fishing huts of the pool of the houses do not count. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "first_jobs_village_maritime")
    public static void maritimeVillageHasAFoodHouseWorkshop(GameTestHelper helper) {
        checkStyle(helper, "maritime/", 3);
    }

    /**
     * Each generated village of a style has the job posts of its start level (house-workshops and homes with a job
     * sign): one for each job slot of the level, at most one for each job that the level and the biome allow. One of
     * them is the house-workshop of a food job. Each job sign names a job of the level that trades. The jobs are the
     * posts of the layout of the village.
     *
     * @param folder the folder of the pieces of the style
     * @param row    the row of the candidate chunks of the style
     */
    private static void checkStyle(GameTestHelper helper, String folder, int row) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        // The style with no variants: the biome and the culture of the GameTest world do not select another style.
        FortifiedVillageStructure village = new FortifiedVillageStructure(new Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                pool(pools, "village/" + folder + "plaza"), structure.maxDistanceFromCenter(),
                structure.wall(), pool(pools, "village/" + folder + "homes"), List.of());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK + row * CANDIDATE_STEP);
            StructureStart start = village.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            FortifiedVillageStructure.Layout layout = village.layout(new Structure.GenerationContext(
                    level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level,
                    biome -> true)).orElseThrow();
            int startLevel = Settlements.startLevel(level.getSeed(), chunk);
            BlockPos center = layout.center();
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(center.getX()),
                    QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(center.getZ()),
                    level.getChunkSource().randomState().sampler());
            int workshops = Math.min(LevelTables.get().traits(startLevel).jobSlots(),
                    Economies.eligibleJobs(biome, startLevel, null).size());
            List<String> ids = workshopJobs(level, start);
            helper.assertTrue(ids.size() == workshops, "The village " + folder + " of the level " + startLevel
                    + " in " + chunk + " must have " + workshops + " house-workshops: " + ids);
            helper.assertTrue(ids.stream().sorted().toList().equals(layout.posts().stream().sorted().toList()),
                    "The house-workshops must be the posts of the layout: " + ids + ", " + layout.posts());
            List<Job> jobs = ids.stream().map(EconomyData.jobs()::get).filter(job -> job != null
                    && job.minLevel() <= startLevel && trades(job)).toList();
            helper.assertTrue(jobs.size() == ids.size(), "Each job must be a job of the level that trades: " + ids);
            helper.assertTrue(jobs.stream().anyMatch(job -> job.producesFood(EconomyData.catalog())),
                    "The village " + folder + " in " + chunk + " must have a food job: " + ids);
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

    /**
     * The job ids of the job signs of a village, as the scan of the heart reads them: the house-workshops
     * ({@code job_<id>} pieces) and the homes with a job sign (the fishing hut of the maritime villages).
     */
    private static List<String> workshopJobs(ServerLevel level, StructureStart start) {
        return start.getPieces().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(piece -> ((PoolElementStructurePiece) piece).getElement())
                .flatMap(element -> VillageReference.templateOf(level, element).stream())
                .flatMap(id -> level.getStructureManager().get(id).map(template -> TemplateSigns.of(id, template))
                        .stream())
                .flatMap(signs -> signs.jobs().stream())
                .toList();
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
