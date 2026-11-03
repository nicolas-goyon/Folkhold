package io.github.nicolasgoyon.folkhold.events.common;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block changes of one event that wait for their batch. A change applies only if its block is still the expected
 * block and its chunk ticks.
 *
 * <p>Two limits apply in each tick: the batch size of the event, and a budget of {@value #MAX_CHANGES_PER_TICK}
 * changes for all the events of the server. The config values of the events cannot go above this budget.
 *
 * @param <C> the type of the changes
 */
public final class BlockChanges<C extends BlockChanges.Change> {
    /** The largest number of block changes of all the events in one tick of the server. */
    public static final int MAX_CHANGES_PER_TICK = 256;

    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    private final Deque<C> queue = new ArrayDeque<>();

    /** A change that waits: the position and the expected block. */
    public interface Change {
        BlockPos pos();

        BlockState expected();
    }

    public void add(C change) {
        queue.add(change);
    }

    public void addAll(Collection<C> changes) {
        queue.addAll(changes);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }

    /**
     * Applies the changes that wait, in their order, up to the batch size and the remaining budget of the tick.
     *
     * @param level  the level
     * @param batch  the largest number of changes of the event in this tick
     * @param action applies one change. It returns false when the change does not apply.
     * @return the number of applied changes
     */
    public int apply(ServerLevel level, int batch, Predicate<C> action) {
        MinecraftServer server = level.getServer();
        int applied = 0;
        while (applied < batch && !queue.isEmpty() && hasBudget(server)) {
            C change = queue.poll();
            if (BlockRules.isReady(level, change.pos()) && level.getBlockState(change.pos()) == change.expected()
                    && action.test(change)) {
                applied++;
                budgetUsed++;
            }
        }
        return applied;
    }

    /** True if the budget of the current tick of the server has room for one more change. */
    private static boolean hasBudget(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        return budgetUsed < MAX_CHANGES_PER_TICK;
    }
}
