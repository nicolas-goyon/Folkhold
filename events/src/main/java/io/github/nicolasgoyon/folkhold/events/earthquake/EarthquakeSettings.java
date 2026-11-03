package io.github.nicolasgoyon.folkhold.events.earthquake;

import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;

/**
 * The size, the strength and the timing of one earthquake.
 *
 * @param radius        the radius of the area around the epicenter, in blocks
 * @param strength      the strength. It multiplies the chances of damage and the camera shake.
 * @param announceTicks the time between the announce and the first damage, in ticks
 * @param durationTicks the time of the shake, in ticks
 * @param blocksPerTick the largest number of blocks that the earthquake changes in one tick
 */
public record EarthquakeSettings(int radius, double strength, int announceTicks, int durationTicks,
        int blocksPerTick) {

    public EarthquakeSettings {
        if (radius < 1 || strength <= 0.0 || announceTicks < 0 || durationTicks < 1 || blocksPerTick < 1) {
            throw new IllegalArgumentException("Bad earthquake settings");
        }
    }

    /**
     * Settings with the timing and the batch size of the server config.
     *
     * @param radius   the radius of the area, in blocks
     * @param strength the strength
     * @return the settings
     */
    public static EarthquakeSettings fromConfig(int radius, double strength) {
        return new EarthquakeSettings(radius, strength, EventsConfig.EARTHQUAKE_ANNOUNCE_SECONDS.get() * 20,
                EventsConfig.EARTHQUAKE_DURATION_SECONDS.get() * 20, EventsConfig.EARTHQUAKE_BLOCKS_PER_TICK.get());
    }
}
