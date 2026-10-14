package io.github.nicolasgoyon.folkhold.world.gametest;

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
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.group.CaravanCargo;
import io.github.nicolasgoyon.folkhold.world.group.Caravans;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
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

    /** The number of guards comes from the level of the origin: 2, 3 from level 8, 4 from level 16. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "caravan_size")
    public static void caravanSizeFollowsTheLevelAndTheCargo(GameTestHelper helper) {
        helper.assertTrue(Caravans.guards(1) == 2 && Caravans.guards(7) == 2 && Caravans.guards(8) == 3
                && Caravans.guards(16) == 4 && Caravans.guards(20) == 4, "Guards from the level");
        helper.assertTrue(Caravans.mules(Map.of("minecraft:bread", 64)) == 1
                && Caravans.mules(Map.of("minecraft:bread", 64 * 16)) == 2
                && Caravans.mules(Map.of("minecraft:bread", 64 * 100)) == 3, "Mules from the cargo");
        helper.succeed();
    }
}
