package io.github.nicolasgoyon.folkhold.core.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiRectTest {
    private static final UiRect RECT = new UiRect(10, 20, 100, 50);

    @Test
    void edgesAndCenter() {
        assertEquals(110, RECT.right());
        assertEquals(70, RECT.bottom());
        assertEquals(60, RECT.centerX());
        assertEquals(45, RECT.centerY());
        assertTrue(RECT.contains(10, 20));
        assertFalse(RECT.contains(110, 20), "The right edge is outside");
        assertFalse(RECT.contains(50, 70), "The bottom edge is outside");
    }

    @Test
    void sizeIsNeverNegative() {
        assertEquals(0, new UiRect(0, 0, -5, -1).width());
        assertEquals(0, RECT.inset(60).width());
        assertEquals(0, RECT.belowTop(40, 20).height());
    }

    @Test
    void cutsGiveThePartsAndTheRest() {
        assertEquals(new UiRect(12, 24, 96, 42), RECT.inset(2, 4));
        assertEquals(new UiRect(10, 20, 100, 12), RECT.top(12));
        assertEquals(new UiRect(10, 54, 100, 16), RECT.bottomPart(16));
        assertEquals(new UiRect(10, 20, 30, 50), RECT.left(30));
        assertEquals(new UiRect(80, 20, 30, 50), RECT.rightPart(30));
        assertEquals(new UiRect(10, 34, 100, 36), RECT.belowTop(12, 2));
        assertEquals(new UiRect(44, 20, 66, 50), RECT.afterLeft(30, 4));
        assertEquals(new UiRect(10, 20, 66, 50), RECT.beforeRight(30, 4));
    }

    @Test
    void columnsAndRowsFillTheRectangle() {
        UiRect first = RECT.column(0, 3, 2);
        UiRect last = RECT.column(2, 3, 2);
        assertEquals(new UiRect(10, 20, 32, 50), first);
        assertEquals(110, last.right(), "The last column ends at the right edge");
        assertEquals(new UiRect(10, 20, 100, 24), RECT.row(0, 2, 2));
        assertEquals(70, RECT.row(1, 2, 2).bottom(), "The last row ends at the bottom edge");
    }

    @Test
    void centeredAndPanel() {
        assertEquals(new UiRect(50, 40, 20, 10), RECT.centered(20, 10));
        assertEquals(RECT, RECT.centered(500, 500));
        assertEquals(new UiRect(70, 25, 340, 220), UiRect.panel(480, 270, 340, 220, 6));
        assertEquals(new UiRect(6, 6, 308, 228), UiRect.panel(320, 240, 480, 270, 6),
                "At the minimum screen size, the panel fills the screen less the margin");
    }
}
