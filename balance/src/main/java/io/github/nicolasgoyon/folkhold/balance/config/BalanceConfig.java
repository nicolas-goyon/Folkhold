package io.github.nicolasgoyon.folkhold.balance.config;

import io.github.nicolasgoyon.folkhold.balance.enchantment.BlockedEnchantments;
import io.github.nicolasgoyon.folkhold.balance.enchantment.EnchantmentFilter;
import io.github.nicolasgoyon.folkhold.balance.rail.Coupling;
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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_ENCHANTMENTS;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_TRADES;
    public static final ModConfigSpec.BooleanValue BLOCKED_CLEAN_EXISTING_OFFERS;
    public static final ModConfigSpec.IntValue BLOCKED_REROLL_ATTEMPTS;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_FISHING;
    public static final ModConfigSpec.BooleanValue BLOCKED_IN_CHEST_LOOT;

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

    private BalanceConfig() {
    }
}
