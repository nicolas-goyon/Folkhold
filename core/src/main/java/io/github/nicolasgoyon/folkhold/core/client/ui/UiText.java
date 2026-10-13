package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text helpers. The texts of the mods are translated, and a French text is often 15 % to 30 % longer than the
 * English text: a text that does not fit ends with "…", or wraps on more lines.
 *
 * <p>All texts are drawn with no shadow.
 */
public final class UiText {
    /** The end of a text that does not fit. */
    public static final String ELLIPSIS = "…";

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

    /** The number of lines of a text in a width. */
    public static int lineCount(Font font, FormattedText text, int width) {
        return font.getSplitter().splitLines(text, Math.max(1, width), Style.EMPTY).size();
    }

    /**
     * Draws a text at a position, cut with "…" to a maximum width.
     *
     * @return the width of the drawn text
     */
    public static int draw(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        FormattedCharSequence line = ellipsize(font, text, maxWidth);
        graphics.drawString(font, line, x, y, color, false);
        return font.width(line);
    }

    /**
     * Draws a text that ends at a right edge, cut with "…" to a maximum width.
     *
     * @return the width of the drawn text
     */
    public static int drawRight(GuiGraphics graphics, Font font, Component text, int right, int y, int maxWidth,
            int color) {
        FormattedCharSequence line = ellipsize(font, text, maxWidth);
        int width = font.width(line);
        graphics.drawString(font, line, right - width, y, color, false);
        return width;
    }

    /** Draws a text at the center of a rectangle, cut with "…" to the width of the rectangle. */
    public static void drawCentered(GuiGraphics graphics, Font font, Component text, UiRect rect, int color) {
        FormattedCharSequence line = ellipsize(font, text, rect.width());
        graphics.drawString(font, line, rect.x() + (rect.width() - font.width(line)) / 2, textY(rect), color, false);
    }

    /** Draws a text at the left of a rectangle, centered vertically, cut with "…" to the width of the rectangle. */
    public static void drawLeft(GuiGraphics graphics, Font font, Component text, UiRect rect, int color) {
        graphics.drawString(font, ellipsize(font, text, rect.width()), rect.x(), textY(rect), color, false);
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

    /** The Y of a line of text at the vertical center of a rectangle. */
    public static int textY(UiRect rect) {
        return rect.y() + (rect.height() - 8) / 2 + 1;
    }
}
