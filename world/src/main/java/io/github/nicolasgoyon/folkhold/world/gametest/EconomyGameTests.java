package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.economy.CycleReport;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Collections;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the economy of the settlements, with the jobs of the mod data.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final int CELL = TerritoryClaims.CELL_BLOCKS;

    private EconomyGameTests() {
    }

    private static Settlement settlement(MinecraftServer server, int cellX, int level) {
        String faction = FactionData.get(server).factions().get(0).id();
        int x = cellX * CELL + CELL / 2;
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, 0, faction, "Marketon", x, 100);
        settlement.setProgress(level, 0);
        settlement.setBiome("minecraft:plains");
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** The data packs of the mod give jobs and item values. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "economy_data")
    public static void modDataHasJobsAndItems(GameTestHelper helper) {
        helper.assertTrue(EconomyData.jobs().containsKey("folkhold_world:farmer"), "The farmer job must exist");
        helper.assertTrue(EconomyData.catalog().info("minecraft:carrot").isFood(), "A carrot must be food");
        helper.succeed();
    }

    /** A new settlement gets a population, jobs and a start stock. A higher level gives more stock. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "economy_start")
    public static void initializationGivesPopulationJobsAndStock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement hamlet = settlement(server, 600, 1);
        Settlement town = settlement(server, 610, 10);

        Economies.initialize(server, hamlet);
        Economies.initialize(server, town);

        EconomyState state = hamlet.economy();
        int slots = LevelTables.get().traits(1).jobSlots();
        helper.assertTrue(state.isInitialized() && state.population() >= 2, "The hamlet must have a population");
        helper.assertTrue(state.jobs().size() == slots, "Expected " + slots + " jobs, not " + state.jobs().size());
        helper.assertTrue(state.stock().total() > 0, "The hamlet must start with a stock");
        helper.assertTrue(town.economy().stock().total() > state.stock().total(),
                "A town must start with more stock than a hamlet");
        helper.succeed();
    }

    /** A cycle of a village of farmers produces wheat, feeds the population and gives a prosperity. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "economy_cycle")
    public static void cycleProducesAndFeeds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = settlement(server, 620, 3);
        Economies.initialize(server, settlement);
        // Only farmers: the test does not depend on the random jobs.
        int slots = LevelTables.get().traits(3).jobSlots();
        settlement.economy().setJobs(Collections.nCopies(slots, "folkhold_world:farmer"));

        CycleReport report = Economies.runCycle(server, settlement, new SplittableRandom(1));
        double prosperity = Economies.prosperity(server, settlement);

        helper.assertTrue(report.workingJobs() > 0, "At least one job must work");
        helper.assertTrue(report.produced().getOrDefault("minecraft:wheat", 0) > 0, "The farmers must produce wheat");
        helper.assertTrue(report.foodCovered() >= 0.9, "The population must eat, not " + report.foodCovered());
        helper.assertTrue(prosperity > 0.5, "A fed settlement must have a good prosperity, not " + prosperity);
        helper.succeed();
    }

    /** A faction with no treasury change has the configured start treasury. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "economy_treasury")
    public static void treasuryStartsAtTheConfiguredValue(GameTestHelper helper) {
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        String faction = "test_faction_" + UUID.randomUUID();

        long start = data.treasury(faction);
        long after = data.addTreasury(faction, -start - 50);

        helper.assertTrue(start == WorldConfig.ECONOMY_START_TREASURY.get(), "Wrong start treasury: " + start);
        helper.assertTrue(after == 0, "The treasury must not go below 0");
        helper.succeed();
    }

    /** The text of a price has two decimals at most and no trailing zero. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "economy_data")
    public static void priceTextHasTwoDecimalsAtMost(GameTestHelper helper) {
        helper.assertTrue(Merchants.priceText(2.0).equals("2"), "2.0 -> " + Merchants.priceText(2.0));
        helper.assertTrue(Merchants.priceText(0.85).equals("0.85"), "0.85 -> " + Merchants.priceText(0.85));
        helper.assertTrue(Merchants.priceText(12.5).equals("12.5"), "12.5 -> " + Merchants.priceText(12.5));
        helper.assertTrue(Merchants.priceText(1.999).equals("2"), "1.999 -> " + Merchants.priceText(1.999));
        helper.succeed();
    }
}
