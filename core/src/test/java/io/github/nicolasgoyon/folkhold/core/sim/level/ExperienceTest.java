package io.github.nicolasgoyon.folkhold.core.sim.level;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.level.Experience.Progress;
import org.junit.jupiter.api.Test;

class ExperienceTest {
    private static final LevelTable TABLE = LevelTable.DEFAULT;

    @Test
    void gainBelowTheThresholdKeepsTheLevel() {
        int needed = TABLE.traits(3).experience();
        assertEquals(new Progress(3, needed - 1), Experience.apply(3, 0, needed - 1, TABLE));
    }

    @Test
    void gainAtTheThresholdGivesTheNextLevelAndKeepsTheRest() {
        int needed = TABLE.traits(3).experience();
        assertEquals(new Progress(4, 2), Experience.apply(3, 5, needed - 3, TABLE));
    }

    @Test
    void largeGainGivesSeveralLevels() {
        int gain = TABLE.traits(1).experience() + TABLE.traits(2).experience() + 1;
        assertEquals(new Progress(3, 1), Experience.apply(1, 0, gain, TABLE));
    }

    @Test
    void lossBelowZeroRemovesOnlyOneLevel() {
        int lowerNeeded = TABLE.traits(6).experience();
        assertEquals(new Progress(6, lowerNeeded - 5), Experience.apply(7, 10, -15, TABLE));
        assertEquals(new Progress(6, 0), Experience.apply(7, 0, -100_000, TABLE));
    }

    @Test
    void levelOneDoesNotGoBelowZero() {
        assertEquals(new Progress(1, 0), Experience.apply(1, 3, -50, TABLE));
    }

    @Test
    void lastLevelKeepsTheExperienceBelowItsThreshold() {
        int max = TABLE.maxLevel();
        assertEquals(new Progress(max, TABLE.traits(max).experience() - 1),
                Experience.apply(max, 0, 1_000_000, TABLE));
    }

    @Test
    void cycleGainFollowsTheProsperity() {
        assertEquals(0, Experience.cycleGain(0.0, 10));
        assertEquals(5, Experience.cycleGain(0.5, 10));
        assertEquals(10, Experience.cycleGain(1.0, 10));
        assertEquals(10, Experience.cycleGain(3.0, 10));
        assertEquals(0, Experience.cycleGain(-1.0, 10));
    }
}
