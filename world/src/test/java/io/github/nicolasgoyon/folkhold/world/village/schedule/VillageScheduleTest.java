package io.github.nicolasgoyon.folkhold.world.village.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.TestConfig;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillageScheduleTest {
    @BeforeAll
    static void loadConfig() {
        TestConfig.loadWorldConfig();
    }

    @Test
    void defaultConfigGivesTheDefaultSchedule() {
        assertEquals(DaySchedule.DEFAULT, VillageSchedule.schedule());
    }

    @Test
    void configChangeGivesANewSchedule() {
        WorldConfig.SCHEDULE_MARKET_END.set(9000);
        try {
            assertEquals(9000, VillageSchedule.schedule().marketEnd());
        } finally {
            WorldConfig.SCHEDULE_MARKET_END.set(DaySchedule.DEFAULT.marketEnd());
        }
        assertEquals(DaySchedule.DEFAULT, VillageSchedule.schedule());
    }

    @Test
    void invalidConfigGivesTheDefaultSchedule() {
        WorldConfig.SCHEDULE_MARKET_START.set(10000);
        try {
            assertEquals(DaySchedule.DEFAULT, VillageSchedule.schedule());
        } finally {
            WorldConfig.SCHEDULE_MARKET_START.set(DaySchedule.DEFAULT.marketStart());
        }
    }

    @Test
    void oneMarketDayForTheWorldHasNoShift() {
        Settlement settlement = new Settlement("schedule-test", 12, -40, "faction", "Test", 200, -640);
        int shift = VillageSchedule.shift(DaySchedule.DEFAULT, settlement);
        assertEquals(DaySchedule.DEFAULT.marketDayShift((12L << 32) ^ (-40 & 0xffffffffL)), shift);
        WorldConfig.SCHEDULE_VILLAGE_MARKET_DAYS.set(false);
        try {
            assertEquals(0, VillageSchedule.shift(DaySchedule.DEFAULT, settlement));
        } finally {
            WorldConfig.SCHEDULE_VILLAGE_MARKET_DAYS.set(true);
        }
    }
}
