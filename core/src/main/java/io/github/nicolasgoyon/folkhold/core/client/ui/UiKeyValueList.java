package io.github.nicolasgoyon.folkhold.core.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A list of {@link UiRow}: the label in a muted color on the left, the value on the right, one row of two on a
 * lighter stripe. A row with a meter is higher. A heading row is a gold title.
 *
 * <p>The label gets at most 3/5 of the width. The value gets the rest. A text that does not fit ends with "…".
 */
public class UiKeyValueList extends UiList<UiRow> {
    private static final int METER_HEIGHT = 3;

    public UiKeyValueList(UiRect rect) {
        super(rect);
    }

    @Override
    protected int rowHeight(UiRow row, int index) {
        if (row.heading()) {
            return UiTheme.ROW + 2;
        }
        return row.meter() == null ? UiTheme.ROW : UiTheme.ROW + METER_HEIGHT + 3;
    }

    @Override
    protected void renderRow(GuiGraphics graphics, UiRow row, int index, UiRect rect, boolean hovered,
            boolean selected, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        UiRect text = rect.top(UiTheme.ROW).inset(3, 0);
        if (row.heading()) {
            UiText.drawLeft(graphics, font, row.label(), new UiRect(text.x(), text.y() + 2, text.width(),
                    UiTheme.ROW), UiTheme.GOLD);
            return;
        }
        if (index % 2 == 0) {
            UiDraw.fill(graphics, rect, UiTheme.WOOD_ROW);
        }
        int y = UiText.textY(text);
        int valueWidth = UiText.drawRight(graphics, font, row.value(), text.right(), y,
                text.width() - Math.min(font.width(row.label()), text.width() * 3 / 5) - 6, UiTheme.TEXT);
        UiText.draw(graphics, font, row.label(), text.x(), y, text.width() - valueWidth - 6, UiTheme.TEXT_MUTED);
        UiMeter meter = row.meter();
        if (meter != null) {
            UiRect bar = new UiRect(text.x(), rect.y() + UiTheme.ROW + 1, text.width(), METER_HEIGHT);
            if (meter.segmented()) {
                UiDraw.segments(graphics, bar, meter.max(), meter.value(), meter.lost(), meter.color(),
                        UiTheme.WOOD_INPUT);
            } else {
                UiDraw.bar(graphics, bar, meter.fraction(), UiTheme.WOOD_INPUT, meter.color());
            }
        }
    }

    @Override
    protected Component narration(UiRow row) {
        return Component.empty().append(row.label()).append(" ").append(row.value());
    }
}
