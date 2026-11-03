package io.github.nicolasgoyon.folkhold.events.earthquake;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.common.ActiveEvents;
import io.github.nicolasgoyon.folkhold.events.common.EventMath;
import io.github.nicolasgoyon.folkhold.events.common.EventPlacement;
import io.github.nicolasgoyon.folkhold.events.common.EventSchedule;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The earthquakes of the server: the active earthquakes of each level and the schedule of the natural earthquakes.
 *
 * <ul>
 *   <li>Clock: the game time of the level. The time between two natural earthquakes is random, between
 *       {@link EventsConfig#EARTHQUAKE_MIN_DAYS} and {@link EventsConfig#EARTHQUAKE_MAX_DAYS} game days. The level
 *       keeps the time of the next earthquake (see {@link EventSchedule}).</li>
 *   <li>At this time, the earthquake starts near a random player of the level, in a loaded chunk. With no player in
 *       the level, no earthquake occurs, and the next earthquake gets a new time. No chunk loads for an
 *       earthquake.</li>
 *   <li>A new earthquake does not start when its area overlaps the area of an active earthquake, or when the server
 *       has {@link EventsConfig#EARTHQUAKE_MAX_ACTIVE} active earthquakes or {@link EventsConfig#MAX_ACTIVE_EVENTS}
 *       active events (see {@link ActiveEvents}).</li>
 *   <li>The active earthquakes are not saved: an earthquake stops when the server stops.</li>
 * </ul>
 */
public final class Earthquakes {
    /** The name of the file of the schedule in the data of the level. */
    public static final String SCHEDULE_FILE = "folkhold_earthquakes";
    /** The schedule examines the time at this interval, in ticks. */
    private static final int SCHEDULE_INTERVAL = 20;

    private static final ActiveEvents<Earthquake> ACTIVE = new ActiveEvents<>();

    private Earthquakes() {
    }

    /**
     * Starts an earthquake with a random seed.
     *
     * @param level    the level
     * @param center   the epicenter
     * @param settings the size, the strength and the timing
     * @return the earthquake, or empty when the area overlaps an active earthquake or the server has the largest
     *         number of active earthquakes or events
     */
    public static Optional<Earthquake> start(ServerLevel level, BlockPos center, EarthquakeSettings settings) {
        return start(level, center, settings, level.getRandom().nextLong());
    }

    /**
     * Starts an earthquake. The same seed in the same level gives the same damage.
     *
     * @param level    the level
     * @param center   the epicenter
     * @param settings the size, the strength and the timing
     * @param seed     the seed of the random numbers
     * @return the earthquake, or empty when the area overlaps an active earthquake or the server has the largest
     *         number of active earthquakes or events
     */
    public static Optional<Earthquake> start(ServerLevel level, BlockPos center, EarthquakeSettings settings,
            long seed) {
        Optional<Earthquake> earthquake = ACTIVE.start(level, center, settings.radius(),
                EventsConfig.EARTHQUAKE_MAX_ACTIVE.get(), () -> new Earthquake(level, center, settings, seed));
        earthquake.ifPresent(started -> FolkholdEvents.LOGGER.info("Earthquake at {} in {}: radius {}, strength {}",
                center.toShortString(), level.dimension().location(), settings.radius(), settings.strength()));
        return earthquake;
    }

    /** The active earthquakes of a level. */
    public static List<Earthquake> active(ServerLevel level) {
        return ACTIVE.active(level);
    }

    /**
     * Stops the active earthquakes of a level.
     *
     * @param level the level
     * @return the number of stopped earthquakes
     */
    public static int stop(ServerLevel level) {
        return ACTIVE.stop(level);
    }

    /** The schedule of the natural earthquakes of a level. */
    public static EventSchedule schedule(ServerLevel level) {
        return EventSchedule.get(level, SCHEDULE_FILE);
    }

    /**
     * True if the level has natural earthquakes: the config turns them on, and the dimension is in the list of the
     * config.
     */
    public static boolean hasNaturalEarthquakes(ServerLevel level) {
        return EventsConfig.EARTHQUAKES.get()
                && EventsConfig.EARTHQUAKE_DIMENSIONS.get().contains(level.dimension().location().toString());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ACTIVE.tick(level, earthquake -> logEnd(level, earthquake));
        if (level.getGameTime() % SCHEDULE_INTERVAL == 0 && hasNaturalEarthquakes(level)
                && schedule(level).isDue(level, EventsConfig.EARTHQUAKE_MIN_DAYS.get(),
                        EventsConfig.EARTHQUAKE_MAX_DAYS.get())
                && startNatural(level).isEmpty()) {
            FolkholdEvents.LOGGER.debug("No earthquake in {}: no player near a loaded epicenter",
                    level.dimension().location());
        }
    }

    /**
     * Starts a natural earthquake near a random player of a level, with the values of the config.
     *
     * @param level the level
     * @return the earthquake, or empty with no player or no loaded epicenter
     */
    public static Optional<Earthquake> startNatural(ServerLevel level) {
        return EventPlacement.nearRandomPlayer(level, EventsConfig.EARTHQUAKE_EPICENTER_DISTANCE.get())
                .flatMap(center -> {
                    double strength = EventMath.between(level.getRandom().nextDouble(),
                            EventsConfig.EARTHQUAKE_MIN_STRENGTH.get(), EventsConfig.EARTHQUAKE_MAX_STRENGTH.get());
                    return start(level, center,
                            EarthquakeSettings.fromConfig(EventsConfig.EARTHQUAKE_RADIUS.get(), strength));
                });
    }

    private static void logEnd(ServerLevel level, Earthquake earthquake) {
        FolkholdEvents.LOGGER.debug("Earthquake at {} in {} ended: {} changed blocks in {} ticks",
                earthquake.center().toShortString(), level.dimension().location(), earthquake.changedBlocks(),
                earthquake.elapsedTicks());
    }
}
