package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.StepResult;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs the abstract simulation on the server: one abstract tick at a fixed interval of game time (see
 * {@link TickScheduler}). Each abstract tick calls each {@link SettlementSystem} for each active settlement. At the
 * end of the abstract tick, it calls each {@link WorldSystem} once. {@link SimulationProfiler} records the time of
 * each call.
 *
 * <p>The tick number and the time of the next abstract tick are saved in {@link SettlementData}.
 *
 * <p>The GameTest server has no automatic abstract tick: the tests call the systems directly, and an abstract tick
 * between two steps of a test would change its settlements.
 */
public final class SimulationTicker {
    private static final List<Named<SettlementSystem>> SYSTEMS = new CopyOnWriteArrayList<>();
    private static final List<Named<WorldSystem>> WORLD_SYSTEMS = new CopyOnWriteArrayList<>();
    /** Name of the measurement of a complete abstract tick of the settlements. */
    public static final String SETTLEMENTS_TICK = "abstract tick (settlements)";

    /** A system with the name of its measurements (see {@link SimulationProfiler}). */
    private record Named<T>(String name, T system) {
    }
    private static TickScheduler<String> scheduler;

    private SimulationTicker() {
    }

    /**
     * Adds a system. The systems run in the order of addition.
     *
     * @param name   the name of the measurements of the system (see {@link SimulationProfiler})
     * @param system the system
     */
    public static void addSystem(String name, SettlementSystem system) {
        SYSTEMS.add(new Named<>(name, system));
    }

    /**
     * Adds a world system. The world systems run in the order of addition.
     *
     * @param name   the name of the measurements of the system (see {@link SimulationProfiler})
     * @param system the system
     */
    public static void addWorldSystem(String name, WorldSystem system) {
        WORLD_SYSTEMS.add(new Named<>(name, system));
    }

    /** The settings from the server configuration. */
    public static TickScheduler.Settings settings() {
        return new TickScheduler.Settings(WorldConfig.SIMULATION_INTERVAL_TICKS.get(),
                WorldConfig.SIMULATION_CYCLE_LENGTH.get(),
                Math.round(WorldConfig.SIMULATION_BUDGET_MS.get() * 1_000_000.0));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server instanceof GameTestServer) {
            return;
        }
        SettlementData data = SettlementData.get(server);
        TickScheduler<String> current = scheduler(data);
        StepResult result = current.step(server.overworld().getGameTime(), () -> activeIds(data),
                (id, tick) -> data.registry().byId(id).filter(Settlement::isActive)
                        .ifPresent(settlement -> runSystems(server, settlement, tick)),
                System::nanoTime, settings());
        if (result.finished() != null) {
            SimulationProfiler.record(SETTLEMENTS_TICK, result.tickNanos());
            runWorldSystems(server, result.finished());
        }
        if (result.finished() != null || result.processed() > 0) {
            data.saveSchedule(current.tickNumber(), current.nextTickAt());
        }
        if (result.finished() != null && result.tickSteps() > 1) {
            FolkholdWorld.LOGGER.warn("Abstract tick {} used more than its budget: {} ms in {} game ticks",
                    result.finished().number(), String.format("%.2f", result.tickNanos() / 1_000_000.0),
                    result.tickSteps());
        }
    }

    /**
     * Starts the next abstract tick at the next server tick.
     *
     * @param server the server
     */
    public static void runNow(MinecraftServer server) {
        scheduler(SettlementData.get(server)).runNow(server.overworld().getGameTime());
    }

    /**
     * Runs the systems for one settlement, now.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param tick       the abstract tick
     */
    public static void runSystems(MinecraftServer server, Settlement settlement, TickInfo tick) {
        for (Named<SettlementSystem> named : SYSTEMS) {
            SimulationProfiler.time(named.name(), () -> named.system().tick(server, settlement, tick));
        }
    }

    /**
     * Runs the world systems, now.
     *
     * @param server the server
     * @param tick   the abstract tick
     */
    public static void runWorldSystems(MinecraftServer server, TickInfo tick) {
        for (Named<WorldSystem> named : WORLD_SYSTEMS) {
            SimulationProfiler.time(named.name(), () -> named.system().tick(server, tick));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        scheduler = null;
    }

    private static TickScheduler<String> scheduler(SettlementData data) {
        if (scheduler == null) {
            scheduler = new TickScheduler<>();
            scheduler.restore(data.tickNumber(), data.nextTickAt());
        }
        return scheduler;
    }

    private static List<String> activeIds(SettlementData data) {
        return data.registry().all().stream().filter(Settlement::isActive).map(Settlement::id).toList();
    }
}
