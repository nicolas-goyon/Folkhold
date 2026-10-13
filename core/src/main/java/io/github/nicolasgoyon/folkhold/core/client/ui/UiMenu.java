package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A drop-down menu below a button: the actions of a group. The menu closes after an action, after a click out of
 * the menu, and with Escape.
 *
 * <p>Items:
 * <ul>
 *   <li>{@link Action}: a row that does an action. A disabled action shows its reason on the right. A dangerous
 *       action has a red text, and usually opens a confirmation dialog.</li>
 *   <li>{@link Value}: a label, a text field and an OK button. The action gets the text of the field.</li>
 *   <li>{@link Separator}: a line between two groups of items.</li>
 * </ul>
 */
public class UiMenu extends UiOverlay {
    private static final int ITEM = 16;
    private static final int VALUE_ITEM = 18;
    private static final int SEPARATOR = 5;
    private static final int FIELD_WIDTH = 34;
    private static final int OK_WIDTH = 22;

    /** An item of a menu. */
    public sealed interface Item permits Action, Value, Separator {
    }

    /**
     * A row that does an action.
     *
     * @param label  the label
     * @param run    the action, after the menu closes
     * @param reason the reason why the action is not available, or null if it is available
     * @param danger true for a dangerous action (red text)
     */
    public record Action(Component label, Runnable run, @Nullable Component reason, boolean danger)
            implements Item {

        /** An available action. */
        public static Action of(Component label, Runnable run) {
            return new Action(label, run, null, false);
        }

        /** A dangerous action. */
        public static Action danger(Component label, Runnable run) {
            return new Action(label, run, null, true);
        }

        /** The same action, not available for a reason. */
        public Action disabled(Component why) {
            return new Action(label, run, why, danger);
        }
    }

    /**
     * A row with a text field and an OK button.
     *
     * @param label   the label
     * @param initial the text of the field when the menu opens
     * @param filter  the texts that the field accepts while the player types
     * @param apply   the action with the text of the field, after the menu closes
     */
    public record Value(Component label, String initial, Predicate<String> filter, Consumer<String> apply)
            implements Item {
    }

    /** A line between two groups of items. */
    public record Separator() implements Item {
    }

    private final UiRect anchor;
    private final List<Item> items;
    private UiRect box = new UiRect(0, 0, 0, 0);

    /**
     * Makes a menu.
     *
     * @param parent the screen of the button
     * @param anchor the rectangle of the button: the menu opens below it
     * @param title  the title for the narration
     * @param items  the items
     */
    public UiMenu(Screen parent, UiRect anchor, Component title, List<Item> items) {
        super(title, parent, 0x30000000);
        this.anchor = anchor;
        this.items = List.copyOf(items);
    }

    /** Opens a menu below a button. */
    public static void open(Screen parent, UiRect anchor, Component title, List<Item> items) {
        Minecraft.getInstance().setScreen(new UiMenu(parent, anchor, title, items));
    }

    private int itemHeight(Item item) {
        return switch (item) {
            case Action action -> ITEM;
            case Value value -> VALUE_ITEM;
            case Separator separator -> SEPARATOR;
        };
    }

    private int itemWidth(Item item, Font font) {
        return switch (item) {
            case Action action -> font.width(action.label()) + 12
                    + (action.reason() != null ? font.width(action.reason()) + 10 : 0);
            case Value value -> font.width(value.label()) + 12 + FIELD_WIDTH + OK_WIDTH + 2 * UiTheme.GAP;
            case Separator separator -> 0;
        };
    }

    @Override
    protected void initOverlay() {
        int contentHeight = 2;
        int contentWidth = anchor.width();
        for (Item item : items) {
            contentHeight += itemHeight(item);
            contentWidth = Math.max(contentWidth, itemWidth(item, font));
        }
        int menuWidth = Math.min(contentWidth + 2, width - 2 * UiTheme.SCREEN_MARGIN);
        int menuHeight = Math.min(contentHeight, height - 2 * UiTheme.SCREEN_MARGIN);
        int x = Math.max(UiTheme.SCREEN_MARGIN, Math.min(anchor.x(), width - UiTheme.SCREEN_MARGIN - menuWidth));
        int y = anchor.bottom() + 1;
        if (y + menuHeight > height - UiTheme.SCREEN_MARGIN) {
            y = Math.max(UiTheme.SCREEN_MARGIN, anchor.y() - 1 - menuHeight);
        }
        box = new UiRect(x, y, menuWidth, menuHeight);
        int top = box.y() + 1;
        UiRect inner = box.inset(1, 0);
        for (Item item : items) {
            int h = itemHeight(item);
            UiRect row = new UiRect(inner.x(), top, inner.width(), h);
            switch (item) {
                case Action action -> addAction(action, row);
                case Value value -> addValue(value, row);
                case Separator separator -> {
                    // Drawn in renderPanel.
                }
            }
            top += h;
        }
    }

    private void addAction(Action action, UiRect row) {
        UiButton button = new UiButton(row, action.label(), UiButton.Style.MENU, b -> {
            onClose();
            action.run().run();
        });
        if (action.danger()) {
            button.setTextColor(UiTheme.TEXT_URGENT);
        }
        if (action.reason() != null) {
            button.disable();
        }
        addRenderableWidget(button);
    }

    private void addValue(Value value, UiRect row) {
        UiRect ok = row.inset(4, 2).rightPart(OK_WIDTH);
        UiRect field = row.inset(4, 2).beforeRight(OK_WIDTH, UiTheme.GAP).rightPart(FIELD_WIDTH);
        UiTextField text = new UiTextField(field, value.label(), null);
        text.setValue(value.initial());
        text.setFilter(value.filter());
        addRenderableWidget(text);
        addRenderableWidget(UiButton.primary(ok, UiStrings.OK, b -> {
            onClose();
            value.apply().accept(text.getValue());
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!box.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiDraw.fill(graphics, box.inset(-1), 0xC0000000);
        UiDraw.box(graphics, box, UiTheme.WOOD_DARK, UiTheme.GOLD, false);
        int top = box.y() + 1;
        UiRect inner = box.inset(1, 0);
        for (Item item : items) {
            int h = itemHeight(item);
            UiRect row = new UiRect(inner.x(), top, inner.width(), h);
            switch (item) {
                case Separator separator -> UiDraw.hLine(graphics, row.x(), row.centerY(), row.width(),
                        UiTheme.WOOD_RAISED);
                case Value value -> UiText.drawLeft(graphics, font, value.label(),
                        row.inset(5, 0).beforeRight(FIELD_WIDTH + OK_WIDTH + UiTheme.GAP + 4, 2), UiTheme.TEXT);
                case Action action -> {
                    if (action.reason() != null) {
                        UiText.drawRight(graphics, font, action.reason(), row.right() - 5, UiText.textY(row),
                                row.width() / 2, UiTheme.TEXT_FAINT);
                    }
                }
            }
            top += h;
        }
    }
}
