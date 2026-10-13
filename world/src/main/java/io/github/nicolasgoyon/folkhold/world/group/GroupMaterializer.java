package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Creates the entities of the mobile groups near the players, and removes them when no player is near.
 *
 * <ul>
 *   <li>Each second, the mod checks the distance from each group to the players of the Overworld.</li>
 *   <li>When a player is in the materialization distance and the chunk at the position of the group is loaded, the
 *       mod creates the entities of the living members (see {@link GroupEntities}). Each entity has the UUID of
 *       its member and the id of its group ({@link #GROUP_TAG}).</li>
 *   <li>While a group has entities, the simulation does not move it. The position of its merchant sets its progress,
 *       and the health of the entities sets the health of the members. When the merchant is at the end of the
 *       route, the group arrives.</li>
 *   <li>When no player is in the materialization distance plus a margin, the mod writes back the state of the
 *       members and removes the entities. It also does this when the chunk of one entity unloads, and when the
 *       server stops.</li>
 *   <li>A dead entity records the death of its member (see {@link MobileGroup#onMemberDied(UUID)}).</li>
 *   <li>An entity of a group that loads from a chunk while its group has no entities does not join the level. So the
 *       entities of a group do not duplicate.</li>
 * </ul>
 */
public final class GroupMaterializer {
    /** Key in the persistent data of an entity: the id of its group. */
    public static final String GROUP_TAG = "folkhold_world:group";
    /** Game ticks between two checks. */
    private static final int CHECK_INTERVAL = 20;
    /** The group arrives when its merchant is nearer than this distance to the end of the route, in blocks. */
    private static final double ARRIVAL_DISTANCE = 3.0;
    /** Distance in blocks between the spawn positions of two members. */
    private static final int SPACING = 2;

    /** The UUIDs of the entities of each group with entities, by group id. Not saved. */
    private static final Map<String, Set<UUID>> MATERIALIZED = new HashMap<>();

    private GroupMaterializer() {
    }

    /** True if the group has entities now. */
    public static boolean isMaterialized(MobileGroup group) {
        return MATERIALIZED.containsKey(group.id());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % CHECK_INTERVAL == 0) {
            update(server);
        }
    }

    /**
     * Checks all groups now: creates, updates or removes their entities.
     *
     * @param server the server
     */
    public static void update(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MobileGroupData data = MobileGroupData.get(server);
        double distance = WorldConfig.GROUP_MATERIALIZE_DISTANCE.get();
        double removeDistance = distance + WorldConfig.GROUP_DEMATERIALIZE_MARGIN.get();
        List<ServerPlayer> players = level.players().stream().filter(player -> !player.isSpectator()).toList();
        for (MobileGroup group : new ArrayList<>(data.groups().all())) {
            boolean materialized = isMaterialized(group);
            if (group.isFinished()) {
                if (materialized) {
                    dematerialize(level, group, false);
                }
                continue;
            }
            Route.Position position = group.position();
            double nearest = nearestPlayer(players, position);
            if (materialized && nearest > removeDistance) {
                dematerialize(level, group, true);
            } else if (materialized) {
                sync(level, group);
            } else if (nearest <= distance) {
                materialize(level, group);
            }
        }
    }

    /**
     * Creates the entities of the living members of a group, near the position of the group.
     *
     * @param level the level
     * @param group the group
     * @return true if the group has entities now
     */
    public static boolean materialize(ServerLevel level, MobileGroup group) {
        if (isMaterialized(group)) {
            return true;
        }
        Route.Position position = group.position();
        BlockPos center = ground(level, (int) Math.floor(position.x()), (int) Math.floor(position.z()));
        if (!level.isLoaded(center) || !level.isPositionEntityTicking(center)) {
            return false;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        MATERIALIZED.put(group.id(), ids);
        int index = 0;
        for (GroupMember member : group.members()) {
            if (!member.isAlive()) {
                continue;
            }
            Entity stale = level.getEntity(member.id());
            if (stale != null) {
                stale.discard();
            }
            BlockPos pos = spawnPos(level, center, index++);
            Mob mob = GroupEntities.create(level, group, member, pos);
            if (mob == null) {
                continue;
            }
            mob.setUUID(member.id());
            mob.getPersistentData().putString(GROUP_TAG, group.id());
            ids.add(member.id());
            level.addFreshEntity(mob);
        }
        return true;
    }

    /**
     * Writes back the state of the members of a group and removes its entities.
     *
     * @param level     the level
     * @param group     the group
     * @param writeBack true to write back the health of the members and the progress of the group
     */
    public static void dematerialize(ServerLevel level, MobileGroup group, boolean writeBack) {
        if (writeBack) {
            sync(level, group);
        }
        Set<UUID> ids = MATERIALIZED.remove(group.id());
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
        MobileGroupData.get(level.getServer()).setDirty();
    }

    /**
     * Copies the state of the entities of a group to the group: the health of the members, the progress from the
     * position of the merchant, and the arrival.
     */
    static void sync(ServerLevel level, MobileGroup group) {
        Set<UUID> ids = MATERIALIZED.get(group.id());
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Optional<GroupMember> member = group.member(id);
            if (member.isEmpty() || !member.get().isAlive()
                    || !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            member.get().setHealth(living.getHealth());
            if (member.get().role() == MemberRole.MERCHANT) {
                group.setProgress(group.route().progressNear(living.getX(), living.getZ()));
                if (group.state() == GroupState.TRAVELING
                        && group.progress() >= group.route().length() - ARRIVAL_DISTANCE) {
                    group.setState(GroupState.ARRIVED);
                }
            }
        }
        MobileGroupData.get(level.getServer()).setDirty();
    }

    /** An entity of a group with no entities does not join the level. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        String groupId = groupId(entity);
        if (groupId == null) {
            return;
        }
        Set<UUID> ids = MATERIALIZED.get(groupId);
        if (ids == null || !ids.contains(entity.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** When the chunk of an entity of a group unloads, the whole group goes back to the abstract state. */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        String groupId = groupId(entity);
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (groupId == null || reason == null || !reason.shouldSave() || !MATERIALIZED.containsKey(groupId)) {
            return;
        }
        MobileGroupData.get(level.getServer()).groups().byId(groupId).ifPresent(group -> {
            if (entity instanceof LivingEntity living) {
                group.member(entity.getUUID()).ifPresent(member -> member.setHealth(living.getHealth()));
            }
            dematerialize(level, group, true);
        });
    }

    /** A dead entity of a group records the death of its member. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        String groupId = groupId(event.getEntity());
        if (groupId == null) {
            return;
        }
        MobileGroupData data = MobileGroupData.get(level.getServer());
        data.groups().byId(groupId).ifPresent(group -> {
            group.onMemberDied(event.getEntity().getUUID());
            data.setDirty();
        });
    }

    /** Before the server saves the world, all groups go back to the abstract state. */
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        MobileGroupData data = MobileGroupData.get(event.getServer());
        for (MobileGroup group : new ArrayList<>(data.groups().all())) {
            if (isMaterialized(group)) {
                dematerialize(level, group, true);
            }
        }
        MATERIALIZED.clear();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MATERIALIZED.clear();
    }

    /** The id of the group of an entity, or null. */
    public static String groupId(Entity entity) {
        return entity.getPersistentData().contains(GROUP_TAG) ? entity.getPersistentData().getString(GROUP_TAG)
                : null;
    }

    private static double nearestPlayer(List<ServerPlayer> players, Route.Position position) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double dx = player.getX() - position.x();
            double dz = player.getZ() - position.z();
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
        }
        return nearest;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** The spawn position of the member with an index: a spiral around the center, on the ground. */
    private static BlockPos spawnPos(ServerLevel level, BlockPos center, int index) {
        if (index == 0) {
            return center;
        }
        double angle = index * 2.4;
        double radius = SPACING * Math.sqrt(index);
        int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
        BlockPos pos = ground(level, x, z);
        return level.isLoaded(pos) ? pos : center;
    }
}
