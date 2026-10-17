package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text helpers. The texts of the mods are translated, and a French text is often 15 % to 30 % longer than the
 * English text.
 *
 * <ul>
 *   <li>A line of text that is a little too wide is drawn smaller, down to {@link #MIN_SCALE} of its size.</li>
 *   <li>A line that does not fit at this size ends with "…".</li>
 *   <li>A long text can also wrap on more lines ({@link #wrap}, {@link #drawWrapped}).</li>
 * </ul>
 *
 * <p>All texts are drawn with no shadow.
 */
public final class UiText {
    /** The end of a text that does not fit. */
    public static final String ELLIPSIS = "…";
    /** The smallest size of a line of text that does not fit, as a part of the normal size. */
    public static final float MIN_SCALE = 0.75F;
    /** The height of the letters of the font, in pixels. */
    private static final int GLYPH_HEIGHT = 8;

    private UiText() {
    }

    /**
     * The text, cut with "…" at the end if it is wider than the maximum width.
     *
     * @param font     the font
     * @param text     the text
     * @param maxWidth the maximum width in pixels
     * @return the text to draw
     */
    public static FormattedCharSequence ellipsize(Font font, FormattedText text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return Language.getInstance().getVisualOrder(text);
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS)));
    }

    /** True if the text is wider than the maximum width. */
    public static boolean isCut(Font font, FormattedText text, int maxWidth) {
        return font.width(text) > maxWidth;
    }

    /**
     * The lines of a text in a width. If the text needs more lines than the maximum, the last line ends with "…".
     *
     * @param font     the font
     * @param text     the text
     * @param width    the width in pixels
     * @param maxLines the maximum number of lines (1 or more)
     * @return the lines to draw
     */
    public static List<FormattedCharSequence> wrap(Font font, FormattedText text, int width, int maxLines) {
        List<FormattedText> lines = font.getSplitter().splitLines(text, Math.max(1, width), Style.EMPTY);
        List<FormattedCharSequence> result = new ArrayList<>();
        int count = Math.min(lines.size(), Math.max(1, maxLines));
        for (int i = 0; i < count; i++) {
            boolean last = i == count - 1 && lines.size() > count;
            FormattedText line = lines.get(i);
            if (last) {
                FormattedText cut = font.substrByWidth(line, Math.max(0, width - font.width(ELLIPSIS)));
                result.add(Language.getInstance().getVisualOrder(FormattedText.composite(cut,
                        FormattedText.of(ELLIPSIS))));
            } else {
                result.add(Language.getInstance().getVisualOrder(line));
            }
        }
        return result;
    }

    /**
     * Draws a line of text from a left edge: at its size if it fits, smaller if it fits at {@link #MIN_SCALE}, else
     * smaller and cut with "…".
     *
     * @return the width of the drawn text
     */
    private static int drawFitted(GuiGraphics graphics, Font font, Component text, float left, int y, int maxWidth,
            int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.drawString(font, text, Math.round(left), y, color, false);
            return width;
        }
        float scale = Math.max(MIN_SCALE, (float) maxWidth / width);
        FormattedCharSequence line = ellipsize(font, text, (int) Math.floor(maxWidth / scale));
        graphics.pose().pushPose();
        graphics.pose().translate(left, y + GLYPH_HEIGHT * (1.0F - scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, line, 0, 0, color, false);
        graphics.pose().popPose();
        return (int) Math.ceil(font.width(line) * scale);
    }

    /** The width of a line of text when {@link #drawFitted} draws it in a maximum width. */
    private static float fittedWidth(Font font, Component text, int maxWidth) {
        int width = font.width(text);
        if (width <= maxWidth) {
            return width;
        }
        float scale = Math.max(MIN_SCALE, (float) maxWidth / width);
        return font.width(ellipsize(font, text, (int) Math.floor(maxWidth / scale))) * scale;
    }

    /**
     * Draws a text at a position, in a maximum width (see {@link UiText}).
     *
     * @return the width of the drawn text
     */
    public static int draw(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        return drawFitted(graphics, font, text, x, y, maxWidth, color);
    }

    /**
     * Draws a text that ends at a right edge, in a maximum width (see {@link UiText}).
     *
     * @return the width of the drawn text
     */
    public static int drawRight(GuiGraphics graphics, Font font, Component text, int right, int y, int maxWidth,
            int color) {
        return drawFitted(graphics, font, text, right - fittedWidth(font, text, maxWidth), y, maxWidth, color);
    }

    /** Draws a text at the center of a rectangle, in the width of the rectangle (see {@link UiText}). */
    public static void drawCentered(GuiGraphics graphics, Font font, Component text, UiRect rect, int color) {
        float width = fittedWidth(font, text, rect.width());
        drawFitted(graphics, font, text, rect.x() + (rect.width() - width) / 2.0F, textY(rect), rect.width(), color);
    }

    /** Draws a text at the left of a rectangle, centered vertically, in the width of the rectangle. */
    public static void drawLeft(GuiGraphics graphics, Font font, Component text, UiRect rect, int color) {
        drawFitted(graphics, font, text, rect.x(), textY(rect), rect.width(), color);
    }

    /**
     * Draws the lines of a text in a rectangle, from the top. The lines that do not fit in the height are not
     * drawn; the last drawn line ends with "…" if the text is longer.
     *
     * @return the height of the drawn lines
     */
    public static int drawWrapped(GuiGraphics graphics, Font font, Component text, UiRect rect, int color) {
        int maxLines = Math.max(1, rect.height() / UiTheme.LINE);
        List<FormattedCharSequence> lines = wrap(font, text, rect.width(), maxLines);
        int y = rect.y();
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, rect.x(), y, color, false);
            y += UiTheme.LINE;
        }
        return lines.size() * UiTheme.LINE;
    }

    /**
     * A time as "m:ss", or "h:mm:ss" for one hour or more.
     *
     * @param seconds the time in seconds (a negative time gives "0:00")
     * @return the text
     */
    public static String clock(long seconds) {
        long s = Math.max(0, seconds);
        if (s >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** The Y of a line of text at the vertical center of a rectangle. */
    public static int textY(UiRect rect) {
        return rect.y() + (rect.height() - 8) / 2 + 1;
    }
}
