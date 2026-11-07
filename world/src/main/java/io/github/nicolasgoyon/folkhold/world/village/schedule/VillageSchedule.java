package io.github.nicolasgoyon.folkhold.world.village.schedule;

import io.github.nicolasgoyon.folkhold.core.sim.schedule.DayPhase;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The daily schedule of the faction villages in a world: the market hours, the phase of each worker and the market
 * day of each village. The values come from the section {@code schedule} of the server config. See
 * {@link DaySchedule} for the time logic.
 *
 * <p>The time is the day time of the level. Each command that changes the day time ({@code /time set},
 * {@code /time add}) also changes the phase and the day number at once: the schedule keeps no state.
 */
public final class VillageSchedule {
    private static volatile Cache cache;

    private VillageSchedule() {
    }

    /**
     * The schedule from the server config. An invalid config gives {@link DaySchedule#DEFAULT} and a warning in the
     * log. A world with no loaded config also gives the default.
     *
     * @return the schedule
     */
    public static DaySchedule schedule() {
        if (!WorldConfig.SPEC.isLoaded()) {
            return DaySchedule.DEFAULT;
        }
        Values values = Values.fromConfig();
        Cache current = cache;
        if (current == null || !current.values().equals(values)) {
            current = new Cache(values, values.toSchedule());
            cache = current;
        }
        return current.schedule();
    }

    /**
     * The phase of the village at the time of a level, with no delay of a worker.
     *
     * @param level the level of the village
     * @return the phase
     */
    public static DayPhase phase(Level level) {
        return schedule().phaseAt(level.getDayTime());
    }

    /**
     * The phase of one worker at the time of a level. The delay of the worker comes from its UUID.
     *
     * @param level  the level of the worker
     * @param worker the worker
     * @return the phase of the worker
     */
    public static DayPhase phase(Level level, Entity worker) {
        DaySchedule schedule = schedule();
        UUID id = worker.getUUID();
        int delay = schedule.workerDelay(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        return schedule.phaseAt(level.getDayTime(), delay);
    }

    /**
     * Tells if the markets are open: the time of the level is in the market phase.
     *
     * @param level the level
     * @return true during the market hours
     */
    public static boolean isMarketOpen(Level level) {
        return phase(level) == DayPhase.MARKET;
    }

    /**
     * Tells if the day of a level is a market day of a settlement.
     *
     * @param level      the level
     * @param settlement the settlement
     * @return true on a market day
     */
    public static boolean isMarketDay(Level level, Settlement settlement) {
        DaySchedule schedule = schedule();
        return schedule.isMarketDay(DaySchedule.day(level.getDayTime()), shift(schedule, settlement));
    }

    /**
     * The number of days from the day of a level to the next market day of a settlement.
     *
     * @param level      the level
     * @param settlement the settlement
     * @return 0 on a market day, else the number of days
     */
    public static int daysToMarketDay(Level level, Settlement settlement) {
        DaySchedule schedule = schedule();
        return schedule.daysToMarketDay(DaySchedule.day(level.getDayTime()), shift(schedule, settlement));
    }

    /**
     * The number of offers that each worker of a settlement adds on the day of a level.
     *
     * @param level      the level
     * @param settlement the settlement
     * @return the extra offers on a market day, else 0
     */
    public static int extraOffers(Level level, Settlement settlement) {
        DaySchedule schedule = schedule();
        return schedule.extraOffers(DaySchedule.day(level.getDayTime()), shift(schedule, settlement));
    }

    /**
     * The shift of the market days of a settlement. It comes from the chunk of its structure start, which does not
     * change. With {@code marketDayForEachVillage} false, the shift is 0 for all the settlements.
     */
    static int shift(DaySchedule schedule, Settlement settlement) {
        if (WorldConfig.SPEC.isLoaded() && !WorldConfig.SCHEDULE_VILLAGE_MARKET_DAYS.get()) {
            return 0;
        }
        long seed = ((long) settlement.startChunkX() << 32) ^ (settlement.startChunkZ() & 0xffffffffL);
        return schedule.marketDayShift(seed);
    }

    /** The raw values of the config. A change of one value makes a new schedule. */
    private record Values(int workStart, int marketStart, int marketEnd, int homeStart, int sleepStart, int period,
            int offers, int spread) {
        static Values fromConfig() {
            return new Values(WorldConfig.SCHEDULE_WORK_START.get(), WorldConfig.SCHEDULE_MARKET_START.get(),
                    WorldConfig.SCHEDULE_MARKET_END.get(), WorldConfig.SCHEDULE_HOME_START.get(),
                    WorldConfig.SCHEDULE_SLEEP_START.get(), WorldConfig.SCHEDULE_MARKET_DAY_PERIOD.get(),
                    WorldConfig.SCHEDULE_MARKET_DAY_OFFERS.get(), WorldConfig.SCHEDULE_WORKER_SPREAD_TICKS.get());
        }

        DaySchedule toSchedule() {
            try {
                return new DaySchedule(workStart, marketStart, marketEnd, homeStart, sleepStart, period, offers,
                        spread);
            } catch (IllegalArgumentException e) {
                FolkholdWorld.LOGGER.warn("Invalid section 'schedule' of the server config: {}. The villages use the "
                        + "default schedule.", e.getMessage());
                return DaySchedule.DEFAULT;
            }
        }
    }

    private record Cache(Values values, DaySchedule schedule) {
    }
}
