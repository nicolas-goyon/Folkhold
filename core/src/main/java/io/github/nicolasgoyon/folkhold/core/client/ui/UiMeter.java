package io.github.nicolasgoyon.folkhold.core.client.ui;

/**
 * A small meter below a row: a progress bar, or a bar of segments (one segment for each unit).
 *
 * @param value     the current value, or the number of filled segments
 * @param max       the maximum value, or the number of segments
 * @param lost      the number of lost segments after the filled segments (only for segments)
 * @param segmented true for a bar of segments
 * @param color     the color of the filled part
 */
public record UiMeter(int value, int max, int lost, boolean segmented, int color) {

    /** A progress bar in gilt. */
    public static UiMeter bar(int value, int max) {
        return new UiMeter(value, max, 0, false, UiTheme.GILT);
    }

    /** A bar of segments: filled, lost (red), and empty. */
    public static UiMeter segments(int filled, int total, int lost, int color) {
        return new UiMeter(filled, total, lost, true, color);
    }

    /** The filled part, 0 to 1. */
    public double fraction() {
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / (double) max));
    }
}
