package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A bar of tabs. The active tab is lighter, with a gold line on top and a gold text.
 *
 * <ul>
 *   <li>A click on a tab selects it. The left and right arrow keys select the previous or the next tab (see
 *       {@link UiScreen#setSectionTabs(UiTabs)}).</li>
 *   <li>Each tab is as wide as its label. If the labels do not fit, the tabs share the width, and the labels end
 *       with "…".</li>
 * </ul>
 */
public class UiTabs extends AbstractWidget {
    private static final int DEFAULT_PADDING = 6;

    private int padding = DEFAULT_PADDING;
    private List<Component> labels;
    private int selected;
    private final IntConsumer onSelect;

    /**
     * Makes a tab bar.
     *
     * @param rect     the position and the size
     * @param labels   the labels of the tabs
     * @param selected the index of the selected tab
     * @param onSelect called with the index of a newly selected tab
     */
    public UiTabs(UiRect rect, List<Component> labels, int selected, IntConsumer onSelect) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), Component.empty());
        this.labels = List.copyOf(labels);
        this.selected = labels.isEmpty() ? 0 : Math.max(0, Math.min(selected, labels.size() - 1));
        this.onSelect = onSelect;
    }

    /** Sets the space on each side of a label, for a narrow tab bar. */
    public UiTabs setPadding(int padding) {
        this.padding = Math.max(1, padding);
        return this;
    }

    /** The index of the selected tab. */
    public int selected() {
        return selected;
    }

    /** The number of tabs. */
    public int count() {
        return labels.size();
    }

    /** Changes the labels, for example a count in a label. */
    public void setLabels(List<Component> labels) {
        this.labels = List.copyOf(labels);
        this.selected = labels.isEmpty() ? 0 : Math.min(selected, labels.size() - 1);
    }

    /**
     * Selects a tab. Nothing changes for an index out of the tabs.
     *
     * @param index the index of the tab
     * @return true if the selection changed
     */
    public boolean select(int index) {
        if (index < 0 || index >= labels.size() || index == selected) {
            return false;
        }
        selected = index;
        onSelect.accept(index);
        return true;
    }

    /** Selects the next tab (+1) or the previous tab (-1). */
    public boolean move(int delta) {
        return select(selected + delta);
    }

    /** The rectangle of each tab. */
    private UiRect tab(int index, Font font) {
        int natural = 0;
        for (Component label : labels) {
            natural += font.width(label) + 2 * padding;
        }
        if (natural <= width) {
            int x = getX();
            for (int i = 0; i < index; i++) {
                x += font.width(labels.get(i)) + 2 * padding;
            }
            return new UiRect(x, getY(), font.width(labels.get(index)) + 2 * padding, height);
        }
        return new UiRect(getX(), getY(), width, height).column(index, labels.size(), 0);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < labels.size(); i++) {
            if (tab(i, font).contains(mouseX, mouseY)) {
                select(i);
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            return move(-1);
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            return move(1);
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        UiDraw.fill(graphics, new UiRect(getX(), getY(), width, height), UiTheme.WOOD_DARK);
        for (int i = 0; i < labels.size(); i++) {
            UiRect rect = tab(i, font);
            boolean active = i == selected;
            boolean hovered = rect.contains(mouseX, mouseY);
            if (active) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_TAB);
                UiDraw.hLine(graphics, rect.x(), rect.y(), rect.width(), UiTheme.GOLD);
            } else if (hovered) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_ROW);
            }
            UiText.drawCentered(graphics, font, labels.get(i), rect.inset(2, 0),
                    active ? UiTheme.GOLD : hovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
        }
        if (isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard() && !labels.isEmpty()) {
            UiDraw.dashedOutline(graphics, tab(selected, font).inset(-1), UiTheme.GOLD);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        if (!labels.isEmpty()) {
            output.add(NarratedElementType.TITLE, labels.get(selected));
        }
    }
}
