package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the NeoForge event bus when the level of a settlement changes. Other systems use it: for example the
 * garrison from the level and the physical growth of the village.
 */
public final class SettlementLevelChangedEvent extends Event {
    private final MinecraftServer server;
    private final Settlement settlement;
    private final int oldLevel;
    private final int newLevel;

    public SettlementLevelChangedEvent(MinecraftServer server, Settlement settlement, int oldLevel, int newLevel) {
        this.server = server;
        this.settlement = settlement;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
