package io.github.nicolasgoyon.folkhold.core.sim.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.faction.Attitude;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionTrait;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerReputationTest {
    private static final ReputationSettings SETTINGS = ReputationSettings.DEFAULT;

    private static FactionSpec faction(String id, Attitude attitude) {
        return new FactionSpec(id, "c", id, 0, attitude, Set.of(FactionTrait.MERCHANT), 0, 0);
    }

    private final FactionSpec ally = faction("ally", Attitude.ALLY);
    private final FactionSpec friendOfAlly = faction("ally2", Attitude.ALLY);
    private final FactionSpec neutral = faction("neutral", Attitude.NEUTRAL);
    private final FactionSpec hostile = faction("hostile", Attitude.HOSTILE);
    private final List<FactionSpec> all = List.of(ally, friendOfAlly, neutral, hostile);

    @Test
    void startValuesComeFromTheAttitude() {
        PlayerReputation reputation = new PlayerReputation();
        assertEquals(300, reputation.faction(ally));
        assertEquals(0, reputation.faction(neutral));
        assertEquals(-400, reputation.faction(hostile));
        assertEquals(ReputationTier.FRIEND, ReputationTier.of(reputation.faction(ally)));
        assertEquals(ReputationTier.HOSTILE, ReputationTier.of(reputation.faction(hostile)));
    }

    @Test
    void effectiveValueMixesVillageAndFaction() {
        PlayerReputation reputation = new PlayerReputation();
        assertEquals(300, reputation.effective(ally, "v1", SETTINGS), "A village without a value uses the faction value");
        reputation.setVillage("v1", -100);
        // 0.7 × -100 + 0.3 × 300 = 20
        assertEquals(20, reputation.effective(ally, "v1", SETTINGS));
        assertEquals(300, reputation.effective(ally, null, SETTINGS));
    }

    @Test
    void actionSpreadsToAlliesAndEnemies() {
        PlayerReputation reputation = new PlayerReputation();
        Relations relations = Relations.initial(all);
        reputation.apply(ally, "v1", -100, all, relations, SETTINGS);

        assertEquals(200, reputation.faction(ally));
        assertEquals(200, reputation.village(ally, "v1"));
        // Alliance (+60): 50 % of -100.
        assertEquals(250, reputation.faction(friendOfAlly));
        // Tension (-40): -30 % of -100 = +30.
        assertEquals(-370, reputation.faction(hostile));
        // Peace (0): no change.
        assertEquals(0, reputation.faction(neutral));
    }

    @Test
    void storedValuesAreInIdOrder() {
        PlayerReputation reputation = new PlayerReputation();
        reputation.setFaction("north", 1);
        reputation.setFaction("east", 2);
        reputation.setVillage("village_b", 3);
        reputation.setVillage("village_a", 4);

        assertEquals(List.of("east", "north"), List.copyOf(reputation.factionValues().keySet()));
        assertEquals(List.of("village_a", "village_b"), List.copyOf(reputation.villageValues().keySet()));
    }

    @Test
    void valuesAreClamped() {
        PlayerReputation reputation = new PlayerReputation();
        reputation.setFaction("hostile", -5000);
        assertEquals(PlayerReputation.MIN, reputation.faction(hostile));
        assertEquals(ReputationTier.SWORN_ENEMY, ReputationTier.of(PlayerReputation.MIN));
    }

    @Test
    void tierBoundaries() {
        assertEquals(ReputationTier.SWORN_ENEMY, ReputationTier.of(-601));
        assertEquals(ReputationTier.HOSTILE, ReputationTier.of(-600));
        assertEquals(ReputationTier.HOSTILE, ReputationTier.of(-201));
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.of(-200));
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.of(200));
        assertEquals(ReputationTier.FRIEND, ReputationTier.of(201));
        assertEquals(ReputationTier.ALLY, ReputationTier.of(601));
    }
}
