package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Time measurements of the systems of the simulation, for the benchmarks.
 *
 * <ul>
 *   <li>Each system records the time of each call: number of calls, total, maximum, last call.</li>
 *   <li>A call on the server thread that takes more than {@link #WARN_MILLIS} ms writes a warning in the log.</li>
 *   <li>{@code /folkhold sim profile} shows the measurements. {@code /folkhold sim profile reset} clears them.</li>
 * </ul>
 *
 * <p>The measurements are not saved. They start again when the server starts.
 */
public final class SimulationProfiler {
    /** A call that takes more than this time writes a warning in the log, in milliseconds. */
    public static final long WARN_MILLIS = 50;

    private static final Map<String, long[]> STATS = new LinkedHashMap<>();
    private static final int COUNT = 0;
    private static final int TOTAL = 1;
    private static final int MAX = 2;
    private static final int LAST = 3;

    /**
     * The measurements of one system.
     *
     * @param name       the name of the system
     * @param calls      the number of calls
     * @param totalNanos the total time in nanoseconds
     * @param maxNanos   the time of the slowest call in nanoseconds
     * @param lastNanos  the time of the last call in nanoseconds
     */
    public record Entry(String name, long calls, long totalNanos, long maxNanos, long lastNanos) {
        /** The average time of a call in nanoseconds. */
        public long averageNanos() {
            return calls == 0 ? 0 : totalNanos / calls;
        }
    }

    private SimulationProfiler() {
    }

    /**
     * Runs a part of the simulation and records its time.
     *
     * @param name     the name of the system
     * @param runnable the part of the simulation
     */
    public static void time(String name, Runnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            long nanos = System.nanoTime() - start;
            record(name, nanos);
            if (nanos > WARN_MILLIS * 1_000_000L) {
                FolkholdWorld.LOGGER.warn("Folkhold simulation: {} took {} ms", name,
                        String.format("%.1f", nanos / 1_000_000.0));
            }
        }
    }

    /**
     * Records the time of one call.
     *
     * @param name  the name of the system
     * @param nanos the time in nanoseconds
     */
    public static synchronized void record(String name, long nanos) {
        long[] stats = STATS.computeIfAbsent(name, key -> new long[4]);
        stats[COUNT]++;
        stats[TOTAL] += nanos;
        stats[MAX] = Math.max(stats[MAX], nanos);
        stats[LAST] = nanos;
    }

    /** The measurements of all systems, in the order of their first call. */
    public static synchronized List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        STATS.forEach((name, stats) -> result.add(new Entry(name, stats[COUNT], stats[TOTAL], stats[MAX],
                stats[LAST])));
        return result;
    }

    /** Clears all measurements. */
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    public static synchronized void reset() {
        STATS.clear();
    }
}
