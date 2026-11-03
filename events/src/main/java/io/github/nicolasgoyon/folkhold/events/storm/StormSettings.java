package io.github.nicolasgoyon.folkhold.events.storm;

import io.github.nicolasgoyon.folkhold.events.common.BlockChanges;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;

/**
 * The size, the strength, the wind and the timing of one storm.
 *
 * @param radius         the radius of the area around the center, in blocks
 * @param strength       the strength. It multiplies the chances of damage and the push of the wind.
 * @param windAngle      the direction to which the wind blows, in degrees (see {@link StormMath})
 * @param announceTicks  the time between the announce and the start of the wind, in ticks
 * @param durationTicks  the time of the wind, in ticks
 * @param blocksPerTick  the largest number of blocks that the storm changes in one tick
 * @param maxChanges     the largest number of blocks that the storm tears up. 0 turns off the damage to the blocks.
 * @param lightningTicks the mean time between two lightning strikes, in ticks. 0 turns off the strikes.
 * @param weather        true: the storm sets the vanilla weather of the level
 */
public record StormSettings(int radius, double strength, double windAngle, int announceTicks, int durationTicks,
        int blocksPerTick, int maxChanges, int lightningTicks, boolean weather) {

    public StormSettings {
        if (radius < 1 || strength <= 0.0 || announceTicks < 0 || durationTicks < 1 || blocksPerTick < 1
                || blocksPerTick > BlockChanges.MAX_CHANGES_PER_TICK || maxChanges < 0 || lightningTicks < 0) {
            throw new IllegalArgumentException("Bad storm settings");
        }
    }

    /**
     * Settings with the timing, the batch size, the largest number of changes and the lightning of the server config.
     * The strength multiplies the largest number of changes of the config.
     *
     * @param radius    the radius of the area, in blocks
     * @param strength  the strength
     * @param windAngle the direction to which the wind blows, in degrees
     * @return the settings
     */
    public static StormSettings fromConfig(int radius, double strength, double windAngle) {
        return new StormSettings(radius, strength, windAngle, EventsConfig.STORM_ANNOUNCE_SECONDS.get() * 20,
                EventsConfig.STORM_DURATION_SECONDS.get() * 20, EventsConfig.STORM_BLOCKS_PER_TICK.get(),
                (int) Math.round(EventsConfig.STORM_MAX_CHANGES.get() * strength),
                EventsConfig.STORM_LIGHTNING_SECONDS.get() * 20, EventsConfig.STORM_WEATHER.get());
    }
}
