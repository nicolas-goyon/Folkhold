package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The rule that tells if a player helps a faction village during a raid ({@link RaidHelpers#helps}). */
class RaidHelpersTest {
    @Test
    void killsMakeAHelper() {
        assertTrue(RaidHelpers.helps(1, 0.0, 1, 40.0));
        assertTrue(RaidHelpers.helps(3, 0.0, 2, 40.0));
        assertFalse(RaidHelpers.helps(1, 10.0, 2, 40.0));
    }

    @Test
    void damageMakesAHelperWithNoKill() {
        assertTrue(RaidHelpers.helps(0, 40.0, 1, 40.0));
        assertFalse(RaidHelpers.helps(0, 39.5, 1, 40.0));
    }

    @Test
    void noHelpWithNoKillAndNoDamage() {
        assertFalse(RaidHelpers.helps(0, 0.0, 1, 40.0));
    }

    @Test
    void zeroMinimumDamageDisablesTheDamageRule() {
        assertFalse(RaidHelpers.helps(0, 500.0, 1, 0.0));
        assertTrue(RaidHelpers.helps(1, 0.0, 1, 0.0));
    }
}
