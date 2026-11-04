package io.github.nicolasgoyon.folkhold.core.changelog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * The rule of the "What's new" screen: the screen opens one time after an update.
 *
 * <p>A small file in the config folder keeps the newest version of the changelog when the screen opened the last
 * time. The screen opens when the newest version of the changelog is not this version, or when there is no file (a
 * new installation). The version of the mod has no effect: a development build of the same release does not open the
 * screen again.
 */
public final class WhatsNew {
    /** The name of the file in the config folder. */
    public static final String FILE = "folkhold_core-whats-new.txt";

    private WhatsNew() {
    }

    /**
     * True when the screen must open: the changelog has a version, and the file has no version or another version
     * than the newest version of the changelog.
     *
     * @param lastVersion the version in the file, or null
     * @param changelog   the changelog in the jar
     */
    public static boolean shouldShow(@Nullable String lastVersion, Changelog changelog) {
        return !changelog.isEmpty() && !changelog.versions().get(0).version().equals(lastVersion);
    }

    /** The version in the file, or null when there is no file or the file cannot be read. */
    @Nullable
    public static String read(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes the version to the file. */
    public static void write(Path file, String version) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, version + "\n", StandardCharsets.UTF_8);
    }
}
