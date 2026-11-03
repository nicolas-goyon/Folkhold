package io.github.nicolasgoyon.folkhold.events.common;

import net.minecraft.core.BlockPos;

/** An active event in a level, with a circular area: an earthquake or a storm. {@link ActiveEvents} ticks it. */
public interface WorldEvent {
    /** The center of the area. */
    BlockPos center();

    /** The radius of the area, in blocks. */
    int radius();

    /** Runs one tick of the event. */
    void tick();

    /** True when the event has ended. */
    boolean isDone();

    /** Stops the event. The changes that wait do not apply. */
    void stop();
}
