package io.github.nicolasgoyon.folkhold.core.sim.schedule;

/** The phase of a worker of a village at a time of the day. See {@link DaySchedule}. */
public enum DayPhase {
    /** The worker works at the building of its job. */
    WORK,
    /** The worker trades at the market of its village. */
    MARKET,
    /** The worker is at home and does not work. */
    HOME,
    /** The worker sleeps in its bed. */
    SLEEP
}
