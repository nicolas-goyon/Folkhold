package io.github.nicolasgoyon.folkhold.core.sim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Recipe;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The records of settings and data refuse the values out of their range. */
class SettingsValidationTest {
    private static final GarrisonTraits GARRISON = new GarrisonTraits(Map.of("guard", 1), "hamlet");

    private static final Recipe RECIPE = new Recipe(List.of(new Recipe.Input("wheat", 6)),
            List.of(new Recipe.Output("bread", 4, 1.0)));

    @Test
    void economySettings() {
        assertDoesNotThrow(() -> EconomySettings.DEFAULT);
        assertThrows(IllegalArgumentException.class,
                () -> new EconomySettings(0, 4, 0.02, 0.1, 0.5, 0.5, 3.0, 1.15, 0.85), "workersPerJob");
        assertThrows(IllegalArgumentException.class,
                () -> new EconomySettings(2, 4, 1.5, 0.1, 0.5, 0.5, 3.0, 1.15, 0.85), "decayRate");
        assertThrows(IllegalArgumentException.class,
                () -> new EconomySettings(2, 4, 0.02, 0.1, 0.5, 0.0, 3.0, 1.15, 0.85), "minPriceFactor");
        assertThrows(IllegalArgumentException.class,
                () -> new EconomySettings(2, 4, 0.02, 0.1, 0.5, 2.0, 1.0, 1.15, 0.85), "maxPriceFactor");
    }

    @Test
    void itemInfo() {
        assertDoesNotThrow(() -> new ItemInfo("bread", 1.0, 5, 32));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo(" ", 1.0, 5, 32));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("bread", 0.0, 5, 32));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("bread", 1.0, -1, 32));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("bread", 1.0, 5, 0));
    }

    @Test
    void recipe() {
        assertDoesNotThrow(() -> RECIPE);
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(), List.of()), "no output");
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Input("wheat", 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Input("wheat", 1, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Output("", 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Output("bread", 1, 0.0));
    }

    @Test
    void job() {
        assertDoesNotThrow(() -> new Job("baker", RECIPE, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Job("", RECIPE, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Job("baker", RECIPE, 0, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Job("baker", RECIPE, 1, 0, List.of()));
    }

    @Test
    void levelTraitsAndTier() {
        assertDoesNotThrow(() -> new LevelTraits(100, 1, 10, 2, 500, 4, 1, GARRISON));
        assertThrows(IllegalArgumentException.class, () -> new LevelTraits(0, 1, 10, 2, 500, 4, 1, GARRISON));
        assertThrows(IllegalArgumentException.class, () -> new LevelTraits(100, -1, 10, 2, 500, 4, 1, GARRISON));
        assertDoesNotThrow(() -> new Tier("hamlet", 1, 4));
        assertThrows(IllegalArgumentException.class, () -> new Tier("hamlet", 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new Tier("hamlet", 5, 4));
        assertThrows(IllegalArgumentException.class, () -> new Tier("", 1, 4));
    }

    @Test
    void garrisonTraits() {
        assertDoesNotThrow(() -> new GarrisonTraits(Map.of("guard", 0), "hamlet"));
        assertThrows(IllegalArgumentException.class, () -> new GarrisonTraits(Map.of("guard", -1), "hamlet"));
        assertThrows(IllegalArgumentException.class, () -> new GarrisonTraits(Map.of(" ", 1), "hamlet"));
        assertThrows(IllegalArgumentException.class, () -> new GarrisonTraits(Map.of("guard", 1), " "));
    }

    @Test
    void factionGeneratorSettings() {
        assertDoesNotThrow(() -> new FactionGenerator.Settings(3, 5, 100, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FactionGenerator.Settings(2, 5, 100, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FactionGenerator.Settings(4, 3, 100, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FactionGenerator.Settings(3, 5, 1000, 100));
    }

    @Test
    void routeFinderSettings() {
        assertDoesNotThrow(() -> new RouteFinder.Settings(8, 2, 3, 2.0, 3.0, true, 1.0, 64.0, 4000.0, 20000, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteFinder.Settings(0, 2, 3, 2.0, 3.0, true, 1.0, 64.0, 4000.0, 20000, 1.5), "step");
        assertThrows(IllegalArgumentException.class,
                () -> new RouteFinder.Settings(8, 2, 3, 2.0, 3.0, true, 1.0, 64.0, 0.0, 20000, 1.5), "maxLength");
        assertThrows(IllegalArgumentException.class,
                () -> new RouteFinder.Settings(8, 2, 3, 2.0, 3.0, true, 1.0, 64.0, 4000.0, 20000, 0.5), "weight");
    }

    @Test
    void tradeMatcherSettings() {
        assertDoesNotThrow(() -> new TradeMatcher.Settings(4, 256, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TradeMatcher.Settings(-1, 256, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TradeMatcher.Settings(4, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TradeMatcher.Settings(4, 256, 0.0));
    }

    @Test
    void questGeneratorSettings() {
        assertDoesNotThrow(() -> new QuestGenerator.Settings(1000, 24_000, 1.5, 20, 64, 50));
        assertThrows(IllegalArgumentException.class, () -> new QuestGenerator.Settings(0, 24_000, 1.5, 20, 64, 50));
        assertThrows(IllegalArgumentException.class, () -> new QuestGenerator.Settings(1000, 0, 1.5, 20, 64, 50));
        assertThrows(IllegalArgumentException.class, () -> new QuestGenerator.Settings(1000, 24_000, 0.0, 20, 64, 50));
        assertThrows(IllegalArgumentException.class, () -> new QuestGenerator.Settings(1000, 24_000, 1.5, 20, 0, 50));
    }

    @Test
    void reputationSettings() {
        assertDoesNotThrow(() -> ReputationSettings.DEFAULT);
        assertThrows(IllegalArgumentException.class, () -> new ReputationSettings(1.5, 0.5, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new ReputationSettings(0.7, -0.1, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new ReputationSettings(0.7, 0.5, 2.0));
    }
}
