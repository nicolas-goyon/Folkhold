package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDialog;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiKeyValueList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiMenu;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiOverlay;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRow;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTextField;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload.Stock;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload.Summary;
import io.github.nicolasgoyon.folkhold.world.network.AdminResultPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The admin screen: all settlements of the world, for an operator (see
 * {@link io.github.nicolasgoyon.folkhold.world.admin.AdminActions}). The command {@code /folkhold admin} opens it.
 *
 * <ul>
 *   <li>Header: refresh, the simulation menu (one tick or one cycle for all settlements, the discovery of the
 *       villages around the operator), and the caravans and quests screen.</li>
 *   <li>Left: the settlements. A text filter (name, id or faction) and three chips (state, level range, sort order)
 *       select them. A click on a settlement shows its details. The name of a settlement that is not generated (it
 *       exists only in the simulation) is in italics.</li>
 *   <li>Right: the selected settlement. Its badges (level, state, alert), the action menus (village, economy,
 *       garrison, world), the teleport button, and its rows in two columns with the largest stocks.</li>
 *   <li>The status line shows the result of each action.</li>
 *   <li>A dangerous action opens a confirmation dialog: to ruin a settlement, the operator types its name.</li>
 * </ul>
 * In the compact layout, the screen shows the list or the details, with a back button. The screen keeps the filters
 * and the selection for the next opening.
 */
public class AdminScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.admin.";
    private static final int LIST_MIN = 140;
    private static final int LIST_MAX = 172;
    private static final int CHIP = 14;
    private static final int LIST_ROW = 14;
    private static final int DETAILS_HEADER = 24;
    private static final int HEADER_BUTTON = 14;
    private static final int SLOT = 18;
    private static final String MENU_CLOSED = " ▾";
    private static final String MENU_OPEN = " ▴";

    /** Filter on the state of the settlements. */
    private enum StateFilter {
        ALL, ACTIVE, RUINS, NOT_GENERATED;

        boolean accepts(Summary summary) {
            return switch (this) {
                case ALL -> true;
                case ACTIVE -> summary.active();
                case RUINS -> !summary.active();
                case NOT_GENERATED -> !summary.generated();
            };
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
    private static StateFilter lastState = StateFilter.ALL;
    private static Sort lastSort = Sort.DISTANCE;
    @Nullable
    private static String lastSelected;

    private List<Summary> settlements;
    private List<Summary> visible = List.of();
    @Nullable
    private AdminDetailsPayload details;
    /** In the compact layout: true to show the details, false to show the list. */
    private boolean compactDetails;
    private int listScroll;

    @Nullable
    private SettlementList list;
    private UiRect listArea = new UiRect(0, 0, 0, 0);
    private UiRect searchRow = listArea;
    private UiRect detailsArea = listArea;
    private UiRect stockStrip = listArea;

    public AdminScreen(List<Summary> settlements) {
        super(Component.translatable(KEY + "title"));
        this.settlements = settlements;
    }

    // ------------------------------------------------------------------ network

    /** Opens the screen, or updates the list of the open screen. Called by the network handler. */
    public static void open(AdminListPayload payload) {
        AdminScreen screen = current();
        if (screen != null) {
            screen.settlements = payload.settlements();
            screen.applyFilters();
            return;
        }
        Minecraft.getInstance().setScreen(new AdminScreen(payload.settlements()));
        if (lastSelected != null) {
            send("details", lastSelected, 0);
        }
    }

    /** Shows the details of a settlement in the open screen. Called by the network handler. */
    public static void showDetails(AdminDetailsPayload payload) {
        AdminScreen screen = current();
        if (screen != null && payload.id().equals(lastSelected)) {
            screen.details = payload;
            screen.rebuild();
        }
    }

    /** Shows the result of an action: in the status line of the open screen, else in the chat. */
    public static void showResult(AdminResultPayload payload) {
        AdminScreen screen = current();
        if (screen != null) {
            screen.showStatus(payload.message(), payload.success() ? UiTone.GOOD : UiTone.BAD);
        } else {
            Minecraft.getInstance().gui.getChat().addMessage(payload.message());
        }
    }

    /** The open admin screen, also behind a menu or a dialog, or null. */
    @Nullable
    private static AdminScreen current() {
        Screen screen = Minecraft.getInstance().screen;
        while (screen instanceof UiOverlay overlay) {
            screen = overlay.parent();
        }
        return screen instanceof AdminScreen admin ? admin : null;
    }

    /** Makes the widgets again from the state. The list keeps its scroll and its keyboard focus. */
    private void rebuild() {
        boolean listFocused = list != null && getFocused() == list;
        rebuildWidgets();
        if (listFocused && list != null) {
            setFocused(list);
        }
    }

    private static void send(String action, String settlementId, int value) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, settlementId, value));
    }

    private void sendSelected(String action, int value) {
        if (details != null) {
            send(action, details.id(), value);
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    @Nullable
    protected Component footerHint() {
        return null;
    }

    private boolean showsList() {
        return !isCompact() || !compactDetails || details == null;
    }

    private boolean showsDetails() {
        return !isCompact() || (compactDetails && details != null);
    }

    @Override
    protected void initBody(UiRect body) {
        if (list != null) {
            listScroll = list.scroll();
        }
        list = null;
        initHeaderButtons();
        UiRect left;
        UiRect right;
        if (!isCompact()) {
            int listWidth = Math.max(LIST_MIN, Math.min(LIST_MAX, body.width() * 32 / 100));
            left = body.left(listWidth);
            right = body.afterLeft(listWidth, 1);
        } else {
            left = body;
            right = body;
        }
        listArea = showsList() ? left : new UiRect(0, 0, 0, 0);
        detailsArea = showsDetails() ? right.inset(UiTheme.PADDING) : new UiRect(0, 0, 0, 0);
        stockStrip = new UiRect(0, 0, 0, 0);
        if (showsList()) {
            initList(left);
        }
        if (showsDetails() && details != null) {
            initDetails(detailsArea, details);
        }
    }

    private void initHeaderButtons() {
        UiRect header = panel().top(HEADER);
        int y = header.y() + (header.height() - HEADER_BUTTON) / 2;
        int x = header.right() - UiTheme.PADDING - 14 - UiTheme.GAP;
        boolean compact = isCompact();
        Component groups = Component.translatable(KEY + (compact ? "action.groups_short" : "action.groups"));
        x -= headerButton(x, y, groups, button -> send("groups", "", 0)).getWidth() + UiTheme.GAP;
        Component simulation = Component.translatable(KEY + "menu.simulation").append(MENU_CLOSED);
        UiButton simulationButton = headerButton(x, y, simulation, button -> {
            button.setMessage(Component.translatable(KEY + "menu.simulation").append(MENU_OPEN));
            UiMenu.open(this, rectOf(button), simulation, List.of(
                    UiMenu.Action.of(Component.translatable(KEY + "action.tick_all"), () -> send("tick_all", "", 0)),
                    UiMenu.Action.of(Component.translatable(KEY + "action.cycle_all"),
                            () -> send("cycle_all", "", 0)),
                    new UiMenu.Separator(),
                    UiMenu.Action.of(Component.translatable(KEY + "action.discover"), () -> send("discover", "", 0))));
        });
        x -= simulationButton.getWidth() + UiTheme.GAP;
        Component refresh = compact ? Component.literal("⟳") : Component.translatable(KEY + "action.refresh");
        headerButton(x, y, refresh, button -> send("refresh", "", 0))
                .setDetails(Component.translatable(KEY + "action.refresh"));
    }

    /** Adds a header button that ends at the x position. */
    private UiButton headerButton(int right, int y, Component label, Consumer<UiButton> action) {
        int w = font.width(label) + 10;
        return addRenderableWidget(UiButton.normal(new UiRect(right - w, y, w, HEADER_BUTTON), label, action));
    }

    private static UiRect rectOf(UiButton button) {
        return new UiRect(button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    // ------------------------------------------------------------------ list

    private void initList(UiRect area) {
        UiRect inner = area.inset(UiTheme.PADDING);
        int countWidth = font.width(Component.translatable(KEY + "count", 999, 999)) + UiTheme.GAP;
        searchRow = inner.top(UiTheme.CONTROL);
        UiTextField search = new UiTextField(searchRow.beforeRight(countWidth, 0), Component.translatable(KEY
                + "filter"), Component.translatable(KEY + "filter"));
        search.setValue(lastText);
        search.setResponder(text -> {
            lastText = text;
            applyFilters();
        });
        addRenderableWidget(search);

        int chipY = searchRow.bottom() + UiTheme.GAP;
        int x = inner.x();
        Component state = Component.translatable(KEY + "state_filter." + lastState.name().toLowerCase(Locale.ROOT));
        x += chip(x, chipY, state, lastState != StateFilter.ALL, button -> {
            lastState = StateFilter.values()[(lastState.ordinal() + 1) % StateFilter.values().length];
            rebuild();
        }) + UiTheme.GAP;
        boolean levelFilter = !lastMinLevel.isEmpty() || !lastMaxLevel.isEmpty();
        x += chip(x, chipY, levelLabel(), levelFilter, this::openLevelMenu) + UiTheme.GAP;
        Component sort = Component.translatable(KEY + "sort." + lastSort.name().toLowerCase(Locale.ROOT));
        chip(x, chipY, sort, false, button -> {
            lastSort = Sort.values()[(lastSort.ordinal() + 1) % Sort.values().length];
            rebuild();
        });

        int listTop = chipY + CHIP + UiTheme.PADDING;
        list = addRenderableWidget(new SettlementList(new UiRect(area.x() + 1, listTop,
                area.width() - (isCompact() ? 2 : 1), area.bottom() - listTop - 1)));
        list.setSelectable(true);
        list.setEmptyText(Component.translatable(KEY + "empty"));
        applyFilters();
        list.setScroll(listScroll);
    }

    /** Adds a filter chip. Returns its width. */
    private int chip(int x, int y, Component label, boolean selected, Consumer<UiButton> action) {
        int w = font.width(label) + 10;
        addRenderableWidget(new UiButton(new UiRect(x, y, w, CHIP), label,
                selected ? UiButton.Style.CHIP_SELECTED : UiButton.Style.CHIP, action));
        return w;
    }

    private static Component levelLabel() {
        if (lastMinLevel.isEmpty() && lastMaxLevel.isEmpty()) {
            return Component.translatable(KEY + "levels.all");
        }
        if (lastMaxLevel.isEmpty()) {
            return Component.translatable(KEY + "levels.min", lastMinLevel);
        }
        if (lastMinLevel.isEmpty()) {
            return Component.translatable(KEY + "levels.max", lastMaxLevel);
        }
        return Component.translatable(KEY + "levels.range", lastMinLevel, lastMaxLevel);
    }

    private void openLevelMenu(UiButton button) {
        List<UiMenu.Item> items = new ArrayList<>();
        items.add(new UiMenu.Value(Component.translatable(KEY + "min_level"), lastMinLevel,
                text -> text.matches("\\d{0,3}"), text -> {
                    lastMinLevel = text;
                    rebuild();
                }));
        items.add(new UiMenu.Value(Component.translatable(KEY + "max_level"), lastMaxLevel,
                text -> text.matches("\\d{0,3}"), text -> {
                    lastMaxLevel = text;
                    rebuild();
                }));
        items.add(new UiMenu.Separator());
        UiMenu.Action clear = UiMenu.Action.of(Component.translatable(KEY + "levels.all"), () -> {
            lastMinLevel = "";
            lastMaxLevel = "";
            rebuild();
        });
        items.add(lastMinLevel.isEmpty() && lastMaxLevel.isEmpty() ? clear.disabled(Component.empty()) : clear);
        UiMenu.open(this, rectOf(button), Component.translatable(KEY + "levels.menu"), items);
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
        if (list != null) {
            list.setItems(visible);
            int index = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i).id().equals(lastSelected)) {
                    index = i;
                }
            }
            list.setSelectedIndex(index);
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void select(Summary summary) {
        if (!summary.id().equals(lastSelected)) {
            details = null;
        }
        lastSelected = summary.id();
        send("details", summary.id(), 0);
        if (isCompact()) {
            compactDetails = true;
        }
        rebuild();
    }

    /** The distance in blocks, or in kilometers with one decimal. */
    private static Component distance(int blocks) {
        if (blocks < 1000) {
            return Component.translatable(KEY + "distance.m", blocks);
        }
        return Component.translatable(KEY + "distance.km", blocks / 1000, blocks % 1000 / 100);
    }

    /** The list of the settlements. */
    private final class SettlementList extends UiList<Summary> {
        SettlementList(UiRect rect) {
            super(rect);
        }

        @Override
        protected int rowHeight(Summary item, int index) {
            return LIST_ROW;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Summary item, int index, UiRect rect, boolean hovered,
                boolean selected, int mouseX, int mouseY) {
            if (selected) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_RAISED);
                UiDraw.fill(graphics, rect.left(2), UiTheme.GILT);
            } else if (hovered) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_HOVER);
            } else if (index % 2 == 0) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_ROW);
            }
            UiRect text = rect.inset(UiTheme.PADDING, 0);
            int y = UiText.textY(text);
            Component info = Component.translatable(KEY + "entry", item.level(), distance(item.distance()));
            int infoWidth = UiText.drawRight(graphics, font, info, text.right(), y, text.width() / 2,
                    UiTheme.TEXT_MUTED);
            MutableComponent name = item.active() ? Component.literal(item.name())
                    : Component.translatable(KEY + "ruins_suffix", item.name());
            if (!item.generated()) {
                // A settlement of the discovery: it exists only in the simulation.
                name.withStyle(ChatFormatting.ITALIC);
            }
            int color = !item.active() ? UiTheme.TEXT_FAINT : selected ? UiTheme.GOLD : UiTheme.TEXT;
            UiText.draw(graphics, font, name, text.x(), y, text.width() - infoWidth - UiTheme.PADDING, color);
        }

        @Override
        protected void onSelect(Summary item, int index) {
            select(item);
        }

        @Override
        protected Component narration(Summary item) {
            return Component.literal(item.name());
        }
    }

    // ------------------------------------------------------------------ details

    private void initDetails(UiRect area, AdminDetailsPayload shown) {
        UiRect menuRow = new UiRect(area.x(), area.y() + DETAILS_HEADER + UiTheme.PADDING, area.width(),
                UiTheme.CONTROL);
        int x = menuRow.x();
        if (isCompact()) {
            UiRect back = new UiRect(x, menuRow.y(), UiTheme.CONTROL, UiTheme.CONTROL);
            addRenderableWidget(UiButton.normal(back, Component.literal("←"), button -> {
                compactDetails = false;
                rebuild();
            }).setDetails(Component.translatable(KEY + "back")));
            x = back.right() + UiTheme.GAP;
        }
        x += menuButton(x, menuRow.y(), "village", villageItems(shown)) + UiTheme.GAP;
        x += menuButton(x, menuRow.y(), "economy", economyItems(shown)) + UiTheme.GAP;
        x += menuButton(x, menuRow.y(), "garrison", garrisonItems(shown)) + UiTheme.GAP;
        x += menuButton(x, menuRow.y(), "world", worldItems(shown));
        // The teleport button shows if it fits: the village menu also has the teleport action.
        Component teleport = Component.translatable(KEY + "action.teleport");
        int teleportWidth = font.width(teleport) + 12;
        if (x + UiTheme.PADDING + teleportWidth <= menuRow.right()) {
            addRenderableWidget(UiButton.primary(menuRow.rightPart(teleportWidth), teleport, button -> teleport()));
        }

        UiRect content = area.belowTop(menuRow.bottom() - area.y(), UiTheme.PADDING);
        List<UiRow> left = new ArrayList<>();
        List<UiRow> right = new ArrayList<>();
        List<Section> sections = shown.sections();
        for (int i = 0; i < sections.size(); i++) {
            List<UiRow> column = isCompact() || i % 2 == 0 ? left : right;
            if (!column.isEmpty()) {
                column.add(UiRow.heading(Component.empty()));
            }
            column.add(UiRow.heading(sections.get(i).heading()));
            column.addAll(InfoRows.of(sections.get(i).rows()));
        }
        int stockHeight = shown.stocks().isEmpty() ? 0 : SLOT + UiTheme.PADDING;
        UiKeyValueList stockColumn;
        if (isCompact()) {
            stockColumn = addColumn(content, left, stockHeight);
        } else {
            addColumn(content.column(0, 2, UiTheme.PADDING), left, 0);
            stockColumn = addColumn(content.column(1, 2, UiTheme.PADDING), right, stockHeight);
        }
        if (stockHeight > 0) {
            stockStrip = new UiRect(stockColumn.getX(), stockColumn.getY() + stockColumn.getHeight()
                    + UiTheme.PADDING, stockColumn.getWidth(), SLOT);
        }
    }

    /** Adds a column of rows. The column is as high as its rows, and leaves the reserved height below it. */
    private UiKeyValueList addColumn(UiRect rect, List<UiRow> rows, int reserved) {
        UiKeyValueList column = new UiKeyValueList(rect);
        column.setItems(rows);
        int height = Math.min(column.contentHeight(), rect.height() - reserved);
        column = new UiKeyValueList(rect.top(Math.max(0, height)));
        column.setItems(rows);
        return addRenderableWidget(column);
    }

    /** Adds a menu button. Returns its width. */
    private int menuButton(int x, int y, String name, List<UiMenu.Item> items) {
        Component label = Component.translatable(KEY + "menu." + name);
        Component closed = label.copy().append(MENU_CLOSED);
        int w = font.width(closed) + 10;
        addRenderableWidget(UiButton.normal(new UiRect(x, y, w, UiTheme.CONTROL), closed, button -> {
            button.setMessage(label.copy().append(MENU_OPEN));
            UiMenu.open(this, rectOf(button), label, items);
        }));
        return w;
    }

    private List<UiMenu.Item> villageItems(AdminDetailsPayload shown) {
        List<UiMenu.Item> items = new ArrayList<>();
        items.add(UiMenu.Action.of(Component.translatable(KEY + "action.teleport_heart"), this::teleport));
        items.add(new UiMenu.Value(Component.translatable(KEY + "action.level_set"),
                String.valueOf(shown.level() + 1), text -> text.matches("\\d{0,4}"),
                text -> number(text, value -> sendSelected("level_set", value))));
        items.add(new UiMenu.Value(Component.translatable(KEY + "action.experience_add"), "",
                text -> text.matches("-?\\d{0,7}"), text -> number(text, value -> sendSelected("experience_add",
                        value))));
        items.add(new UiMenu.Separator());
        UiMenu.Action restore = UiMenu.Action.of(Component.translatable(KEY + "action.restore"),
                () -> sendSelected("restore", 0));
        items.add(shown.active() ? restore.disabled(Component.translatable(KEY + "menu.only_ruins")) : restore);
        UiMenu.Action ruin = UiMenu.Action.danger(Component.translatable(KEY + "action.ruin"),
                () -> confirmRuin(shown));
        items.add(shown.active() ? ruin : ruin.disabled(Component.translatable(KEY + "menu.already_ruins")));
        return items;
    }

    private List<UiMenu.Item> economyItems(AdminDetailsPayload shown) {
        return List.of(
                UiMenu.Action.of(Component.translatable(KEY + "action.cycle"), () -> sendSelected("cycle", 0)),
                new UiMenu.Separator(),
                UiMenu.Action.danger(Component.translatable(KEY + "action.economy_reset"),
                        () -> confirmEconomyReset(shown)));
    }

    private List<UiMenu.Item> garrisonItems(AdminDetailsPayload shown) {
        List<UiMenu.Item> items = new ArrayList<>();
        String[] states = {"calm", "alert", "assault"};
        for (int i = 0; i < states.length; i++) {
            int value = i;
            UiMenu.Action action = UiMenu.Action.of(Component.translatable(KEY + "action." + states[i]),
                    () -> sendSelected("alert", value));
            if (!shown.heartLoaded()) {
                action = action.disabled(Component.translatable(KEY + "menu.heart_unloaded"));
            } else if (shown.alert() == i) {
                action = action.disabled(Component.translatable(KEY + "menu.current"));
            }
            items.add(action);
        }
        items.add(new UiMenu.Separator());
        UiMenu.Action bells = UiMenu.Action.of(Component.translatable(KEY + "action.bells"),
                () -> sendSelected("bells", 0));
        items.add(shown.heartLoaded() ? bells : bells.disabled(Component.translatable(KEY + "menu.heart_unloaded")));
        return items;
    }

    private List<UiMenu.Item> worldItems(AdminDetailsPayload shown) {
        String y = shown.hasY() ? String.valueOf(shown.y()) : "~";
        String position = shown.x() + " " + y + " " + shown.z();
        return List.of(
                UiMenu.Action.of(Component.translatable(KEY + "action.copy_position"), () -> copy(position)),
                UiMenu.Action.of(Component.translatable(KEY + "action.copy_id"), () -> copy(shown.id())),
                UiMenu.Action.of(Component.translatable(KEY + "action.copy_teleport"),
                        () -> copy("/tp @s " + position)));
    }

    private void teleport() {
        sendSelected("teleport", 0);
        onClose();
    }

    private void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        showStatus(Component.translatable(KEY + "copied", text), UiTone.GOOD);
    }

    private void number(String text, IntConsumer action) {
        try {
            action.accept(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            showStatus(Component.translatable(KEY + "invalid_number"), UiTone.BAD);
        }
    }

    private void confirmRuin(AdminDetailsPayload shown) {
        String id = shown.id();
        UiDialog.builder(this, Component.translatable(KEY + "ruin.title", shown.title()))
                .message(Component.translatable(KEY + "ruin.message"))
                .bullet(Component.translatable(KEY + "ruin.stock"))
                .bullet(Component.translatable(KEY + "ruin.restore"))
                .danger()
                .typeToConfirm(shown.title().getString())
                .confirm(Component.translatable(KEY + "ruin.confirm"), () -> send("ruin", id, 0))
                .open();
    }

    private void confirmEconomyReset(AdminDetailsPayload shown) {
        String id = shown.id();
        UiDialog.builder(this, Component.translatable(KEY + "economy_reset.title", shown.title()))
                .message(Component.translatable(KEY + "economy_reset.message"))
                .danger()
                .confirm(Component.translatable(KEY + "economy_reset.confirm"), () -> send("economy_reset", id, 0))
                .open();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        if (showsList()) {
            Component count = Component.translatable(KEY + "count", visible.size(), settlements.size());
            UiText.drawRight(graphics, font, count, searchRow.right(), UiText.textY(searchRow), searchRow.width() / 3,
                    UiTheme.TEXT_MUTED);
        }
        if (!isCompact()) {
            UiDraw.vLine(graphics, listArea.right(), body.y(), body.height() - 1, UiTheme.GILT);
        }
        if (!showsDetails()) {
            return;
        }
        if (details == null) {
            Component text = Component.translatable(KEY + (lastSelected == null ? "no_selection" : "loading"));
            UiText.drawCentered(graphics, font, text, detailsArea.top(UiTheme.CONTROL), UiTheme.TEXT_MUTED);
            return;
        }
        renderDetailsHeader(graphics, detailsArea.top(DETAILS_HEADER), details);
        UiDraw.hLine(graphics, detailsArea.x() - UiTheme.PADDING + 1,
                detailsArea.y() + DETAILS_HEADER + UiTheme.PADDING / 2 - 1,
                detailsArea.width() + 2 * UiTheme.PADDING - 2, UiTheme.WOOD_EDGE);
        renderStocks(graphics, details);
    }

    private void renderDetailsHeader(GuiGraphics graphics, UiRect header, AdminDetailsPayload shown) {
        List<Component> texts = new ArrayList<>();
        List<UiTone> tones = new ArrayList<>();
        texts.add(Component.translatable(KEY + "badge.level", shown.level(), shown.tier()));
        tones.add(UiTone.GOLD);
        texts.add(Component.translatable(KEY + "state." + (shown.active() ? "active" : "ruins")));
        tones.add(shown.active() ? UiTone.GOOD : UiTone.MUTED);
        if (!shown.hasY()) {
            texts.add(Component.translatable(KEY + "badge.not_generated"));
            tones.add(UiTone.INFO);
        }
        if (shown.heartLoaded()) {
            String[] states = {"calm", "alert", "assault"};
            int alert = Math.max(0, Math.min(states.length - 1, shown.alert()));
            texts.add(Component.translatable(KEY + "badge.alert",
                    Component.translatable(KEY + "alert." + states[alert])));
            tones.add(alert == 0 ? UiTone.GOOD : alert == 1 ? UiTone.WARN : UiTone.BAD);
        }
        int right = header.right();
        for (int i = texts.size() - 1; i >= 0; i--) {
            int w = UiDraw.badgeWidth(font, texts.get(i));
            if (right - w < header.x() + header.width() / 3) {
                break;
            }
            right -= w;
            UiDraw.badge(graphics, font, right, header.y() + 1, texts.get(i), tones.get(i));
            right -= UiTheme.GAP;
        }
        UiText.draw(graphics, font, shown.title(), header.x(), header.y() + 2, right - header.x() - UiTheme.GAP,
                shown.active() ? UiTheme.TEXT : UiTheme.TEXT_FAINT);
        String id = shown.id().length() > 6 ? shown.id().substring(0, 6) + "…" : shown.id();
        Component subtitle = Component.translatable(KEY + "subtitle", shown.faction(), id,
                shown.biome().isEmpty() ? "-" : shown.biome());
        UiText.draw(graphics, font, subtitle, header.x(), header.y() + 14, header.width(), UiTheme.TEXT_MUTED);
    }

    private void renderStocks(GuiGraphics graphics, AdminDetailsPayload shown) {
        if (stockStrip.width() <= 0) {
            return;
        }
        int x = stockStrip.x();
        for (Stock stock : shown.stocks()) {
            if (x + SLOT > stockStrip.right()) {
                break;
            }
            UiDraw.itemSlot(graphics, UiDraw.stack(stock.itemId()), x, stockStrip.y());
            String count = stock.count() >= 1000 ? stock.count() / 1000 + "k" : String.valueOf(stock.count());
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.drawString(font, count, x + SLOT - 1 - font.width(count), stockStrip.y() + SLOT - 8,
                    0xFFFFFFFF, true);
            graphics.pose().popPose();
            x += SLOT + UiTheme.GAP;
        }
        if (shown.moreStocks() > 0) {
            Component more = Component.translatable(KEY + "stock.more", shown.moreStocks());
            graphics.drawString(font, more, x + 1, UiText.textY(stockStrip), UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (details == null || stockStrip.width() <= 0 || !stockStrip.contains(mouseX, mouseY)) {
            return;
        }
        int index = (mouseX - stockStrip.x()) / (SLOT + UiTheme.GAP);
        if (index < 0 || index >= details.stocks().size()
                || mouseX - stockStrip.x() - index * (SLOT + UiTheme.GAP) >= SLOT) {
            return;
        }
        Stock stock = details.stocks().get(index);
        ItemStack item = UiDraw.stack(stock.itemId());
        Component name = item.isEmpty() ? Component.literal(stock.itemId()) : item.getHoverName();
        graphics.renderComponentTooltip(font, List.of(name,
                Component.translatable(KEY + "stock.count", stock.count())), mouseX, mouseY);
    }
}
