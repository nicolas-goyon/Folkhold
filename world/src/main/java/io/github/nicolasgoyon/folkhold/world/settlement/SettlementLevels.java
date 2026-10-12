package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.level.Experience;
import io.github.nicolasgoyon.folkhold.core.sim.level.Experience.Progress;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Level and experience of the settlements.
 *
 * <ul>
 *   <li>At each cycle, a settlement gets experience from its prosperity (see {@link #addProsperitySource}).</li>
 *   <li>The death of the captain removes experience.</li>
 *   <li>When the level changes, the mod posts a {@link SettlementLevelChangedEvent}.</li>
 * </ul>
 */
public final class SettlementLevels {
    private static final List<ToDoubleBiFunction<MinecraftServer, Settlement>> PROSPERITY = new CopyOnWriteArrayList<>();

    private SettlementLevels() {
    }

    /**
     * Adds a source of prosperity. The prosperity of a settlement is the mean of all sources, from 0 to 1. With no
     * source, the prosperity is the configured base prosperity.
     *
     * @param source gives the prosperity of a settlement, from 0 to 1
     */
    public static void addProsperitySource(ToDoubleBiFunction<MinecraftServer, Settlement> source) {
        PROSPERITY.add(source);
    }

    /** The prosperity of a settlement, from 0 to 1. */
    public static double prosperity(MinecraftServer server, Settlement settlement) {
        if (PROSPERITY.isEmpty()) {
            return WorldConfig.LEVEL_BASE_PROSPERITY.get();
        }
        double sum = 0.0;
        for (ToDoubleBiFunction<MinecraftServer, Settlement> source : PROSPERITY) {
            sum += source.applyAsDouble(server, settlement);
        }
        return sum / PROSPERITY.size();
    }

    /** The {@link SettlementSystem} of the level: experience from the prosperity, once each cycle. */
    public static void tick(MinecraftServer server, Settlement settlement, TickInfo tick) {
        if (tick.cycle()) {
            int gain = Experience.cycleGain(prosperity(server, settlement), WorldConfig.LEVEL_MAX_GAIN_PER_CYCLE.get());
            addExperience(server, settlement, gain);
        }
    }

    /**
     * Changes the experience of a settlement. Posts a {@link SettlementLevelChangedEvent} if the level changes.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param delta      the change (negative for a loss)
     */
    public static void addExperience(MinecraftServer server, Settlement settlement, int delta) {
        LevelTable table = LevelTables.get();
        int oldLevel = settlement.level();
        Progress progress = Experience.apply(oldLevel, settlement.experience(), delta, table);
        setProgress(server, settlement, progress.level(), progress.experience());
    }

    /**
     * Sets the level and the experience of a settlement. Posts a {@link SettlementLevelChangedEvent} if the level
     * changes.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param level      the new level (limited to the levels of the table)
     * @param experience the new experience
     */
    public static void setProgress(MinecraftServer server, Settlement settlement, int level, int experience) {
        int oldLevel = settlement.level();
        int newLevel = LevelTables.get().clamp(level);
        settlement.setProgress(newLevel, Math.max(0, experience));
        SettlementData.get(server).setDirty();
        if (newLevel != oldLevel) {
            NeoForge.EVENT_BUS.post(new SettlementLevelChangedEvent(server, settlement, oldLevel, newLevel));
        }
    }

    /** The translated name of the tier of a level. */
    public static Component tierName(int level) {
        return Component.translatable("folkhold_world.settlement_tier." + LevelTables.get().tier(level).id());
    }
}
