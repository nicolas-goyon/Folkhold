package io.github.nicolasgoyon.folkhold.core.sim.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FactionGeneratorTest {
    private static final List<CultureInfo> CULTURES = List.of(
            new CultureInfo("a", 0xFF0000, List.of("val", "mor"), List.of("heim", "dor")),
            new CultureInfo("b", 0x00FF00, List.of("ash", "kel"), List.of("mark", "wyn")));
    private static final FactionGenerator.Settings SETTINGS = FactionGenerator.Settings.DEFAULT;

    @Test
    void sameSeedGivesSameFactions() {
        assertEquals(FactionGenerator.generate(42L, CULTURES, 0, 0, SETTINGS),
                FactionGenerator.generate(42L, CULTURES, 0, 0, SETTINGS));
    }

    @Test
    void countAndAttitudesFollowTheRules() {
        for (long seed = 0; seed < 200; seed++) {
            List<FactionSpec> factions = FactionGenerator.generate(seed, CULTURES, 0, 0, SETTINGS);
            assertTrue(factions.size() >= 5 && factions.size() <= 7, "count " + factions.size());
            long allies = factions.stream().filter(f -> f.attitude() == Attitude.ALLY).count();
            long neutral = factions.stream().filter(f -> f.attitude() == Attitude.NEUTRAL).count();
            assertTrue(allies == 1 || allies == 2, "allies " + allies);
            assertEquals(1, neutral);
            Set<String> names = factions.stream().map(FactionSpec::name).collect(Collectors.toSet());
            assertEquals(factions.size(), names.size(), "names must be unique");
            for (FactionSpec faction : factions) {
                double distance = Math.hypot(faction.centerX(), faction.centerZ());
                assertTrue(distance >= SETTINGS.minRadius() - 1 && distance <= SETTINGS.maxRadius() + 1);
                assertFalse(faction.traits().isEmpty());
                assertFalse(faction.traits().contains(FactionTrait.MERCHANT)
                        && faction.traits().contains(FactionTrait.ISOLATIONIST));
            }
        }
    }

    @Test
    void threeFactionsHaveOneOfEachAttitude() {
        FactionGenerator.Settings three = new FactionGenerator.Settings(3, 3, 600, 2500);
        for (long seed = 0; seed < 50; seed++) {
            List<FactionSpec> factions = FactionGenerator.generate(seed, CULTURES, 0, 0, three);
            assertEquals(3, factions.size());
            for (Attitude attitude : Attitude.values()) {
                assertEquals(1, factions.stream().filter(f -> f.attitude() == attitude).count(),
                        "seed " + seed + ": " + factions.stream().map(FactionSpec::attitude).toList());
            }
        }
    }

    @Test
    void ownerIsTheNearestCenter() {
        List<FactionSpec> factions = FactionGenerator.generate(7L, CULTURES, 0, 0, SETTINGS);
        for (FactionSpec faction : factions) {
            assertEquals(faction, FactionGenerator.owner(factions, faction.centerX() + 3, faction.centerZ() - 2));
        }
    }
}
