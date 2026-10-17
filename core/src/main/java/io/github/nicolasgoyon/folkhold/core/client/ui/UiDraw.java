package io.github.nicolasgoyon.folkhold.core.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drawing helpers: frames, panels, cards, progress bars, badges and item icons. All sizes are in GUI pixels.
 */
public final class UiDraw {
    /** The length of a dash and of a gap in a dashed outline. */
    private static final int DASH = 2;

    private UiDraw() {
    }

    /** Fills a rectangle. */
    public static void fill(GuiGraphics graphics, UiRect rect, int color) {
        if (rect.width() > 0 && rect.height() > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        }
    }

    /** Draws a line of 1 pixel on the inner edge of a rectangle. */
    public static void outline(GuiGraphics graphics, UiRect rect, int color) {
        if (rect.width() > 0 && rect.height() > 0) {
            graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), color);
        }
    }

    /** Draws a dashed line of 1 pixel on the inner edge of a rectangle: a disabled or empty element. */
    public static void dashedOutline(GuiGraphics graphics, UiRect rect, int color) {
        for (int x = rect.x(); x < rect.right(); x += 2 * DASH) {
            int end = Math.min(x + DASH, rect.right());
            graphics.fill(x, rect.y(), end, rect.y() + 1, color);
            graphics.fill(x, rect.bottom() - 1, end, rect.bottom(), color);
        }
        for (int y = rect.y(); y < rect.bottom(); y += 2 * DASH) {
            int end = Math.min(y + DASH, rect.bottom());
            graphics.fill(rect.x(), y, rect.x() + 1, end, color);
            graphics.fill(rect.right() - 1, y, rect.right(), end, color);
        }
    }

    /**
     * Draws a box: a background and a border.
     *
     * @param dashed true for a dashed border (disabled, empty)
     */
    public static void box(GuiGraphics graphics, UiRect rect, int background, int border, boolean dashed) {
        fill(graphics, rect, background);
        if (dashed) {
            dashedOutline(graphics, rect, border);
        } else {
            outline(graphics, rect, border);
        }
    }

    /** Draws the frame of a screen panel: a dark outer line, a gilt line, and the wood inside. */
    public static void panel(GuiGraphics graphics, UiRect rect) {
        fill(graphics, rect.inset(-1), 0xFF0E0A06);
        fill(graphics, rect, UiTheme.WOOD);
        outline(graphics, rect, UiTheme.GILT);
    }

    /** Draws a horizontal line. */
    public static void hLine(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    /** Draws a vertical line. */
    public static void vLine(GuiGraphics graphics, int x, int y, int height, int color) {
        graphics.fill(x, y, x + 1, y + height, color);
    }

    /**
     * Draws a progress bar.
     *
     * @param fraction the filled part, 0 to 1
     */
    public static void bar(GuiGraphics graphics, UiRect rect, double fraction, int track, int fill) {
        fill(graphics, rect, track);
        int filled = (int) Math.round(rect.width() * Math.max(0.0, Math.min(1.0, fraction)));
        fill(graphics, rect.left(filled), fill);
    }

    /**
     * Draws a bar of segments, one for each unit: filled, empty or lost. For example the guards of a garrison: alive,
     * missing, dead.
     *
     * @param total  the number of segments
     * @param filled the number of filled segments, from the left
     * @param lost   the number of lost segments, after the filled segments
     */
    public static void segments(GuiGraphics graphics, UiRect rect, int total, int filled, int lost, int fillColor,
            int emptyColor) {
        if (total <= 0) {
            return;
        }
        int gap = total > 1 ? 1 : 0;
        for (int i = 0; i < total; i++) {
            UiRect cell = rect.column(i, total, gap);
            if (i < filled) {
                fill(graphics, cell, fillColor);
            } else if (i < filled + lost) {
                box(graphics, cell, UiTheme.DANGER, UiTheme.DANGER_EDGE, false);
            } else {
                fill(graphics, cell, emptyColor);
            }
        }
    }

    /** The width of a badge with a text. */
    public static int badgeWidth(Font font, Component text) {
        return font.width(text) + 8;
    }

    /**
     * Draws a badge: a small box with a border and a text, {@link UiTheme#BADGE} pixels high.
     *
     * @return the width of the badge
     */
    public static int badge(GuiGraphics graphics, Font font, int x, int y, Component text, UiTone tone) {
        int width = badgeWidth(font, text);
        UiRect rect = new UiRect(x, y, width, UiTheme.BADGE);
        box(graphics, rect, tone.background(), tone.border(), false);
        graphics.drawString(font, text, x + 4, UiText.textY(rect), tone.text(), false);
        return width;
    }

    /** Draws a small square mark, for example the color of a toast. */
    public static void mark(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + size, color);
    }

    /**
     * Draws a small banner: a flag of the faction color with a gold square. The header of a village screen.
     *
     * @param color the faction color (RGB or ARGB)
     */
    public static void banner(GuiGraphics graphics, int x, int y, int color) {
        int argb = 0xFF000000 | color;
        graphics.fill(x, y, x + 10, y + 11, argb);
        graphics.fill(x, y + 11, x + 4, y + 14, argb);
        graphics.fill(x + 6, y + 11, x + 10, y + 14, argb);
        graphics.fill(x + 3, y + 3, x + 7, y + 7, UiTheme.GOLD);
    }

    /**
     * Draws an item icon in a square of a size. The vanilla icon is 16 pixels: a smaller size scales it down.
     *
     * @param stack the item
     * @param size  the size of the square, for example 16 or 9
     */
    public static void item(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        if (size == 16) {
            graphics.renderItem(stack, x, y);
            return;
        }
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /** Draws an item icon in a grey slot of 18 pixels, as a vanilla inventory slot. */
    public static void itemSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
        box(graphics, new UiRect(x, y, 18, 18), 0xFF8B8B8B, 0xFF373737, false);
        item(graphics, stack, x + 1, y + 1, 16);
    }

    /**
     * The item with an id, for an icon.
     *
     * @param itemId an item id, for example "minecraft:bread"
     * @return the item, or an empty stack for an unknown id
     */
    public static ItemStack stack(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(location)
                .filter(item -> item != Items.AIR)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Draws a vertical scroll bar: a track and a gold thumb. Nothing if the content fits.
     *
     * @param track         the rectangle of the track
     * @param contentHeight the height of the complete content
     * @param scroll        the scroll offset
     * @param maxScroll     the maximum scroll offset
     */
    public static void scrollBar(GuiGraphics graphics, UiRect track, int contentHeight, int scroll, int maxScroll) {
        if (maxScroll <= 0 || contentHeight <= 0 || track.height() <= 0) {
            return;
        }
        int thumbHeight = Math.max(8, track.height() * track.height() / contentHeight);
        int thumbTop = track.y() + (track.height() - thumbHeight) * scroll / maxScroll;
        fill(graphics, track, 0x40FFFFFF);
        fill(graphics, new UiRect(track.x(), thumbTop, track.width(), thumbHeight), UiTheme.GILT);
    }
}
