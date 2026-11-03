package io.github.nicolasgoyon.folkhold.events.earthquake;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The schedule of the natural earthquakes of one level. Saved in the data of the level
 * ({@code folkhold_earthquakes.dat}).
 */
public final class EarthquakeData extends SavedData {
    private static final String FILE_ID = "folkhold_earthquakes";
    private static final String NEXT_TIME = "next_time";
    private static final SavedData.Factory<EarthquakeData> FACTORY =
            new SavedData.Factory<>(EarthquakeData::new, EarthquakeData::load, null);

    /** The game time of the next natural earthquake, or -1 when no earthquake has a time. */
    private long nextTime = -1L;

    private EarthquakeData() {
    }

    /** The data of a level. The method makes the data when the level has none. */
    public static EarthquakeData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The game time of the next natural earthquake, or -1 when no earthquake has a time. */
    public long nextTime() {
        return nextTime;
    }

    /** Sets the game time of the next natural earthquake. */
    public void setNextTime(long time) {
        nextTime = time;
        setDirty();
    }

    private static EarthquakeData load(CompoundTag tag, HolderLookup.Provider registries) {
        EarthquakeData data = new EarthquakeData();
        data.nextTime = tag.contains(NEXT_TIME) ? tag.getLong(NEXT_TIME) : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(NEXT_TIME, nextTime);
        return tag;
    }
}
