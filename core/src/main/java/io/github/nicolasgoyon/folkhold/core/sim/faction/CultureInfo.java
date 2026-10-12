package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.List;

/**
 * Data of a culture that the faction generator needs.
 *
 * @param id               the culture id, for example {@code valeholm}
 * @param color            the primary color of the culture (RGB)
 * @param prefixes         first syllables of the faction names
 * @param suffixes         last syllables of the faction names
 */
public record CultureInfo(String id, int color, List<String> prefixes, List<String> suffixes) {
    public CultureInfo {
        if (prefixes.isEmpty() || suffixes.isEmpty()) {
            throw new IllegalArgumentException("A culture needs at least one prefix and one suffix: " + id);
        }
        prefixes = List.copyOf(prefixes);
        suffixes = List.copyOf(suffixes);
    }
}
