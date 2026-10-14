package io.github.nicolasgoyon.folkhold.world.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.config.TestConfig;
import io.github.nicolasgoyon.folkhold.world.group.TravelingMerchants.RareItem;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TravelingMerchantsTest {
    @BeforeAll
    static void loadConfig() {
        TestConfig.loadWorldConfig();
    }

    /** A traveling merchant with a full cargo of its rare items. */
    private static MobileGroup merchant() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(500, 0)));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.TRAVELING_MERCHANT,
                "test_neutral", "test_origin", "test_destination", route);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 22.0F));
        for (RareItem rare : TravelingMerchants.itemsOf(group)) {
            group.cargo().set(rare.item(), rare.max());
        }
        return group;
    }

    /** Each rare item exists, with a price and a maximum. A merchant has 6 different rare items that do not change. */
    @Test
    void eachMerchantHasSixRareItems() {
        for (RareItem rare : TravelingMerchants.RARE_ITEMS) {
            assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(rare.item())),
                    "The rare item must exist: " + rare.item());
            assertTrue(rare.price() > 0 && rare.max() > 0, "A rare item needs a price and a maximum");
        }
        MobileGroup group = merchant();
        List<RareItem> items = TravelingMerchants.itemsOf(group);
        assertEquals(TravelingMerchants.ITEM_KINDS, items.size());
        assertEquals(items.size(), new HashSet<>(items).size(), "The rare items of a merchant are different");
        assertEquals(items, TravelingMerchants.itemsOf(group), "The items of a merchant do not change");
    }

    /** The merchant sells one rare item for its price in emeralds. An ally does not pay more. */
    @Test
    void merchantSellsItsRareItems() {
        MobileGroup group = merchant();

        MerchantOffers neutral = TravelingMerchants.offers(group, ReputationTier.NEUTRAL);
        MerchantOffers ally = TravelingMerchants.offers(group, ReputationTier.ALLY);

        assertEquals(TravelingMerchants.ITEM_KINDS, neutral.size(), "One offer for each rare item");
        for (int i = 0; i < neutral.size(); i++) {
            MerchantOffer offer = neutral.get(i);
            String item = BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).toString();
            RareItem rare = TravelingMerchants.rareItem(item).orElseThrow();
            assertTrue(offer.getCostA().is(Items.EMERALD), "The price is in emeralds");
            assertEquals(rare.price(), offer.getCostA().getCount(), "The price of " + item);
            assertEquals(1, offer.getResult().getCount());
            assertTrue(offer.getMaxUses() <= rare.max(), "The uses stop at the maximum");
            assertTrue(ally.get(i).getCostA().getCount() <= offer.getCostA().getCount(), "An ally must not pay more");
        }
    }

    /** The restock gives back one missing item, up to the maximum of the item. */
    @Test
    void restockGivesBackOneItem() {
        MobileGroup group = merchant();
        RareItem first = TravelingMerchants.itemsOf(group).get(0);
        group.cargo().set(first.item(), 0);
        int before = group.cargo().total();

        TravelingMerchants.restock(group, new SplittableRandom(7));

        assertEquals(before + 1, group.cargo().total());
        assertEquals(1, group.cargo().count(first.item()), "The only missing item must come back");
        TravelingMerchants.restock(group, new SplittableRandom(7));
        assertTrue(group.cargo().count(first.item()) <= first.max(), "The restock stops at the maximum");
    }
}
