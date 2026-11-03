package io.github.nicolasgoyon.folkhold.events.common;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The schedule of the natural events of one type in one level: the game time of the next event. Saved in the data of
 * the level, in one file for each type (for example {@code folkhold_earthquakes.dat}).
 *
 * <p>The clock is the game time of the level, not the time of the day: a night of sleep does not bring an event
 * nearer.
 */
public final class EventSchedule extends SavedData {
    private static final String NEXT_TIME = "next_time";

    /** The game time of the next natural event, or -1 when no event has a time. */
    private long nextTime = -1L;

    private EventSchedule() {
    }

    /**
     * The schedule of a type of event in a level. The method makes the schedule when the level has none.
     *
     * @param level  the level
     * @param fileId the name of the file of the type, for example {@code folkhold_storms}
     * @return the schedule
     */
    public static EventSchedule get(ServerLevel level, String fileId) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EventSchedule::new, EventSchedule::load, null), fileId);
    }

    /** The game time of the next natural event, or -1 when no event has a time. */
    public long nextTime() {
        return nextTime;
    }

    /** Sets the game time of the next natural event. */
    public void setNextTime(long time) {
        nextTime = time;
        setDirty();
    }

    /**
     * Examines the time of the next event. With no time, the schedule gets a time and the method returns false. At
     * the time or after it, the schedule gets the time of the event after it, and the method returns true.
     *
     * @param level   the level
     * @param minDays the shortest time between two events, in game days
     * @param maxDays the longest time between two events, in game days
     * @return true if an event must start now
     */
    public boolean isDue(ServerLevel level, int minDays, int maxDays) {
        long now = level.getGameTime();
        if (nextTime < 0L) {
            setNextTime(now + EventMath.nextDelayTicks(level.getRandom().nextDouble(), minDays, maxDays));
            return false;
        }
        if (now < nextTime) {
            return false;
        }
        setNextTime(now + EventMath.nextDelayTicks(level.getRandom().nextDouble(), minDays, maxDays));
        return true;
    }

    private static EventSchedule load(CompoundTag tag, HolderLookup.Provider registries) {
        EventSchedule schedule = new EventSchedule();
        schedule.nextTime = tag.contains(NEXT_TIME) ? tag.getLong(NEXT_TIME) : -1L;
        return schedule;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(NEXT_TIME, nextTime);
        return tag;
    }
}
