package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload.Summary;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The admin screen: all settlements of the world, for an operator (see
 * {@link io.github.nicolasgoyon.folkhold.world.admin.AdminActions}). The command {@code /folkhold admin} opens it.
 *
 * <ul>
 *   <li>Left: the settlements. A text filter (name, id or faction), a level range, a state filter and a sort order
 *       select the settlements. A click on a settlement shows its details.</li>
 *   <li>Right: the details of the selected settlement and the actions on it. The value box gives the value of the
 *       level and experience actions.</li>
 *   <li>Bottom: the global actions.</li>
 * </ul>
 * The screen keeps the filters and the selection for the next opening.
 */
public class AdminScreen extends Screen {
    private static final String KEY = "screen.folkhold_world.admin.";
    private static final int MAX_PANEL_WIDTH = 420;
    private static final int MAX_PANEL_HEIGHT = 260;
    private static final int MARGIN = 6;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 24;
    private static final int GAP = 2;
    private static final int CONTROL_HEIGHT = 14;
    private static final int ROW_HEIGHT = 11;
    private static final int SCROLL_STEP = ROW_HEIGHT * 2;
    private static final int SCROLL_BAR_WIDTH = 3;
    private static final int ACTION_COLUMNS = 4;
    private static final int ACTION_ROWS = 3;

    private static final int FRAME_OUTER = 0xFF2B1F12;
    private static final int FRAME_GOLD = 0xFFB8913F;
    private static final int PANEL_COLOR = 0xF0211B14;
    private static final int HEADER_COLOR = 0xF0302619;
    private static final int LIST_COLOR = 0xFF1A1510;
    private static final int ROW_STRIPE_COLOR = 0x14FFFFFF;
    private static final int ROW_HOVER_COLOR = 0x30FFFFFF;
    private static final int ROW_SELECTED_COLOR = 0xFF3A2F21;
    private static final int SCROLL_TRACK_COLOR = 0x40FFFFFF;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xA89F8C;
    private static final int VALUE_COLOR = 0xFFFFFF;
    private static final int RUINS_COLOR = 0x8A6F5A;
    private static final int ACCENT_COLOR = 0xFFD37F;
    private static final int EMPTY_COLOR = 0x7A7262;

    /** Filter on the state of the settlements. */
    private enum StateFilter {
        ALL, ACTIVE, RUINS;

        boolean accepts(Summary summary) {
            return this == ALL || (this == ACTIVE) == summary.active();
        }
    }

    /** Sort order of the list. */
    private enum Sort {
        DISTANCE(Comparator.comparingInt(Summary::distance)),
        LEVEL(Comparator.comparingInt(Summary::level).reversed().thenComparingInt(Summary::distance)),
        NAME(Comparator.comparing(summary -> summary.name().toLowerCase(Locale.ROOT)));

        private final Comparator<Summary> comparator;

        Sort(Comparator<Summary> comparator) {
            this.comparator = comparator;
        }
    }

    // State kept between two openings.
    private static String lastText = "";
    private static String lastMinLevel = "";
    private static String lastMaxLevel = "";
    private static String lastValue = "1";
    private static StateFilter lastState = StateFilter.ALL;
    private static Sort lastSort = Sort.DISTANCE;
    @Nullable
    private static String lastSelected;

    private List<Summary> settlements;
    private List<Summary> visible = List.of();
    @Nullable
    private AdminDetailsPayload details;
    private int listScroll;
    private int detailsScroll;

    private EditBox textBox;
    private EditBox minLevelBox;
    private EditBox maxLevelBox;
    private EditBox valueBox;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public AdminScreen(List<Summary> settlements) {
        super(Component.translatable(KEY + "title"));
        this.settlements = settlements;
    }

    /** Opens the screen, or updates the list of the open screen. Called by the network handler. */
    public static void open(AdminListPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AdminScreen screen) {
            screen.settlements = payload.settlements();
            screen.applyFilters();
        } else {
            minecraft.setScreen(new AdminScreen(payload.settlements()));
            if (lastSelected != null) {
                send("details", lastSelected, 0);
            }
        }
    }

    /** Shows the details of a settlement in the open screen. Called by the network handler. */
    public static void showDetails(AdminDetailsPayload payload) {
        if (Minecraft.getInstance().screen instanceof AdminScreen screen) {
            boolean same = screen.details != null && screen.details.id().equals(payload.id());
            screen.details = payload;
            if (!same) {
                screen.detailsScroll = 0;
            }
            screen.detailsScroll = Mth.clamp(screen.detailsScroll, 0, screen.maxDetailsScroll());
        }
    }

    private static void send(String action, String settlementId, int value) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, settlementId, value));
    }

    // ------------------------------------------------------------------ layout

    private int columnWidth() {
        return (panelWidth - 2 * PADDING - PADDING) / 2;
    }

    private int listLeft() {
        return left + PADDING;
    }

    private int detailsLeft() {
        return listLeft() + columnWidth() + PADDING;
    }

    private int bodyTop() {
        return top + HEADER_HEIGHT + 4;
    }

    private int bodyBottom() {
        return top + panelHeight - FOOTER_HEIGHT;
    }

    private int listTop() {
        return bodyTop() + 2 * (CONTROL_HEIGHT + GAP);
    }

    private int actionsTop() {
        return bodyBottom() - ACTION_ROWS * (CONTROL_HEIGHT + GAP);
    }

    private int detailsTop() {
        return bodyTop() + ROW_HEIGHT + GAP;
    }

    private int detailsBottom() {
        return actionsTop() - GAP;
    }

    private int maxListScroll() {
        return Math.max(0, visible.size() * ROW_HEIGHT - (bodyBottom() - listTop()));
    }

    private int maxDetailsScroll() {
        int rows = details == null ? 0 : details.rows().size();
        return Math.max(0, rows * ROW_HEIGHT - (detailsBottom() - detailsTop()));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 2 * MARGIN);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 2 * MARGIN);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int column = columnWidth();

        // Filters, line 1: text. Line 2: level range, state, sort.
        int x = listLeft();
        int y = bodyTop();
        textBox = addRenderableWidget(new EditBox(font, x, y, column, CONTROL_HEIGHT,
                Component.translatable(KEY + "filter")));
        textBox.setHint(Component.translatable(KEY + "filter"));
        textBox.setValue(lastText);
        textBox.setResponder(value -> {
            lastText = value;
            applyFilters();
        });
        y += CONTROL_HEIGHT + GAP;
        int small = (column - 3 * GAP) / 4;
        minLevelBox = addRenderableWidget(levelBox(x, y, small, "min_level", lastMinLevel));
        minLevelBox.setResponder(value -> {
            lastMinLevel = value;
            applyFilters();
        });
        maxLevelBox = addRenderableWidget(levelBox(x + small + GAP, y, small, "max_level", lastMaxLevel));
        maxLevelBox.setResponder(value -> {
            lastMaxLevel = value;
            applyFilters();
        });
        addRenderableWidget(Button.builder(stateLabel(), button -> {
            lastState = StateFilter.values()[(lastState.ordinal() + 1) % StateFilter.values().length];
            button.setMessage(stateLabel());
            applyFilters();
        }).bounds(x + 2 * (small + GAP), y, small, CONTROL_HEIGHT).build());
        addRenderableWidget(Button.builder(sortLabel(), button -> {
            lastSort = Sort.values()[(lastSort.ordinal() + 1) % Sort.values().length];
            button.setMessage(sortLabel());
            applyFilters();
        }).bounds(x + 3 * (small + GAP), y, column - 3 * (small + GAP), CONTROL_HEIGHT).build());

        // Actions on the selected settlement.
        int dx = detailsLeft();
        int cell = (column - (ACTION_COLUMNS - 1) * GAP) / ACTION_COLUMNS;
        int ay = actionsTop();
        valueBox = addRenderableWidget(new EditBox(font, dx + 1, ay + 1, cell - 2, CONTROL_HEIGHT - 2,
                Component.translatable(KEY + "value")));
        valueBox.setFilter(value -> value.isEmpty() || value.equals("-") || value.matches("-?\\d{1,7}"));
        valueBox.setValue(lastValue);
        valueBox.setResponder(value -> lastValue = value);
        valueBox.setTooltip(Tooltip.create(
                Component.translatable(KEY + "value.tooltip")));
        actionButton(1, 0, "level_set", () -> selectedAction("level_set", parse(valueBox.getValue(), 1)));
        actionButton(2, 0, "experience_add",
                () -> selectedAction("experience_add", parse(valueBox.getValue(), 0)));
        actionButton(3, 0, "cycle", () -> selectedAction("cycle", 0));
        actionButton(0, 1, "teleport", () -> {
            selectedAction("teleport", 0);
            onClose();
        });
        actionButton(1, 1, "economy_reset", () -> selectedAction("economy_reset", 0));
        actionButton(2, 1, "ruin", () -> selectedAction("ruin", 0));
        actionButton(3, 1, "restore", () -> selectedAction("restore", 0));
        actionButton(0, 2, "calm", () -> selectedAction("alert", 0));
        actionButton(1, 2, "alert", () -> selectedAction("alert", 1));
        actionButton(2, 2, "assault", () -> selectedAction("alert", 2));
        actionButton(3, 2, "bells", () -> selectedAction("bells", 0));

        // Global actions.
        int footerButtons = 4;
        int buttonWidth = Math.min(90, (panelWidth - 2 * PADDING - (footerButtons - 1) * GAP) / footerButtons);
        int fx = left + (panelWidth - footerButtons * buttonWidth - (footerButtons - 1) * GAP) / 2;
        int fy = top + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - CONTROL_HEIGHT - 2) / 2 + 1;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "action.refresh"),
                button -> send("refresh", "", 0)).bounds(fx, fy, buttonWidth, CONTROL_HEIGHT + 2).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "action.tick_all"),
                button -> send("tick_all", "", 0))
                .bounds(fx + (buttonWidth + GAP), fy, buttonWidth, CONTROL_HEIGHT + 2).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "action.cycle_all"),
                button -> send("cycle_all", "", 0))
                .bounds(fx + 2 * (buttonWidth + GAP), fy, buttonWidth, CONTROL_HEIGHT + 2).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(fx + 3 * (buttonWidth + GAP), fy, buttonWidth, CONTROL_HEIGHT + 2).build());

        applyFilters();
    }

    private EditBox levelBox(int x, int y, int w, String hintKey, String value) {
        EditBox box = new EditBox(font, x + 1, y + 1, w - 2, CONTROL_HEIGHT - 2, Component.translatable(KEY + hintKey));
        box.setHint(Component.translatable(KEY + hintKey));
        box.setFilter(text -> text.matches("\\d{0,3}"));
        box.setValue(value);
        return box;
    }

    private void actionButton(int column, int row, String name, Runnable action) {
        int cell = (columnWidth() - (ACTION_COLUMNS - 1) * GAP) / ACTION_COLUMNS;
        int x = detailsLeft() + column * (cell + GAP);
        int y = actionsTop() + row * (CONTROL_HEIGHT + GAP);
        Button button = Button.builder(Component.translatable(KEY + "action." + name), b -> action.run())
                .bounds(x, y, cell, CONTROL_HEIGHT)
                .tooltip(Tooltip.create(
                        Component.translatable(KEY + "action." + name + ".tooltip")))
                .build();
        addRenderableWidget(button);
    }

    private void selectedAction(String action, int value) {
        if (lastSelected != null) {
            send(action, lastSelected, value);
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Component stateLabel() {
        return Component.translatable(KEY + "state_filter." + lastState.name().toLowerCase(Locale.ROOT));
    }

    private static Component sortLabel() {
        return Component.translatable(KEY + "sort." + lastSort.name().toLowerCase(Locale.ROOT));
    }

    private void applyFilters() {
        String text = lastText.trim().toLowerCase(Locale.ROOT);
        int min = parse(lastMinLevel, Integer.MIN_VALUE);
        int max = parse(lastMaxLevel, Integer.MAX_VALUE);
        visible = settlements.stream()
                .filter(lastState::accepts)
                .filter(summary -> summary.level() >= min && summary.level() <= max)
                .filter(summary -> text.isEmpty()
                        || summary.name().toLowerCase(Locale.ROOT).contains(text)
                        || summary.id().toLowerCase(Locale.ROOT).startsWith(text)
                        || summary.faction().getString().toLowerCase(Locale.ROOT).contains(text))
                .sorted(lastSort.comparator)
                .toList();
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = listLeft();
        if (button == 0 && mouseX >= x && mouseX < x + columnWidth() && mouseY >= listTop()
                && mouseY < bodyBottom()) {
            int index = ((int) mouseY - listTop() + listScroll) / ROW_HEIGHT;
            if (index >= 0 && index < visible.size()) {
                lastSelected = visible.get(index).id();
                send("details", lastSelected, 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.round(scrollY * SCROLL_STEP);
        if (mouseX < detailsLeft()) {
            listScroll = Mth.clamp(listScroll - step, 0, maxListScroll());
        } else {
            detailsScroll = Mth.clamp(detailsScroll - step, 0, maxDetailsScroll());
        }
        return true;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, FRAME_OUTER);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, FRAME_GOLD);
        graphics.fill(left, top, right, bottom, PANEL_COLOR);
        graphics.fill(left, top, right, top + HEADER_HEIGHT, HEADER_COLOR);
        graphics.fill(left + PADDING, top + HEADER_HEIGHT - 1, right - PADDING, top + HEADER_HEIGHT, FRAME_GOLD);
        graphics.fill(left + PADDING, bodyBottom() + 1, right - PADDING, bodyBottom() + 2, FRAME_GOLD);
        graphics.fill(listLeft(), listTop(), listLeft() + columnWidth(), bodyBottom() - 1, LIST_COLOR);
        int separator = detailsLeft() - PADDING / 2 - 1;
        graphics.fill(separator, bodyTop(), separator + 1, bodyBottom() - 2, FRAME_GOLD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component header = Component.translatable(KEY + "header", visible.size(), settlements.size());
        graphics.drawString(font, title, left + PADDING, top + (HEADER_HEIGHT - font.lineHeight) / 2 + 1,
                TITLE_COLOR, false);
        graphics.drawString(font, header, left + panelWidth - PADDING - font.width(header),
                top + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, LABEL_COLOR, false);
        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listLeft();
        int right = x + columnWidth() - SCROLL_BAR_WIDTH - 1;
        int listTop = listTop();
        int listBottom = bodyBottom() - 1;
        if (visible.isEmpty()) {
            Component empty = Component.translatable(KEY + "empty");
            graphics.drawString(font, empty, x + (right - x - font.width(empty)) / 2, listTop + 4, EMPTY_COLOR,
                    false);
            return;
        }
        graphics.enableScissor(x, listTop, right, listBottom);
        int y = listTop - listScroll;
        for (int i = 0; i < visible.size(); i++) {
            if (y + ROW_HEIGHT >= listTop && y <= listBottom) {
                Summary summary = visible.get(i);
                boolean selected = summary.id().equals(lastSelected);
                boolean hover = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT
                        && mouseY >= listTop && mouseY < listBottom;
                if (selected) {
                    graphics.fill(x, y, right, y + ROW_HEIGHT, ROW_SELECTED_COLOR);
                } else if (hover) {
                    graphics.fill(x, y, right, y + ROW_HEIGHT, ROW_HOVER_COLOR);
                } else if (i % 2 == 0) {
                    graphics.fill(x, y, right, y + ROW_HEIGHT, ROW_STRIPE_COLOR);
                }
                int textY = y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
                Component info = Component.translatable(KEY + "entry", summary.level(), summary.distance());
                int infoWidth = font.width(info);
                FormattedCharSequence name = ellipsize(Component.literal(summary.name()),
                        right - x - infoWidth - 10);
                graphics.drawString(font, name, x + 3, textY,
                        summary.active() ? (selected ? ACCENT_COLOR : VALUE_COLOR) : RUINS_COLOR, false);
                graphics.drawString(font, info, right - 3 - infoWidth, textY, LABEL_COLOR, false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + columnWidth() - SCROLL_BAR_WIDTH, listTop, listBottom - listTop,
                visible.size() * ROW_HEIGHT, listScroll, maxListScroll());
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = detailsLeft();
        int right = x + columnWidth() - SCROLL_BAR_WIDTH - 1;
        int titleY = bodyTop() + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
        if (details == null) {
            graphics.drawString(font, Component.translatable(KEY + "no_selection"), x + 3, titleY, EMPTY_COLOR,
                    false);
            return;
        }
        graphics.drawString(font, ellipsize(details.title(), right - x - 3), x + 3, titleY,
                details.active() ? ACCENT_COLOR : RUINS_COLOR, false);
        int contentTop = detailsTop();
        int contentBottom = detailsBottom();
        graphics.enableScissor(x, contentTop, right, contentBottom);
        int y = contentTop - detailsScroll;
        List<Row> rows = details.rows();
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW_HEIGHT >= contentTop && y <= contentBottom) {
                Row row = rows.get(i);
                if (i % 2 == 0) {
                    graphics.fill(x, y, right, y + ROW_HEIGHT, ROW_STRIPE_COLOR);
                }
                int textY = y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
                int available = right - x - 6;
                FormattedCharSequence label = ellipsize(row.label(), available / 2);
                FormattedCharSequence value = ellipsize(row.value(), available - font.width(label) - 6);
                graphics.drawString(font, label, x + 3, textY, LABEL_COLOR, false);
                graphics.drawString(font, value, right - 3 - font.width(value), textY, VALUE_COLOR, false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + columnWidth() - SCROLL_BAR_WIDTH, contentTop, contentBottom - contentTop,
                rows.size() * ROW_HEIGHT, detailsScroll, maxDetailsScroll());
    }

    private static void renderScrollBar(GuiGraphics graphics, int x, int trackTop, int trackHeight,
            int contentHeight, int scroll, int max) {
        if (max <= 0 || contentHeight <= 0) {
            return;
        }
        int thumbHeight = Math.max(8, trackHeight * trackHeight / contentHeight);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll / max;
        graphics.fill(x, trackTop, x + SCROLL_BAR_WIDTH, trackTop + trackHeight, SCROLL_TRACK_COLOR);
        graphics.fill(x, thumbTop, x + SCROLL_BAR_WIDTH, thumbTop + thumbHeight, FRAME_GOLD);
    }

    /** The text, cut with "…" at the end if it is wider than the maximum width. */
    private FormattedCharSequence ellipsize(Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        String ellipsis = "…";
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ellipsis)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
