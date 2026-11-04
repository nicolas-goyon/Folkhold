package io.github.nicolasgoyon.folkhold.core.changelog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * The rule of the "What's new" screen: the screen opens one time after an update.
 *
 * <p>A small file in the config folder keeps the version of the mod when the screen opened the last time. The screen
 * opens when the changelog has a version that is newer than this version. With no file (a new installation), the
 * screen opens when the changelog has a version.
 */
public final class WhatsNew {
    /** The name of the file in the config folder. */
    public static final String FILE = "folkhold_core-whats-new.txt";

    private WhatsNew() {
    }

    /**
     * True when the screen must open.
     *
     * @param lastVersion the version of the mod when the screen opened the last time, or null
     * @param version     the version of the mod
     * @param changelog   the changelog in the jar
     */
    public static boolean shouldShow(@Nullable String lastVersion, String version, Changelog changelog) {
        if (changelog.isEmpty() || version.equals(lastVersion)) {
            return false;
        }
        return lastVersion == null || compare(changelog.versions().get(0).version(), lastVersion) > 0;
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

    /**
     * Compares two versions "major.minor.patch[-pre-release][+build]". A pre-release comes before the release with the
     * same numbers. A part that is not a number counts as 0.
     */
    public static int compare(String a, String b) {
        String[] left = a.split("\\+", 2)[0].split("-", 2);
        String[] right = b.split("\\+", 2)[0].split("-", 2);
        String[] numbersLeft = left[0].split("\\.");
        String[] numbersRight = right[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(number(numbersLeft, i), number(numbersRight, i));
            if (result != 0) {
                return result;
            }
        }
        if (left.length == 1 || right.length == 1) {
            return Integer.compare(right.length, left.length);
        }
        return left[1].compareTo(right[1]);
    }

    private static int number(String[] parts, int index) {
        try {
            return index < parts.length ? Integer.parseInt(parts[index]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
