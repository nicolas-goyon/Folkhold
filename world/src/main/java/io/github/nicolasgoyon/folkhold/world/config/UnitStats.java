package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configurable combat values of one unit type (for example the guard).
 *
 * <p>The values are base values. {@link WorldConfig} applies the global difficulty multipliers.
 */
public final class UnitStats {
    private final ModConfigSpec.DoubleValue health;
    private final ModConfigSpec.DoubleValue attackDamage;
    private final ModConfigSpec.DoubleValue armor;
    private final ModConfigSpec.DoubleValue movementSpeed;
    private final ModConfigSpec.DoubleValue followRange;

    /** Default values of a unit type. */
    public record Defaults(double health, double attackDamage, double armor, double movementSpeed, double followRange) {
    }

    UnitStats(ModConfigSpec.Builder builder, String unit, Defaults defaults) {
        String prefix = WorldConfig.KEY_PREFIX + "unit." + unit + ".";
        builder.translation(WorldConfig.KEY_PREFIX + "unit." + unit).push(unit);
        health = builder
                .comment("Base maximum health (half hearts).")
                .translation(prefix + "health")
                .defineInRange("health", defaults.health(), 1.0, 1024.0);
        attackDamage = builder
                .comment("Base attack damage without the weapon.")
                .translation(prefix + "attackDamage")
                .defineInRange("attackDamage", defaults.attackDamage(), 0.0, 100.0);
        armor = builder
                .comment("Base armor points without the armor items.")
                .translation(prefix + "armor")
                .defineInRange("armor", defaults.armor(), 0.0, 30.0);
        movementSpeed = builder
                .comment("Movement speed attribute (zombie: 0.23, vindicator: 0.35).")
                .translation(prefix + "movementSpeed")
                .defineInRange("movementSpeed", defaults.movementSpeed(), 0.05, 1.0);
        followRange = builder
                .comment("Distance in blocks at which the unit can see a target.")
                .translation(prefix + "followRange")
                .defineInRange("followRange", defaults.followRange(), 4.0, 128.0);
        builder.pop();
    }

    /** Maximum health, with the difficulty multiplier. */
    public double health() {
        return health.get() * WorldConfig.HEALTH_MULTIPLIER.get();
    }

    /** Attack damage, with the difficulty multiplier. */
    public double attackDamage() {
        return attackDamage.get() * WorldConfig.DAMAGE_MULTIPLIER.get();
    }

    public double armor() {
        return armor.get();
    }

    public double movementSpeed() {
        return movementSpeed.get();
    }

    public double followRange() {
        return followRange.get();
    }
}
