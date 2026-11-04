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
        assertTrue(WhatsNew.shouldShow("0.5.0", CHANGELOG));
        assertTrue(WhatsNew.shouldShow("0.4.2", CHANGELOG), "The player skipped a version");
    }

    @Test
    void opensOnANewInstallation() {
        assertTrue(WhatsNew.shouldShow(null, CHANGELOG));
    }

    @Test
    void doesNotOpenAgainForTheSameNewestVersion() {
        assertFalse(WhatsNew.shouldShow("0.6.0", CHANGELOG));
    }

    @Test
    void doesNotOpenForADevelopmentBuildOfTheSameRelease() {
        // The version of the mod is 0.33.0-dev.x: only the newest version of the changelog counts.
        Changelog changelog = new Changelog(List.of(release("0.33.0")));
        assertFalse(WhatsNew.shouldShow("0.33.0", changelog));
    }

    @Test
    void doesNotOpenWithNoChangelog() {
        assertFalse(WhatsNew.shouldShow(null, Changelog.EMPTY));
        assertFalse(WhatsNew.shouldShow("0.5.0", Changelog.EMPTY));
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
