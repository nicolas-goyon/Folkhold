package io.github.nicolasgoyon.folkhold.core.sim.reputation;

/**
 * Settings of the reputation model.
 *
 * @param villageWeight share of the village reputation in the effective reputation (0 to 1). The faction
 *                      reputation has the remaining share.
 * @param allySpread    share of a change that also applies to the factions in alliance with the target faction
 * @param enemySpread   share of a change that applies, with the opposite sign, to the factions in tension or in
 *                      war with the target faction
 */
public record ReputationSettings(double villageWeight, double allySpread, double enemySpread) {
    public static final ReputationSettings DEFAULT = new ReputationSettings(0.7, 0.5, 0.3);

    public ReputationSettings {
        check("villageWeight", villageWeight);
        check("allySpread", allySpread);
        check("enemySpread", enemySpread);
    }

    private static void check(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be from 0 to 1: " + value);
        }
    }
}
