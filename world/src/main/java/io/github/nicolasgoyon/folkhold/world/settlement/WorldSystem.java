package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import net.minecraft.server.MinecraftServer;

/**
 * One part of the abstract simulation that is not for one settlement (for example the mobile groups). The
 * {@link SimulationTicker} calls each world system once at the end of each abstract tick, after the
 * {@link SettlementSystem}s of all settlements.
 */
@FunctionalInterface
public interface WorldSystem {

    /**
     * Updates the world.
     *
     * @param server the server
     * @param tick   the abstract tick. {@link TickInfo#cycle()} is true once each cycle.
     */
    void tick(MinecraftServer server, TickInfo tick);
}
