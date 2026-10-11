package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration of the world mod: file {@code folkhold_world-server.toml} in the {@code serverconfig}
 * folder of each world.
 *
 * <p>The values apply to units that spawn after the change. Existing units keep their values.
 */
public final class WorldConfig {
    static final String KEY_PREFIX = "folkhold_world.configuration.";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    public static final ModConfigSpec.IntValue PATROL_RADIUS;
    public static final ModConfigSpec.IntValue CHASE_RADIUS;
    public static final ModConfigSpec.IntValue ALERT_RADIUS;

    public static final UnitStats GUARD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Global difficulty of the faction units.")
                .translation(KEY_PREFIX + "difficulty")
                .push("difficulty");
        HEALTH_MULTIPLIER = builder
                .comment("Multiplier for the health of all faction units.")
                .translation(KEY_PREFIX + "healthMultiplier")
                .defineInRange("healthMultiplier", 1.0, 0.1, 10.0);
        DAMAGE_MULTIPLIER = builder
                .comment("Multiplier for the attack damage of all faction units.")
                .translation(KEY_PREFIX + "damageMultiplier")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        builder.pop();

        builder.comment("Behavior of the village defenders.")
                .translation(KEY_PREFIX + "ai")
                .push("ai");
        PATROL_RADIUS = builder
                .comment("Radius in blocks of the patrol area around the home position.")
                .translation(KEY_PREFIX + "patrolRadius")
                .defineInRange("patrolRadius", 16, 2, 128);
        CHASE_RADIUS = builder
                .comment("Maximum distance in blocks from the home position during a chase. Then the unit goes back.")
                .translation(KEY_PREFIX + "chaseRadius")
                .defineInRange("chaseRadius", 32, 4, 256);
        ALERT_RADIUS = builder
                .comment("Radius in blocks in which an attack on one unit alerts the other units.")
                .translation(KEY_PREFIX + "alertRadius")
                .defineInRange("alertRadius", 24, 4, 128);
        builder.pop();

        builder.comment("Base values of each unit type.")
                .translation(KEY_PREFIX + "unit")
                .push("unit");
        GUARD = new UnitStats(builder, "guard", new UnitStats.Defaults(30.0, 3.0, 4.0, 0.3, 24.0));
        builder.pop();

        SPEC = builder.build();
    }

    private WorldConfig() {
    }
}
