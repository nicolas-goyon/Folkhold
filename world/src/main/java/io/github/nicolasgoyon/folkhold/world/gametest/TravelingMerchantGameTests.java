package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupSystem;
import io.github.nicolasgoyon.folkhold.world.group.TravelingMerchants;
import io.github.nicolasgoyon.folkhold.world.group.TravelingMerchants.RareItem;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the traveling merchants: rare items, offers, stay at a village, restock. See {@link TestSupport}
 * for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelingMerchantGameTests {
    private static final int FAR_X = 23_000_000;

    private TravelingMerchantGameTests() {
    }

    /** A traveling merchant far from the tests, with its rare items. */
    private static MobileGroup merchant() {
        Route route = new Route(List.of(new Route.Point(FAR_X, 0), new Route.Point(FAR_X + 500, 0)));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.TRAVELING_MERCHANT,
                "test_neutral", "test_origin", "test_destination", route);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 22.0F));
        for (RareItem rare : TravelingMerchants.itemsOf(group)) {
            group.cargo().set(rare.item(), rare.max());
        }
        return group;
    }

    /** The rare items exist, and each merchant has 6 different rare items that do not change. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "traveling_items")
    public static void eachMerchantHasSixRareItems(GameTestHelper helper) {
        for (RareItem rare : TravelingMerchants.RARE_ITEMS) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(rare.item())),
                    "The rare item must exist: " + rare.item());
            helper.assertTrue(rare.price() > 0 && rare.max() > 0, "A rare item needs a price and a maximum");
        }
        MobileGroup group = merchant();
        List<RareItem> items = TravelingMerchants.itemsOf(group);
        helper.assertTrue(items.size() == TravelingMerchants.ITEM_KINDS
                && new HashSet<>(items).size() == items.size(), "A merchant has 6 different rare items");
        helper.assertTrue(items.equals(TravelingMerchants.itemsOf(group)), "The items of a merchant do not change");
        helper.succeed();
    }

    /** The offers sell one rare item for its price in emeralds, with a discount for an ally. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "traveling_offers")
    public static void merchantSellsItsRareItems(GameTestHelper helper) {
        MobileGroup group = merchant();

        MerchantOffers neutral = TravelingMerchants.offers(group, ReputationTier.NEUTRAL);
        MerchantOffers ally = TravelingMerchants.offers(group, ReputationTier.ALLY);

        helper.assertTrue(neutral.size() == TravelingMerchants.ITEM_KINDS, "One offer for each rare item");
        for (int i = 0; i < neutral.size(); i++) {
            MerchantOffer offer = neutral.get(i);
            String item = BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).toString();
            RareItem rare = TravelingMerchants.rareItem(item).orElse(null);
            helper.assertTrue(rare != null && offer.getCostA().is(Items.EMERALD)
                    && offer.getCostA().getCount() == rare.price() && offer.getResult().getCount() == 1
                    && offer.getMaxUses() <= rare.max(), "The offer must sell one rare item for its price: " + item);
            helper.assertTrue(ally.get(i).getCostA().getCount() <= offer.getCostA().getCount(),
                    "An ally must not pay more");
        }
        helper.succeed();
    }

    /** At the end of its route, a traveling merchant stays near the village. It does not arrive. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "traveling_stay")
    public static void merchantStaysAtTheEndOfItsRoute(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MobileGroup group = merchant();
        group.setState(GroupState.TRAVELING);
        group.setProgress(group.route().length() - 1.0);
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(group);

        MobileGroupSystem.tick(server, new TickInfo(1000, false));

        boolean kept = data.groups().byId(group.id()).isPresent();
        data.groups().remove(group.id());
        helper.assertTrue(kept, "The traveling merchant must stay in the world");
        helper.assertTrue(group.state() == GroupState.STOPPED, "The traveling merchant must stop");
        helper.assertTrue(group.departureTick() == 1000 + WorldConfig.TRAVELING_STAY_TICKS.get(),
                "The traveling merchant must stay for the configured time: " + group.departureTick());
        helper.succeed();
    }

    /** A restock gives back one item of one offer, up to the maximum. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "traveling_restock")
    public static void restockGivesBackOneItem(GameTestHelper helper) {
        MobileGroup group = merchant();
        RareItem first = TravelingMerchants.itemsOf(group).get(0);
        group.cargo().set(first.item(), 0);
        int before = group.cargo().total();

        TravelingMerchants.restock(group, new SplittableRandom(7));

        helper.assertTrue(group.cargo().total() == before + 1 && group.cargo().count(first.item()) == 1,
                "The only missing item must come back");
        TravelingMerchants.restock(group, new SplittableRandom(7));
        helper.assertTrue(group.cargo().count(first.item()) <= first.max(), "The restock stops at the maximum");
        helper.succeed();
    }
}
