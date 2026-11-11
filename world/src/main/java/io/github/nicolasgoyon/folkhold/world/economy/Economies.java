package io.github.nicolasgoyon.folkhold.world.economy;

import io.github.nicolasgoyon.folkhold.core.sim.economy.CycleReport;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Economy;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The economy of the settlements in the game: jobs from the data packs, cycles, prosperity.
 *
 * <ul>
 *   <li>The first abstract tick of a settlement sets its population, its jobs and its start stock. A settlement
 *       with a higher level starts with more stock.</li>
 *   <li>At each cycle: the job slots follow the level (more slots, new jobs), then the settlement runs one
 *       economic cycle (see {@link Economy}). Only the active jobs work: the jobs of the active job posts of a scanned
 *       village, and the job slots of a village with no scan (see {@link #activeJobIds}).</li>
 *   <li>A food shortage removes experience.</li>
 *   <li>The prosperity of a settlement comes from its food, its jobs and the treasury of its faction.</li>
 * </ul>
 */
public final class Economies {
    /** Share of the maximum population at the start. */
    private static final double START_POPULATION = 0.6;
    /** A settlement with less food than this share has a food shortage. */
    private static final double SHORTAGE = 0.5;

    private Economies() {
    }

    /** The economy settings from the server configuration. */
    public static EconomySettings settings() {
        EconomySettings defaults = EconomySettings.DEFAULT;
        return new EconomySettings(WorldConfig.ECONOMY_WORKERS_PER_JOB.get(),
                WorldConfig.ECONOMY_FOOD_PER_INHABITANT.get(), WorldConfig.ECONOMY_DECAY_RATE.get(),
                defaults.growthRate(), WorldConfig.ECONOMY_ELASTICITY.get(), defaults.minPriceFactor(),
                defaults.maxPriceFactor(), defaults.sellMarkup(), defaults.buyMarkdown());
    }

    /** The {@link io.github.nicolasgoyon.folkhold.world.settlement.SettlementSystem} of the economy. */
    public static void tick(MinecraftServer server, Settlement settlement, TickInfo tick) {
        if (!EconomyData.isActive()) {
            return;
        }
        EconomyState state = settlement.economy();
        if (!state.isInitialized()) {
            initialize(server, settlement);
        }
        if (tick.cycle()) {
            runCycle(server, settlement, new SplittableRandom(Settlements.seed(settlement.id()) ^ tick.number()));
        }
    }

    /**
     * Sets the population, the jobs and the start stock of a settlement.
     *
     * @param server     the server
     * @param settlement the settlement
     */
    public static void initialize(MinecraftServer server, Settlement settlement) {
        if (settlement.biome().isEmpty()) {
            BlockPos pos = Settlements.position(server, settlement);
            settlement.setBiome(biomeAt(server.overworld(), pos.getX(), pos.getY(), pos.getZ()));
        }
        LevelTraits traits = LevelTables.get().traits(settlement.level());
        EconomyState state = settlement.economy();
        state.setPopulation(Math.max(2, (int) Math.round(traits.maxPopulation() * START_POPULATION)));
        updateJobs(server, settlement);
        Economy.seedStock(state, jobs(settlement), 2 + settlement.level(), traits.stockCapacity(),
                EconomyData.catalog(), settings());
        state.setInitialized(true);
        SettlementData.get(server).setDirty();
    }

    /**
     * Runs one economic cycle of a settlement now.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param random     the random source
     * @return the report of the cycle
     */
    public static CycleReport runCycle(MinecraftServer server, Settlement settlement, SplittableRandom random) {
        updateJobs(server, settlement);
        LevelTraits traits = LevelTables.get().traits(settlement.level());
        CycleReport report = Economy.runCycle(settlement.economy(), activeJobs(settlement), traits.maxPopulation(),
                traits.stockCapacity(), EconomyData.catalog(), settings(), random);
        SettlementData.get(server).setDirty();
        if (report.foodCovered() < SHORTAGE) {
            SettlementLevels.addExperience(server, settlement, -WorldConfig.ECONOMY_FOOD_SHORTAGE_LOSS.get());
        }
        return report;
    }

    /**
     * The prosperity of a settlement (0 to 1): 50 % food, 30 % working jobs, 20 % faction treasury. A settlement with
     * job slots and no active job (for example no active job post) gets no share for the jobs.
     */
    public static double prosperity(MinecraftServer server, Settlement settlement) {
        EconomyState state = settlement.economy();
        if (!state.isInitialized()) {
            return WorldConfig.LEVEL_BASE_PROSPERITY.get();
        }
        long treasury = SettlementData.get(server).treasury(settlement.factionId());
        double treasuryScore = treasury >= WorldConfig.ECONOMY_TREASURY_MINIMUM.get() ? 1.0 : 0.0;
        double jobsScore = !state.jobs().isEmpty() && activeJobIds(settlement).isEmpty() ? 0.0 : state.jobsWorking();
        return 0.5 * state.foodCovered() + 0.3 * jobsScore + 0.2 * treasuryScore;
    }

    /**
     * Gives the settlement the job slots of its level (see {@link JobAssignment}). The settlement keeps its valid jobs.
     * The job slots are the jobs of a village with no scan, and the legacy job slots of a legacy village (see
     * {@link JobPosts}).
     */
    static void updateJobs(MinecraftServer server, Settlement settlement) {
        int slots = LevelTables.get().traits(settlement.level()).jobSlots();
        List<Job> eligible = eligibleJobs(server, settlement);
        List<String> current = settlement.economy().jobs();
        List<String> updated = JobAssignment.assign(current, eligible, slots, EconomyData.catalog(),
                Settlements.seed(settlement.id()));
        if (!updated.equals(current)) {
            settlement.economy().setJobs(updated);
        }
    }

    /**
     * The jobs that a settlement can have: minimum level and biome. The culture of the faction of the settlement
     * changes the weights of the jobs (see {@link Job#forCulture}).
     */
    public static List<Job> eligibleJobs(MinecraftServer server, Settlement settlement) {
        Optional<Holder.Reference<Biome>> biome = biomeHolder(server, settlement.biome());
        String culture = FactionData.get(server).byId(settlement.factionId()).map(FactionSpec::cultureId)
                .orElse(null);
        List<Job> result = new ArrayList<>();
        for (Job job : EconomyData.jobs().values()) {
            if (job.minLevel() <= settlement.level() && matchesBiome(job, settlement.biome(), biome)) {
                result.add(job.forCulture(culture));
            }
        }
        return result;
    }

    /**
     * The jobs that a village of a level can have in a biome: minimum level and biome. The world generation uses this
     * method: it reads no saved data.
     *
     * @param biome   the biome of the village
     * @param level   the level of the village
     * @param culture the id of the culture that changes the weights of the jobs (see {@link Job#forCulture}), or null
     * @return the jobs
     */
    public static List<Job> eligibleJobs(Holder<Biome> biome, int level, @Nullable String culture) {
        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
        List<Job> result = new ArrayList<>();
        for (Job job : EconomyData.jobs().values()) {
            if (job.minLevel() <= level && matchesBiome(job, biomeId, Optional.of(biome))) {
                result.add(job.forCulture(culture));
            }
        }
        return result;
    }

    /** The jobs of a settlement, in its order. A job id with no definition is skipped. */
    public static List<Job> jobs(Settlement settlement) {
        List<Job> result = new ArrayList<>();
        for (String id : settlement.economy().jobs()) {
            Job job = EconomyData.jobs().get(id);
            if (job != null) {
                result.add(job);
            }
        }
        return result;
    }

    /**
     * The job ids of the active job slots of a settlement, in its order (see {@link JobPosts#jobs}).
     *
     * <ul>
     *   <li>A village with no scan of its homes: the job slots of its level (see {@link JobAssignment}).</li>
     *   <li>A scanned village: the jobs of its active posts. A post is active when its job is valid (see
     *       {@link #isValidJob}) and its home has an inhabitant (see {@link #homeInhabitants}).</li>
     *   <li>A legacy village: the jobs of its active posts and its legacy job slots.</li>
     * </ul>
     *
     * @param settlement the settlement
     * @return the job ids
     */
    public static List<String> activeJobIds(Settlement settlement) {
        int slots = LevelTables.get().traits(settlement.level()).jobSlots();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return JobPosts.jobs(settlement.jobPosts(), settlement.legacyJobs(), settlement.economy().jobs(), slots,
                job -> isValidJob(server, settlement, job), homeInhabitants(settlement));
    }

    /**
     * True if a job is valid in a settlement: the job file exists, its minimum level is not above the level of the
     * settlement, and the biome of the settlement matches.
     *
     * @param server     the server, or null to skip the biome check
     * @param settlement the settlement
     * @param jobId      the job id
     * @return true for a valid job
     */
    public static boolean isValidJob(@Nullable MinecraftServer server, Settlement settlement, String jobId) {
        Job job = EconomyData.jobs().get(jobId);
        if (job == null || job.minLevel() > settlement.level()) {
            return false;
        }
        return server == null || matchesBiome(job, settlement.biome(), biomeHolder(server, settlement.biome()));
    }

    /**
     * True if a job post must get a new job (see {@code VillageHeartBlockEntity.renewJobPosts}): a job sign with no
     * job, or a job that is not valid in the biome of the settlement. The biome of a settlement does not change.
     *
     * <ul>
     *   <li>A minimum level above the level of the settlement is not a reason: the level can go down and come back,
     *       and the post is inactive in this time.</li>
     *   <li>A job id with no job file is not a reason: a data pack can remove a file, or a file can have an error.
     *       The post is inactive, and it works again when the job file comes back.</li>
     * </ul>
     *
     * @param server     the server
     * @param settlement the settlement
     * @param jobId      the job id of the post
     * @return true for a post that must get a new job
     */
    public static boolean needsNewJob(MinecraftServer server, Settlement settlement, String jobId) {
        if (jobId.isEmpty()) {
            return true;
        }
        Job job = EconomyData.jobs().get(jobId);
        return job != null && !matchesBiome(job, settlement.biome(), biomeHolder(server, settlement.biome()));
    }

    /**
     * True if a job produces food (see {@link Job#producesFood}).
     *
     * @param jobId the job id
     * @return false for a job id with no definition
     */
    public static boolean producesFood(String jobId) {
        Job job = EconomyData.jobs().get(jobId);
        return job != null && job.producesFood(EconomyData.catalog());
    }

    /**
     * The inhabitants of the homes of a settlement: they fill the homes of the valid posts in the order of the posts. The builders and
     * the handymen live in the hall. The first inhabitant is the trader of the village (see
     * {@link io.github.nicolasgoyon.folkhold.world.village.VillageFolk#professions}): it lives in the first home.
     *
     * @param settlement the settlement
     * @return the number of inhabitants of the homes
     */
    public static int homeInhabitants(Settlement settlement) {
        int inhabitants = VillagePlaces.inhabitants(settlement.economy().population(), settlement.beds(),
                VillageBeds.staff());
        return inhabitants <= 0 ? 0 : Math.max(1, inhabitants - VillageBeds.staff());
    }

    /**
     * The active jobs of a settlement, in its order (see {@link #activeJobIds}). A job id with no definition is
     * skipped.
     */
    public static List<Job> activeJobs(Settlement settlement) {
        List<Job> result = new ArrayList<>();
        for (String id : activeJobIds(settlement)) {
            Job job = EconomyData.jobs().get(id);
            if (job != null) {
                result.add(job);
            }
        }
        return result;
    }

    private static boolean matchesBiome(Job job, String biomeId, Optional<? extends Holder<Biome>> biome) {
        if (job.biomes().isEmpty()) {
            return true;
        }
        for (String entry : job.biomes()) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && biome.isPresent() && biome.get().is(TagKey.create(Registries.BIOME, tag))) {
                    return true;
                }
            } else if (entry.equals(biomeId)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Holder.Reference<Biome>> biomeHolder(MinecraftServer server, String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        if (id == null || biomeId.isEmpty()) {
            return Optional.empty();
        }
        return server.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, id));
    }

    /**
     * The biome id at a position, from the biome source. The chunk does not need to exist.
     *
     * @param level the level
     * @param x     block X
     * @param y     block Y
     * @param z     block Z
     * @return the biome id, or an empty text
     */
    public static String biomeAt(ServerLevel level, int x, int y, int z) {
        return level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
                .unwrapKey().map(key -> key.location().toString()).orElse("");
    }

}
