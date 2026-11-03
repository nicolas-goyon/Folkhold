package io.github.nicolasgoyon.folkhold.balance.enchantment;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The enchantments that villagers do not sell and that fishing does not give (section {@code blockedEnchantments}
 * of the server config).
 */
public final class BlockedEnchantments {
    /** The default list of blocked enchantments: enchantments that remove the progression. */
    public static final List<String> DEFAULT = List.of("minecraft:mending");

    private BlockedEnchantments() {
    }

    /** The filter from the config. The server config must be loaded. */
    public static EnchantmentFilter filter() {
        return EnchantmentFilter.parse(BalanceConfig.BLOCKED_ENCHANTMENTS.get());
    }

    /**
     * Tells if an item has a blocked enchantment, as an enchantment or as a stored enchantment (enchanted book).
     *
     * @param stack  the item
     * @param filter the blocked enchantments
     * @return true if the item has a blocked enchantment
     */
    public static boolean hasBlocked(ItemStack stack, EnchantmentFilter filter) {
        if (filter.isEmpty()) {
            return false;
        }
        for (DataComponentType<ItemEnchantments> component : components()) {
            for (Holder<Enchantment> enchantment : stack.getOrDefault(component, ItemEnchantments.EMPTY).keySet()) {
                if (filter.test(enchantment)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces each blocked enchantment of an item with a random enchantment of a pool. The new enchantment is not
     * blocked, is not on the item, is compatible with the other enchantments of the item and, for an item that is not
     * an enchanted book, is supported by the item. Its level is the level of the blocked enchantment, in the range of
     * levels of the new enchantment. When no enchantment of the pool is possible, the item loses the blocked
     * enchantment.
     *
     * @param stack    the item, changed in place
     * @param filter   the blocked enchantments
     * @param pool     the tag of the possible new enchantments
     * @param weighted true to pick the new enchantment with the weights of the enchantments, as the loot functions
     *                 do; false to pick with the same chance for each enchantment, as a librarian does
     * @param registry the registries of the level
     * @param random   the random source
     * @return true if the item changed
     */
    public static boolean replaceBlocked(ItemStack stack, EnchantmentFilter filter, TagKey<Enchantment> pool,
            boolean weighted, RegistryAccess registry, RandomSource random) {
        if (!hasBlocked(stack, filter)) {
            return false;
        }
        boolean book = stack.is(Items.ENCHANTED_BOOK);
        Iterable<Holder<Enchantment>> candidates = registry.registryOrThrow(Registries.ENCHANTMENT).getTagOrEmpty(pool);
        for (DataComponentType<ItemEnchantments> component : components()) {
            ItemEnchantments enchantments = stack.get(component);
            if (enchantments == null) {
                continue;
            }
            ItemEnchantments.Mutable result = new ItemEnchantments.Mutable(enchantments);
            List<Holder<Enchantment>> blocked = new ArrayList<>();
            for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                if (filter.test(enchantment)) {
                    blocked.add(enchantment);
                }
            }
            if (blocked.isEmpty()) {
                continue;
            }
            result.removeIf(blocked::contains);
            for (Holder<Enchantment> old : blocked) {
                List<WeightedEntry.Wrapper<Holder<Enchantment>>> possible = new ArrayList<>();
                for (Holder<Enchantment> candidate : candidates) {
                    if (!filter.test(candidate) && (book || stack.supportsEnchantment(candidate))
                            && isCompatible(candidate, result)) {
                        possible.add(WeightedEntry.wrap(candidate, weighted ? candidate.value().getWeight() : 1));
                    }
                }
                Optional<WeightedEntry.Wrapper<Holder<Enchantment>>> picked = WeightedRandom.getRandomItem(random,
                        possible);
                if (picked.isPresent()) {
                    Holder<Enchantment> replacement = picked.get().data();
                    result.set(replacement, BookOffers.replacementLevel(enchantments.getLevel(old),
                            replacement.value().getMinLevel(), replacement.value().getMaxLevel()));
                }
            }
            stack.set(component, result.toImmutable());
        }
        return true;
    }

    /**
     * The components with enchantments. This is a method, not a constant: the JUnit tests load this class with no
     * game classes.
     */
    private static List<DataComponentType<ItemEnchantments>> components() {
        return List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS);
    }

    private static boolean isCompatible(Holder<Enchantment> candidate, ItemEnchantments.Mutable enchantments) {
        for (Holder<Enchantment> present : enchantments.keySet()) {
            // areCompatible is false for the same enchantment: an enchantment of the item is not a candidate.
            if (!Enchantment.areCompatible(candidate, present)) {
                return false;
            }
        }
        return true;
    }
}
