package io.github.nicolasgoyon.folkhold.world.construction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The construction runs in progress. Each server tick applies the next steps of each run. A run stops when it applied
 * all its steps, or when a command stops it.
 */
public final class ConstructionRuns {
    private static final List<Active> ACTIVE = new ArrayList<>();

    private ConstructionRuns() {
    }

    /** The action at the end of a run. */
    @FunctionalInterface
    public interface EndAction {
        /**
         * Runs the action.
         *
         * @param finished true when the run applied all its steps, false when a command stopped it
         */
        void ended(boolean finished);
    }

    /** A run in progress, and the action at its end. */
    private record Active(ConstructionRun run, EndAction onEnd) {
    }

    /**
     * Starts a run: its first steps come at the next server tick.
     *
     * @param run   the run
     * @param onEnd the action at the end of the run
     */
    public static void start(ConstructionRun run, EndAction onEnd) {
        ACTIVE.add(new Active(run, onEnd));
    }

    /**
     * Stops the runs in a level. The blocks of the applied steps stay.
     *
     * @param level the level
     * @return the number of stopped runs
     */
    public static int stop(ServerLevel level) {
        List<Active> stopped = ACTIVE.stream().filter(active -> active.run().level() == level).toList();
        ACTIVE.removeAll(stopped);
        stopped.forEach(active -> active.onEnd().ended(false));
        return stopped.size();
    }

    /** The number of runs in progress. */
    public static int count() {
        return ACTIVE.size();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Active active : List.copyOf(ACTIVE)) {
            active.run().tick();
            if (active.run().finished()) {
                ACTIVE.remove(active);
                active.onEnd().ended(true);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
