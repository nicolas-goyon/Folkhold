package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Garrison of a village: the list of its units, with a stable id for each unit.
 *
 * <p>Rules:
 * <ul>
 *   <li>The level of the village sets the number of units of each role and their equipment set
 *       ({@link GarrisonTraits}).</li>
 *   <li>The garrison never creates a unit for an entry that is alive. A unit in a chunk that is not loaded
 *       is still alive. This prevents duplicates after a chunk unload and reload.</li>
 *   <li>An entry becomes dead when its unit dies (the unit reports its death), or when the unit is missing
 *       for {@link #MISSING_TIMEOUT_TICKS} while the chunk of its last known position is loaded.</li>
 *   <li>The garrison spawns a new unit for a dead one after the refill delay, only when the village is calm.</li>
 *   <li>When the level goes down, the units that are alive stay. The garrison removes the dead entries above the
 *       number of the level, so it does not spawn new units for them.</li>
 *   <li>A loaded unit with another equipment set gets the set of the level.</li>
 *   <li>Archers get a free tower post of the village (see {@link VillageLayout}), and golems a free gate post, if
 *       there is one. They spawn on it. The other units spawn on the ground near the heart (see
 *       {@link #groundSpawnPos}).</li>
 * </ul>
 */
public final class Garrison {
    /** A unit that is missing for 5 minutes in a loaded chunk is dead. */
    static final long MISSING_TIMEOUT_TICKS = 5 * 60 * 20;
    /** Units spawn at this distance from the heart, in blocks. */
    private static final int SPAWN_MIN_DISTANCE = 3;
    private static final int SPAWN_MAX_DISTANCE = 7;
    /** Number of random columns to try for a spawn position. */
    private static final int SPAWN_ATTEMPTS = 24;
    /** A spawn position is at most this number of blocks above or below the heart. */
    private static final int SPAWN_MAX_HEIGHT = 2;

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
        @Nullable
        private BlockPos post;

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

        /** The post of the unit, or null. */
        @Nullable
        public BlockPos post() {
            return post;
        }

        /** Gives a new post to the unit of this entry. */
        void setPost(@Nullable BlockPos post) {
            this.post = post == null ? null : post.immutable();
        }

        void markDead(long now) {
            if (!dead) {
                dead = true;
                deadSince = now;
            }
        }
    }

    /**
     * Gives a new post to the entry of a unit.
     *
     * @param unitId the id of the unit
     * @param post   the new post
     */
    public void setPost(UUID unitId, BlockPos post) {
        for (Entry entry : entries) {
            if (entry.unitId.equals(unitId)) {
                entry.setPost(post);
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
     *   <li>Updates the entries: last seen time and position of the loaded units, timeout of missing units. A
     *       loaded unit with another equipment set gets the set of the level.</li>
     *   <li>Removes the dead entries above the number of units of each role.</li>
     *   <li>Adds entries until each role has its number of units. A new garrison spawns completely.</li>
     *   <li>Spawns new units for the dead ones after the refill delay, if the village is calm.</li>
     * </ol>
     *
     * @param level     the level of the heart
     * @param heartPos  the position of the heart
     * @param factionId the faction of the village, or null
     * @param calm      true if the village is calm
     * @param now       the current game time
     * @param posts     the posts of the village for each role that has posts: the towers for the archers, the gates
     *                  for the golems
     * @param traits    the garrison of the level of the village
     * @return true if the garrison changed (the heart must save)
     */
    public boolean ensure(ServerLevel level, BlockPos heartPos, @Nullable String factionId, boolean calm, long now,
            Map<GarrisonRole, List<BlockPos>> posts, GarrisonTraits traits) {
        boolean changed = false;
        for (Entry entry : entries) {
            if (entry.dead) {
                continue;
            }
            Entity entity = level.getEntity(entry.unitId);
            if (entity != null && entity.isAlive()) {
                entry.lastSeen = now;
                entry.lastPos = entity.blockPosition();
                if (entity instanceof FactionUnit unit && !unit.getEquipmentSet().equals(traits.equipment())) {
                    unit.equip(traits.equipment());
                }
            } else if (entity == null && entry.lastPos != null && level.isLoaded(entry.lastPos)
                    && now - entry.lastSeen > MISSING_TIMEOUT_TICKS) {
                entry.markDead(now);
                changed = true;
            }
        }

        for (GarrisonRole role : GarrisonRole.values()) {
            changed |= removeExtraDead(role, role.count(traits));
        }

        for (GarrisonRole role : GarrisonRole.values()) {
            int count = (int) entries.stream().filter(entry -> entry.role == role).count();
            for (int i = count; i < role.count(traits); i++) {
                BlockPos post = freePost(posts.getOrDefault(role, List.of()));
                FactionUnit unit = spawnUnit(level, heartPos, factionId, role, post);
                if (unit != null) {
                    Entry entry = new Entry(unit.getUUID(), role);
                    entry.post = post;
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
                    if (entry.post == null) {
                        entry.post = freePost(posts.getOrDefault(entry.role, List.of()));
                    }
                    FactionUnit unit = spawnUnit(level, heartPos, factionId, entry.role, entry.post);
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

    /**
     * Removes the dead entries of a role above a number of entries. The entries that are alive stay.
     *
     * @param role  the role
     * @param count the number of units of the role
     * @return true if the method removed an entry
     */
    private boolean removeExtraDead(GarrisonRole role, int count) {
        int extra = (int) entries.stream().filter(entry -> entry.role == role).count() - count;
        boolean removed = false;
        for (Iterator<Entry> it = entries.iterator(); extra > 0 && it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.role == role && entry.dead) {
                it.remove();
                extra--;
                removed = true;
            }
        }
        return removed;
    }

    /** A post that no entry uses, or null. */
    @Nullable
    private BlockPos freePost(List<BlockPos> posts) {
        for (BlockPos post : posts) {
            boolean used = entries.stream().anyMatch(entry -> post.equals(entry.post));
            if (!used) {
                return post;
            }
        }
        return null;
    }

    @Nullable
    private static FactionUnit spawnUnit(ServerLevel level, BlockPos heartPos, @Nullable String factionId,
            GarrisonRole role, @Nullable BlockPos post) {
        FactionUnit unit = role.entityType().create(level);
        if (unit == null) {
            return null;
        }
        unit.setFactionId(factionId);
        if (post != null) {
            unit.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            unit.setHomePos(post);
            unit.setPostPos(post);
            unit.setVillagePos(heartPos);
            EventHooks.finalizeMobSpawn(unit, level, level.getCurrentDifficultyAt(post), MobSpawnType.STRUCTURE, null);
            level.addFreshEntity(unit);
            return unit;
        }
        BlockPos pos = groundSpawnPos(level, heartPos);
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        unit.setHomePos(pos);
        unit.setVillagePos(heartPos);
        EventHooks.finalizeMobSpawn(unit, level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(unit);
        return unit;
    }

    /**
     * A spawn position on the ground of the village, near the heart. The position is under the open sky (not in a
     * house, not on a roof), at most {@link #SPAWN_MAX_HEIGHT} blocks above or below the heart, on a solid block,
     * with space for a unit.
     *
     * @param level    the level
     * @param heartPos the position of the heart
     * @return the spawn position, or a position next to the heart if there is no good position
     */
    public static BlockPos groundSpawnPos(ServerLevel level, BlockPos heartPos) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            float angle = level.random.nextFloat() * (float) (Math.PI * 2.0);
            int distance = Mth.nextInt(level.random, SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);
            int dx = Math.round(Mth.cos(angle) * distance);
            int dz = Math.round(Mth.sin(angle) * distance);
            for (int dy = SPAWN_MAX_HEIGHT; dy >= -SPAWN_MAX_HEIGHT; dy--) {
                BlockPos pos = heartPos.offset(dx, dy, dz);
                if (isGoodSpawnPos(level, pos)) {
                    return pos;
                }
            }
        }
        return heartPos.east(2);
    }

    private static boolean isGoodSpawnPos(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.canSeeSky(pos);
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
            if (entry.post != null) {
                tag.put("Post", NbtUtils.writeBlockPos(entry.post));
            }
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            Optional<GarrisonRole> role = GarrisonRole.fromSerializedName(tag.getString("Role"));
            if (role.isEmpty()) {
                FolkholdWorld.LOGGER.warn("Unknown garrison role '{}': the unit is not in the garrison",
                        tag.getString("Role"));
                continue;
            }
            if (!tag.hasUUID("Id")) {
                continue;
            }
            Entry entry = new Entry(tag.getUUID("Id"), role.get());
            entry.dead = tag.getBoolean("Dead");
            entry.deadSince = tag.getLong("DeadSince");
            entry.lastSeen = tag.getLong("LastSeen");
            entry.lastPos = NbtUtils.readBlockPos(tag, "LastPos").orElse(null);
            entry.post = NbtUtils.readBlockPos(tag, "Post").orElse(null);
            entries.add(entry);
        }
    }

    /** NBT type of the list elements. */
    public static final int TAG_TYPE = Tag.TAG_COMPOUND;
}
