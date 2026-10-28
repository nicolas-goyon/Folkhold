package io.github.nicolasgoyon.folkhold.world.economy;

import io.github.nicolasgoyon.folkhold.core.sim.economy.CycleReport;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Economy;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
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

/**
 * The economy of the settlements in the game: jobs from the data packs, cycles, prosperity.
 *
 * <ul>
 *   <li>The first abstract tick of a settlement sets its population, its jobs and its start stock. A settlement
 *       with a higher level starts with more stock.</li>
 *   <li>At each cycle: the jobs follow the level (more slots, new jobs), then the settlement runs one
 *       economic cycle (see {@link Economy}).</li>
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
        CycleReport report = Economy.runCycle(settlement.economy(), jobs(settlement), traits.maxPopulation(),
                traits.stockCapacity(), EconomyData.catalog(), settings(), random);
        SettlementData.get(server).setDirty();
        if (report.foodCovered() < SHORTAGE) {
            SettlementLevels.addExperience(server, settlement, -WorldConfig.ECONOMY_FOOD_SHORTAGE_LOSS.get());
        }
        return report;
    }

    /** The prosperity of a settlement (0 to 1): 50 % food, 30 % working jobs, 20 % faction treasury. */
    public static double prosperity(MinecraftServer server, Settlement settlement) {
        EconomyState state = settlement.economy();
        if (!state.isInitialized()) {
            return WorldConfig.LEVEL_BASE_PROSPERITY.get();
        }
        long treasury = SettlementData.get(server).treasury(settlement.factionId());
        double treasuryScore = treasury >= WorldConfig.ECONOMY_TREASURY_MINIMUM.get() ? 1.0 : 0.0;
        return 0.5 * state.foodCovered() + 0.3 * state.jobsWorking() + 0.2 * treasuryScore;
    }

    /** Gives the settlement the number of jobs of its level. The settlement keeps its valid jobs. */
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

    private static boolean matchesBiome(Job job, String biomeId, Optional<Holder.Reference<Biome>> biome) {
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
