package io.github.nicolasgoyon.folkhold.events.config;

import io.github.nicolasgoyon.folkhold.events.common.BlockChanges;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration of the events mod: file {@code folkhold_events-server.toml} in the {@code serverconfig} folder
 * of each world.
 */
public final class EventsConfig {
    static final String KEY_PREFIX = "folkhold_events.configuration.";
    /** The default shortest time between two natural events of one type, in game days. */
    public static final int DEFAULT_MIN_DAYS = 15;
    /** The default longest time between two natural events of one type, in game days. */
    public static final int DEFAULT_MAX_DAYS = 30;

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_ACTIVE_EVENTS;

    public static final ModConfigSpec.DoubleValue FRAGILE_BELOW;
    public static final ModConfigSpec.DoubleValue RESISTANT_FROM;

    public static final ModConfigSpec.BooleanValue EARTHQUAKES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EARTHQUAKE_DIMENSIONS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_MIN_DAYS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_MAX_DAYS;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_MIN_STRENGTH;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue EARTHQUAKE_MAX_ACTIVE;
    public static final ModConfigSpec.IntValue EARTHQUAKE_RADIUS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_EPICENTER_DISTANCE;
    public static final ModConfigSpec.IntValue EARTHQUAKE_ANNOUNCE_SECONDS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue EARTHQUAKE_SCAN_DEPTH;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_FRAGILE_CHANCE;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_STURDY_CHANCE;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_RESISTANT_CHANCE;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_COLLAPSE_FACTOR;
    public static final ModConfigSpec.IntValue EARTHQUAKE_COLLAPSE_DEPTH;
    public static final ModConfigSpec.IntValue EARTHQUAKE_CRACKS;
    public static final ModConfigSpec.IntValue EARTHQUAKE_CRACK_DEPTH;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_FALL_DAMAGE;
    public static final ModConfigSpec.IntValue EARTHQUAKE_MAX_FALL_DAMAGE;

    public static final ModConfigSpec.BooleanValue STORMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORM_DIMENSIONS;
    public static final ModConfigSpec.IntValue STORM_MIN_DAYS;
    public static final ModConfigSpec.IntValue STORM_MAX_DAYS;
    public static final ModConfigSpec.DoubleValue STORM_MIN_STRENGTH;
    public static final ModConfigSpec.DoubleValue STORM_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue STORM_MAX_ACTIVE;
    public static final ModConfigSpec.IntValue STORM_RADIUS;
    public static final ModConfigSpec.IntValue STORM_CENTER_DISTANCE;
    public static final ModConfigSpec.IntValue STORM_ANNOUNCE_SECONDS;
    public static final ModConfigSpec.IntValue STORM_DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue STORM_WEATHER;
    public static final ModConfigSpec.IntValue STORM_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue STORM_MAX_CHANGES;
    public static final ModConfigSpec.IntValue STORM_SCAN_DEPTH;
    public static final ModConfigSpec.DoubleValue STORM_FRAGILE_CHANCE;
    public static final ModConfigSpec.DoubleValue STORM_STURDY_CHANCE;
    public static final ModConfigSpec.DoubleValue STORM_RESISTANT_CHANCE;
    public static final ModConfigSpec.DoubleValue STORM_WIND_PUSH;
    public static final ModConfigSpec.DoubleValue STORM_THROW_CHANCE;
    public static final ModConfigSpec.DoubleValue STORM_FLYING_DAMAGE;
    public static final ModConfigSpec.IntValue STORM_LIGHTNING_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Rules for all the natural events.")
                .translation(KEY_PREFIX + "general")
                .push("general");
        MAX_ACTIVE_EVENTS = builder
                .comment("Largest number of active events on the server, for all the types and all the dimensions.",
                        "The cap of each type also applies.")
                .translation(KEY_PREFIX + "maxActiveEvents")
                .defineInRange("maxActiveEvents", 3, 1, 16);
        builder.pop();

        builder.comment("Classes of the blocks for the natural events. The block tags folkhold_events:fragile,",
                        "folkhold_events:sturdy and folkhold_events:resistant give the class of a block. A block with",
                        "no tag gets its class from its blast resistance.")
                .translation(KEY_PREFIX + "blockClasses")
                .push("blockClasses");
        FRAGILE_BELOW = builder
                .comment("A block with no tag and a blast resistance below this value is fragile.",
                        "Examples: dirt 0.5, glass 0.3, logs 2.0, planks 3.0.")
                .translation(KEY_PREFIX + "fragileBelow")
                .defineInRange("fragileBelow", 3.0, 0.0, 3_600_000.0);
        RESISTANT_FROM = builder
                .comment("A block with no tag and a blast resistance at this value or more is resistant. The other",
                        "blocks with no tag are sturdy. Examples: stone 6.0, iron block 6.0, obsidian 1200.")
                .translation(KEY_PREFIX + "resistantFrom")
                .defineInRange("resistantFrom", 6.0, 0.0, 3_600_000.0);
        builder.pop();

        builder.comment("Earthquakes. They occur only in loaded chunks near a player.")
                .translation(KEY_PREFIX + "earthquake")
                .push("earthquake");
        EARTHQUAKES = builder
                .comment("True: natural earthquakes occur. The command /folkhold earthquake start works also when",
                        "this value is false.")
                .translation(KEY_PREFIX + "earthquakes")
                .define("enabled", true);
        EARTHQUAKE_DIMENSIONS = builder
                .comment("The dimensions with natural earthquakes.")
                .translation(KEY_PREFIX + "earthquakeDimensions")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
                        value -> value instanceof String);
        EARTHQUAKE_MIN_DAYS = builder
                .comment("Shortest time between two natural earthquakes in one dimension, in game days of 24,000",
                        "ticks of game time. The time of each interval is random between minDays and maxDays.")
                .translation(KEY_PREFIX + "earthquakeMinDays")
                .defineInRange("minDays", DEFAULT_MIN_DAYS, 1, 1000);
        EARTHQUAKE_MAX_DAYS = builder
                .comment("Longest time between two natural earthquakes in one dimension, in game days. A value",
                        "below minDays counts as minDays.")
                .translation(KEY_PREFIX + "earthquakeMaxDays")
                .defineInRange("maxDays", DEFAULT_MAX_DAYS, 1, 1000);
        EARTHQUAKE_MIN_STRENGTH = builder
                .comment("Lowest strength of a natural earthquake. The strength multiplies the chances of damage.")
                .translation(KEY_PREFIX + "earthquakeMinStrength")
                .defineInRange("minStrength", 0.8, 0.1, 10.0);
        EARTHQUAKE_MAX_STRENGTH = builder
                .comment("Highest strength of a natural earthquake. A value below minStrength counts as",
                        "minStrength.")
                .translation(KEY_PREFIX + "earthquakeMaxStrength")
                .defineInRange("maxStrength", 1.5, 0.1, 10.0);
        EARTHQUAKE_MAX_ACTIVE = builder
                .comment("Largest number of active earthquakes on the server, for all the dimensions. The areas of",
                        "two active earthquakes never overlap.")
                .translation(KEY_PREFIX + "earthquakeMaxActive")
                .defineInRange("maxActive", 2, 1, 16);
        EARTHQUAKE_RADIUS = builder
                .comment("Radius in blocks of the area of a natural earthquake around its epicenter.")
                .translation(KEY_PREFIX + "earthquakeRadius")
                .defineInRange("radius", 24, 4, 64);
        EARTHQUAKE_EPICENTER_DISTANCE = builder
                .comment("Largest distance in blocks between a player and the epicenter of a natural earthquake.")
                .translation(KEY_PREFIX + "earthquakeEpicenterDistance")
                .defineInRange("epicenterDistance", 32, 0, 128);
        EARTHQUAKE_ANNOUNCE_SECONDS = builder
                .comment("Time in seconds between the announce (rumble, light camera shake, animals run away) and",
                        "the first damage.")
                .translation(KEY_PREFIX + "earthquakeAnnounceSeconds")
                .defineInRange("announceSeconds", 5, 0, 60);
        EARTHQUAKE_DURATION_SECONDS = builder
                .comment("Time in seconds of the shake. The damage spreads over this time.")
                .translation(KEY_PREFIX + "earthquakeDurationSeconds")
                .defineInRange("durationSeconds", 12, 1, 120);
        EARTHQUAKE_BLOCKS_PER_TICK = builder
                .comment("Largest number of blocks that an earthquake changes in one tick. The other changes wait",
                        "for the next ticks. All the events together change 256 blocks at most in one tick.")
                .translation(KEY_PREFIX + "earthquakeBlocksPerTick")
                .defineInRange("blocksPerTick", 32, 1, BlockChanges.MAX_CHANGES_PER_TICK);
        EARTHQUAKE_SCAN_DEPTH = builder
                .comment("An earthquake examines the blocks of each column from the top block down to this number",
                        "of blocks.")
                .translation(KEY_PREFIX + "earthquakeScanDepth")
                .defineInRange("scanDepth", 24, 1, 64);
        EARTHQUAKE_FRAGILE_CHANCE = builder
                .comment("Chance that an exposed fragile block falls or breaks, at the epicenter, for a strength",
                        "of 1. The chance goes down to 0 at the edge of the area.")
                .translation(KEY_PREFIX + "earthquakeFragileChance")
                .defineInRange("fragileChance", 0.3, 0.0, 1.0);
        EARTHQUAKE_STURDY_CHANCE = builder
                .comment("Chance that an exposed sturdy block falls or cracks, at the epicenter, for a strength",
                        "of 1.")
                .translation(KEY_PREFIX + "earthquakeSturdyChance")
                .defineInRange("sturdyChance", 0.1, 0.0, 1.0);
        EARTHQUAKE_RESISTANT_CHANCE = builder
                .comment("Chance that an exposed resistant block falls or cracks, at the epicenter, for a strength",
                        "of 1.")
                .translation(KEY_PREFIX + "earthquakeResistantChance")
                .defineInRange("resistantChance", 0.03, 0.0, 1.0);
        EARTHQUAKE_COLLAPSE_FACTOR = builder
                .comment("The chance that the roof of a cavity collapses in one column is this factor multiplied",
                        "by the chance of the block at the bottom of the roof. 0 turns off the collapses.")
                .translation(KEY_PREFIX + "earthquakeCollapseFactor")
                .defineInRange("collapseFactor", 0.5, 0.0, 10.0);
        EARTHQUAKE_COLLAPSE_DEPTH = builder
                .comment("Largest thickness in blocks of the roof of a cavity that collapses.")
                .translation(KEY_PREFIX + "earthquakeCollapseDepth")
                .defineInRange("collapseDepth", 8, 2, 32);
        EARTHQUAKE_CRACKS = builder
                .comment("Number of cracks in the surface for a strength of 1. 0 turns off the cracks.")
                .translation(KEY_PREFIX + "earthquakeCracks")
                .defineInRange("cracks", 2, 0, 16);
        EARTHQUAKE_CRACK_DEPTH = builder
                .comment("Depth in blocks of a crack at its center. A crack is less deep at its ends.")
                .translation(KEY_PREFIX + "earthquakeCrackDepth")
                .defineInRange("crackDepth", 3, 1, 16);
        EARTHQUAKE_FALL_DAMAGE = builder
                .comment("Damage of a falling block to an entity, for each block of the fall after the first.")
                .translation(KEY_PREFIX + "earthquakeFallDamage")
                .defineInRange("fallDamage", 2.0, 0.0, 100.0);
        EARTHQUAKE_MAX_FALL_DAMAGE = builder
                .comment("Largest damage of one falling block to an entity.")
                .translation(KEY_PREFIX + "earthquakeMaxFallDamage")
                .defineInRange("maxFallDamage", 20, 0, 1000);
        builder.pop();

        builder.comment("Storms. They occur only in loaded chunks near a player.")
                .translation(KEY_PREFIX + "storm")
                .push("storm");
        STORMS = builder
                .comment("True: natural storms occur. The command /folkhold storm start works also when this value",
                        "is false.")
                .translation(KEY_PREFIX + "storms")
                .define("enabled", true);
        STORM_DIMENSIONS = builder
                .comment("The dimensions with natural storms. A dimension with no sky light, with a ceiling, or the",
                        "End has no storms, also in this list.")
                .translation(KEY_PREFIX + "stormDimensions")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
                        value -> value instanceof String);
        STORM_MIN_DAYS = builder
                .comment("Shortest time between two natural storms in one dimension, in game days of 24,000 ticks",
                        "of game time. The time of each interval is random between minDays and maxDays.")
                .translation(KEY_PREFIX + "stormMinDays")
                .defineInRange("minDays", DEFAULT_MIN_DAYS, 1, 1000);
        STORM_MAX_DAYS = builder
                .comment("Longest time between two natural storms in one dimension, in game days. A value below",
                        "minDays counts as minDays.")
                .translation(KEY_PREFIX + "stormMaxDays")
                .defineInRange("maxDays", DEFAULT_MAX_DAYS, 1, 1000);
        STORM_MIN_STRENGTH = builder
                .comment("Lowest strength of a natural storm. The strength multiplies the chances of damage, the",
                        "largest number of changed blocks and the push of the wind.")
                .translation(KEY_PREFIX + "stormMinStrength")
                .defineInRange("minStrength", 0.8, 0.1, 10.0);
        STORM_MAX_STRENGTH = builder
                .comment("Highest strength of a natural storm. A value below minStrength counts as minStrength.")
                .translation(KEY_PREFIX + "stormMaxStrength")
                .defineInRange("maxStrength", 1.5, 0.1, 10.0);
        STORM_MAX_ACTIVE = builder
                .comment("Largest number of active storms on the server, for all the dimensions. The areas of two",
                        "active storms never overlap.")
                .translation(KEY_PREFIX + "stormMaxActive")
                .defineInRange("maxActive", 1, 1, 16);
        STORM_RADIUS = builder
                .comment("Radius in blocks of the area of a natural storm around its center.")
                .translation(KEY_PREFIX + "stormRadius")
                .defineInRange("radius", 48, 8, 96);
        STORM_CENTER_DISTANCE = builder
                .comment("Largest distance in blocks between a player and the center of a natural storm.")
                .translation(KEY_PREFIX + "stormCenterDistance")
                .defineInRange("centerDistance", 32, 0, 128);
        STORM_ANNOUNCE_SECONDS = builder
                .comment("Time in seconds between the announce (rain, dark sky, wind sound) and the start of the",
                        "wind.")
                .translation(KEY_PREFIX + "stormAnnounceSeconds")
                .defineInRange("announceSeconds", 60, 0, 600);
        STORM_DURATION_SECONDS = builder
                .comment("Time in seconds of the wind. The damage spreads over this time.")
                .translation(KEY_PREFIX + "stormDurationSeconds")
                .defineInRange("durationSeconds", 180, 10, 1800);
        STORM_WEATHER = builder
                .comment("True: a storm sets the vanilla weather of the dimension: rain during the announce, thunder",
                        "during the wind, clear weather after the storm. All the players of the dimension see this",
                        "weather.")
                .translation(KEY_PREFIX + "stormWeather")
                .define("weather", true);
        STORM_BLOCKS_PER_TICK = builder
                .comment("Largest number of blocks that a storm changes in one tick. The other changes wait for the",
                        "next ticks. All the events together change 256 blocks at most in one tick.")
                .translation(KEY_PREFIX + "stormBlocksPerTick")
                .defineInRange("blocksPerTick", 4, 1, BlockChanges.MAX_CHANGES_PER_TICK);
        STORM_MAX_CHANGES = builder
                .comment("Largest number of blocks that a storm tears up, for a strength of 1. The storm selects",
                        "these blocks at random among all the blocks that the wind can tear up in its area. 0 turns",
                        "off the damage to the blocks.")
                .translation(KEY_PREFIX + "stormMaxChanges")
                .defineInRange("maxChanges", 40, 0, 1024);
        STORM_SCAN_DEPTH = builder
                .comment("A storm examines the blocks of each column from the top block down to this number of",
                        "blocks.")
                .translation(KEY_PREFIX + "stormScanDepth")
                .defineInRange("scanDepth", 24, 1, 64);
        STORM_FRAGILE_CHANCE = builder
                .comment("Chance that the wind tears up an exposed fragile block, at the center, for a strength of",
                        "1. The chance goes down to 0 at the edge of the area. A block is exposed when the wind hits",
                        "one of its faces from the open sky.")
                .translation(KEY_PREFIX + "stormFragileChance")
                .defineInRange("fragileChance", 0.1, 0.0, 1.0);
        STORM_STURDY_CHANCE = builder
                .comment("Chance that the wind tears up an exposed sturdy block, at the center, for a strength of 1.")
                .translation(KEY_PREFIX + "stormSturdyChance")
                .defineInRange("sturdyChance", 0.01, 0.0, 1.0);
        STORM_RESISTANT_CHANCE = builder
                .comment("Chance that the wind tears up an exposed resistant block, at the center, for a strength",
                        "of 1.")
                .translation(KEY_PREFIX + "stormResistantChance")
                .defineInRange("resistantChance", 0.0, 0.0, 1.0);
        STORM_WIND_PUSH = builder
                .comment("Push of the wind on the mobs and the animals under the open sky, in blocks per tick for",
                        "each tick, for a strength of 1. A gust pushes 3 times more, and also pushes the players. 0",
                        "turns off the push.")
                .translation(KEY_PREFIX + "stormWindPush")
                .defineInRange("windPush", 0.02, 0.0, 0.5);
        STORM_THROW_CHANCE = builder
                .comment("Chance that a gust throws a mob or an animal on the ground under the open sky into the air,",
                        "for a strength of 1.")
                .translation(KEY_PREFIX + "stormThrowChance")
                .defineInRange("throwChance", 0.25, 0.0, 1.0);
        STORM_FLYING_DAMAGE = builder
                .comment("Damage of a flying block to an entity that it hits.")
                .translation(KEY_PREFIX + "stormFlyingDamage")
                .defineInRange("flyingDamage", 4.0, 0.0, 100.0);
        STORM_LIGHTNING_SECONDS = builder
                .comment("Mean time in seconds between two lightning strikes in the area of a storm, during the wind.",
                        "A lightning rod in the area attracts the strikes near it. 0 turns off these strikes.")
                .translation(KEY_PREFIX + "stormLightningSeconds")
                .defineInRange("lightningSeconds", 6, 0, 600);
        builder.pop();

        SPEC = builder.build();
    }

    private EventsConfig() {
    }
}
