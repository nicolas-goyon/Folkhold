package io.github.nicolasgoyon.folkhold.events.earthquake;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The earthquakes of the server: the active earthquakes of each level and the schedule of the natural earthquakes.
 *
 * <ul>
 *   <li>Clock: the game time of the level. The time between two natural earthquakes is random, between
 *       {@link EventsConfig#EARTHQUAKE_MIN_DAYS} and {@link EventsConfig#EARTHQUAKE_MAX_DAYS} game days. The level
 *       keeps the time of the next earthquake (see {@link EarthquakeData}).</li>
 *   <li>At this time, the earthquake starts near a random player of the level, in a loaded chunk. With no player in
 *       the level, no earthquake occurs, and the next earthquake gets a new time. No chunk loads for an
 *       earthquake.</li>
 *   <li>A new earthquake does not start when its area overlaps the area of an active earthquake, or when the server
 *       has {@link EventsConfig#EARTHQUAKE_MAX_ACTIVE} active earthquakes.</li>
 *   <li>The active earthquakes are not saved: an earthquake stops when the server stops.</li>
 * </ul>
 */
public final class Earthquakes {
    /** The schedule examines the time at this interval, in ticks. */
    private static final int SCHEDULE_INTERVAL = 20;
    /** Attempts to find an epicenter in a loaded chunk near the player. */
    private static final int EPICENTER_ATTEMPTS = 8;

    private static final Map<ResourceKey<Level>, List<Earthquake>> ACTIVE = new HashMap<>();

    private Earthquakes() {
    }

    /**
     * Starts an earthquake with a random seed.
     *
     * @param level    the level
     * @param center   the epicenter
     * @param settings the size, the strength and the timing
     * @return the earthquake, or empty when the area overlaps an active earthquake or the server has the largest
     *         number of active earthquakes
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
     *         number of active earthquakes
     */
    public static Optional<Earthquake> start(ServerLevel level, BlockPos center, EarthquakeSettings settings,
            long seed) {
        int count = ACTIVE.values().stream().mapToInt(List::size).sum();
        if (count >= EventsConfig.EARTHQUAKE_MAX_ACTIVE.get() || overlapsActive(level, center, settings.radius())) {
            return Optional.empty();
        }
        Earthquake earthquake = new Earthquake(level, center, settings, seed);
        ACTIVE.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(earthquake);
        FolkholdEvents.LOGGER.info("Earthquake at {} in {}: radius {}, strength {}", center.toShortString(),
                level.dimension().location(), settings.radius(), settings.strength());
        return Optional.of(earthquake);
    }

    /** True if a circle of a level overlaps the area of an active earthquake. */
    private static boolean overlapsActive(ServerLevel level, BlockPos center, int radius) {
        for (Earthquake active : ACTIVE.getOrDefault(level.dimension(), List.of())) {
            double dx = center.getX() - active.center().getX();
            double dz = center.getZ() - active.center().getZ();
            double reach = radius + active.settings().radius();
            if (dx * dx + dz * dz < reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** The active earthquakes of a level. */
    public static List<Earthquake> active(ServerLevel level) {
        return List.copyOf(ACTIVE.getOrDefault(level.dimension(), List.of()));
    }

    /**
     * Stops the active earthquakes of a level.
     *
     * @param level the level
     * @return the number of stopped earthquakes
     */
    public static int stop(ServerLevel level) {
        List<Earthquake> earthquakes = ACTIVE.remove(level.dimension());
        if (earthquakes == null) {
            return 0;
        }
        earthquakes.forEach(Earthquake::stop);
        return earthquakes.size();
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
        List<Earthquake> earthquakes = ACTIVE.get(level.dimension());
        if (earthquakes != null) {
            // A copy: the loop removes the earthquakes that end.
            for (Earthquake earthquake : List.copyOf(earthquakes)) {
                earthquake.tick();
                if (earthquake.isDone()) {
                    earthquakes.remove(earthquake);
                    logEnd(level, earthquake);
                }
            }
            if (earthquakes.isEmpty()) {
                ACTIVE.remove(level.dimension());
            }
        }
        if (level.getGameTime() % SCHEDULE_INTERVAL == 0 && hasNaturalEarthquakes(level)) {
            schedule(level);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static void schedule(ServerLevel level) {
        EarthquakeData data = EarthquakeData.get(level);
        long now = level.getGameTime();
        if (data.nextTime() < 0L) {
            data.setNextTime(now + nextDelay(level.getRandom()));
            return;
        }
        if (now < data.nextTime()) {
            return;
        }
        data.setNextTime(now + nextDelay(level.getRandom()));
        if (startNatural(level).isEmpty()) {
            FolkholdEvents.LOGGER.debug("No earthquake in {}: no player near a loaded epicenter",
                    level.dimension().location());
        }
    }

    private static long nextDelay(RandomSource random) {
        return EarthquakeMath.nextDelayTicks(random.nextDouble(), EventsConfig.EARTHQUAKE_MIN_DAYS.get(),
                EventsConfig.EARTHQUAKE_MAX_DAYS.get());
    }

    /**
     * Starts a natural earthquake near a random player of a level, with the values of the config.
     *
     * @param level the level
     * @return the earthquake, or empty with no player or no loaded epicenter
     */
    public static Optional<Earthquake> startNatural(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()).toList();
        if (players.isEmpty()) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();
        ServerPlayer player = players.get(random.nextInt(players.size()));
        int distance = EventsConfig.EARTHQUAKE_EPICENTER_DISTANCE.get();
        for (int attempt = 0; attempt < EPICENTER_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double length = random.nextDouble() * distance;
            int x = Mth.floor(player.getX() + Math.cos(angle) * length);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * length);
            BlockPos column = new BlockPos(x, player.getBlockY(), z);
            if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
                continue;
            }
            BlockPos center = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            double minStrength = EventsConfig.EARTHQUAKE_MIN_STRENGTH.get();
            double maxStrength = Math.max(minStrength, EventsConfig.EARTHQUAKE_MAX_STRENGTH.get());
            double strength = minStrength + random.nextDouble() * (maxStrength - minStrength);
            return start(level, center, EarthquakeSettings.fromConfig(EventsConfig.EARTHQUAKE_RADIUS.get(), strength));
        }
        return Optional.empty();
    }

    private static void logEnd(ServerLevel level, Earthquake earthquake) {
        FolkholdEvents.LOGGER.debug("Earthquake at {} in {} ended: {} changed blocks in {} ticks",
                earthquake.center().toShortString(), level.dimension().location(), earthquake.changedBlocks(),
                earthquake.elapsedTicks());
    }
}
