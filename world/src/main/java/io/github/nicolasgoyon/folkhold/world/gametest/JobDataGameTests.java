package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Economy;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the job data files of the mod.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobDataGameTests {
    private static final int CELL = TerritoryClaims.CELL_BLOCKS;
    private static final int CYCLES = 60;

    private JobDataGameTests() {
    }

    private static List<String> items(Job job) {
        List<String> items = new ArrayList<>();
        job.recipe().inputs().forEach(input -> items.add(input.item()));
        job.recipe().outputs().forEach(output -> items.add(output.item()));
        return items;
    }

    /** Each item of each job is a game item and has values in folkhold/items.json. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "job_data_items")
    public static void eachJobItemExistsAndHasValues(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Job job : EconomyData.jobs().values()) {
            for (String item : items(job)) {
                ResourceLocation id = ResourceLocation.tryParse(item);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    problems.add(job.id() + ": unknown item " + item);
                } else if (!EconomyData.catalog().contains(item)) {
                    problems.add(job.id() + ": no values for " + item);
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Problems in the job data: " + problems);
        helper.succeed();
    }

    /** A hamlet with a farmer and a baker does not starve. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "job_data_hamlet")
    public static void hamletWithAFarmerAndABakerDoesNotStarve(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String faction = FactionData.get(server).factions().get(0).id();
        int x = 700 * CELL + CELL / 2;
        Settlement hamlet = new Settlement(UUID.randomUUID().toString(), x >> 4, 0, faction, "Breadby", x, 100);
        hamlet.setProgress(1, 0);
        hamlet.setBiome("minecraft:plains");
        SettlementData.get(server).registry().add(hamlet);
        hamlet.economy().setPopulation(5);
        hamlet.economy().setJobs(List.of("folkhold_world:farmer", "folkhold_world:baker"));
        Economy.seedStock(hamlet.economy(), Economies.jobs(hamlet), 3, LevelTables.get().traits(1).stockCapacity(),
                EconomyData.catalog(), Economies.settings());
        hamlet.economy().setInitialized(true);
        SplittableRandom random = new SplittableRandom(5);

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            hamlet.setProgress(1, 0);
            double food = Economies.runCycle(server, hamlet, random).foodCovered();
            helper.assertTrue(food >= 0.9, "The hamlet starves at the cycle " + cycle + ": " + food);
        }
        helper.succeed();
    }
}
