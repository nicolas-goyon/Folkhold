package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Garrison of a village: the list of its units, with a stable id for each unit.
 *
 * <p>Rules:
 * <ul>
 *   <li>The garrison never creates a unit for an entry that is alive. A unit in a chunk that is not loaded
 *       is still alive. This prevents duplicates after a chunk unload and reload.</li>
 *   <li>An entry becomes dead when its unit dies (the unit reports its death), or when the unit is missing
 *       for {@link #MISSING_TIMEOUT_TICKS} while the chunk of its last known position is loaded.</li>
 *   <li>The garrison replaces a dead unit after the refill delay, only when the village is calm.</li>
 * </ul>
 */
public final class Garrison {
    /** A unit that is missing for 5 minutes in a loaded chunk is dead. */
    static final long MISSING_TIMEOUT_TICKS = 5 * 60 * 20;
    /** Units spawn at this distance from the heart, in blocks. */
    private static final int SPAWN_MIN_DISTANCE = 3;
    private static final int SPAWN_MAX_DISTANCE = 7;

    private final List<Entry> entries = new ArrayList<>();

    /** One unit of the garrison. */
    public static final class Entry {
        private UUID unitId;
        private final GarrisonRole role;
        private boolean dead;
        private long deadSince;
        private long lastSeen;
        @Nullable
        private BlockPos lastPos;

        Entry(UUID unitId, GarrisonRole role) {
            this.unitId = unitId;
            this.role = role;
        }

        public UUID unitId() {
            return unitId;
        }

        public GarrisonRole role() {
            return role;
        }

        public boolean isDead() {
            return dead;
        }

        void markDead(long now) {
            if (!dead) {
                dead = true;
                deadSince = now;
            }
        }
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Number of entries that are alive. */
    public int aliveCount() {
        return (int) entries.stream().filter(entry -> !entry.dead).count();
    }

    /**
     * Records the death of a unit.
     *
     * @param unitId the id of the unit
     * @param now    the current game time
     * @return true if the unit is in this garrison
     */
    public boolean onUnitDied(UUID unitId, long now) {
        for (Entry entry : entries) {
            if (entry.unitId.equals(unitId)) {
                entry.markDead(now);
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the missing units of the garrison.
     *
     * <ol>
     *   <li>Updates the entries: last seen time and position of the loaded units, timeout of missing units.</li>
     *   <li>Adds entries until each role has its configured count. A new garrison spawns completely.</li>
     *   <li>Replaces dead units after the refill delay, if the village is calm.</li>
     * </ol>
     *
     * @param level     the level of the heart
     * @param heartPos  the position of the heart
     * @param calm      true if the village is calm
     * @param now       the current game time
     * @return true if the garrison changed (the heart must save)
     */
    public boolean ensure(ServerLevel level, BlockPos heartPos, boolean calm, long now) {
        boolean changed = false;
        for (Entry entry : entries) {
            if (entry.dead) {
                continue;
            }
            Entity entity = level.getEntity(entry.unitId);
            if (entity != null && entity.isAlive()) {
                entry.lastSeen = now;
                entry.lastPos = entity.blockPosition();
            } else if (entity == null && entry.lastPos != null && level.isLoaded(entry.lastPos)
                    && now - entry.lastSeen > MISSING_TIMEOUT_TICKS) {
                entry.markDead(now);
                changed = true;
            }
        }

        for (GarrisonRole role : GarrisonRole.values()) {
            int count = (int) entries.stream().filter(entry -> entry.role == role).count();
            for (int i = count; i < role.configuredCount(); i++) {
                FactionUnit unit = spawnUnit(level, heartPos, role);
                if (unit != null) {
                    Entry entry = new Entry(unit.getUUID(), role);
                    entry.lastSeen = now;
                    entry.lastPos = unit.blockPosition();
                    entries.add(entry);
                    changed = true;
                }
            }
        }

        if (calm) {
            long refillDelay = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
            for (Entry entry : entries) {
                if (entry.dead && now - entry.deadSince >= refillDelay) {
                    FactionUnit unit = spawnUnit(level, heartPos, entry.role);
                    if (unit != null) {
                        entry.unitId = unit.getUUID();
                        entry.dead = false;
                        entry.lastSeen = now;
                        entry.lastPos = unit.blockPosition();
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    @Nullable
    private static FactionUnit spawnUnit(ServerLevel level, BlockPos heartPos, GarrisonRole role) {
        FactionUnit unit = role.entityType().create(level);
        if (unit == null) {
            return null;
        }
        float angle = level.random.nextFloat() * (float) (Math.PI * 2.0);
        int distance = Mth.nextInt(level.random, SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);
        BlockPos column = heartPos.offset(Math.round(Mth.cos(angle) * distance), 0, Math.round(Mth.sin(angle) * distance));
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        unit.setHomePos(pos);
        unit.setVillagePos(heartPos);
        unit.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(unit);
        return unit;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", entry.unitId);
            tag.putString("Role", entry.role.serializedName());
            tag.putBoolean("Dead", entry.dead);
            tag.putLong("DeadSince", entry.deadSince);
            tag.putLong("LastSeen", entry.lastSeen);
            if (entry.lastPos != null) {
                tag.put("LastPos", NbtUtils.writeBlockPos(entry.lastPos));
            }
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            GarrisonRole role = GarrisonRole.fromSerializedName(tag.getString("Role"));
            if (role == null || !tag.hasUUID("Id")) {
                continue;
            }
            Entry entry = new Entry(tag.getUUID("Id"), role);
            entry.dead = tag.getBoolean("Dead");
            entry.deadSince = tag.getLong("DeadSince");
            entry.lastSeen = tag.getLong("LastSeen");
            entry.lastPos = NbtUtils.readBlockPos(tag, "LastPos").orElse(null);
            entries.add(entry);
        }
    }

    /** NBT type of the list elements. */
    public static final int TAG_TYPE = Tag.TAG_COMPOUND;
}
