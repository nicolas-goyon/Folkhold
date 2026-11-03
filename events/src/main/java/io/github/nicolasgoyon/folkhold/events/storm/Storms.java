package io.github.nicolasgoyon.folkhold.events.storm;

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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The storms of the server: the active storms of each level and the schedule of the natural storms.
 *
 * <ul>
 *   <li>Clock: the game time of the level. The time between two natural storms is random, between
 *       {@link EventsConfig#STORM_MIN_DAYS} and {@link EventsConfig#STORM_MAX_DAYS} game days. The level keeps the
 *       time of the next storm (see {@link EventSchedule}).</li>
 *   <li>At this time, the storm starts near a random player of the level, in a loaded chunk, with a random wind
 *       direction. With no player in the level, no storm occurs, and the next storm gets a new time. No chunk loads
 *       for a storm.</li>
 *   <li>A new storm does not start when its area overlaps the area of an active storm, or when the server has
 *       {@link EventsConfig#STORM_MAX_ACTIVE} active storms or {@link EventsConfig#MAX_ACTIVE_EVENTS} active events
 *       (see {@link ActiveEvents}).</li>
 *   <li>A level with no sky light, with a ceiling, or the End has no storms (see {@link #allowsStorms}).</li>
 *   <li>The active storms are not saved: a storm stops when the server stops, and gives back the weather of the
 *       level before the save.</li>
 * </ul>
 */
public final class Storms {
    /** The name of the file of the schedule in the data of the level. */
    public static final String SCHEDULE_FILE = "folkhold_storms";
    /** The schedule examines the time at this interval, in ticks. */
    private static final int SCHEDULE_INTERVAL = 20;

    private static final ActiveEvents<Storm> ACTIVE = new ActiveEvents<>();

    private Storms() {
    }

    /**
     * Starts a storm with a random seed.
     *
     * @param level    the level
     * @param center   the center of the area
     * @param settings the size, the strength, the wind and the timing
     * @return the storm, or empty when the area overlaps an active storm or the server has the largest number of
     *         active storms or events
     */
    public static Optional<Storm> start(ServerLevel level, BlockPos center, StormSettings settings) {
        return start(level, center, settings, level.getRandom().nextLong());
    }

    /**
     * Starts a storm. The same seed in the same level gives the same damage.
     *
     * @param level    the level
     * @param center   the center of the area
     * @param settings the size, the strength, the wind and the timing
     * @param seed     the seed of the random numbers
     * @return the storm, or empty when the area overlaps an active storm or the server has the largest number of
     *         active storms or events
     */
    public static Optional<Storm> start(ServerLevel level, BlockPos center, StormSettings settings, long seed) {
        if (!allowsStorms(level)) {
            return Optional.empty();
        }
        Optional<Storm> storm = ACTIVE.start(level, center, settings.radius(), EventsConfig.STORM_MAX_ACTIVE.get(),
                () -> new Storm(level, center, settings, seed));
        storm.ifPresent(started -> FolkholdEvents.LOGGER.info(
                "Storm at {} in {}: radius {}, strength {}, wind to the {}, {} blocks at most",
                center.toShortString(), level.dimension().location(), settings.radius(), settings.strength(),
                StormMath.compass(settings.windAngle()), settings.maxChanges()));
        return storm;
    }

    /** The active storms of a level. */
    public static List<Storm> active(ServerLevel level) {
        return ACTIVE.active(level);
    }

    /**
     * Stops the active storms of a level.
     *
     * @param level the level
     * @return the number of stopped storms
     */
    public static int stop(ServerLevel level) {
        return ACTIVE.stop(level);
    }

    /**
     * True if a level can have storms: a dimension with sky light and no ceiling, not the End. Over the void of the
     * End, a flying block falls out of the world and its item goes away.
     */
    public static boolean allowsStorms(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && level.dimension() != Level.END;
    }

    /**
     * Stops the storms of all the levels before the save of the world: each storm gives back the weather of the level.
     * Thus the world does not keep the rain and the thunder of a storm that does not exist any more.
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            stop(level);
        }
    }

    /** The schedule of the natural storms of a level. */
    public static EventSchedule schedule(ServerLevel level) {
        return EventSchedule.get(level, SCHEDULE_FILE);
    }

    /**
     * True if the level has natural storms: the config turns them on, the dimension is in the list of the config, and
     * the level allows storms (see {@link #allowsStorms}).
     */
    public static boolean hasNaturalStorms(ServerLevel level) {
        return EventsConfig.STORMS.get() && allowsStorms(level)
                && EventsConfig.STORM_DIMENSIONS.get().contains(level.dimension().location().toString());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ACTIVE.tick(level, storm -> logEnd(level, storm));
        if (level.getGameTime() % SCHEDULE_INTERVAL == 0 && hasNaturalStorms(level)
                && schedule(level).isDue(level, EventsConfig.STORM_MIN_DAYS.get(), EventsConfig.STORM_MAX_DAYS.get())
                && startNatural(level).isEmpty()) {
            FolkholdEvents.LOGGER.debug("No storm in {}: no player near a loaded center", level.dimension().location());
        }
    }

    /**
     * Starts a natural storm near a random player of a level, with the values of the config and a random wind
     * direction.
     *
     * @param level the level
     * @return the storm, or empty with no player or no loaded center
     */
    public static Optional<Storm> startNatural(ServerLevel level) {
        return EventPlacement.nearRandomPlayer(level, EventsConfig.STORM_CENTER_DISTANCE.get()).flatMap(center -> {
            double strength = EventMath.between(level.getRandom().nextDouble(), EventsConfig.STORM_MIN_STRENGTH.get(),
                    EventsConfig.STORM_MAX_STRENGTH.get());
            double windAngle = level.getRandom().nextDouble() * 360.0;
            return start(level, center, StormSettings.fromConfig(EventsConfig.STORM_RADIUS.get(), strength,
                    windAngle));
        });
    }

    private static void logEnd(ServerLevel level, Storm storm) {
        FolkholdEvents.LOGGER.debug("Storm at {} in {} ended: {} torn blocks, {} lightning strikes in {} ticks",
                storm.center().toShortString(), level.dimension().location(), storm.changedBlocks(),
                storm.lightningStrikes(), storm.elapsedTicks());
    }
}
