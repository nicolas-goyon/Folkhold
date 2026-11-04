package io.github.nicolasgoyon.folkhold.core.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The player-facing changes of the versions of the mods, from the resource {@value #RESOURCE} of the core jar.
 *
 * <p>{@code scripts/changelog.py build --json} writes the file: {@code {"versions": [{"version": "0.5.0", "entries":
 * [{"category": "new", "en_us": "...", "fr_fr": "..."}]}]}}, the newest version first. The parser ignores a version
 * or an entry that is not valid, and keeps the others.
 *
 * @param versions the versions, the newest first
 */
public record Changelog(List<Release> versions) {
    /** The path of the resource in the jar. */
    public static final String RESOURCE = "assets/folkhold_core/changelog.json";
    /** The language of the texts when the language of the client has no text. */
    public static final String FALLBACK_LANGUAGE = "en_us";
    /** An empty changelog. */
    public static final Changelog EMPTY = new Changelog(List.of());

    public Changelog {
        versions = List.copyOf(versions);
    }

    /** The categories of an entry, in the order of the screen. */
    public enum Category {
        NEW, CHANGED, FIXED;

        /** The name in the JSON file and in the translation keys. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The entries of one version, for example "0.5.0". */
    public record Release(String version, List<Entry> entries) {
        public Release {
            entries = List.copyOf(entries);
        }

        /** The entries of one category. */
        public List<Entry> entries(Category category) {
            return entries.stream().filter(entry -> entry.category() == category).toList();
        }
    }

    /** One change: its category, and its text in each language (for example "en_us"), with an English text. */
    public record Entry(Category category, Map<String, String> texts) {
        public Entry {
            texts = Map.copyOf(texts);
        }

        /** The text in a language, or the English text when the language has no text. */
        public String text(String language) {
            String text = texts.get(language.toLowerCase(Locale.ROOT));
            return text != null ? text : texts.get(FALLBACK_LANGUAGE);
        }
    }

    /** True when the changelog has no entry. */
    public boolean isEmpty() {
        return versions.isEmpty();
    }

    /** The first versions, the newest first. */
    public List<Release> newest(int count) {
        return versions.subList(0, Math.min(count, versions.size()));
    }

    /**
     * Reads a changelog.
     *
     * @throws IllegalArgumentException when the text is not JSON or has no list of versions
     */
    public static Changelog parse(Reader reader) {
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("the changelog is not valid JSON: " + e.getMessage(), e);
        }
        JsonArray items = root.isJsonObject() ? array(root.getAsJsonObject(), "versions") : null;
        if (items == null) {
            throw new IllegalArgumentException("the changelog has no \"versions\" list");
        }
        List<Release> releases = new ArrayList<>();
        for (JsonElement item : items) {
            JsonArray list = item.isJsonObject() ? array(item.getAsJsonObject(), "entries") : null;
            String version = item.isJsonObject() ? string(item.getAsJsonObject(), "version") : null;
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : list != null ? list : new JsonArray()) {
                Entry entry = element.isJsonObject() ? entry(element.getAsJsonObject()) : null;
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (version != null && !entries.isEmpty()) {
                releases.add(new Release(version, entries));
            }
        }
        return new Changelog(releases);
    }

    private static Entry entry(JsonObject object) {
        Category category = Arrays.stream(Category.values())
                .filter(value -> value.id().equals(string(object, "category"))).findFirst().orElse(null);
        Map<String, String> texts = new TreeMap<>();
        for (String key : object.keySet()) {
            String text = string(object, key);
            if (key.matches("[a-z]{2,3}_[a-z]{2,3}") && text != null && !text.isBlank()) {
                texts.put(key, text);
            }
        }
        return category == null || !texts.containsKey(FALLBACK_LANGUAGE) ? null : new Entry(category, texts);
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
}
