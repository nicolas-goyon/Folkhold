package io.github.nicolasgoyon.folkhold.balance.enchantment;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * A set of enchantments from a list of entries. An entry is an enchantment id ({@code minecraft:mending}) or an
 * enchantment tag ({@code #minecraft:treasure}). An id with no namespace has the namespace {@code minecraft}.
 *
 * @param ids  the enchantment ids, as {@code namespace:path}
 * @param tags the ids of the enchantment tags, as {@code namespace:path}
 */
public record EnchantmentFilter(Set<String> ids, Set<String> tags) {
    /** A resource location: an optional namespace, then a path, with the characters that the game accepts. */
    private static final Pattern RESOURCE_LOCATION = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    public EnchantmentFilter {
        ids = Set.copyOf(ids);
        tags = Set.copyOf(tags);
    }

    /**
     * Makes a filter from a list of entries. The filter ignores an entry that is not valid.
     *
     * @param entries the entries, see {@link #isValidEntry(Object)}
     * @return the filter
     */
    public static EnchantmentFilter parse(Collection<?> entries) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (Object entry : entries) {
            if (!isValidEntry(entry)) {
                continue;
            }
            String text = (String) entry;
            if (text.startsWith("#")) {
                tags.add(withNamespace(text.substring(1)));
            } else {
                ids.add(withNamespace(text));
            }
        }
        return new EnchantmentFilter(ids, tags);
    }

    /**
     * Tells if an object is a valid entry: a string with a resource location ({@code namespace:path} or
     * {@code path}), with an optional {@code #} before it for a tag.
     *
     * @param entry the object
     * @return true if the object is a valid entry
     */
    public static boolean isValidEntry(Object entry) {
        if (!(entry instanceof String text)) {
            return false;
        }
        String id = text.startsWith("#") ? text.substring(1) : text;
        return RESOURCE_LOCATION.matcher(id).matches();
    }

    private static String withNamespace(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** True if the filter has no entries. */
    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    /**
     * Tells if an enchantment is in the filter: its id is in the ids, or it is in one of the tags.
     *
     * @param enchantment the enchantment
     * @return true if the enchantment is in the filter
     */
    public boolean test(Holder<Enchantment> enchantment) {
        boolean byId = enchantment.unwrapKey().map(key -> ids.contains(key.location().toString())).orElse(false);
        return byId || (!tags.isEmpty() && enchantment.tags().anyMatch(tag -> tags.contains(tag.location().toString())));
    }
}
