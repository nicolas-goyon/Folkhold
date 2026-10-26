package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The braziers of the faction villages: they burn at night and they do not burn by day.
 *
 * <ul>
 *   <li>The handymen of the village light the braziers in the evening and put them out in the morning (see
 *       {@link Handymen}). The periods of the day come from the schedule of the vanilla villagers (see
 *       {@link #period}).</li>
 *   <li>At the start of the night, the village heart lights the braziers that do not burn.</li>
 *   <li>At the start of the day, the village heart puts out the braziers of a village with no handymen. In a village
 *       with handymen, it puts out the braziers that burn at the start of the work of the villagers
 *       ({@link #MORNING_END}).</li>
 *   <li>A campfire of the village layout becomes a brazier of the block library, which does not burn the
 *       entities.</li>
 *   <li>A brazier in water does not burn: the village does not light it.</li>
 * </ul>
 */
public final class VillageBraziers {
    /** The evening starts at this day time: the vanilla villagers stop their work. */
    public static final int EVENING = 11_000;
    /** The morning stops at this day time: the vanilla villagers start their work. */
    public static final int MORNING_END = 2_000;
    /** The evening stops and the morning starts at this day time, during the night. */
    static final int MIDNIGHT = 18_000;
    private static final int DAY_LENGTH = 24_000;

    /** A period of the day for the braziers. */
    public enum Period {
        /** From {@link #MORNING_END} to {@link #EVENING}: the braziers do not burn. */
        DAY,
        /** From {@link #EVENING} to the night: the handymen light the braziers. */
        EVENING,
        /** The night of the village heart: the braziers burn. */
        NIGHT,
        /** From the end of the night to {@link #MORNING_END}: the handymen put out the braziers. */
        MORNING
    }

    private VillageBraziers() {
    }

    /**
     * The period of the day for the braziers.
     *
     * @param dayTime the day time of the level
     * @param night   true during the night of the village heart
     * @return the period
     */
    public static Period period(long dayTime, boolean night) {
        if (night) {
            return Period.NIGHT;
        }
        int time = Math.floorMod(dayTime, DAY_LENGTH);
        if (time >= EVENING && time < MIDNIGHT) {
            return Period.EVENING;
        }
        return time >= MIDNIGHT || time < MORNING_END ? Period.MORNING : Period.DAY;
    }

    /**
     * True if a block must change to get a state: a campfire, or a brazier with the other state. A brazier in water
     * does not burn, thus it does not change to burn.
     *
     * @param state the block state
     * @param lit   true for a brazier that burns
     * @return true if the block must change
     */
    public static boolean needsChange(BlockState state, boolean lit) {
        if (state.getBlock() instanceof CampfireBlock) {
            return true;
        }
        return state.getBlock() instanceof BrazierBlock && state.getValue(BrazierBlock.LIT) != burns(state, lit);
    }

    /**
     * Lights a brazier or puts it out. A campfire becomes a brazier of the block library. A brazier in water does not
     * burn.
     *
     * @param level the level
     * @param pos   the position of the brazier
     * @param lit   true to light the brazier, false to put it out
     * @return true if the block changed
     */
    public static boolean set(Level level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!needsChange(state, lit)) {
            return false;
        }
        // A campfire burns the entities: the brazier of the block library takes its place, in the same water.
        BlockState brazier = state.getBlock() instanceof BrazierBlock ? state
                : ModBlocks.BRAZIER.get().defaultBlockState()
                        .setValue(BrazierBlock.WATERLOGGED, state.getValue(CampfireBlock.WATERLOGGED));
        level.setBlock(pos, brazier.setValue(BrazierBlock.LIT, burns(brazier, lit)), Block.UPDATE_ALL);
        return true;
    }

    /** The state of the embers of a brazier: lit only if the village lights it and it is not in water. */
    private static boolean burns(BlockState brazier, boolean lit) {
        return lit && !brazier.getValue(BrazierBlock.WATERLOGGED);
    }
}
