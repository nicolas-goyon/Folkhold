package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The boats of the mobile groups with entities on the water segments of their routes (see {@link Route.Surface}).
 *
 * <ul>
 *   <li>When the merchant is at the start of a water segment, the mod makes vanilla boats. The merchant and the first
 *       guard get into the first boat. The other guards get into the next boats, two in each boat. The mules swim
 *       behind the boats.</li>
 *   <li>Each tick, the first boat goes along the water segment, and the other boats follow it in a line. A mule that
 *       is far from the boats moves near the last boat.</li>
 *   <li>At the end of the water segment, the members get out on the shore, and the boats disappear. If the first
 *       boat does not go forward for {@link #STUCK_TICKS}, the members also go to the shore.</li>
 *   <li>The boats are not saved. A boat of a group that loads from a chunk does not join the level. A player cannot
 *       break or use a boat of a group.</li>
 *   <li>A stopped group (for example an escort with no player near) stays in its boats and does not move.</li>
 * </ul>
 */
public final class GroupBoats {
    /** Key in the persistent data of a boat: the id of its group. */
    public static final String BOAT_TAG = "folkhold_world:group_boat";
    /** Speed of the boats, in blocks per tick. A vanilla boat goes faster with a player. */
    static final double BOAT_SPEED = 0.25;
    /** Distance from the first boat to its target on the route, in blocks. */
    private static final double LOOKAHEAD = 8.0;
    /** The crossing starts when the merchant is at this distance or less from the start of the water segment. */
    private static final double START_DISTANCE = 3.0;
    /** The crossing ends when the first boat is at this distance or less from the end of the water segment. */
    private static final double END_DISTANCE = 2.0;
    /** Distance between two boats in the line, in blocks. */
    private static final double BOAT_SPACING = 3.0;
    /** A mule farther than this distance from the last boat moves near it, in blocks. */
    private static final double MULE_DISTANCE = 10.0;
    /** After this time with no progress of the first boat, the members go to the shore. */
    static final int STUCK_TICKS = 300;
    /** A progress below this distance in blocks is no progress. */
    private static final double MIN_PROGRESS = 1.0;
    /** The members get out on the shore at this distance after the end of the water segment, in blocks. */
    private static final double SHORE_DISTANCE = 3.0;
    /** Passengers in one boat. */
    private static final int SEATS = 2;

    /** The crossings on the way, by group id. Not saved. */
    private static final Map<String, Crossing> CROSSINGS = new HashMap<>();
    /** The UUIDs of the boats of the crossings. Not saved. */
    private static final Set<UUID> BOATS = new HashSet<>();

    /** One crossing of a water segment by a group. */
    private static final class Crossing {
        private final List<UUID> boats = new ArrayList<>();
        private final double endProgress;
        private double bestProgress = -1.0;
        private int stuckTicks;

        private Crossing(double endProgress) {
            this.endProgress = endProgress;
        }
    }

    private GroupBoats() {
    }

    /** True if the group crosses a water segment in boats now. */
    public static boolean isCrossing(String groupId) {
        return CROSSINGS.containsKey(groupId);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    /**
     * Starts, steers and ends the crossings of all groups with entities.
     *
     * @param server the server
     */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MobileGroupData data = MobileGroupData.get(server);
        for (String groupId : new ArrayList<>(CROSSINGS.keySet())) {
            Optional<MobileGroup> group = data.groups().byId(groupId);
            if (group.isEmpty() || group.get().isFinished() || !GroupMaterializer.isMaterialized(group.get())) {
                remove(level, groupId);
            }
        }
        for (MobileGroup group : data.groups().all()) {
            if (!GroupMaterializer.isMaterialized(group) || group.isFinished()) {
                continue;
            }
            Crossing crossing = CROSSINGS.get(group.id());
            if (crossing == null) {
                if (group.state() == GroupState.TRAVELING) {
                    startIfAtWater(level, group);
                }
            } else {
                steer(level, group, crossing);
            }
        }
    }

    /** Starts the crossing if the merchant is at the start of a water segment, or on it. */
    private static void startIfAtWater(ServerLevel level, MobileGroup group) {
        Optional<LivingEntity> merchant = merchant(level, group);
        if (merchant.isEmpty() || merchant.get().isPassenger()) {
            return;
        }
        Route route = group.route();
        double progress = route.progressNear(merchant.get().getX(), merchant.get().getZ());
        int segment = route.segmentAt(Math.min(route.length(), progress + START_DISTANCE));
        if (route.surfaces().get(segment) != Route.Surface.WATER
                || progress < route.progressOf(segment) - START_DISTANCE) {
            return;
        }
        start(level, group, Math.max(progress, route.progressOf(segment)), route.progressOf(segment + 1));
    }

    /**
     * Puts the members of a group into boats.
     *
     * @param level         the level
     * @param group         the group with entities
     * @param startProgress the progress where the boats appear
     * @param endProgress   the progress of the end of the water segment
     */
    static void start(ServerLevel level, MobileGroup group, double startProgress, double endProgress) {
        Route.Position position = group.route().positionAt(startProgress);
        int x = (int) Math.floor(position.x());
        int z = (int) Math.floor(position.z());
        if (!level.isLoaded(new BlockPos(x, Settlements.seaLevel(level), z))) {
            return;
        }
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Crossing crossing = new Crossing(endProgress);
        CROSSINGS.put(group.id(), crossing);
        List<LivingEntity> riders = new ArrayList<>();
        merchant(level, group).ifPresent(riders::add);
        for (LivingEntity member : GroupMaterializer.memberEntities(level, group)) {
            if (role(group, member) == MemberRole.GUARD) {
                riders.add(member);
            }
        }
        Boat boat = null;
        for (int i = 0; i < riders.size(); i++) {
            if (i % SEATS == 0) {
                boat = new Boat(level, x + 0.5, y, z + 0.5);
                boat.setVariant(Boat.Type.SPRUCE);
                boat.getPersistentData().putString(BOAT_TAG, group.id());
                BOATS.add(boat.getUUID());
                crossing.boats.add(boat.getUUID());
                level.addFreshEntity(boat);
            }
            LivingEntity rider = riders.get(i);
            if (rider instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            rider.startRiding(boat, true);
        }
    }

    /** Moves the boats of a crossing, and ends the crossing at the end of the water segment. */
    private static void steer(ServerLevel level, MobileGroup group, Crossing crossing) {
        List<Boat> boats = new ArrayList<>();
        for (UUID id : crossing.boats) {
            if (level.getEntity(id) instanceof Boat boat && boat.isAlive()) {
                boats.add(boat);
            }
        }
        if (boats.isEmpty()) {
            end(level, group, crossing);
            return;
        }
        Boat lead = boats.get(0);
        if (group.state() != GroupState.TRAVELING) {
            boats.forEach(boat -> boat.setDeltaMovement(0.0, boat.getDeltaMovement().y, 0.0));
            return;
        }
        Route route = group.route();
        double progress = route.progressNear(lead.getX(), lead.getZ());
        if (progress > crossing.bestProgress + MIN_PROGRESS) {
            crossing.bestProgress = progress;
            crossing.stuckTicks = 0;
        } else {
            crossing.stuckTicks++;
        }
        if (progress >= crossing.endProgress - END_DISTANCE || crossing.stuckTicks > STUCK_TICKS) {
            end(level, group, crossing);
            return;
        }
        Route.Position target = route.positionAt(Math.min(progress + LOOKAHEAD, crossing.endProgress));
        Vec3 heading = drive(lead, target.x(), target.z(), BOAT_SPEED);
        for (int i = 1; i < boats.size(); i++) {
            Boat previous = boats.get(i - 1);
            double targetX = previous.getX() - heading.x * BOAT_SPACING;
            double targetZ = previous.getZ() - heading.z * BOAT_SPACING;
            Boat boat = boats.get(i);
            double distance = Math.hypot(targetX - boat.getX(), targetZ - boat.getZ());
            drive(boat, targetX, targetZ, Math.min(BOAT_SPEED * 1.4, distance * 0.2));
        }
        Boat last = boats.get(boats.size() - 1);
        for (LivingEntity member : GroupMaterializer.memberEntities(level, group)) {
            if (role(group, member) == MemberRole.PACK_ANIMAL && member.distanceTo(last) > MULE_DISTANCE) {
                member.teleportTo(last.getX() - heading.x * BOAT_SPACING, last.getY(),
                        last.getZ() - heading.z * BOAT_SPACING);
            }
        }
    }

    /**
     * Turns a boat to a target and sets its speed.
     *
     * @return the unit vector from the boat to the target (no vertical part)
     */
    private static Vec3 drive(Boat boat, double targetX, double targetZ, double speed) {
        double dx = targetX - boat.getX();
        double dz = targetZ - boat.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0e-3) {
            boat.setDeltaMovement(0.0, boat.getDeltaMovement().y, 0.0);
            return new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 heading = new Vec3(dx / distance, 0.0, dz / distance);
        // The forward direction of a boat is (-sin(yaw), cos(yaw)).
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        boat.setYRot(yaw);
        boat.setDeltaMovement(heading.x * speed, boat.getDeltaMovement().y, heading.z * speed);
        return heading;
    }

    /** The members get out on the shore after the water segment, and the boats disappear. */
    private static void end(ServerLevel level, MobileGroup group, Crossing crossing) {
        Route route = group.route();
        Route.Position shore = route.positionAt(Math.min(route.length(), crossing.endProgress + SHORE_DISTANCE));
        int x = (int) Math.floor(shore.x());
        int z = (int) Math.floor(shore.z());
        boolean loaded = level.isLoaded(new BlockPos(x, Settlements.seaLevel(level), z));
        int y = loaded ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : 0;
        int index = 0;
        for (UUID id : crossing.boats) {
            Entity boat = level.getEntity(id);
            if (boat == null) {
                continue;
            }
            for (Entity passenger : List.copyOf(boat.getPassengers())) {
                passenger.stopRiding();
                if (loaded) {
                    // The passengers land on a grid of three columns around the shore position.
                    int column = index % 3;
                    int row = index / 3;
                    passenger.teleportTo(x + 0.5 + column - 1, y, z + 0.5 + row - 1);
                }
                index++;
            }
        }
        if (loaded) {
            for (LivingEntity member : GroupMaterializer.memberEntities(level, group)) {
                if (role(group, member) == MemberRole.PACK_ANIMAL) {
                    member.teleportTo(x + 0.5, y, z + 0.5);
                }
            }
        }
        remove(level, group.id());
    }

    /**
     * Removes the boats of a group. The passengers get out where they are.
     *
     * @param level   the level
     * @param groupId the id of the group
     */
    public static void remove(ServerLevel level, String groupId) {
        Crossing crossing = CROSSINGS.remove(groupId);
        if (crossing == null) {
            return;
        }
        for (UUID id : crossing.boats) {
            BOATS.remove(id);
            Entity boat = level.getEntity(id);
            if (boat != null) {
                boat.ejectPassengers();
                boat.discard();
            }
        }
    }

    /** A boat of a group that is not in a crossing does not join the level. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.getPersistentData().contains(BOAT_TAG) && !BOATS.contains(entity.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** A player cannot break a boat of a group. */
    public static void onAttack(AttackEntityEvent event) {
        if (isGroupBoat(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /** A player cannot get into a boat of a group. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (isGroupBoat(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CROSSINGS.clear();
        BOATS.clear();
    }

    /** True if the entity is a boat of a group. */
    public static boolean isGroupBoat(Entity entity) {
        return entity.getPersistentData().contains(BOAT_TAG);
    }

    private static Optional<LivingEntity> merchant(ServerLevel level, MobileGroup group) {
        return group.merchant().map(member -> level.getEntity(member.id()))
                .filter(entity -> entity instanceof LivingEntity living && living.isAlive())
                .map(entity -> (LivingEntity) entity);
    }

    private static MemberRole role(MobileGroup group, Entity entity) {
        return group.member(entity.getUUID()).map(GroupMember::role).orElse(null);
    }
}
