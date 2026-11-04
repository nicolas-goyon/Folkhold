package io.github.nicolasgoyon.folkhold.core.changelog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Category;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Release;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangelogTest {
    private static Changelog parse(String json) {
        return Changelog.parse(new StringReader(json));
    }

    @Test
    void readsTheOutputOfTheScript() {
        Changelog changelog = parse("""
                {"versions": [
                  {"version": "0.6.0", "entries": [
                    {"category": "fixed", "pull_request": 12, "en_us": "Gates close.", "fr_fr": "Les portes ferment."},
                    {"category": "new", "pull_request": null, "en_us": "Caravans.", "fr_fr": "Caravanes."}]},
                  {"version": "0.5.0", "entries": [
                    {"category": "changed", "pull_request": 3, "en_us": "Faster.", "fr_fr": "Plus vite."}]}]}
                """);
        assertEquals(2, changelog.versions().size());
        Release newest = changelog.versions().get(0);
        assertEquals("0.6.0", newest.version());
        assertEquals("Les portes ferment.", newest.entries(Category.FIXED).get(0).text("fr_fr"));
        assertEquals(List.of(newest), changelog.newest(1));
        assertEquals(2, changelog.newest(5).size());
    }

    @Test
    void englishWhenTheLanguageHasNoText() {
        Changelog changelog = parse("""
                {"versions": [{"version": "1.0.0", "entries": [
                  {"category": "new", "en_us": "Bells.", "de_de": "Glocken."}]}]}
                """);
        assertEquals("Bells.", changelog.versions().get(0).entries().get(0).text("de_de"),
                "The changelog reads only en_us and fr_fr");
        assertEquals("Bells.", changelog.versions().get(0).entries().get(0).text("fr_fr"));
    }

    @Test
    void ignoresTheEntriesThatAreNotValid() {
        Changelog changelog = parse("""
                {"versions": [
                  {"version": "1.0.0", "entries": [
                    {"category": "removed", "en_us": "Bad category."},
                    {"category": "new", "fr_fr": "Pas d'anglais."},
                    {"category": "new", "en_us": 4},
                    "text",
                    {"category": "fixed", "en_us": "Good."}]},
                  {"version": "0.9.0", "entries": []},
                  {"entries": [{"category": "new", "en_us": "No version."}]},
                  42]}
                """);
        assertEquals(1, changelog.versions().size());
        assertEquals(1, changelog.versions().get(0).entries().size());
        assertEquals("Good.", changelog.versions().get(0).entries().get(0).text("en_us"));
    }

    @Test
    void refusesAFileThatIsNotAChangelog() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"versions\": [}"));
        assertThrows(IllegalArgumentException.class, () -> parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"versions\": {}}"));
        assertTrue(parse("{\"versions\": []}").isEmpty());
    }
}
