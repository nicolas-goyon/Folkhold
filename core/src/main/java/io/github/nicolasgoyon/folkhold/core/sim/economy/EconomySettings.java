package io.github.nicolasgoyon.folkhold.core.sim.economy;

/**
 * Values of the economy.
 *
 * @param workersPerJob     inhabitants for one active job
 * @param foodPerInhabitant food points that one inhabitant eats in one cycle
 * @param decayRate         share of each stock that rots or wears in one cycle (0 to 1)
 * @param growthRate        share of the population that the population grows in one fed cycle (0 to 1)
 * @param elasticity        elasticity of the price formula
 * @param minPriceFactor    minimum factor of the price formula
 * @param maxPriceFactor    maximum factor of the price formula
 * @param sellMarkup        factor of the price when the settlement sells to the player
 * @param buyMarkdown       factor of the price when the settlement buys from the player
 */
public record EconomySettings(int workersPerJob, int foodPerInhabitant, double decayRate, double growthRate,
        double elasticity, double minPriceFactor, double maxPriceFactor, double sellMarkup, double buyMarkdown) {

    public static final EconomySettings DEFAULT = new EconomySettings(2, 4, 0.02, 0.1, 0.5, 0.5, 3.0, 1.15, 0.85);

    public EconomySettings {
        if (workersPerJob < 1 || foodPerInhabitant < 0 || decayRate < 0.0 || decayRate > 1.0 || growthRate < 0.0
                || elasticity < 0.0 || minPriceFactor <= 0.0 || maxPriceFactor < minPriceFactor) {
            throw new IllegalArgumentException("Invalid economy settings");
        }
    }
}
