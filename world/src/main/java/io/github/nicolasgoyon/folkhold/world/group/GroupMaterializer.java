package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.Item;
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
 *       and the health of the entities sets the health of the members.</li>
 *   <li>The group arrives (see {@link MobileGroupSystem#onEndOfRoute(MobileGroup, long)}) when its merchant is at
 *       {@link #ARRIVAL_RADIUS} blocks or less from the heart of the destination. If the heart of the destination is
 *       not known, the group arrives when its merchant is at the end of the route.</li>
 *   <li>Each second, the home position of the guards of a group goes to the position of its merchant.</li>
 *   <li>The entities of an arrived group stay in the village for some time (see {@link #release}). Then they
 *       disappear when no player is near, or after {@link #LINGER_MAX_TICKS}.</li>
 *   <li>When no player is in the materialization distance plus a margin, the mod writes back the state of the
 *       members and removes the entities (except for a kept group, see {@link #keep(String, boolean)}). It also
 *       does this when the chunk of one entity unloads, and when the server stops.</li>
 *   <li>A dead entity records the death of its member (see {@link MobileGroup#onMemberDied(UUID)}).</li>
 *   <li>The cargo of a caravan with entities is in the chests of its mules (see {@link CaravanCargo}).</li>
 *   <li>On a water segment of the route, the members cross in boats (see {@link GroupBoats}).</li>
 *   <li>An entity of a group that loads from a chunk while its group has no entities does not join the level. So the
 *       entities of a group do not duplicate.</li>
 * </ul>
 */
public final class GroupMaterializer {
    /** Key in the persistent data of an entity: the id of its group. */
    public static final String GROUP_TAG = "folkhold_world:group";
    /** Game ticks between two checks. */
    private static final int CHECK_INTERVAL = 20;
    /** With no known heart, the group arrives when its merchant is nearer than this distance to the end of the route. */
    private static final double ARRIVAL_DISTANCE = 3.0;
    /** The group arrives when its merchant is nearer than this horizontal distance to the destination heart. */
    public static final double ARRIVAL_RADIUS = 8.0;
    /** The entities of an arrived group stay at least this time, in game ticks. */
    static final long LINGER_MIN_TICKS = 600;
    /** The entities of an arrived group disappear after this time, in game ticks, also near a player. */
    static final long LINGER_MAX_TICKS = 3600;
    /** After {@link #LINGER_MIN_TICKS}, an entity of an arrived group disappears when no player is this near. */
    private static final double LINGER_SEEN_DISTANCE = 32.0;
    /** The merchant of an arrived group walks at this distance or less from the heart, in blocks. */
    private static final int LINGER_RADIUS = 10;
    /** Distance in blocks between the spawn positions of two members. */
    private static final int SPACING = 2;

    /** The UUIDs of the entities of each group with entities, by group id. Not saved. */
    private static final Map<String, Set<UUID>> MATERIALIZED = new HashMap<>();
    /** The ids of the groups that keep their entities with no player near. Not saved. */
    private static final Set<String> KEPT = new HashSet<>();
    /** The cargo of each group with entities that is not in the mule chests, by group id. Not saved. */
    private static final Map<String, Map<String, Integer>> OVERFLOW = new HashMap<>();
    /** The entities of the arrived groups that stay in the village, with their arrival game time. Not saved. */
    private static final Map<UUID, Long> LINGERING = new HashMap<>();

    private GroupMaterializer() {
    }

    /**
     * Keeps the entities of a group with no player near, or ends this. For example an escorted caravan, or a test.
     *
     * @param groupId the id of the group
     * @param keep    true to keep the entities
     */
    public static void keep(String groupId, boolean keep) {
        if (keep) {
            KEPT.add(groupId);
        } else {
            KEPT.remove(groupId);
        }
    }

    /** True if the group keeps its entities with no player near. */
    public static boolean isKept(String groupId) {
        return KEPT.contains(groupId);
    }

    /** True if the group has entities now. */
    public static boolean isMaterialized(MobileGroup group) {
        return MATERIALIZED.containsKey(group.id());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % CHECK_INTERVAL == 0) {
            SimulationProfiler.time("materialization (each second)", () -> update(server));
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
        updateLingering(level, players);
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
            if (materialized && nearest > removeDistance && !KEPT.contains(group.id())) {
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
        int centerX = (int) Math.floor(position.x());
        int centerZ = (int) Math.floor(position.z());
        if (!level.hasChunk(centerX >> 4, centerZ >> 4)) {
            return false;
        }
        BlockPos center = ground(level, centerX, centerZ);
        if (!level.isPositionEntityTicking(center)) {
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
        OVERFLOW.put(group.id(), CaravanCargo.load(group.cargo().asMap(), mules(level, group)));
        return true;
    }

    /**
     * The loaded mules of the living pack animals of a group with entities.
     *
     * @param level the level
     * @param group the group
     * @return the mules
     */
    public static List<AbstractChestedHorse> mules(ServerLevel level, MobileGroup group) {
        List<AbstractChestedHorse> mules = new ArrayList<>();
        Set<UUID> ids = MATERIALIZED.getOrDefault(group.id(), Set.of());
        for (GroupMember member : group.members()) {
            if (member.role() == MemberRole.PACK_ANIMAL && member.isAlive() && ids.contains(member.id())
                    && level.getEntity(member.id()) instanceof AbstractChestedHorse mule && mule.isAlive()) {
                mules.add(mule);
            }
        }
        return mules;
    }

    /**
     * Removes items from the cargo of a group: from the mule chests first if the group has entities, then from the
     * rest of the cargo. The payment of the group gets smaller in proportion.
     *
     * @param level the level
     * @param group the group
     * @param item  the item id
     * @param count the quantity
     * @return the quantity that the cargo had
     */
    public static int removeCargo(ServerLevel level, MobileGroup group, String item, int count) {
        if (!isMaterialized(group)) {
            int before = group.cargo().total();
            int removed = group.cargo().remove(item, count);
            if (before > 0) {
                group.setPayment(Math.round(group.payment() * (double) (before - removed) / before));
            }
            MobileGroupData.get(level.getServer()).setDirty();
            return removed;
        }
        int removed = 0;
        ResourceLocation location = ResourceLocation.tryParse(item);
        if (location != null) {
            Item type = BuiltInRegistries.ITEM.get(location);
            removed = CaravanCargo.remove(mules(level, group), type, count);
        }
        Map<String, Integer> overflow = OVERFLOW.get(group.id());
        if (removed < count && overflow != null) {
            int fromOverflow = Math.min(overflow.getOrDefault(item, 0), count - removed);
            if (fromOverflow > 0) {
                overflow.merge(item, -fromOverflow, Integer::sum);
                overflow.remove(item, 0);
                removed += fromOverflow;
            }
        }
        sync(level, group);
        return removed;
    }

    /**
     * The living entities of the members of a group with entities.
     *
     * @param level the level
     * @param group the group
     * @return the entities, in the order of the members
     */
    public static List<LivingEntity> memberEntities(ServerLevel level, MobileGroup group) {
        List<LivingEntity> entities = new ArrayList<>();
        Set<UUID> ids = MATERIALIZED.getOrDefault(group.id(), Set.of());
        for (GroupMember member : group.members()) {
            if (member.isAlive() && ids.contains(member.id())
                    && level.getEntity(member.id()) instanceof LivingEntity living && living.isAlive()) {
                entities.add(living);
            }
        }
        return entities;
    }

    /**
     * The position of the heart of the destination of a group, if the heart is known.
     *
     * @param server the server
     * @param group  the group
     * @return the heart position
     */
    public static Optional<BlockPos> destinationHeart(MinecraftServer server, MobileGroup group) {
        return SettlementData.get(server).registry().byId(group.destinationId())
                .filter(Settlement::hasHeart)
                .map(settlement -> new BlockPos(settlement.x(), settlement.heartY(), settlement.z()));
    }

    /**
     * True if the merchant of a group is at the destination: near the destination heart, or at the end of the route
     * if the heart is not known.
     *
     * @param server   the server
     * @param group    the group, with its progress from the position of the merchant
     * @param merchant the merchant entity
     * @return true if the merchant is at the destination
     */
    public static boolean isAtDestination(MinecraftServer server, MobileGroup group, Entity merchant) {
        Optional<BlockPos> heart = destinationHeart(server, group);
        if (heart.isPresent()) {
            double dx = heart.get().getX() + 0.5 - merchant.getX();
            double dz = heart.get().getZ() + 0.5 - merchant.getZ();
            return dx * dx + dz * dz <= ARRIVAL_RADIUS * ARRIVAL_RADIUS;
        }
        return group.progress() >= group.route().length() - ARRIVAL_DISTANCE;
    }

    /**
     * Ends the link between an arrived group and its entities. The entities stay in the village for some time:
     *
     * <ul>
     *   <li>the mule chests are empty, because the cargo goes into the stock of the destination;</li>
     *   <li>the guards stay near the destination heart, and the merchant walks near it;</li>
     *   <li>the entities disappear after {@link #LINGER_MIN_TICKS} when no player is near, or after
     *       {@link #LINGER_MAX_TICKS};</li>
     *   <li>an entity that unloads does not come back, because its group does not exist.</li>
     * </ul>
     *
     * @param level the level
     * @param group the arrived group
     */
    public static void release(ServerLevel level, MobileGroup group) {
        GroupBoats.remove(level, group.id());
        Set<UUID> ids = MATERIALIZED.remove(group.id());
        OVERFLOW.remove(group.id());
        if (ids == null) {
            return;
        }
        Optional<BlockPos> heart = destinationHeart(level.getServer(), group);
        long now = level.getGameTime();
        for (UUID id : ids) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (mob instanceof AbstractChestedHorse mule) {
                CaravanCargo.clear(mule);
            }
            if (heart.isPresent()) {
                if (mob instanceof FactionUnit unit) {
                    unit.setHomePos(heart.get());
                } else {
                    mob.restrictTo(heart.get(), LINGER_RADIUS);
                }
            }
            LINGERING.put(id, now);
        }
        MobileGroupData.get(level.getServer()).setDirty();
    }

    /** True if the entity is an entity of an arrived group that stays in the village. */
    public static boolean isLingering(Entity entity) {
        return LINGERING.containsKey(entity.getUUID());
    }

    /** Removes the entities of the arrived groups at the end of their stay. */
    private static void updateLingering(ServerLevel level, List<ServerPlayer> players) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> iterator = LINGERING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            long age = now - entry.getValue();
            boolean seen = players.stream()
                    .anyMatch(player -> player.distanceToSqr(entity) < LINGER_SEEN_DISTANCE * LINGER_SEEN_DISTANCE);
            if (age >= LINGER_MAX_TICKS || (age >= LINGER_MIN_TICKS && !seen)) {
                entity.discard();
                iterator.remove();
            }
        }
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
        GroupBoats.remove(level, group.id());
        Set<UUID> ids = MATERIALIZED.remove(group.id());
        OVERFLOW.remove(group.id());
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
     * position of the merchant, and the arrival. The home position of each guard goes to the position of the
     * merchant.
     */
    static void sync(ServerLevel level, MobileGroup group) {
        Set<UUID> ids = MATERIALIZED.get(group.id());
        if (ids == null) {
            return;
        }
        LivingEntity merchant = null;
        for (UUID id : ids) {
            Optional<GroupMember> member = group.member(id);
            if (member.isEmpty() || !member.get().isAlive()
                    || !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            member.get().setHealth(living.getHealth());
            if (member.get().role() == MemberRole.MERCHANT) {
                merchant = living;
                group.setProgress(group.route().progressNear(living.getX(), living.getZ()));
                if (group.state() == GroupState.TRAVELING
                        && isAtDestination(level.getServer(), group, living)
                        && MobileGroupSystem.onEndOfRoute(group,
                                SettlementData.get(level.getServer()).tickNumber())) {
                    group.setState(GroupState.ARRIVED);
                }
            }
        }
        if (merchant != null) {
            for (UUID id : ids) {
                if (level.getEntity(id) instanceof FactionUnit guard && guard.isAlive()) {
                    guard.setHomePos(merchant.blockPosition());
                }
            }
        }
        syncCargo(level, group, ids);
        MobileGroupData.get(level.getServer()).setDirty();
    }

    /**
     * Copies the content of the mule chests to the cargo. When the cargo gets smaller, the payment of the group gets
     * smaller in proportion. If the entity of a living mule is not loaded, the cargo does not change.
     */
    private static void syncCargo(ServerLevel level, MobileGroup group, Set<UUID> ids) {
        Map<String, Integer> overflow = OVERFLOW.get(group.id());
        if (overflow == null) {
            return;
        }
        Map<String, Integer> cargo = new TreeMap<>(overflow);
        for (GroupMember member : group.members()) {
            if (member.role() != MemberRole.PACK_ANIMAL || !member.isAlive() || !ids.contains(member.id())) {
                continue;
            }
            if (!(level.getEntity(member.id()) instanceof AbstractChestedHorse mule) || !mule.isAlive()) {
                return;
            }
            CaravanCargo.contents(mule).forEach((item, count) -> cargo.merge(item, count, Integer::sum));
        }
        if (cargo.equals(group.cargo().asMap())) {
            return;
        }
        int before = group.cargo().total();
        for (String item : new ArrayList<>(group.cargo().asMap().keySet())) {
            group.cargo().set(item, 0);
        }
        cargo.forEach((item, count) -> group.cargo().set(item, count));
        int after = group.cargo().total();
        if (before > 0 && after < before) {
            group.setPayment(Math.round(group.payment() * (double) after / before));
        }
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
        for (UUID id : LINGERING.keySet()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        MATERIALIZED.clear();
        OVERFLOW.clear();
        LINGERING.clear();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MATERIALIZED.clear();
        KEPT.clear();
        OVERFLOW.clear();
        LINGERING.clear();
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

    /** The ground at a position. The chunk must be loaded: a height in a chunk that is not loaded loads it. */
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
        return level.hasChunk(x >> 4, z >> 4) ? ground(level, x, z) : center;
    }
}
