package io.github.nicolasgoyon.folkhold.balance.anvil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Integer values for ids and tags, from config entries {@code "namespace:path=value"} or
 * {@code "#namespace:path=value"}. An id with no namespace has the namespace {@code minecraft}.
 *
 * @param ids  the values for the ids, as {@code namespace:path}, in the order of the entries
 * @param tags the values for the tags, as {@code namespace:path}, in the order of the entries
 */
public record KeyedValues(Map<String, Integer> ids, Map<String, Integer> tags) {
    private static final Pattern ENTRY = Pattern.compile("(#?)((?:[a-z0-9_.-]+:)?[a-z0-9_./-]+)=(\\d{1,9})");

    public KeyedValues {
        ids = Collections.unmodifiableMap(new LinkedHashMap<>(ids));
        tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    /**
     * Reads a list of entries. The last entry for a key wins. An entry that is not valid is ignored.
     *
     * @param entries the entries, see {@link #isValidEntry(Object)}
     * @return the values
     */
    public static KeyedValues parse(Collection<?> entries) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        Map<String, Integer> tags = new LinkedHashMap<>();
        for (Object entry : entries) {
            if (!(entry instanceof String text)) {
                continue;
            }
            Matcher matcher = ENTRY.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(2).indexOf(':') < 0 ? "minecraft:" + matcher.group(2) : matcher.group(2);
            (matcher.group(1).isEmpty() ? ids : tags).put(key, Integer.parseInt(matcher.group(3)));
        }
        return new KeyedValues(ids, tags);
    }

    /**
     * Tells if an object is a valid entry: a string {@code id=value} or {@code #tag=value}, with a value from 0 to
     * 999,999,999.
     *
     * @param entry the object
     * @return true if the entry is valid
     */
    public static boolean isValidEntry(Object entry) {
        return entry instanceof String text && ENTRY.matcher(text).matches();
    }
}
