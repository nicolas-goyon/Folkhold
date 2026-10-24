package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiKeyValueList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiOverlay;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRow;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTabs;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTextField;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Faction;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Village;
import io.github.nicolasgoyon.folkhold.world.network.JournalRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The journal of the player: the villages and the factions that the player discovered (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.Journals}). The key of the journal (default J) opens it.
 *
 * <ul>
 *   <li>Tab "Villages": a text filter and two chips (discovered or visited, sort order) select the villages. The name
 *       of a village that the player only discovered is in italics. A click shows the details of the village: its
 *       faction, the reputation of the player in it, its position and direction, the day of the discovery and of
 *       the last visit.</li>
 *   <li>Tab "Factions": the factions of the discovered villages, with their culture, their attitude and the
 *       reputation of the player.</li>
 *   <li>Tab "Routes": the player selects two discovered villages, and the screen shows the route of the caravans
 *       between them on a map (see {@link ClientRouteView}). The color of the route shows the surface. A button shows
 *       the route in the world ({@link ClientRouteOverlay}).</li>
 * </ul>
 * In the compact layout, the screen shows the list or the details, with a back button. The screen keeps the tab, the
 * filters and the selection for the next opening.
 */
public class JournalScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.journal.";
    private static final int LIST_MIN = 140;
    private static final int LIST_MAX = 180;
    private static final int CHIP = 14;
    private static final int LIST_ROW = 14;
    private static final int DETAILS_HEADER = 24;
    /** The index of the tab of the villages. */
    private static final int TAB_VILLAGES = 0;
    /** The index of the tab of the routes, after the tab of the factions. */
    private static final int TAB_ROUTES = 2;
    private static final int LEGEND = 12;
    private static final int MAP_MARGIN = 8;
    /** The smallest span of the map, in blocks. */
    private static final int MAP_MIN_SPAN = 64;
    /** The background of the map. */
    private static final int MAP_BACKGROUND = 0xFFFFFFFF;
    /** The grey of the lowest floor of the relief (0 to 255). */
    private static final int RELIEF_LOW = 250;
    /** The grey of the highest floor of the relief (0 to 255). */
    private static final int RELIEF_HIGH = 175;
    /** The smallest height range of the relief, in blocks: a flat area stays light. */
    private static final int RELIEF_MIN_RANGE = 24;
    /** The color of the water on the map. */
    private static final int RELIEF_WATER = 0xFFD5E6F3;
    /** The width of the progress bar, in pixels. */
    private static final int PROGRESS_BAR = 60;

    /** The end of the route that the next click in the list of the tab "Routes" sets. */
    private enum RouteSlot {
        FROM, TO
    }

    /** Filter on the visits of the villages. */
    private enum VisitFilter {
        ALL, VISITED, DISCOVERED;

        boolean accepts(Village village) {
            return switch (this) {
                case ALL -> true;
                case VISITED -> village.visited();
                case DISCOVERED -> !village.visited();
            };
        }
    }

    /** Sort order of the villages. */
    private enum Sort {
        DISTANCE(Comparator.comparingInt(Village::distance)),
        NAME(Comparator.comparing(village -> village.name().toLowerCase(Locale.ROOT))),
        REPUTATION(Comparator.comparing(Village::reputation).reversed().thenComparingInt(Village::distance));

        @SuppressWarnings("ImmutableEnumChecker") // The comparators have no state.
        private final Comparator<Village> comparator;

        Sort(Comparator<Village> comparator) {
            this.comparator = comparator;
        }
    }

    // State kept between two openings.
    private static int lastTab = TAB_VILLAGES;
    private static String lastText = "";
    private static VisitFilter lastFilter = VisitFilter.ALL;
    private static Sort lastSort = Sort.DISTANCE;
    @Nullable
    private static String lastVillage;
    @Nullable
    private static String lastFaction;
    private static RouteSlot lastSlot = RouteSlot.FROM;

    private JournalPayload journal;
    private List<Village> visibleVillages = List.of();
    /** In the compact layout: true to show the details, false to show the list. */
    private boolean compactDetails;
    private int listScroll;
    @Nullable
    private UiList<?> list;
    private UiRect listArea = new UiRect(0, 0, 0, 0);
    private UiRect searchRow = listArea;
    private UiRect detailsArea = listArea;
    private UiRect mapArea = listArea;
    /** The area at the right of the button of the line in the world. */
    private UiRect lineInfo = listArea;
    /** The row of the state of the calculation, above the map. */
    private UiRect statusArea = listArea;

    public JournalScreen(JournalPayload journal) {
        super(Component.translatable(KEY + "title"));
        this.journal = journal;
    }

    // ------------------------------------------------------------------ network and key

    /** Opens the journal, or updates the open journal. Called by the network handler. */
    public static void open(JournalPayload payload) {
        String from = ClientRouteView.fromId();
        String to = ClientRouteView.toId();
        ClientRouteView.select(known(payload, from) ? from : null, known(payload, to) ? to : null);
        JournalScreen screen = current();
        if (screen != null) {
            screen.journal = payload;
            screen.rebuild();
            return;
        }
        ClientRouteView.refresh();
        Minecraft.getInstance().setScreen(new JournalScreen(payload));
    }

    /** True if a village is in the journal. */
    private static boolean known(JournalPayload payload, @Nullable String id) {
        return id != null && payload.villages().stream().anyMatch(village -> village.id().equals(id));
    }

    /** The server sent a route: the open journal shows it. Called by {@link ClientRouteView}. */
    static void onRoute() {
        JournalScreen screen = current();
        if (screen != null && lastTab == TAB_ROUTES) {
            screen.rebuild();
        }
    }

    /** The key of the journal asks the server for the journal of the player. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (JournalKeys.OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(JournalRequestPayload.INSTANCE);
            }
        }
    }

    /** The open journal, also behind a menu or a dialog, or null. */
    @Nullable
    private static JournalScreen current() {
        return UiOverlay.rootScreen() instanceof JournalScreen screen ? screen : null;
    }

    /** Forgets the state kept between two openings. Called when the player leaves a world or a server. */
    public static void clear() {
        lastTab = TAB_VILLAGES;
        lastText = "";
        lastFilter = VisitFilter.ALL;
        lastSort = Sort.DISTANCE;
        lastVillage = null;
        lastFaction = null;
        lastSlot = RouteSlot.FROM;
    }

    private void rebuild() {
        boolean listFocused = list != null && getFocused() == list;
        rebuildWidgets();
        if (listFocused && list != null) {
            setFocused(list);
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    @Nullable
    protected Component footerHint() {
        return null;
    }

    @Override
    protected Component subtitle() {
        return Component.translatable(KEY + "subtitle", journal.villages().size(), journal.factions().size());
    }

    private boolean hasSelection() {
        return switch (lastTab) {
            case TAB_VILLAGES -> selectedVillage().isPresent();
            case TAB_ROUTES -> routeEnd(ClientRouteView.fromId()).isPresent()
                    && routeEnd(ClientRouteView.toId()).isPresent();
            default -> selectedFaction().isPresent();
        };
    }

    private boolean showsList() {
        return !isCompact() || !compactDetails || !hasSelection();
    }

    private boolean showsDetails() {
        return !isCompact() || (compactDetails && hasSelection());
    }

    @Override
    protected void initBody(UiRect body) {
        if (list != null) {
            listScroll = list.scroll();
        }
        list = null;
        UiRect tabsRect = body.inset(UiTheme.PADDING, 4).top(UiTheme.CONTROL);
        UiTabs tabs = addRenderableWidget(new UiTabs(tabsRect, List.of(
                Component.translatable(KEY + "tab.villages", journal.villages().size()),
                Component.translatable(KEY + "tab.factions", journal.factions().size()),
                Component.translatable(KEY + "tab.routes")), lastTab, index -> {
                    lastTab = index;
                    compactDetails = false;
                    listScroll = 0;
                    rebuild();
                }));
        setSectionTabs(tabs);
        UiRect content = body.belowTop(4 + UiTheme.CONTROL, 2);
        UiRect left;
        UiRect right;
        if (!isCompact()) {
            int listWidth = Math.max(LIST_MIN, Math.min(LIST_MAX, content.width() * 34 / 100));
            left = content.left(listWidth);
            right = content.afterLeft(listWidth, 1);
        } else {
            left = content;
            right = content;
        }
        listArea = showsList() ? left : new UiRect(0, 0, 0, 0);
        detailsArea = showsDetails() ? right.inset(UiTheme.PADDING) : new UiRect(0, 0, 0, 0);
        mapArea = new UiRect(0, 0, 0, 0);
        lineInfo = mapArea;
        statusArea = mapArea;
        if (showsList()) {
            switch (lastTab) {
                case TAB_VILLAGES, TAB_ROUTES -> initVillageList(left);
                default -> initFactionList(left);
            }
        }
        if (showsDetails()) {
            if (isCompact()) {
                UiRect back = new UiRect(detailsArea.x(), detailsArea.y(), UiTheme.CONTROL, UiTheme.CONTROL);
                addRenderableWidget(UiButton.normal(back, Component.literal("←"), button -> {
                    compactDetails = false;
                    rebuild();
                }).setDetails(Component.translatable(KEY + "back")));
            }
            UiRect rows = detailsArea.belowTop(DETAILS_HEADER + UiTheme.PADDING, 0);
            switch (lastTab) {
                case TAB_VILLAGES -> selectedVillage().ifPresent(village -> addRows(rows, villageRows(village)));
                case TAB_ROUTES -> initRouteDetails(rows);
                default -> selectedFaction().ifPresent(faction -> addRows(rows, factionRows(faction)));
            }
        }
    }

    private void addRows(UiRect rect, List<UiRow> rows) {
        UiKeyValueList column = new UiKeyValueList(rect);
        column.setItems(rows);
        addRenderableWidget(column);
    }

    /** Adds a filter chip. Returns its width. */
    private int chip(int x, int y, Component label, boolean selected, Consumer<UiButton> action) {
        int w = font.width(label) + 10;
        addRenderableWidget(new UiButton(new UiRect(x, y, w, CHIP), label,
                selected ? UiButton.Style.CHIP_SELECTED : UiButton.Style.CHIP, action));
        return w;
    }

    /** The constant after the given one, in the declaration order. The first one follows the last one. */
    @SuppressWarnings("EnumOrdinal") // The chips cycle through the constants in their declaration order.
    private static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    // ------------------------------------------------------------------ villages

    private void initVillageList(UiRect area) {
        UiRect inner = area.inset(UiTheme.PADDING);
        if (lastTab == TAB_ROUTES) {
            inner = initRouteChips(inner);
        }
        searchRow = inner.top(UiTheme.CONTROL);
        UiTextField search = new UiTextField(searchRow, Component.translatable(KEY + "filter"),
                Component.translatable(KEY + "filter"));
        search.setValue(lastText);
        search.setResponder(text -> {
            lastText = text;
            applyVillageFilters();
        });
        addRenderableWidget(search);
        int listTop = searchRow.bottom() + UiTheme.PADDING;
        if (lastTab != TAB_ROUTES) {
            listTop = initVillageChips(inner.x(), searchRow.bottom() + UiTheme.GAP);
        }
        VillageList villages = addRenderableWidget(new VillageList(new UiRect(area.x() + 1, listTop,
                area.width() - (isCompact() ? 2 : 1), area.bottom() - listTop - 1)));
        villages.setSelectable(true);
        villages.setEmptyText(Component.translatable(KEY + "empty_villages"));
        list = villages;
        applyVillageFilters();
        villages.setScroll(listScroll);
    }

    /** Adds the chips of the filter and of the sort order. Returns the top of the list. */
    private int initVillageChips(int x, int chipY) {
        Component filter = Component.translatable(KEY + "visit_filter." + lastFilter.name().toLowerCase(Locale.ROOT));
        x += chip(x, chipY, filter, lastFilter != VisitFilter.ALL, button -> {
            lastFilter = next(lastFilter);
            rebuild();
        }) + UiTheme.GAP;
        Component sort = Component.translatable(KEY + "sort." + lastSort.name().toLowerCase(Locale.ROOT));
        chip(x, chipY, sort, false, button -> {
            lastSort = next(lastSort);
            rebuild();
        });
        return chipY + CHIP + UiTheme.PADDING;
    }

    private void applyVillageFilters() {
        String text = lastText.trim().toLowerCase(Locale.ROOT);
        boolean routes = lastTab == TAB_ROUTES;
        visibleVillages = journal.villages().stream()
                .filter(village -> routes || lastFilter.accepts(village))
                .filter(village -> text.isEmpty() || village.name().toLowerCase(Locale.ROOT).contains(text)
                        || village.faction().getString().toLowerCase(Locale.ROOT).contains(text))
                .sorted(routes ? Sort.DISTANCE.comparator : lastSort.comparator)
                .toList();
        if (list instanceof VillageList villages) {
            villages.setItems(visibleVillages);
            String selected = !routes ? lastVillage
                    : lastSlot == RouteSlot.FROM ? ClientRouteView.fromId() : ClientRouteView.toId();
            int index = -1;
            for (int i = 0; i < visibleVillages.size(); i++) {
                if (visibleVillages.get(i).id().equals(selected)) {
                    index = i;
                }
            }
            villages.setSelectedIndex(index);
        }
    }

    private Optional<Village> selectedVillage() {
        return journal.villages().stream().filter(village -> village.id().equals(lastVillage)).findFirst();
    }

    private List<UiRow> villageRows(Village village) {
        List<UiRow> rows = new ArrayList<>();
        rows.add(UiRow.of(Component.translatable(KEY + "row.faction"), village.faction()));
        rows.add(UiRow.of(Component.translatable(KEY + "row.reputation"), tierText(village.reputation())));
        rows.add(UiRow.of(Component.translatable(KEY + "row.level"),
                Component.translatable(KEY + "level", village.level(), village.tier())));
        rows.add(UiRow.of(Component.translatable(KEY + "row.position"),
                Component.translatable(KEY + "position", village.x(), village.z())));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            rows.add(UiRow.of(Component.translatable(KEY + "row.direction"),
                    HudDirections.toward(player, village.x() + 0.5, village.z() + 0.5)));
        }
        rows.add(UiRow.of(Component.translatable(KEY + "row.discovered"), dayText(village.discovered())));
        rows.add(UiRow.of(Component.translatable(KEY + "row.last_visit"), village.visited()
                ? dayText(village.lastVisit()) : Component.translatable(KEY + "never")));
        return rows;
    }

    /** The list of the villages. */
    private final class VillageList extends UiList<Village> {
        VillageList(UiRect rect) {
            super(rect);
        }

        @Override
        protected int rowHeight(Village item, int index) {
            return LIST_ROW;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Village item, int index, UiRect rect, boolean hovered,
                boolean selected, int mouseX, int mouseY) {
            rowBackground(graphics, rect, index, hovered, selected);
            UiRect text = rect.inset(UiTheme.PADDING, 0);
            int y = UiText.textY(text);
            LocalPlayer player = Minecraft.getInstance().player;
            Component info = player != null ? HudDirections.toward(player, item.x() + 0.5, item.z() + 0.5)
                    : HudDirections.distanceText(item.distance());
            int infoColor = UiTheme.TEXT_MUTED;
            if (lastTab == TAB_ROUTES && item.id().equals(ClientRouteView.fromId())) {
                info = Component.translatable(KEY + "route.from_mark");
                infoColor = UiTheme.GOLD;
            } else if (lastTab == TAB_ROUTES && item.id().equals(ClientRouteView.toId())) {
                info = Component.translatable(KEY + "route.to_mark");
                infoColor = UiTheme.GOLD;
            }
            int infoWidth = UiText.drawRight(graphics, font, info, text.right(), y, text.width() / 2, infoColor);
            MutableComponent name = item.active() ? Component.literal(item.name())
                    : Component.translatable(KEY + "ruins_suffix", item.name());
            if (!item.visited()) {
                name.withStyle(ChatFormatting.ITALIC);
            }
            int color = !item.active() ? UiTheme.TEXT_FAINT : selected ? UiTheme.GOLD : UiTheme.TEXT;
            UiText.draw(graphics, font, name, text.x(), y, text.width() - infoWidth - UiTheme.PADDING, color);
        }

        @Override
        protected void onSelect(Village item, int index) {
            if (lastTab == TAB_ROUTES) {
                pickRouteEnd(item.id());
                return;
            }
            lastVillage = item.id();
            if (isCompact()) {
                compactDetails = true;
            }
            rebuild();
        }

        @Override
        protected Component narration(Village item) {
            return Component.literal(item.name());
        }
    }

    // ------------------------------------------------------------------ factions

    private void initFactionList(UiRect area) {
        UiRect inner = area.inset(UiTheme.PADDING);
        searchRow = new UiRect(inner.x(), inner.y(), inner.width(), 0);
        FactionList factions = addRenderableWidget(new FactionList(new UiRect(area.x() + 1, inner.y(),
                area.width() - (isCompact() ? 2 : 1), area.bottom() - inner.y() - 1)));
        factions.setSelectable(true);
        factions.setEmptyText(Component.translatable(KEY + "empty_factions"));
        factions.setItems(journal.factions());
        int index = -1;
        for (int i = 0; i < journal.factions().size(); i++) {
            if (journal.factions().get(i).id().equals(lastFaction)) {
                index = i;
            }
        }
        factions.setSelectedIndex(index);
        list = factions;
        factions.setScroll(listScroll);
    }

    private Optional<Faction> selectedFaction() {
        return journal.factions().stream().filter(faction -> faction.id().equals(lastFaction)).findFirst();
    }

    private List<UiRow> factionRows(Faction faction) {
        List<UiRow> rows = new ArrayList<>();
        rows.add(UiRow.of(Component.translatable(KEY + "row.culture"), faction.culture()));
        rows.add(UiRow.of(Component.translatable(KEY + "row.attitude"), faction.attitude()));
        rows.add(UiRow.of(Component.translatable(KEY + "row.reputation"),
                Component.translatable(KEY + "reputation", tierText(faction.reputation()), faction.value())));
        rows.add(UiRow.heading(Component.translatable(KEY + "faction_villages", faction.villages())));
        for (Village village : journal.villages()) {
            if (village.factionId().equals(faction.id())) {
                rows.add(UiRow.of(Component.literal(village.name()), village.visited()
                        ? Component.translatable(KEY + "visited") : Component.translatable(KEY + "discovered")));
            }
        }
        return rows;
    }

    /** The list of the factions. */
    private final class FactionList extends UiList<Faction> {
        FactionList(UiRect rect) {
            super(rect);
        }

        @Override
        protected int rowHeight(Faction item, int index) {
            return LIST_ROW;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Faction item, int index, UiRect rect, boolean hovered,
                boolean selected, int mouseX, int mouseY) {
            rowBackground(graphics, rect, index, hovered, selected);
            UiRect text = rect.inset(UiTheme.PADDING, 0);
            int y = UiText.textY(text);
            int tierWidth = UiText.drawRight(graphics, font, tierText(item.reputation()), text.right(), y,
                    text.width() / 2, UiReputation.textOnWood(item.reputation()));
            UiText.draw(graphics, font, item.name(), text.x(), y, text.width() - tierWidth - UiTheme.PADDING,
                    UiTheme.TEXT);
        }

        @Override
        protected void onSelect(Faction item, int index) {
            lastFaction = item.id();
            if (isCompact()) {
                compactDetails = true;
            }
            rebuild();
        }

        @Override
        protected Component narration(Faction item) {
            return item.name();
        }
    }

    // ------------------------------------------------------------------ routes

    /** Adds the chips of the two ends of the route. Returns the area below them. */
    private UiRect initRouteChips(UiRect inner) {
        int width = inner.width() - CHIP - UiTheme.GAP;
        int y = inner.y();
        for (RouteSlot slot : RouteSlot.values()) {
            Optional<Village> village = routeEnd(slot == RouteSlot.FROM ? ClientRouteView.fromId()
                    : ClientRouteView.toId());
            Component name = village.<Component>map(found -> Component.literal(found.name()))
                    .orElse(Component.translatable(KEY + "route.none"));
            Component label = Component.translatable(KEY + "route." + slot.name().toLowerCase(Locale.ROOT), name);
            addRenderableWidget(new UiButton(new UiRect(inner.x(), y, width, CHIP), label,
                    slot == lastSlot ? UiButton.Style.CHIP_SELECTED : UiButton.Style.CHIP, button -> {
                        lastSlot = slot;
                        rebuild();
                    }).setDetails(Component.translatable(KEY + "route.slot_hint")));
            y += CHIP + UiTheme.GAP;
        }
        addRenderableWidget(new UiButton(new UiRect(inner.right() - CHIP, inner.y(), CHIP, 2 * CHIP + UiTheme.GAP),
                Component.literal("⇅"), UiButton.Style.CHIP, button -> {
                    ClientRouteView.select(ClientRouteView.toId(), ClientRouteView.fromId());
                    rebuild();
                }).setDetails(Component.translatable(KEY + "route.swap")));
        return inner.belowTop(y - inner.y(), UiTheme.GAP);
    }

    /**
     * Sets the end of the route of the current slot to a village. If the village is at the other end, the two ends
     * change places. The slot moves to the other end if that end is empty.
     */
    private void pickRouteEnd(String id) {
        String from = ClientRouteView.fromId();
        String to = ClientRouteView.toId();
        if (lastSlot == RouteSlot.FROM) {
            if (id.equals(to)) {
                to = from;
            }
            from = id;
            if (to == null) {
                lastSlot = RouteSlot.TO;
            }
        } else {
            if (id.equals(from)) {
                from = to;
            }
            to = id;
            if (from == null) {
                lastSlot = RouteSlot.FROM;
            }
        }
        ClientRouteView.select(from, to);
        if (isCompact() && from != null && to != null) {
            compactDetails = true;
        }
        rebuild();
    }

    /** The village of the journal at an end of the route. */
    private Optional<Village> routeEnd(@Nullable String id) {
        return id == null ? Optional.empty()
                : journal.villages().stream().filter(village -> village.id().equals(id)).findFirst();
    }

    /** The route of the two selected villages, if the server sent it. */
    private static Optional<Route> shownRoute() {
        return ClientRouteView.shownRoute();
    }

    private void initRouteDetails(UiRect area) {
        if (!hasSelection()) {
            return;
        }
        statusArea = area.top(LEGEND);
        int mapHeight = area.height() - UiTheme.CONTROL - 2 * LEGEND - 3 * UiTheme.GAP;
        mapArea = new UiRect(area.x(), area.y() + LEGEND + UiTheme.GAP, area.width(), Math.max(0, mapHeight));
        Component label = Component.translatable(KEY + (ClientRouteView.inWorld() ? "route.hide" : "route.show"));
        UiRect row = area.bottomPart(UiTheme.CONTROL);
        UiRect buttonRect = row.left(Math.min(row.width(), font.width(label) + 20));
        lineInfo = row.afterLeft(buttonRect.width(), UiTheme.PADDING);
        UiButton button = addRenderableWidget(UiButton.normal(buttonRect, label, b -> {
            ClientRouteView.setInWorld(!ClientRouteView.inWorld());
            rebuild();
        }).setDetails(Component.translatable(KEY + "route.show_hint")));
        if (shownRoute().isEmpty() && !ClientRouteView.inWorld()) {
            button.setDisabled(Component.translatable(KEY + "route.no_line"));
        }
    }

    private void renderRouteHeader(GuiGraphics graphics, UiRect header, Village from, Village to) {
        RoutePayload payload = ClientRouteView.route();
        Component state;
        UiTone tone;
        if (payload == null || payload.status() == RoutePayload.Status.PENDING) {
            state = Component.translatable(KEY + "route.state.pending");
            tone = UiTone.INFO;
        } else {
            switch (payload.status()) {
                case READY -> {
                    state = Component.translatable(KEY + (payload.detailed() ? "route.state.detailed"
                            : "route.state.coarse"));
                    tone = payload.detailed() ? UiTone.GOOD : UiTone.WARN;
                }
                case NONE -> {
                    state = Component.translatable(KEY + "route.state.none");
                    tone = UiTone.BAD;
                }
                default -> {
                    state = Component.translatable(KEY + "route.state.refused");
                    tone = UiTone.MUTED;
                }
            }
        }
        int w = UiDraw.badgeWidth(font, state);
        int right = header.right() - w;
        UiDraw.badge(graphics, font, right, header.y() + 1, state, tone);
        UiText.draw(graphics, font, Component.translatable(KEY + "route.title", from.name(), to.name()), header.x(),
                header.y() + 2, right - header.x() - UiTheme.GAP, UiTheme.TEXT);
        Optional<Route> route = shownRoute();
        Component line = route.isPresent()
                ? Component.translatable(KEY + "route.length", (int) Math.round(route.get().length()))
                : Component.translatable(KEY + "route.distance",
                        (int) Math.round(Math.hypot(to.x() - from.x(), to.z() - from.z())));
        UiText.draw(graphics, font, line, header.x(), header.y() + 14, header.width(), UiTheme.TEXT_MUTED);
    }

    /** Draws the map of the route: the route, the two ends, the other villages of the journal and the player. */
    private void renderRouteMap(GuiGraphics graphics, UiRect rect, Village from, Village to) {
        if (rect.width() < 2 * MAP_MARGIN || rect.height() < 2 * MAP_MARGIN) {
            return;
        }
        UiDraw.box(graphics, rect, MAP_BACKGROUND, UiTheme.PARCHMENT_EDGE, false);
        Optional<Route> route = shownRoute();
        RoutePayload.Relief relief = ClientRouteView.relief();
        int minX = Math.min(from.x(), to.x());
        int maxX = Math.max(from.x(), to.x());
        int minZ = Math.min(from.z(), to.z());
        int maxZ = Math.max(from.z(), to.z());
        if (relief != null) {
            minX = Math.min(minX, relief.minX());
            minZ = Math.min(minZ, relief.minZ());
            maxX = Math.max(maxX, relief.minX() + (relief.width() - 1) * relief.cell());
            maxZ = Math.max(maxZ, relief.minZ() + (relief.height() - 1) * relief.cell());
        }
        if (route.isPresent()) {
            for (Route.Point point : route.get().points()) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minZ = Math.min(minZ, point.z());
                maxZ = Math.max(maxZ, point.z());
            }
        }
        double spanX = Math.max(MAP_MIN_SPAN, maxX - minX);
        double spanZ = Math.max(MAP_MIN_SPAN, maxZ - minZ);
        UiRect inner = rect.inset(MAP_MARGIN);
        MapProjection map = new MapProjection(inner, (minX + maxX) / 2.0, (minZ + maxZ) / 2.0,
                Math.min(inner.width() / spanX, inner.height() / spanZ));
        graphics.enableScissor(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1);
        if (relief != null) {
            renderRelief(graphics, map, relief);
        }
        for (Village village : journal.villages()) {
            if (!village.id().equals(from.id()) && !village.id().equals(to.id())) {
                int x = map.x(village.x());
                int y = map.y(village.z());
                if (rect.contains(x, y)) {
                    UiDraw.mark(graphics, x - 1, y - 1, 3, UiTheme.INK_FAINT);
                }
            }
        }
        RoutePayload payload = ClientRouteView.route();
        if (route.isPresent()) {
            List<Route.Point> points = route.get().points();
            List<Route.Surface> surfaces = route.get().surfaces();
            for (int i = 0; i < surfaces.size(); i++) {
                int color = mapColor(surfaces.get(i), ClientRouteView.isDetailed());
                Route.Point a = points.get(i);
                Route.Point b = points.get(i + 1);
                drawLine(graphics, map.x(a.x()), map.y(a.z()), map.x(b.x()), map.y(b.z()), color);
            }
        } else if (payload != null && payload.status() != RoutePayload.Status.PENDING) {
            UiText.drawCentered(graphics, font, Component.translatable(KEY + (payload.status()
                    == RoutePayload.Status.NONE ? "route.no_route" : "route.refused")), rect.inset(4), UiTheme.INK);
        }
        endMark(graphics, map, from, 0xFF2F6B2A);
        endMark(graphics, map, to, UiTheme.WAX);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int x = map.x(player.getX());
            int y = map.y(player.getZ());
            if (rect.contains(x, y)) {
                graphics.fill(x - 2, y - 2, x + 3, y + 3, UiTheme.INK);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF5F95C4);
            }
        }
        graphics.disableScissor();
    }

    /**
     * Draws the relief of the map: a light grey for each cell, darker for a higher floor. A cell with water is light
     * blue.
     */
    private static void renderRelief(GuiGraphics graphics, MapProjection map, RoutePayload.Relief relief) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int j = 0; j < relief.height(); j++) {
            for (int i = 0; i < relief.width(); i++) {
                if (relief.water(i, j) == 0) {
                    low = Math.min(low, relief.floor(i, j));
                    high = Math.max(high, relief.floor(i, j));
                }
            }
        }
        double range = Math.max(RELIEF_MIN_RANGE, high - low);
        int half = relief.cell() / 2;
        for (int j = 0; j < relief.height(); j++) {
            int z = relief.minZ() + j * relief.cell();
            int y0 = map.y(z - half);
            int y1 = Math.max(y0 + 1, map.y(z - half + relief.cell()));
            for (int i = 0; i < relief.width(); i++) {
                int x = relief.minX() + i * relief.cell();
                int x0 = map.x(x - half);
                int x1 = Math.max(x0 + 1, map.x(x - half + relief.cell()));
                int color;
                if (relief.water(i, j) > 0) {
                    color = RELIEF_WATER;
                } else {
                    double t = Math.max(0.0, Math.min(1.0, (relief.floor(i, j) - low) / range));
                    int grey = (int) Math.round(RELIEF_LOW - t * (RELIEF_LOW - RELIEF_HIGH));
                    color = 0xFF000000 | grey << 16 | grey << 8 | grey;
                }
                graphics.fill(x0, y0, x1, y1, color);
            }
        }
    }

    /** Draws the state of the calculation of the route: the progress, or why the route is coarse. */
    private void renderRouteStatus(GuiGraphics graphics, UiRect rect) {
        RoutePayload payload = ClientRouteView.route();
        if (payload == null || rect.width() <= 0) {
            return;
        }
        RoutePayload.Progress progress = payload.progress();
        Component text;
        double fraction = -1;
        if (progress != null) {
            String kind = progress.detail() ? "detailed" : "coarse";
            if (progress.ahead() > 0) {
                text = Component.translatable(KEY + "route.progress.waiting." + kind, progress.ahead());
            } else {
                text = Component.translatable(KEY + "route.progress.running." + kind, progress.percent(),
                        progress.examined(), progress.budget());
                fraction = progress.percent() / 100.0;
            }
        } else if (payload.status() == RoutePayload.Status.PENDING) {
            text = Component.translatable(KEY + "route.progress.starting");
        } else if (payload.status() == RoutePayload.Status.READY && !payload.detailed()) {
            text = Component.translatable(KEY + "route.progress.coarse_final");
        } else if (payload.status() == RoutePayload.Status.READY && payload.more()) {
            text = Component.translatable(KEY + "route.progress.relief");
        } else if (payload.status() == RoutePayload.Status.READY) {
            text = Component.translatable(KEY + "route.progress.done");
        } else {
            return;
        }
        int textRight = rect.right();
        if (fraction >= 0) {
            UiRect bar = rect.rightPart(Math.min(PROGRESS_BAR, rect.width() / 3)).inset(0, 3);
            UiDraw.bar(graphics, bar, fraction, UiTheme.WOOD_DARK, UiTheme.GILT);
            textRight = bar.x() - UiTheme.PADDING;
        }
        UiText.draw(graphics, font, text, rect.x(), UiText.textY(rect), textRight - rect.x(), UiTheme.TEXT_MUTED);
    }

    /** Draws an end of the route: a square and the name of the village. */
    private void endMark(GuiGraphics graphics, MapProjection map, Village village, int color) {
        int x = map.x(village.x());
        int y = map.y(village.z());
        graphics.fill(x - 3, y - 3, x + 4, y + 4, UiTheme.INK);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        graphics.drawString(font, village.name(), x + 6, y - 4, UiTheme.INK, false);
    }

    /** Draws a line of 2 pixels. */
    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * (float) i / steps);
            int y = y0 + Math.round((y1 - y0) * (float) i / steps);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    /** The color of a surface on the map. A coarse route is grey. */
    private static int mapColor(Route.Surface surface, boolean detailed) {
        if (!detailed) {
            return UiTheme.INK_MUTED;
        }
        return switch (surface) {
            case ROAD -> 0xFF8A5A1E;
            case LAND -> 0xFF4E7D34;
            case FORD -> 0xFF2E8FB0;
            case WATER -> 0xFF2F4FA8;
        };
    }

    /** Draws the legend: for each surface on the route, its color, its name and its part of the length. */
    private void renderRouteLegend(GuiGraphics graphics, int x, int y, int right) {
        Optional<Route> route = shownRoute();
        if (route.isEmpty()) {
            return;
        }
        if (!ClientRouteView.isDetailed()) {
            UiDraw.mark(graphics, x, y + 1, 7, UiTheme.INK_MUTED);
            UiText.draw(graphics, font, Component.translatable(KEY + "route.coarse_legend"), x + 10, y,
                    right - x - 10, UiTheme.TEXT_MUTED);
            return;
        }
        Map<Route.Surface, Double> lengths = new EnumMap<>(Route.Surface.class);
        List<Route.Point> points = route.get().points();
        for (int i = 0; i < route.get().surfaces().size(); i++) {
            Route.Point a = points.get(i);
            Route.Point b = points.get(i + 1);
            lengths.merge(route.get().surfaces().get(i), Math.hypot(b.x() - a.x(), b.z() - a.z()), Double::sum);
        }
        double total = Math.max(1.0, route.get().length());
        for (Route.Surface surface : Route.Surface.values()) {
            Double length = lengths.get(surface);
            if (length == null || x >= right) {
                continue;
            }
            Component text = Component.translatable(KEY + "route.surface." + surface.name().toLowerCase(Locale.ROOT),
                    (int) Math.round(100 * length / total));
            UiDraw.mark(graphics, x, y + 1, 7, mapColor(surface, true));
            int w = UiText.draw(graphics, font, text, x + 10, y, right - x - 10, UiTheme.TEXT);
            x += 10 + w + 2 * UiTheme.PADDING;
        }
    }

    /**
     * The projection of the block coordinates on the map. North is up.
     *
     * @param area    the area of the map
     * @param centerX the block X at the center of the area
     * @param centerZ the block Z at the center of the area
     * @param scale   the pixels for one block
     */
    private record MapProjection(UiRect area, double centerX, double centerZ, double scale) {
        int x(double blockX) {
            return (int) Math.round(area.centerX() + (blockX - centerX) * scale);
        }

        int y(double blockZ) {
            return (int) Math.round(area.centerY() + (blockZ - centerZ) * scale);
        }
    }

    // ------------------------------------------------------------------ rendering

    private static void rowBackground(GuiGraphics graphics, UiRect rect, int index, boolean hovered,
            boolean selected) {
        if (selected) {
            UiDraw.fill(graphics, rect, UiTheme.WOOD_RAISED);
            UiDraw.fill(graphics, rect.left(2), UiTheme.GILT);
        } else if (hovered) {
            UiDraw.fill(graphics, rect, UiTheme.WOOD_HOVER);
        } else if (index % 2 == 0) {
            UiDraw.fill(graphics, rect, UiTheme.WOOD_ROW);
        }
    }

    private static Component tierText(ReputationTier tier) {
        return FactionVisuals.tierTitle(tier).withColor(UiReputation.color(tier));
    }

    private Component dayText(long day) {
        long ago = journal.today() - day;
        return ago <= 0 ? Component.translatable(KEY + "day_today", day + 1)
                : Component.translatable(KEY + "day_ago", day + 1, ago);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        if (!isCompact() && listArea.width() > 0) {
            UiDraw.vLine(graphics, listArea.right(), listArea.y(), listArea.height() - 1, UiTheme.GILT);
        }
        if (!showsDetails()) {
            return;
        }
        UiRect header = detailsArea.top(DETAILS_HEADER);
        if (isCompact()) {
            header = header.afterLeft(UiTheme.CONTROL, UiTheme.GAP);
        }
        if (lastTab == TAB_ROUTES) {
            Optional<Village> from = routeEnd(ClientRouteView.fromId());
            Optional<Village> to = routeEnd(ClientRouteView.toId());
            if (from.isEmpty() || to.isEmpty()) {
                UiText.drawCentered(graphics, font, Component.translatable(KEY + "route.select"),
                        detailsArea.top(UiTheme.CONTROL), UiTheme.TEXT_MUTED);
                return;
            }
            renderRouteHeader(graphics, header, from.get(), to.get());
            renderRouteStatus(graphics, statusArea);
            renderRouteMap(graphics, mapArea, from.get(), to.get());
            renderRouteLegend(graphics, mapArea.x(), mapArea.bottom() + UiTheme.GAP + 2, mapArea.right());
            if (ClientRouteView.inWorld() && lineInfo.width() > 0) {
                int blocks = ClientRouteOverlay.segmentCount();
                Component info = blocks > 0 ? Component.translatable(KEY + "route.line_blocks", blocks)
                        : Component.translatable(KEY + "route.line_far");
                UiText.drawLeft(graphics, font, info, lineInfo, UiTheme.TEXT_MUTED);
            }
        } else if (lastTab == TAB_VILLAGES) {
            Optional<Village> village = selectedVillage();
            if (village.isEmpty()) {
                UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_selection"),
                        detailsArea.top(UiTheme.CONTROL), UiTheme.TEXT_MUTED);
                return;
            }
            renderVillageHeader(graphics, header, village.get());
        } else {
            Optional<Faction> faction = selectedFaction();
            if (faction.isEmpty()) {
                UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_selection"),
                        detailsArea.top(UiTheme.CONTROL), UiTheme.TEXT_MUTED);
                return;
            }
            UiText.draw(graphics, font, faction.get().name(), header.x(), header.y() + 2, header.width(),
                    UiTheme.TEXT);
            UiText.draw(graphics, font, faction.get().culture(), header.x(), header.y() + 14, header.width(),
                    UiTheme.TEXT_MUTED);
        }
        UiDraw.hLine(graphics, detailsArea.x() - UiTheme.PADDING + 1,
                detailsArea.y() + DETAILS_HEADER + UiTheme.PADDING / 2 - 1,
                detailsArea.width() + 2 * UiTheme.PADDING - 2, UiTheme.WOOD_EDGE);
    }

    private void renderVillageHeader(GuiGraphics graphics, UiRect header, Village village) {
        List<Component> texts = new ArrayList<>();
        List<UiTone> tones = new ArrayList<>();
        texts.add(Component.translatable(KEY + (village.visited() ? "visited" : "discovered")));
        tones.add(village.visited() ? UiTone.GOOD : UiTone.INFO);
        if (!village.active()) {
            texts.add(Component.translatable(KEY + "ruins"));
            tones.add(UiTone.MUTED);
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
        UiText.draw(graphics, font, Component.literal(village.name()), header.x(), header.y() + 2,
                right - header.x() - UiTheme.GAP, village.active() ? UiTheme.TEXT : UiTheme.TEXT_FAINT);
        UiText.draw(graphics, font, village.faction(), header.x(), header.y() + 14, header.width(),
                UiTheme.TEXT_MUTED);
    }
}
