package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the materialization of the mobile groups. The tests call the materializer directly, so the
 * result does not depend on the players. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterializationGameTests {
    private static final BlockPos START = new BlockPos(2, 1, 8);

    private MaterializationGameTests() {
    }

    /** A caravan on the floor of the test, on a route of 40 blocks to the east: merchant, mule, two guards. */
    private static MobileGroup caravan(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + 40, start.getZ())));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", "test_destination", route);
        group.setState(GroupState.TRAVELING);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        MobileGroupData.get(helper.getLevel().getServer()).groups().add(group);
        return group;
    }

    private static void removeGroup(GameTestHelper helper, MobileGroup group) {
        GroupMaterializer.dematerialize(helper.getLevel(), group, false);
        MobileGroupData.get(helper.getLevel().getServer()).groups().remove(group.id());
    }

    /** The entities of the living members appear with the UUIDs of the members, and the same UUIDs come back. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "group_materialize")
    public static void materializationKeepsTheMemberIds(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);
        GroupMember deadGuard = group.members().get(3);
        deadGuard.setHealth(0.0F);

        helper.assertTrue(GroupMaterializer.materialize(level, group), "The group must get its entities");
        helper.assertTrue(GroupMaterializer.isMaterialized(group), "The group must be materialized");
        Entity merchant = level.getEntity(group.members().get(0).id());
        Entity mule = level.getEntity(group.members().get(1).id());
        Entity guard = level.getEntity(group.members().get(2).id());
        helper.assertTrue(merchant instanceof CaravanMerchant, "The merchant must be a caravan merchant");
        helper.assertTrue(mule instanceof Mule chested && chested.hasChest(),
                "The pack animal must be a mule with a chest");
        helper.assertTrue(guard instanceof GuardEntity unit && "test_faction".equals(unit.getFactionId())
                && unit.getVillagePos() == null, "The guard must have the faction of the group and no village");
        helper.assertTrue(level.getEntity(deadGuard.id()) == null, "A dead member must not appear");
        helper.assertTrue(group.id().equals(GroupMaterializer.groupId(merchant)), "An entity must have its group id");

        ((LivingEntity) guard).setHealth(12.0F);
        GroupMaterializer.dematerialize(level, group, true);
        helper.assertTrue(!GroupMaterializer.isMaterialized(group) && merchant.isRemoved() && guard.isRemoved(),
                "The entities must disappear");
        helper.assertTrue(group.members().get(2).health() == 12.0F, "The health of the guard must be written back");

        GroupMaterializer.materialize(level, group);
        Entity again = level.getEntity(group.members().get(2).id());
        boolean sameGuard = again instanceof GuardEntity && ((LivingEntity) again).getHealth() == 12.0F;
        removeGroup(helper, group);
        helper.assertTrue(sameGuard, "The guard must come back with its UUID and its health");
        helper.succeed();
    }

    /** An entity of a group with no entities does not join the level, so the group does not duplicate. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "group_duplicate")
    public static void entityOfAnAbstractGroupDoesNotJoin(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);
        Villager stale = EntityType.VILLAGER.create(level);
        BlockPos pos = helper.absolutePos(START);
        stale.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stale.setUUID(group.members().get(0).id());
        stale.getPersistentData().putString(GroupMaterializer.GROUP_TAG, group.id());

        level.addFreshEntity(stale);

        boolean joined = level.getEntity(stale.getUUID()) != null;
        removeGroup(helper, group);
        helper.assertTrue(!joined, "The entity of an abstract group must not join the level");
        helper.succeed();
    }

    /** A death in the world changes the group. The death of the merchant makes the group lost. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "group_death")
    public static void deathInTheWorldChangesTheGroup(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);
        GroupMaterializer.materialize(level, group);

        level.getEntity(group.members().get(2).id()).kill();
        boolean guardDead = !group.members().get(2).isAlive() && group.state() == GroupState.TRAVELING;
        level.getEntity(group.members().get(0).id()).kill();
        boolean lost = group.state() == GroupState.LOST;

        removeGroup(helper, group);
        helper.assertTrue(guardDead, "A dead guard must be dead in the group, and the group must travel");
        helper.assertTrue(lost, "The death of the merchant must make the group lost");
        helper.succeed();
    }

    /**
     * With no player near, the update removes the entities. The position of the merchant sets the progress of the
     * group.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "group_progress")
    public static void merchantPositionSetsTheProgress(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);
        GroupMaterializer.materialize(level, group);
        Entity merchant = level.getEntity(group.members().get(0).id());
        BlockPos start = helper.absolutePos(START);
        merchant.teleportTo(start.getX() + 20.5, merchant.getY(), start.getZ() + 0.5);

        GroupMaterializer.update(level.getServer());

        boolean removed = !GroupMaterializer.isMaterialized(group) && merchant.isRemoved();
        double progress = group.progress();
        removeGroup(helper, group);
        helper.assertTrue(removed, "With no player near, the entities must disappear");
        helper.assertTrue(Math.abs(progress - 20.5) < 1.0, "The progress must come from the merchant: " + progress);
        helper.succeed();
    }
}
