package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import net.minecraft.server.MinecraftServer;

/**
 * One part of the abstract simulation (for example the level or the economy). At each abstract tick, the
 * {@link SimulationTicker} calls each system once for each active settlement.
 */
@FunctionalInterface
public interface SettlementSystem {

    /**
     * Updates one settlement.
     *
     * @param server     the server
     * @param settlement the settlement, in the state {@code ACTIVE}
     * @param tick       the abstract tick. {@link TickInfo#cycle()} is true once each cycle.
     */
    void tick(MinecraftServer server, Settlement settlement, TickInfo tick);
}
