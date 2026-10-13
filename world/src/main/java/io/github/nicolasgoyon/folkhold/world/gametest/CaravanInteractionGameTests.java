package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.group.CaravanCargo;
import io.github.nicolasgoyon.folkhold.world.group.CaravanInteractions;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the trade with a caravan and the raid of a caravan. The groups of the tests keep their
 * entities with no player near. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaravanInteractionGameTests {
    private static final BlockPos START = new BlockPos(4, 1, 8);
    private static final String BREAD = "minecraft:bread";
    private static final String IRON = "minecraft:iron_ingot";

    private CaravanInteractionGameTests() {
    }

    /** A stopped caravan of a real faction with a merchant, a mule, a guard, 100 bread and 10 iron. */
    private static MobileGroup caravan(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String faction = FactionData.get(server).factions().get(0).id();
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + 10, start.getZ())));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, faction,
                "test_origin", "test_destination", route);
        group.setState(GroupState.STOPPED);
        group.setPayment(110);
        group.cargo().set(BREAD, 100);
        group.cargo().set(IRON, 10);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        MobileGroupData.get(server).groups().add(group);
        GroupMaterializer.keep(group.id(), true);
        GroupMaterializer.materialize(helper.getLevel(), group);
        return group;
    }

    private static void remove(GameTestHelper helper, MobileGroup group) {
        GroupMaterializer.keep(group.id(), false);
        GroupMaterializer.dematerialize(helper.getLevel(), group, false);
        MobileGroupData.get(helper.getLevel().getServer()).groups().remove(group.id());
    }

    private static AbstractChestedHorse mule(GameTestHelper helper, MobileGroup group) {
        return (AbstractChestedHorse) helper.getLevel().getEntity(group.members().get(1).id());
    }

    /** The mule chest holds the cargo. A theft from the chest makes the cargo and the payment smaller. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "caravan_cargo")
    public static void muleChestHoldsTheCargo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);
        AbstractChestedHorse mule = mule(helper, group);
        Map<String, Integer> contents = CaravanCargo.contents(mule);

        int stolen = CaravanCargo.remove(List.of(mule), Items.BREAD, 20);
        GroupMaterializer.update(level.getServer());
        int bread = group.cargo().count(BREAD);
        long payment = group.payment();

        remove(helper, group);
        helper.assertTrue(contents.equals(Map.of(BREAD, 100, IRON, 10)), "The chest must hold the cargo: " + contents);
        helper.assertTrue(stolen == 20 && bread == 80, "The theft must remove bread from the cargo: " + bread);
        helper.assertTrue(payment == 90, "The payment must get smaller in proportion: " + payment);
        helper.succeed();
    }

    /** The merchant sells the items of the cargo. A sale removes the items from the cargo. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "caravan_trade")
    public static void merchantSellsTheCargo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);

        MerchantOffers offers = CaravanInteractions.offers(level.getServer(), group, ReputationTier.NEUTRAL);
        boolean breadOffer = offers.stream().anyMatch(offer -> offer.getResult().is(Items.BREAD)
                && offer.getCostA().is(Items.EMERALD));
        int removed = GroupMaterializer.removeCargo(level, group, BREAD, 10);
        int bread = group.cargo().count(BREAD);
        int inChest = CaravanCargo.contents(mule(helper, group)).getOrDefault(BREAD, 0);

        remove(helper, group);
        helper.assertTrue(breadOffer, "The merchant must sell bread for emeralds");
        helper.assertTrue(offers.size() <= 6, "The merchant has at most 6 offers");
        helper.assertTrue(removed == 10 && bread == 90 && inChest == 90,
                "A sale must remove the bread from the chest and the cargo: " + bread + ", " + inChest);
        helper.succeed();
    }

    /** A raid lowers the reputation with the faction of the caravan once, and the guards attack the player. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "caravan_raid")
    public static void raidLowersTheReputation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        MobileGroup group = caravan(helper);
        FactionSpec faction = FactionData.get(server).byId(group.factionId()).orElseThrow();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(faction.id(), 0);

        CaravanInteractions.raid(level, player, group);
        CaravanInteractions.raid(level, player, group);
        int value = reputation.get(player.getUUID()).faction(faction);
        GuardEntity guard = (GuardEntity) level.getEntity(group.members().get(2).id());
        boolean attacks = guard != null && guard.getTarget() == player;

        server.getPlayerList().remove(player);
        remove(helper, group);
        helper.assertTrue(value == -150, "One raid in the window must lower the reputation once: " + value);
        helper.assertTrue(attacks, "The guard must attack the player");
        helper.succeed();
    }

    /** A dead mule drops its chest: its items leave the cargo. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "caravan_mule_death")
    public static void deadMuleLosesItsCargo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MobileGroup group = caravan(helper);

        mule(helper, group).kill();
        GroupMaterializer.update(level.getServer());
        int total = group.cargo().total();
        long payment = group.payment();

        remove(helper, group);
        helper.assertTrue(total == 0, "The cargo of the dead mule must leave the group: " + total);
        helper.assertTrue(payment == 0, "With no cargo, the payment must be 0: " + payment);
        helper.succeed();
    }
}
