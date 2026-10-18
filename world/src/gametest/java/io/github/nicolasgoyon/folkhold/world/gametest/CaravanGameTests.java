package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.group.CaravanCargo;
import io.github.nicolasgoyon.folkhold.world.group.Caravans;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the units of a caravan: the merchant walks on the route, the mules follow the merchant, the
 * guards stay near the merchant. The groups of the tests keep their entities with no player near. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaravanGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 8);
    private static final int ROUTE_LENGTH = 14;
    /** Size of the template {@code arena_large}, in blocks. */
    private static final int LARGE_ARENA = 32;

    private CaravanGameTests() {
    }

    /** A caravan on a route along the arena, with a merchant, a mule and a guard. Its entities stay. */
    private static MobileGroup caravan(GameTestHelper helper, GroupState state) {
        return caravan(helper, state, Map.of());
    }

    /** A caravan with a cargo. */
    private static MobileGroup caravan(GameTestHelper helper, GroupState state, Map<String, Integer> cargo) {
        TestSupport.buildFloor(helper);
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + ROUTE_LENGTH, start.getZ())));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", "test_destination", route);
        group.setState(state);
        cargo.forEach((item, count) -> group.cargo().set(item, count));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        MobileGroupData.get(helper.getLevel().getServer()).groups().add(group);
        GroupMaterializer.keep(group.id(), true);
        GroupMaterializer.materialize(helper.getLevel(), group);
        return group;
    }

    private static void remove(GameTestHelper helper, MobileGroup group) {
        GroupMaterializer.keep(group.id(), false);
        GroupMaterializer.dematerialize(helper.getLevel(), group, false);
        MobileGroupData.get(helper.getLevel().getServer()).groups().remove(group.id());
    }

    private static Entity entity(GameTestHelper helper, MobileGroup group, int index) {
        return helper.getLevel().getEntity(group.members().get(index).id());
    }

    /** The merchant of a traveling caravan walks on the route. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "caravan_walk")
    public static void merchantWalksOnTheRoute(GameTestHelper helper) {
        MobileGroup group = caravan(helper, GroupState.TRAVELING);
        Entity merchant = entity(helper, group, 0);
        BlockPos start = helper.absolutePos(START);
        helper.assertTrue(merchant != null, "The caravan must have its merchant");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(merchant.getX() >= start.getX() + ROUTE_LENGTH / 2.0,
                        "The merchant must walk on the route: " + merchant.getX()))
                .thenExecute(() -> remove(helper, group))
                .thenSucceed();
    }

    /** The mule of a caravan goes back near the merchant. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "caravan_mule")
    public static void muleFollowsTheMerchant(GameTestHelper helper) {
        MobileGroup group = caravan(helper, GroupState.STOPPED);
        Entity merchant = entity(helper, group, 0);
        Entity mule = entity(helper, group, 1);
        BlockPos far = helper.absolutePos(START.east(11));
        mule.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(mule.distanceTo(merchant) < 6.0,
                        "The mule must follow the merchant: " + mule.distanceTo(merchant)))
                .thenExecute(() -> remove(helper, group))
                .thenSucceed();
    }

    /** The home position of a guard goes to the position of the merchant. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "caravan_guard")
    public static void guardHomeFollowsTheMerchant(GameTestHelper helper) {
        MobileGroup group = caravan(helper, GroupState.STOPPED);
        Entity merchant = entity(helper, group, 0);
        Entity guard = entity(helper, group, 2);
        BlockPos far = helper.absolutePos(START.east(10));
        merchant.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);

        GroupMaterializer.update(helper.getLevel().getServer());

        boolean kept = GroupMaterializer.isMaterialized(group);
        BlockPos home = guard instanceof GuardEntity unit ? unit.getHomePos() : null;
        remove(helper, group);
        helper.assertTrue(kept, "A kept group must keep its entities with no player near");
        helper.assertTrue(far.equals(home), "The home of the guard must be the merchant position: " + home);
        helper.succeed();
    }

    /**
     * The merchant waits while a member of its group is far behind. When the member is near again, the merchant
     * continues.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "caravan_wait")
    public static void merchantWaitsForAMemberFarBehind(GameTestHelper helper) {
        MobileGroup group = caravan(helper, GroupState.TRAVELING);
        Entity merchant = entity(helper, group, 0);
        Entity guard = entity(helper, group, 2);
        BlockPos start = helper.absolutePos(START);
        BlockPos far = helper.absolutePos(new BlockPos(14, 1, 0));
        ((GuardEntity) guard).setNoAi(true);
        guard.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(merchant.getX() < start.getX() + 3.0,
                            "The merchant must wait for the guard: " + merchant.getX());
                    guard.teleportTo(merchant.getX(), merchant.getY(), merchant.getZ());
                    ((GuardEntity) guard).setNoAi(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(merchant.getX() >= start.getX() + ROUTE_LENGTH / 2.0,
                        "The merchant must continue when the guard is near: " + merchant.getX()))
                .thenExecute(() -> remove(helper, group))
                .thenSucceed();
    }

    /**
     * The end of the route is far from the heart of the destination. The merchant goes to the heart, and the group
     * arrives near the heart at once: the group leaves the registry, the entities stay in the village, the mule
     * chests are empty, and the stock of the destination gets the cargo.
     */
    @GameTest(template = "arena", timeoutTicks = 800, batch = "caravan_arrival")
    public static void caravanArrivesAtTheDestinationHeart(GameTestHelper helper) {
        MobileGroup group = caravan(helper, GroupState.TRAVELING, Map.of("minecraft:bread", 8));
        BlockPos heartPos = new BlockPos(13, 1, 13);
        helper.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(heartPos);
        heart.ensureSettlement();
        MinecraftServer server = helper.getLevel().getServer();
        Settlement destination = Settlements.of(server, heart).orElseThrow();
        int breadBefore = destination.economy().stock().count("minecraft:bread");
        BlockPos start = helper.absolutePos(START);
        group.setJourney("test_origin", destination.id(), new Route(List.of(
                new Route.Point(start.getX(), start.getZ()), new Route.Point(start.getX() + 3, start.getZ()))));
        Entity merchant = entity(helper, group, 0);
        Entity mule = entity(helper, group, 1);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GroupMaterializer.update(server);
                    helper.assertTrue(group.state() == GroupState.ARRIVED, "The caravan must arrive. Merchant at "
                            + merchant.blockPosition() + ", heart at " + helper.absolutePos(heartPos));
                })
                .thenExecute(() -> {
                    double distance = Math.sqrt(merchant.distanceToSqr(helper.absolutePos(heartPos).getCenter()));
                    boolean removed = MobileGroupData.get(server).groups().byId(group.id()).isEmpty();
                    boolean lingering = merchant.isAlive() && GroupMaterializer.isLingering(merchant);
                    boolean emptyChest = mule instanceof AbstractChestedHorse chested
                            && CaravanCargo.contents(chested).isEmpty();
                    int bread = destination.economy().stock().count("minecraft:bread");
                    GroupMaterializer.keep(group.id(), false);
                    merchant.discard();
                    mule.discard();
                    group.members().forEach(member -> {
                        Entity entity = helper.getLevel().getEntity(member.id());
                        if (entity != null) {
                            entity.discard();
                        }
                    });
                    destination.setState(SettlementState.RUINS);
                    SettlementData.get(server).setDirty();
                    helper.assertTrue(distance <= GroupMaterializer.ARRIVAL_RADIUS + 1.5,
                            "The merchant must be near the heart: " + distance);
                    helper.assertTrue(removed, "The arrived caravan must leave the registry at once");
                    helper.assertTrue(lingering, "The merchant must stay in the village after the arrival");
                    helper.assertTrue(emptyChest, "The mule chest must be empty");
                    helper.assertTrue(bread == breadBefore + 8, "The stock of the destination must get the 8 bread, "
                            + "found " + (bread - breadBefore));
                })
                .thenSucceed();
    }

    /**
     * A caravan leaves a walled area through a closed fence gate. The route crosses the wall at a position with no
     * gate. The merchant goes to the gate, opens it and goes through it, and the mule follows it out.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "caravan_gate_departure")
    public static void caravanLeavesThroughAClosedGate(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        // Walls of 3 blocks around x 8..14, z 3..12, and a closed gate of 3 fence gates in the west wall at z 9..11.
        walls(helper, 8, 14, 3, 12);
        BlockPos gate = new BlockPos(8, 1, 10);
        gate(helper, gate, Direction.WEST, false);
        BlockPos start = new BlockPos(11, 1, 5);
        MobileGroup group = groundCaravan(helper, "test_destination", start, new BlockPos(1, 1, 5));
        Entity merchant = entity(helper, group, 0);
        Entity mule = entity(helper, group, 1);
        int wallX = helper.absolutePos(gate).getX();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(merchant.getX() < wallX && mule.getX() < wallX,
                        "The merchant and the mule must go out through the gate. Merchant at "
                                + merchant.blockPosition() + ", mule at " + mule.blockPosition() + ", gate at "
                                + helper.absolutePos(gate)))
                .thenExecute(() -> remove(helper, group))
                .thenSucceed();
    }

    /**
     * A caravan arrives at a walled village from a side with no gate. The merchant goes around the wall and through
     * the gate to the heart, and the mule follows it into the village.
     */
    @GameTest(template = "arena_large", timeoutTicks = 1200, batch = "caravan_gate_arrival")
    public static void caravanGoesThroughAGateToTheHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper, LARGE_ARENA);
        // Walls of 3 blocks around x 10..30, z 6..26 (10 blocks from the heart), and a gate of 3 fence gates in the
        // north wall at x 19..21.
        walls(helper, 10, 30, 6, 26);
        gate(helper, new BlockPos(20, 1, 6), Direction.NORTH, false);
        BlockPos heartPos = new BlockPos(20, 1, 16);
        helper.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(heartPos);
        heart.ensureSettlement();
        MinecraftServer server = helper.getLevel().getServer();
        Settlement destination = Settlements.of(server, heart).orElseThrow();
        MobileGroup group = groundCaravan(helper, destination.id(), new BlockPos(2, 1, 18), new BlockPos(3, 1, 18));
        Entity merchant = entity(helper, group, 0);
        Entity mule = entity(helper, group, 1);
        BlockPos min = helper.absolutePos(new BlockPos(10, 1, 6));
        BlockPos max = helper.absolutePos(new BlockPos(30, 1, 26));

        helper.startSequence()
                .thenWaitUntil(() -> {
                    GroupMaterializer.update(server);
                    helper.assertTrue(group.state() == GroupState.ARRIVED && inside(mule, min, max),
                            "The caravan must arrive with its mule in the village. Merchant at "
                                    + merchant.blockPosition() + ", mule at " + mule.blockPosition());
                })
                .thenExecute(() -> {
                    GroupMaterializer.keep(group.id(), false);
                    merchant.discard();
                    mule.discard();
                    heart.getUnits().forEach(Entity::discard);
                    heart.getCats().forEach(Entity::discard);
                    destination.setState(SettlementState.RUINS);
                    SettlementData.get(server).setDirty();
                })
                .thenSucceed();
    }

    /** Walls of 3 blocks on the border of a rectangle, on the floor of the test. */
    private static void walls(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
                    }
                }
            }
        }
    }

    /**
     * A gate of 3 spruce fence gates in a wall, with its center at the position, and one block of air above it. A
     * gate of the village has 3 fence gates: a mule is wider than one block.
     */
    private static void gate(GameTestHelper helper, BlockPos center, Direction facing, boolean open) {
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos pos = center.relative(facing.getClockWise(), offset);
            helper.setBlock(pos, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing)
                    .setValue(FenceGateBlock.OPEN, open));
            helper.setBlock(pos.above(), Blocks.AIR);
        }
    }

    /**
     * A traveling caravan with a merchant and a mule on a route from {@code start} to {@code end}. The entities stand
     * on the floor of the test at the start: the materialization puts them on the highest block, and the GameTest
     * server can put a frame of barriers over the test.
     */
    private static MobileGroup groundCaravan(GameTestHelper helper, String destinationId, BlockPos start,
            BlockPos end) {
        BlockPos from = helper.absolutePos(start);
        BlockPos to = helper.absolutePos(end);
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", destinationId, new Route(List.of(new Route.Point(from.getX(), from.getZ()),
                        new Route.Point(to.getX(), to.getZ()))));
        group.setState(GroupState.TRAVELING);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        MobileGroupData.get(helper.getLevel().getServer()).groups().add(group);
        GroupMaterializer.keep(group.id(), true);
        GroupMaterializer.materialize(helper.getLevel(), group);
        for (int i = 0; i < group.members().size(); i++) {
            Entity entity = entity(helper, group, i);
            entity.teleportTo(from.getX() + 0.5 + i, from.getY(), from.getZ() + 0.5);
        }
        return group;
    }

    private static boolean inside(Entity entity, BlockPos min, BlockPos max) {
        return entity.getX() > Math.min(min.getX(), max.getX()) && entity.getX() < Math.max(min.getX(), max.getX()) + 1
                && entity.getZ() > Math.min(min.getZ(), max.getZ()) && entity.getZ() < Math.max(min.getZ(), max.getZ()) + 1
                && Math.abs(entity.getY() - min.getY()) < 2.0;
    }

    /** A caravan guard has the health of a village guard, with the health multiplier of the config. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "caravan_guard_health")
    public static void caravanGuardHasTheHealthOfAVillageGuard(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        double multiplier = WorldConfig.HEALTH_MULTIPLIER.get();
        WorldConfig.HEALTH_MULTIPLIER.set(2.0);
        try {
            Settlement origin = new Settlement(UUID.randomUUID().toString(), 0, 0, "test_faction", "Origin", 0, 0);
            Settlement destination = new Settlement(UUID.randomUUID().toString(), 10, 0, "test_faction",
                    "Destination", 160, 0);
            Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(160, 0)));
            MobileGroup caravan = Caravans.create(origin, destination, route, Map.of("minecraft:bread", 8), 10, 0);
            GuardEntity villageGuard = TestSupport.spawnGuard(helper, START);
            float caravanGuard = caravan.members().stream()
                    .filter(member -> member.role() == MemberRole.GUARD)
                    .findFirst().orElseThrow().health();
            float village = villageGuard.getMaxHealth();
            villageGuard.discard();
            helper.assertTrue(caravanGuard == village, "A caravan guard must have the health of a village guard: "
                    + caravanGuard + " and " + village);
        } finally {
            WorldConfig.HEALTH_MULTIPLIER.set(multiplier);
        }
        helper.succeed();
    }
}
