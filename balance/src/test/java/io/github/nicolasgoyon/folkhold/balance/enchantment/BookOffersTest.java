package io.github.nicolasgoyon.folkhold.balance.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BookOffersTest {

    @Test
    void priceIsTwoPlusRollPlusThreeTimesTheLevel() {
        assertEquals(5, BookOffers.price(1, 0, false));
        assertEquals(2 + 14 + 3, BookOffers.price(1, 14, false));
        assertEquals(2 + 7 + 9, BookOffers.price(3, 7, false));
    }

    @Test
    void rollBoundIsFivePlusTenTimesTheLevel() {
        assertEquals(15, BookOffers.priceRollBound(1));
        assertEquals(55, BookOffers.priceRollBound(5));
    }

    @Test
    void doublePriceTagDoublesThePrice() {
        assertEquals(10, BookOffers.price(1, 0, true));
        assertEquals(2 * 19, BookOffers.price(1, 14, true));
    }

    @Test
    void priceIsAtMostSixtyFour() {
        assertEquals(64, BookOffers.price(5, 54, false));
        assertEquals(64, BookOffers.price(3, 30, true));
    }

    @Test
    void randomPriceStaysInTheVanillaRange() {
        Random random = new Random(42L);
        for (int level = 1; level <= 5; level++) {
            for (int i = 0; i < 1000; i++) {
                int price = BookOffers.price(level, random.nextInt(BookOffers.priceRollBound(level)), false);
                assertTrue(price >= 2 + 3 * level, "price " + price + " at level " + level);
                assertTrue(price <= Math.min(64, 2 + 4 + 10 * level + 3 * level), "price " + price);
            }
        }
    }

    @Test
    void replacementLevelStaysInTheRangeOfTheNewEnchantment() {
        assertEquals(1, BookOffers.replacementLevel(1, 1, 5));
        assertEquals(3, BookOffers.replacementLevel(3, 1, 5));
        assertEquals(1, BookOffers.replacementLevel(4, 1, 1));
        assertEquals(3, BookOffers.replacementLevel(5, 1, 3));
        assertEquals(2, BookOffers.replacementLevel(1, 2, 4));
    }
}
