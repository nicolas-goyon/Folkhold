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
    public static final ModConfigSpec.BooleanValue MONSTERS_ATTACK_GUARDS;

    public static final ModConfigSpec.IntValue VILLAGE_RADIUS;
    public static final ModConfigSpec.IntValue SPAWN_PROTECTION_RADIUS;
    public static final ModConfigSpec.IntValue CALM_DELAY_SECONDS;

    public static final ModConfigSpec.IntValue GARRISON_GUARDS;
    public static final ModConfigSpec.IntValue GARRISON_REFILL_DELAY_SECONDS;
    public static final ModConfigSpec.IntValue GARRISON_ACTIVATION_RADIUS;

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
        MONSTERS_ATTACK_GUARDS = builder
                .comment("Hostile monsters attack guards first, as they attack iron golems.",
                        "The entity types in the tag folkhold_world:ignores_guards never do.",
                        "Applies to monsters that spawn or load after the change.")
                .translation(KEY_PREFIX + "monstersAttackGuards")
                .define("monstersAttackGuards", true);
        builder.pop();

        builder.comment("Faction villages.")
                .translation(KEY_PREFIX + "village")
                .push("village");
        VILLAGE_RADIUS = builder
                .comment("Radius in blocks of a village around its heart. Units in this radius join the village",
                        "when they spawn. An alert reaches all units of the village in this radius.")
                .translation(KEY_PREFIX + "villageRadius")
                .defineInRange("radius", 48, 8, 256);
        SPAWN_PROTECTION_RADIUS = builder
                .comment("Radius in blocks around a village heart where hostile monsters do not spawn naturally.",
                        "0 turns off the protection.")
                .translation(KEY_PREFIX + "spawnProtectionRadius")
                .defineInRange("spawnProtectionRadius", 48, 0, 256);
        CALM_DELAY_SECONDS = builder
                .comment("Time in seconds without a threat before an alerted village becomes calm again.")
                .translation(KEY_PREFIX + "calmDelaySeconds")
                .defineInRange("calmDelaySeconds", 60, 5, 3600);
        builder.pop();

        builder.comment("Garrison of each village.")
                .translation(KEY_PREFIX + "garrison")
                .push("garrison");
        GARRISON_GUARDS = builder
                .comment("Number of guards in a full garrison.")
                .translation(KEY_PREFIX + "garrisonGuards")
                .defineInRange("guards", 4, 0, 32);
        GARRISON_REFILL_DELAY_SECONDS = builder
                .comment("Time in seconds before the village replaces a dead unit. The village must be calm.")
                .translation(KEY_PREFIX + "garrisonRefillDelaySeconds")
                .defineInRange("refillDelaySeconds", 300, 10, 86400);
        GARRISON_ACTIVATION_RADIUS = builder
                .comment("A heart creates its missing units only when a player is in this radius, in blocks.")
                .translation(KEY_PREFIX + "garrisonActivationRadius")
                .defineInRange("activationRadius", 64, 16, 256);
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
