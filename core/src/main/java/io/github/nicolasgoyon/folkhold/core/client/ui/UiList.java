package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A list of rows that scrolls with the mouse wheel. The subclass draws each row.
 *
 * <ul>
 *   <li>The rows can have different heights ({@link #rowHeight}).</li>
 *   <li>A list can be selectable: a click on a row, or the up and down arrow keys, select it.</li>
 *   <li>A scroll bar shows when the rows do not fit.</li>
 *   <li>An empty list shows a centered text.</li>
 * </ul>
 *
 * @param <T> the type of the items
 */
public abstract class UiList<T> extends AbstractWidget {
    private List<T> items = List.of();
    private int scroll;
    private int selected = -1;
    private boolean selectable;
    private Component emptyText = UiStrings.EMPTY;

    protected UiList(UiRect rect) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), Component.empty());
    }

    /** The items. */
    public List<T> items() {
        return items;
    }

    /** Changes the items. The scroll stays in the new limits. */
    public void setItems(List<T> items) {
        this.items = List.copyOf(items);
        if (selected >= this.items.size()) {
            selected = -1;
        }
        setScroll(scroll);
    }

    /** A click and the arrow keys select a row. */
    public UiList<T> setSelectable(boolean selectable) {
        this.selectable = selectable;
        return this;
    }

    /** The text of an empty list. */
    public UiList<T> setEmptyText(Component text) {
        this.emptyText = text;
        return this;
    }

    /** The index of the selected row, or -1. */
    public int selectedIndex() {
        return selected;
    }

    /** The selected item, or null. */
    @Nullable
    public T selectedItem() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    /** Selects a row with no call of {@link #onSelect}. -1 selects no row. */
    public void setSelectedIndex(int index) {
        this.selected = index >= 0 && index < items.size() ? index : -1;
    }

    /** The scroll offset in pixels. */
    public int scroll() {
        return scroll;
    }

    /** Sets the scroll offset. The value is clamped to the content. */
    public void setScroll(int value) {
        scroll = Mth.clamp(value, 0, maxScroll());
    }

    /** The height of a row. */
    protected abstract int rowHeight(T item, int index);

    /**
     * Draws a row.
     *
     * @param rect     the rectangle of the row (the scroll bar is not in it)
     * @param hovered  true if the mouse is on the row
     * @param selected true if the row is selected
     */
    protected abstract void renderRow(GuiGraphics graphics, T item, int index, UiRect rect, boolean hovered,
            boolean selected, int mouseX, int mouseY);

    /** Called when the player selects a row. */
    protected void onSelect(T item, int index) {
    }

    /**
     * Called for a click on a row, before the selection. The rectangle is the rectangle of the row.
     *
     * @return true if the click is used (no selection)
     */
    protected boolean onRowClick(T item, int index, UiRect rect, double mouseX, double mouseY) {
        return false;
    }

    /** The rectangle of the rows, without the scroll bar. */
    protected UiRect rowsRect() {
        UiRect all = new UiRect(getX(), getY(), width, height);
        return maxScroll() > 0 ? all.beforeRight(UiTheme.SCROLL_BAR, 1) : all;
    }

    /** The height of all rows. */
    public int contentHeight() {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            total += rowHeight(items.get(i), i);
        }
        return total;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0) {
            return false;
        }
        setScroll(scroll - (int) Math.round(scrollY * UiTheme.ROW * 2));
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        UiRect rows = rowsRect();
        int y = rows.y() - scroll;
        for (int i = 0; i < items.size(); i++) {
            int h = rowHeight(items.get(i), i);
            UiRect rect = new UiRect(rows.x(), y, rows.width(), h);
            if (rect.contains(mouseX, mouseY) && mouseY >= rows.y() && mouseY < rows.bottom()) {
                if (onRowClick(items.get(i), i, rect, mouseX, mouseY)) {
                    return;
                }
                if (selectable) {
                    selected = i;
                    onSelect(items.get(i), i);
                }
                return;
            }
            y += h;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!selectable || items.isEmpty()) {
            return false;
        }
        int delta = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : keyCode == GLFW.GLFW_KEY_UP ? -1 : 0;
        if (delta == 0) {
            return false;
        }
        int next = Mth.clamp(selected + delta, 0, items.size() - 1);
        if (next != selected) {
            selected = next;
            ensureVisible(next);
            onSelect(items.get(next), next);
        }
        return true;
    }

    private void ensureVisible(int index) {
        int top = 0;
        for (int i = 0; i < index; i++) {
            top += rowHeight(items.get(i), i);
        }
        int bottom = top + rowHeight(items.get(index), index);
        if (top < scroll) {
            setScroll(top);
        } else if (bottom > scroll + height) {
            setScroll(bottom - height);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiRect all = new UiRect(getX(), getY(), width, height);
        if (items.isEmpty()) {
            UiRect box = all.top(UiTheme.CONTROL + 4).inset(4, 2);
            UiDraw.dashedOutline(graphics, box, UiTheme.WOOD_EDGE);
            UiText.drawCentered(graphics, Minecraft.getInstance().font, emptyText, box.inset(3, 0),
                    UiTheme.TEXT_MUTED);
            return;
        }
        UiRect rows = rowsRect();
        graphics.enableScissor(all.x(), all.y(), all.right(), all.bottom());
        int y = rows.y() - scroll;
        for (int i = 0; i < items.size(); i++) {
            int h = rowHeight(items.get(i), i);
            if (y + h > rows.y() && y < rows.bottom()) {
                UiRect rect = new UiRect(rows.x(), y, rows.width(), h);
                boolean hovered = rect.contains(mouseX, mouseY) && all.contains(mouseX, mouseY);
                renderRow(graphics, items.get(i), i, rect, hovered, i == selected, mouseX, mouseY);
            }
            y += h;
        }
        graphics.disableScissor();
        UiDraw.scrollBar(graphics, all.rightPart(UiTheme.SCROLL_BAR), contentHeight(), scroll, maxScroll());
        if (isFocused() && selectable && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            UiDraw.dashedOutline(graphics, all.inset(-1), UiTheme.GOLD);
        }
    }

    /** The text of an item for the narrator, or null. */
    @Nullable
    protected Component narration(T item) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        T item = selectedItem();
        Component text = item == null ? null : narration(item);
        if (text != null) {
            output.add(NarratedElementType.TITLE, text);
        }
    }
}
