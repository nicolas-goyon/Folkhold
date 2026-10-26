package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.village.VillageBraziers.Period;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class VillageBraziersTest {
    private static final long DAY = 24_000L;

    /** The day of the braziers goes from the start of the work of the villagers to the evening. */
    @Test
    void dayIsBetweenTheMorningAndTheEvening() {
        assertEquals(Period.DAY, VillageBraziers.period(VillageBraziers.MORNING_END, false));
        assertEquals(Period.DAY, VillageBraziers.period(6_000L, false));
        assertEquals(Period.DAY, VillageBraziers.period(VillageBraziers.EVENING - 1L, false));
    }

    /** The evening goes from the end of the work of the villagers to the night. */
    @Test
    void eveningStartsWhenTheVillagersStopTheirWork() {
        assertEquals(Period.EVENING, VillageBraziers.period(VillageBraziers.EVENING, false));
        assertEquals(Period.EVENING, VillageBraziers.period(12_500L, false));
        assertEquals(Period.EVENING, VillageBraziers.period(VillageBraziers.MIDNIGHT - 1L, false));
    }

    /** The morning goes from the end of the night to the start of the work of the villagers. */
    @Test
    void morningStopsWhenTheVillagersStartTheirWork() {
        assertEquals(Period.MORNING, VillageBraziers.period(VillageBraziers.MIDNIGHT, false));
        assertEquals(Period.MORNING, VillageBraziers.period(23_500L, false));
        assertEquals(Period.MORNING, VillageBraziers.period(0L, false));
        assertEquals(Period.MORNING, VillageBraziers.period(VillageBraziers.MORNING_END - 1L, false));
    }

    /** The night of the village heart has priority on the day time. */
    @Test
    void nightOfTheHeartIsTheNight() {
        assertEquals(Period.NIGHT, VillageBraziers.period(18_000L, true));
        assertEquals(Period.NIGHT, VillageBraziers.period(6_000L, true));
    }

    /** The period repeats each day. */
    @Test
    void periodRepeatsEachDay() {
        for (long time = 0; time < DAY; time += 500L) {
            assertEquals(VillageBraziers.period(time, false), VillageBraziers.period(7 * DAY + time, false),
                    "Day time " + time);
        }
    }

    /** The village does not light a brazier in water. A campfire always becomes a brazier. */
    @Test
    void villageDoesNotLightABrazierInWater() {
        BlockState brazier = ModBlocks.BRAZIER.get().defaultBlockState();
        BlockState inWater = brazier.setValue(BrazierBlock.WATERLOGGED, true);

        assertTrue(VillageBraziers.needsChange(brazier, true));
        assertFalse(VillageBraziers.needsChange(inWater, true));
        assertFalse(VillageBraziers.needsChange(inWater, false));
        assertTrue(VillageBraziers.needsChange(inWater.setValue(BrazierBlock.LIT, true), true));
        assertTrue(VillageBraziers.needsChange(Blocks.CAMPFIRE.defaultBlockState(), false));
    }
}
