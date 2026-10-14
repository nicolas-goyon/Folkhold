package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.GroupBoats;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the boats of the mobile groups. The arena has a pool of water between two banks. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoatGameTests {
    private static final BlockPos START = new BlockPos(1, 2, 8);
    /** The water is between these X values, and between Z 3 and Z 13. */
    private static final int WATER_MIN_X = 4;
    private static final int WATER_MAX_X = 12;

    private BoatGameTests() {
    }

    /** A floor, two banks of stone at Y 1, and water at Y 1 between them. The banks close the pool. */
    private static void buildPool(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int x = 0; x < TestSupport.ARENA_SIZE; x++) {
            for (int z = 0; z < TestSupport.ARENA_SIZE; z++) {
                boolean water = x >= WATER_MIN_X && x <= WATER_MAX_X && z >= 3 && z <= 13;
                helper.setBlock(new BlockPos(x, 1, z), water ? Blocks.WATER : Blocks.STONE);
            }
        }
    }

    /**
     * The merchant and the guards cross the pool in boats, the mule swims. At the end of the water segment, the
     * members get out on the other bank, and the boats disappear.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "group_boats")
    public static void caravanCrossesWaterInBoats(GameTestHelper helper) {
        buildPool(helper);
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + 3, start.getZ()), new Route.Point(start.getX() + 11, start.getZ()),
                new Route.Point(start.getX() + 30, start.getZ())),
                List.of(Route.Surface.LAND, Route.Surface.WATER, Route.Surface.LAND));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", "test_destination", route);
        group.setState(GroupState.TRAVELING);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        MobileGroupData.get(helper.getLevel().getServer()).groups().add(group);
        GroupMaterializer.keep(group.id(), true);
        GroupMaterializer.materialize(helper.getLevel(), group);
        Entity merchant = helper.getLevel().getEntity(group.members().get(0).id());
        Entity guard = helper.getLevel().getEntity(group.members().get(2).id());
        AtomicBoolean crossed = new AtomicBoolean();
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(TestSupport.ARENA_SIZE, 8,
                TestSupport.ARENA_SIZE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(GroupBoats.isCrossing(group.id())
                        && merchant.getVehicle() instanceof Boat && guard.getVehicle() instanceof Boat,
                        "The merchant and the guard must get into boats"))
                .thenExecute(() -> crossed.set(true))
                .thenWaitUntil(() -> helper.assertTrue(!GroupBoats.isCrossing(group.id()),
                        "The crossing must end. Merchant at " + merchant.position()))
                .thenExecute(() -> {
                    boolean onShore = !merchant.isPassenger() && !guard.isPassenger()
                            && merchant.getX() >= start.getX() + WATER_MAX_X - START.getX();
                    int boats = helper.getLevel().getEntitiesOfClass(Boat.class, arena).size();
                    GroupMaterializer.keep(group.id(), false);
                    GroupMaterializer.dematerialize(helper.getLevel(), group, false);
                    MobileGroupData.get(helper.getLevel().getServer()).groups().remove(group.id());
                    helper.assertTrue(crossed.get(), "The group must cross in boats");
                    helper.assertTrue(onShore, "The members must get out on the other bank: " + merchant.position());
                    helper.assertTrue(boats == 0, "The boats must disappear: " + boats);
                })
                .thenSucceed();
    }
}
