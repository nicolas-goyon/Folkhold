package io.github.nicolasgoyon.folkhold.core.sim;

/**
 * Two int values packed into one long, for the keys of the maps: chunk coordinates, grid cells.
 */
public final class IntPair {
    private IntPair() {
    }

    /**
     * Packs two values into one key. Two different pairs give two different keys.
     *
     * @param first  the first value
     * @param second the second value
     * @return the key
     */
    public static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xFFFFFFFFL);
    }

    /** The first value of a key from {@link #pack(int, int)}. */
    public static int first(long key) {
        return (int) (key >> 32);
    }

    /** The second value of a key from {@link #pack(int, int)}. */
    public static int second(long key) {
        return (int) key;
    }
}
