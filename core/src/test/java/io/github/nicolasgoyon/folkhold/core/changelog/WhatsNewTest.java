package io.github.nicolasgoyon.folkhold.core.changelog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Category;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Entry;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Release;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhatsNewTest {
    private static final Changelog CHANGELOG = new Changelog(List.of(release("0.6.0"), release("0.5.0")));

    private static Release release(String version) {
        return new Release(version, List.of(new Entry(Category.NEW, Map.of("en_us", "Text of " + version))));
    }

    @Test
    void opensAfterAnUpdate() {
        assertTrue(WhatsNew.shouldShow("0.5.0", "0.6.0", CHANGELOG));
        assertTrue(WhatsNew.shouldShow("0.4.2", "0.6.0", CHANGELOG), "The player skipped a version");
    }

    @Test
    void opensOnANewInstallation() {
        assertTrue(WhatsNew.shouldShow(null, "0.6.0", CHANGELOG));
    }

    @Test
    void doesNotOpenAgainForTheSameVersion() {
        assertFalse(WhatsNew.shouldShow("0.6.0", "0.6.0", CHANGELOG));
    }

    @Test
    void doesNotOpenWithNoNewEntry() {
        assertFalse(WhatsNew.shouldShow("0.6.0", "0.6.1", CHANGELOG), "The version 0.6.1 has no entry");
        assertFalse(WhatsNew.shouldShow("0.7.0", "0.6.0", CHANGELOG), "A downgrade");
        assertFalse(WhatsNew.shouldShow(null, "0.6.0", Changelog.EMPTY), "No changelog in the jar");
        assertFalse(WhatsNew.shouldShow("0.5.0", "0.6.0", Changelog.EMPTY));
    }

    @Test
    void comparesVersions() {
        assertTrue(WhatsNew.compare("0.10.0", "0.9.9") > 0);
        assertTrue(WhatsNew.compare("1.0.0", "1.0.0-alpha.1") > 0, "A pre-release comes before the release");
        assertTrue(WhatsNew.compare("0.6.0-pr.12.abc", "0.6.0") < 0);
        assertTrue(WhatsNew.compare("1.0.0-alpha.2", "1.0.0-alpha.1") > 0);
        assertEquals(0, WhatsNew.compare("0.6.0+build.5", "0.6.0"));
        assertEquals(0, WhatsNew.compare("x.y", "0.0.0"));
    }

    @Test
    void keepsTheLastVersionInAFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config").resolve(WhatsNew.FILE);
        assertNull(WhatsNew.read(file), "No file");
        WhatsNew.write(file, "0.6.0");
        assertEquals("0.6.0", WhatsNew.read(file));
        Files.writeString(file, "  \n");
        assertNull(WhatsNew.read(file), "An empty file");
    }
}
