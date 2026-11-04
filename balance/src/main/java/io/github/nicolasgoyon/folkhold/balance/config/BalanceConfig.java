package io.github.nicolasgoyon.folkhold.balance.config;

import io.github.nicolasgoyon.folkhold.balance.anvil.KeyedValues;
import io.github.nicolasgoyon.folkhold.balance.anvil.MergeRule;
import io.github.nicolasgoyon.folkhold.balance.enchantment.BlockedEnchantments;
import io.github.nicolasgoyon.folkhold.balance.enchantment.EnchantmentFilter;
import io.github.nicolasgoyon.folkhold.balance.rail.Coupling;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.rail.LocomotiveStats;
import io.github.nicolasgoyon.folkhold.balance.rail.Traction;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration of the balance mod: file {@code folkhold_balance-server.toml} in the {@code serverconfig}
 * folder of each world.
 */
public final class BalanceConfig {
    static final String KEY_PREFIX = "folkhold_balance.configuration.";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CHAIN_BUFFER_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHAIN_REST_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHAIN_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHAIN_BREAK_DISTANCE;
    public static final ModConfigSpec.IntValue CHAIN_BREAK_TICKS;
    public static final ModConfigSpec.DoubleValue CHAIN_STIFFNESS;
    public static final ModConfigSpec.DoubleValue CHAIN_DAMPING;
    public static final ModConfigSpec.IntValue TRAIN_MAX_CARTS;
    public static final ModConfigSpec.DoubleValue WAGON_MASS;
    public static final ModConfigSpec.DoubleValue CARGO_MASS;
    public static final ModConfigSpec.DoubleValue FRICTION;
    public static final ModConfigSpec.DoubleValue SLOPE_FORCE;
    public static final ModConfigSpec.DoubleValue POWERED_RAIL_FORCE;
    public static final ModConfigSpec.DoubleValue BRAKE_FORCE;
    public static final ModConfigSpec.DoubleValue CURVE_SPEED;
    public static final ModConfigSpec.IntValue LOOK_AHEAD;
    private static final LocomotiveValues FURNACE;
    private static final LocomotiveValues HEAVY;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_ENCHANTMENTS;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_TRADES;
    public static final ModConfigSpec.BooleanValue BLOCKED_CLEAN_EXISTING_OFFERS;
    public static final ModConfigSpec.IntValue BLOCKED_REROLL_ATTEMPTS;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_FISHING;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_CHEST_LOOT;

    public static final ModConfigSpec.BooleanValue ANVIL_ENABLED;
    public static final ModConfigSpec.EnumValue<MergeRule> ANVIL_MERGE_RULE;
    public static final ModConfigSpec.DoubleValue ANVIL_ENCHANT_COST_FACTOR;
    public static final ModConfigSpec.IntValue ANVIL_OPERATION_COST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANVIL_WEIGHTS;
    public static final ModConfigSpec.DoubleValue ANVIL_REPAIR_ENCHANT_FACTOR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANVIL_REPAIR_BASE_COST;
    public static final ModConfigSpec.IntValue ANVIL_REPAIR_DEFAULT_BASE_COST;
    public static final ModConfigSpec.BooleanValue ANVIL_PRIOR_WORK_PENALTY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANVIL_LEVEL_CAPS;
    public static final ModConfigSpec.IntValue ANVIL_ABSOLUTE_MAX_LEVEL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Coupling defaults = Coupling.DEFAULTS;

        builder.comment("Trains: minecarts that a chain links.")
                .translation(KEY_PREFIX + "rails")
                .push("rails");
        CHAIN_BUFFER_DISTANCE = builder
                .comment("Smallest distance in blocks between the centers of two linked minecarts. Below this",
                        "distance, the back minecart slows down: linked minecarts do not push each other as vanilla",
                        "minecarts do.")
                .translation(KEY_PREFIX + "bufferDistance")
                .defineInRange("bufferDistance", defaults.bufferDistance(), 0.5, 4.0);
        CHAIN_REST_DISTANCE = builder
                .comment("Distance in blocks between the centers of two linked minecarts when the chain is not",
                        "stretched. Below this distance, the chain does not pull. The value must be more than",
                        "bufferDistance.")
                .translation(KEY_PREFIX + "restDistance")
                .defineInRange("restDistance", defaults.restDistance(), 1.2, 4.0);
        CHAIN_MAX_DISTANCE = builder
                .comment("Largest distance in blocks of a stretched chain. Above this distance, the back minecart",
                        "follows the front minecart as with a rigid bar. A player links two minecarts only at this",
                        "distance or nearer. The value must be more than restDistance.")
                .translation(KEY_PREFIX + "maxDistance")
                .defineInRange("maxDistance", defaults.maxDistance(), 1.3, 8.0);
        CHAIN_BREAK_DISTANCE = builder
                .comment("A link breaks when the distance stays above this value in blocks for breakTicks ticks.",
                        "The value must be more than maxDistance.")
                .translation(KEY_PREFIX + "breakDistance")
                .defineInRange("breakDistance", defaults.breakDistance(), 1.5, 16.0);
        CHAIN_BREAK_TICKS = builder
                .comment("Number of ticks (20 ticks = 1 second) above breakDistance before a link breaks. This",
                        "tolerance keeps the train together after a lag of some ticks.")
                .translation(KEY_PREFIX + "breakTicks")
                .defineInRange("breakTicks", defaults.breakTicks(), 1, 1200);
        CHAIN_STIFFNESS = builder
                .comment("Force of the spring: the speed in blocks per tick that the back minecart gets each tick",
                        "for each block of stretch above restDistance.")
                .translation(KEY_PREFIX + "stiffness")
                .defineInRange("stiffness", defaults.stiffness(), 0.0, 1.0);
        CHAIN_DAMPING = builder
                .comment("Damping of the spring: the part of the speed difference between the two minecarts that",
                        "the back minecart gets back each tick while the chain is stretched.")
                .translation(KEY_PREFIX + "damping")
                .defineInRange("damping", defaults.damping(), 0.0, 1.0);
        TRAIN_MAX_CARTS = builder
                .comment("Largest number of minecarts in one train.")
                .translation(KEY_PREFIX + "maxCarts")
                .defineInRange("maxCarts", 16, 2, 256);
        Traction traction = Traction.DEFAULTS;
        WAGON_MASS = builder
                .comment("Mass of a wagon with no cargo. A locomotive moves its mass and the mass of the minecarts",
                        "behind it.")
                .translation(KEY_PREFIX + "wagonMass")
                .defineInRange("wagonMass", traction.wagonMass(), 0.1, 100.0);
        CARGO_MASS = builder
                .comment("Mass of the cargo of a full wagon. A wagon with half of its slots full has half of this",
                        "mass. The stack size of each item gives its fill.")
                .translation(KEY_PREFIX + "cargoMass")
                .defineInRange("cargoMass", traction.cargoMass(), 0.0, 100.0);
        FRICTION = builder
                .comment("Friction force for each unit of mass of a train.")
                .translation(KEY_PREFIX + "friction")
                .defineInRange("friction", traction.friction(), 0.0, 1.0);
        SLOPE_FORCE = builder
                .comment("Force of a slope against a train for each unit of mass of a minecart on the slope. A",
                        "slope down pushes the train with the same force.")
                .translation(KEY_PREFIX + "slopeForce")
                .defineInRange("slopeForce", traction.slopeForce(), 0.0, 1.0);
        POWERED_RAIL_FORCE = builder
                .comment("Push of a powered rail with power, for each unit of mass of the minecart on it.")
                .translation(KEY_PREFIX + "poweredRailForce")
                .defineInRange("poweredRailForce", traction.poweredRailForce(), 0.0, 1.0);
        BRAKE_FORCE = builder
                .comment("Brake of a powered rail with no power, for each unit of mass of the minecart on it.")
                .translation(KEY_PREFIX + "brakeForce")
                .defineInRange("brakeForce", traction.brakeForce(), 0.0, 10.0);

        CURVE_SPEED = builder
                .comment("Speed cap in blocks per tick of a train with a locomotive in a curve, on a slope and at the",
                        "end of the rails. The train slows down to this speed before it gets there. 0.4 is the speed",
                        "cap of the vanilla rails: above it, a minecart can leave a curve.")
                .translation(KEY_PREFIX + "curveSpeed")
                .defineInRange("curveSpeed", Locomotive.RAIL_SPEED_CAP, 0.05, Locomotive.RAIL_SPEED_CAP);
        LOOK_AHEAD = builder
                .comment("Number of blocks that each minecart of a train with a locomotive examines on the rails in",
                        "front of it. A curve, a slope or the end of the rails in this distance sets the speed cap",
                        "of the train to curveSpeed.")
                .translation(KEY_PREFIX + "lookAhead")
                .defineInRange("lookAhead", 3, 2, 16);
        FURNACE = locomotive(builder, "furnaceLocomotive", "The furnace locomotive. It burns the fuels of the furnace.",
                LocomotiveStats.FURNACE_DEFAULTS, "200 blocks for 1 coal");
        HEAVY = locomotive(builder, "heavyLocomotive", "The heavy locomotive. It burns blocks of coal.",
                LocomotiveStats.HEAVY_DEFAULTS, "1000 blocks for 1 block of coal");
        builder.pop();

        builder.comment("Blocked enchantments: enchantments that villagers do not sell and that fishing does not give.")
                .translation(KEY_PREFIX + "blockedEnchantments")
                .push("blockedEnchantments");
        BLOCKED_ENCHANTMENTS = builder
                .comment("Enchantment ids (\"minecraft:mending\") or enchantment tags (\"#minecraft:treasure\").")
                .translation(KEY_PREFIX + "enchantments")
                .defineListAllowEmpty("enchantments", BlockedEnchantments.DEFAULT, () -> "minecraft:mending",
                        EnchantmentFilter::isValidEntry);
        BLOCKED_IN_TRADES = builder
                .comment("Villagers and wandering traders do not make new offers with a blocked enchantment.")
                .translation(KEY_PREFIX + "inTrades")
                .define("inTrades", true);
        BLOCKED_CLEAN_EXISTING_OFFERS = builder
                .comment("When a villager or a wandering trader loads, each blocked enchantment in its offers changes",
                        "to a random enchantment of the tag #minecraft:tradeable. Needs inTrades.")
                .translation(KEY_PREFIX + "cleanExistingOffers")
                .define("cleanExistingOffers", true);
        BLOCKED_REROLL_ATTEMPTS = builder
                .comment("Number of new attempts for an offer with a blocked enchantment. When all the attempts fail,",
                        "the villager takes a different offer.")
                .translation(KEY_PREFIX + "rerollAttempts")
                .defineInRange("rerollAttempts", 10, 0, 100);
        BLOCKED_IN_FISHING = builder
                .comment("Each blocked enchantment in the loot of fishing changes to a random enchantment of the tag",
                        "#minecraft:on_random_loot.")
                .translation(KEY_PREFIX + "inFishing")
                .define("inFishing", true);
        BLOCKED_IN_CHEST_LOOT = builder
                .comment("Each blocked enchantment in the loot of chests changes to a random enchantment of the tag",
                        "#minecraft:on_random_loot.")
                .translation(KEY_PREFIX + "inChestLoot")
                .define("inChestLoot", false);
        builder.pop();

        builder.comment("Anvil: no level cap, no \"Too Expensive!\" limit, a quadratic enchantment cost and a repair",
                        "cost with no prior work penalty. All costs are in levels.")
                .translation(KEY_PREFIX + "anvil")
                .push("anvil");
        ANVIL_ENABLED = builder
                .comment("Use the anvil rules of this section. False gives the vanilla anvil.")
                .translation(KEY_PREFIX + "anvilEnabled")
                .define("enabled", true);
        ANVIL_MERGE_RULE = builder
                .comment("Level of an enchantment when the anvil combines two levels. VANILLA: two equal levels give",
                        "the level + 1, two different levels give the higher level. SUM: the levels add together.",
                        "There is no cap at the max level of the enchantment: see levelCaps.")
                .translation(KEY_PREFIX + "mergeRule")
                .defineEnum("mergeRule", MergeRule.VANILLA);
        ANVIL_ENCHANT_COST_FACTOR = builder
                .comment("Factor k of the enchantment cost: operationCost + ceil(k × sum of weight × (b² - a²)).",
                        "a is the level on the left item, b is the level of the result.")
                .translation(KEY_PREFIX + "enchantCostFactor")
                .defineInRange("enchantCostFactor", 1.0, 0.0, 1000.0);
        ANVIL_OPERATION_COST = builder
                .comment("Fixed cost of an operation that raises the level of an enchantment.")
                .translation(KEY_PREFIX + "operationCost")
                .defineInRange("operationCost", 1, 0, 1000);
        ANVIL_WEIGHTS = builder
                .comment("Weight of an enchantment, as \"id=weight\" or \"#tag=weight\". An enchantment that is not",
                        "in the list uses its anvil_cost.")
                .translation(KEY_PREFIX + "weights")
                .defineListAllowEmpty("weights", List.of(), () -> "minecraft:efficiency=1",
                        KeyedValues::isValidEntry);
        ANVIL_REPAIR_ENCHANT_FACTOR = builder
                .comment("Factor r of the repair cost: base + ceil(r × sum of weight × level) for a full repair. Curses",
                        "do not count. A repair of a quarter of the durability costs a quarter of a full repair,",
                        "rounded up.")
                .translation(KEY_PREFIX + "repairEnchantFactor")
                .defineInRange("repairEnchantFactor", 1.0, 0.0, 1000.0);
        ANVIL_REPAIR_BASE_COST = builder
                .comment("Base cost of a repair, as \"material=cost\" or \"#material_tag=cost\". The first material",
                        "that repairs the item gives the base, ids before tags.")
                .translation(KEY_PREFIX + "repairBaseCost")
                .defineListAllowEmpty("repairBaseCost", List.of("#minecraft:planks=1", "minecraft:leather=1",
                        "#minecraft:stone_tool_materials=2", "minecraft:gold_ingot=2", "minecraft:iron_ingot=3",
                        "minecraft:turtle_scute=3", "minecraft:diamond=4", "minecraft:netherite_ingot=5"),
                        () -> "minecraft:iron_ingot=3", KeyedValues::isValidEntry);
        ANVIL_REPAIR_DEFAULT_BASE_COST = builder
                .comment("Base cost of a repair for an item with no material in repairBaseCost.")
                .translation(KEY_PREFIX + "repairDefaultBaseCost")
                .defineInRange("repairDefaultBaseCost", 3, 0, 1000);
        ANVIL_PRIOR_WORK_PENALTY = builder
                .comment("Add the vanilla prior work penalty to the cost. False: the result has no penalty.")
                .translation(KEY_PREFIX + "priorWorkPenalty")
                .define("priorWorkPenalty", false);
        ANVIL_LEVEL_CAPS = builder
                .comment("Largest level of an enchantment in the anvil, as \"id=level\" or \"#tag=level\".",
                        "An enchantment that is not in the list has no cap below absoluteMaxLevel.")
                .translation(KEY_PREFIX + "levelCaps")
                .defineListAllowEmpty("levelCaps", List.of(), () -> "minecraft:fortune=5", KeyedValues::isValidEntry);
        ANVIL_ABSOLUTE_MAX_LEVEL = builder
                .comment("Largest level of all enchantments in the anvil. The game stores a maximum of 255.")
                .translation(KEY_PREFIX + "absoluteMaxLevel")
                .defineInRange("absoluteMaxLevel", 255, 1, 255);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * The coupling from the values of the section {@code rails}. Each distance is at least the distance before it in
     * the order buffer, rest, maximum, break.
     */
    public static Coupling coupling() {
        double buffer = CHAIN_BUFFER_DISTANCE.get();
        double rest = Math.max(buffer, CHAIN_REST_DISTANCE.get());
        double max = Math.max(rest, CHAIN_MAX_DISTANCE.get());
        double breakDistance = Math.max(max, CHAIN_BREAK_DISTANCE.get());
        return new Coupling(buffer, rest, max, breakDistance, CHAIN_BREAK_TICKS.get(), CHAIN_STIFFNESS.get(),
                CHAIN_DAMPING.get());
    }

    /** The load of the trains, from the values of the section {@code rails}. */
    public static Traction traction() {
        return new Traction(WAGON_MASS.get(), CARGO_MASS.get(), FRICTION.get(), SLOPE_FORCE.get(),
                POWERED_RAIL_FORCE.get(), BRAKE_FORCE.get());
    }

    /** The stats of the furnace locomotive, from the section {@code rails.furnaceLocomotive}. */
    public static LocomotiveStats furnaceLocomotive() {
        return FURNACE.stats();
    }

    /** The stats of the heavy locomotive, from the section {@code rails.heavyLocomotive}. */
    public static LocomotiveStats heavyLocomotive() {
        return HEAVY.stats();
    }

    /** Defines the section of a type of locomotive in the section {@code rails}. */
    private static LocomotiveValues locomotive(ModConfigSpec.Builder builder, String name, String comment,
                                               LocomotiveStats defaults, String fuelUse) {
        builder.comment(comment).translation(KEY_PREFIX + name).push(name);
        LocomotiveValues values = new LocomotiveValues(
                builder.comment("Largest speed in blocks per tick on straight and flat rails. The GameTests examine",
                                "the speeds up to " + Locomotive.MAX_SPEED + ".")
                        .translation(KEY_PREFIX + "maxSpeed")
                        .defineInRange("maxSpeed", defaults.maxSpeed(), 0.01, Locomotive.MAX_SPEED),
                builder.comment("Pulling force at speed 0. The force decreases in a line to 0 at maxSpeed. Speed",
                                "change each tick = (force - resistance) / mass of the train.")
                        .translation(KEY_PREFIX + "pullingForce")
                        .defineInRange("pullingForce", defaults.force(), 0.0, 10.0),
                builder.comment("Mass of the locomotive.")
                        .translation(KEY_PREFIX + "mass")
                        .defineInRange("mass", defaults.mass(), 0.1, 100.0),
                builder.comment("Largest quantity of fuel, in ticks of furnace fuel (1 coal = 1600).")
                        .translation(KEY_PREFIX + "fuelCapacity")
                        .defineInRange("fuelCapacity", defaults.fuelCapacity(), 1, 1_000_000),
                builder.comment("Fuel that the locomotive burns for each block that it moves while it runs. The",
                                "default gives about " + fuelUse + ".")
                        .translation(KEY_PREFIX + "fuelPerBlock")
                        .defineInRange("fuelPerBlock", defaults.fuelPerBlock(), 0.0, 1000.0));
        builder.pop();
        return values;
    }

    /** The configuration values of a type of locomotive. */
    private record LocomotiveValues(ModConfigSpec.DoubleValue maxSpeed, ModConfigSpec.DoubleValue force,
                                    ModConfigSpec.DoubleValue mass, ModConfigSpec.IntValue fuelCapacity,
                                    ModConfigSpec.DoubleValue fuelPerBlock) {
        LocomotiveStats stats() {
            return new LocomotiveStats(maxSpeed.get(), force.get(), mass.get(), fuelCapacity.get(),
                    fuelPerBlock.get());
        }
    }

    private BalanceConfig() {
    }
}
