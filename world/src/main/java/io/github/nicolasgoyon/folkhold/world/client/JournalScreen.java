package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.cartofold.api.client.CartofoldWorldMap;
import io.github.nicolasgoyon.cartofold.api.client.MapFrame;
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
import io.github.nicolasgoyon.folkhold.world.journal.CartofoldMaps;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Faction;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Village;
import io.github.nicolasgoyon.folkhold.world.network.JournalRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
 *       between them on a map frame of Cartofold (see {@link ClientRouteView}). The color of the route shows the
 *       surface. A button shows the route in the world ({@link ClientRouteOverlay}).</li>
 *   <li>Tab "Map": a map frame of Cartofold with the discovered territories and villages on the relief
 *       ({@link ClientJournalMap}). A click on a village selects it. A button opens the map of the world of Cartofold
 *       at the selected village. The tab "Villages" shows a village on the map.</li>
 *   <li>The frames keep their views between two openings. The arrow keys move a focused frame, and the keys + and -
 *       zoom ({@link JournalMapViews}).</li>
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
    /** The index of the tab of the map. */
    private static final int TAB_MAP = 3;
    /** The preferred width of the journal on the tab of the map. */
    private static final int MAP_TAB_WIDTH = 520;
    /** The preferred height of the journal on the tab of the map. */
    private static final int MAP_TAB_HEIGHT = 320;
    private static final int LEGEND = 12;
    /** The background of the frames where the relief and the explored chunks are not. */
    private static final int MAP_BACKGROUND = 0xFFFFFFFF;
    /** The scale after the journal shows a village on the map, if the scale was smaller. */
    private static final double FOCUS_SCALE = 0.5;
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
    /** The view of the frame of the tab "Map", or null before the first opening. */
    @Nullable
    private static MapFrame.View mapView;
    /** True while the frame of the tab "Map" puts the player at the center. */
    private static boolean mapFollow;
    /** The view of the frame of the tab "Routes". */
    @Nullable
    private static MapFrame.View routeView;
    /** The route and the relief that {@link #routeView} shows all of, or null. A change gives a new view. */
    @Nullable
    private static JournalMapViews.RouteState routeFit;

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
    /** The frame of the tab "Map", or null on the other tabs. */
    @Nullable
    private MapFrame mapFrame;
    /** The frame of the tab "Routes", or null. */
    @Nullable
    private MapFrame routeFrame;
    /**
     * The two frames stay across the rebuilds of the screen: a drag, the focus and the keys continue while the server
     * sends a route or the journal.
     */
    @Nullable
    private MapFrame keptMapFrame;
    @Nullable
    private MapFrame keptRouteFrame;
    /** The state of the route at the last rebuild, or null. */
    @Nullable
    private JournalMapViews.RouteState routeState;
    /** The row above the map of the tab "Map". */
    private UiRect mapHeader = listArea;
    /** The legend of the factions, below the map of the tab "Map". */
    private UiRect mapLegend = listArea;

    public JournalScreen(JournalPayload journal) {
        super(Component.translatable(KEY + "title"));
        this.journal = journal;
    }

    // ------------------------------------------------------------------ network and key

    /** Opens the journal, or updates the open journal. Called by the network handler. */
    public static void open(JournalPayload payload) {
        if (known(payload, payload.focus())) {
            // A click on a village of a frame of the open journal selects the village and keeps the tab.
            if (current() == null) {
                lastTab = TAB_VILLAGES;
            }
            lastVillage = payload.focus();
        }
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
        if (screen != null && lastTab == TAB_ROUTES && !currentRouteState().equals(screen.routeState)) {
            screen.rebuild();
        }
    }

    /** The state of the route that the buttons and the frame of the tab "Routes" use. */
    private static JournalMapViews.RouteState currentRouteState() {
        return JournalMapViews.routeState(ClientRouteView.fromId(), ClientRouteView.toId(), ClientRouteView.route(),
                shownRoute(), ClientRouteView.relief());
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
        mapView = null;
        mapFollow = false;
        routeView = null;
        routeFit = null;
    }

    private void rebuild() {
        boolean listFocused = list != null && getFocused() == list;
        MapFrame focusedFrame = getFocused() instanceof MapFrame frame ? frame : null;
        rebuildWidgets();
        if (listFocused && list != null) {
            setFocused(list);
        } else if (focusedFrame != null && children().contains(focusedFrame)) {
            setFocused(focusedFrame);
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    @Nullable
    protected Component footerHint() {
        return null;
    }

    @Override
    protected int preferredWidth() {
        return lastTab == TAB_MAP ? MAP_TAB_WIDTH : super.preferredWidth();
    }

    @Override
    protected int preferredHeight() {
        return lastTab == TAB_MAP ? MAP_TAB_HEIGHT : super.preferredHeight();
    }

    @Override
    public void tick() {
        super.tick();
        if (lastTab == TAB_MAP) {
            ClientJournalMap.tick();
        }
    }

    @Override
    protected Component subtitle() {
        return Component.translatable(KEY + "subtitle", journal.villages().size(), journal.factions().size());
    }

    private boolean hasSelection() {
        return switch (lastTab) {
            case TAB_VILLAGES, TAB_MAP -> selectedVillage().isPresent();
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
                Component.translatable(KEY + "tab.routes"),
                Component.translatable(KEY + "tab.map")), lastTab, index -> {
                    lastTab = index;
                    compactDetails = false;
                    listScroll = 0;
                    rebuild();
                }));
        setSectionTabs(tabs);
        UiRect content = body.belowTop(4 + UiTheme.CONTROL, 2);
        mapFrame = null;
        routeFrame = null;
        routeState = currentRouteState();
        if (lastTab == TAB_MAP) {
            listArea = new UiRect(0, 0, 0, 0);
            detailsArea = listArea;
            mapArea = listArea;
            lineInfo = listArea;
            statusArea = listArea;
            initMap(content.inset(UiTheme.PADDING));
            return;
        }
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
                case TAB_VILLAGES -> initVillageDetails(rows);
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

    /** Adds the rows of the selected village, and the buttons of the maps below them. */
    private void initVillageDetails(UiRect area) {
        Optional<Village> village = selectedVillage();
        if (village.isEmpty()) {
            return;
        }
        UiRect row = area.bottomPart(UiTheme.CONTROL);
        addRows(new UiRect(area.x(), area.y(), area.width(), Math.max(0, row.y() - UiTheme.GAP - area.y())),
                villageRows(village.get()));
        int x = mapButton(row, row.x(), Component.translatable(KEY + "map.show"),
                Component.translatable(KEY + "map.show_hint"), () -> showOnMap(village.get()));
        worldMapButton(row, x, village.get());
    }

    /** Opens the tab "Map" with a village at the center of the map. */
    private void showOnMap(Village village) {
        lastTab = TAB_MAP;
        lastVillage = village.id();
        compactDetails = false;
        mapView = new MapFrame.View(village.x() + 0.5, village.z() + 0.5,
                Math.max(FOCUS_SCALE, mapView == null ? 0 : mapView.scale()));
        mapFollow = false;
        rebuild();
    }

    private List<UiRow> villageRows(Village village) {
        List<UiRow> rows = new ArrayList<>();
        rows.add(UiRow.of(Component.translatable(KEY + "row.faction"), village.faction()));
        rows.add(UiRow.of(Component.translatable(KEY + "row.reputation"), tierText(village.reputation())));
        rows.add(UiRow.of(Component.translatable(KEY + "row.level"),
                Component.translatable(KEY + "level", village.level(), village.tier())));
        if (village.places() >= 0) {
            rows.add(UiRow.of(Component.translatable(KEY + "row.housing"),
                    Component.translatable(KEY + "housing", village.inhabitants(), village.places())));
        }
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

    // ------------------------------------------------------------------ map

    /** Adds the frame of the map, and the row of buttons below it. */
    private void initMap(UiRect inner) {
        mapHeader = inner.top(UiTheme.CONTROL);
        UiRect row = inner.bottomPart(UiTheme.CONTROL);
        mapLegend = new UiRect(inner.x(), row.y() - UiTheme.GAP - LEGEND, inner.width(), LEGEND);
        int top = mapHeader.bottom() + UiTheme.GAP;
        mapArea = new UiRect(inner.x(), top, inner.width(), Math.max(0, mapLegend.y() - UiTheme.GAP - top));
        MapFrame frame = frame(keptMapFrame, mapArea, layer -> layer.equals(CartofoldMaps.TERRITORIES)
                || layer.equals(CartofoldMaps.VILLAGES), ClientJournalMap::relief);
        if (frame == null) {
            return;
        }
        keptMapFrame = frame;
        frame.onMarkerClick(this::selectOnMap).onViewChange(view -> {
            mapView = view;
            mapFollow = false;
        }).clearSelection();
        selectedVillage().ifPresent(village -> frame.select(CartofoldMaps.VILLAGES, village.id()));
        if (mapFollow) {
            frame.followPlayer(true);
        } else if (mapView != null) {
            frame.view(mapView);
        } else {
            showAll(frame);
        }
        mapFrame = frame;
        int x = mapButton(row, row.x(), Component.translatable(KEY + "map.show_all"),
                Component.translatable(KEY + "map.show_all_hint"), () -> showAll(frame));
        x = mapButton(row, x, Component.translatable(KEY + "map.center_player"),
                Component.translatable(KEY + "map.center_player_hint"), () -> {
                    frame.followPlayer(true);
                    mapFollow = true;
                });
        x = mapButton(row, x, Component.literal("−"), Component.translatable(KEY + "map.zoom_out"),
                () -> setView(frame, JournalMapViews.zoom(frame.view(), -1)));
        x = mapButton(row, x, Component.literal("+"), Component.translatable(KEY + "map.zoom_in"),
                () -> setView(frame, JournalMapViews.zoom(frame.view(), 1)));
        Optional<Village> selected = selectedVillage();
        Component details = Component.translatable(KEY + "map.details");
        UiButton detailsButton = addRenderableWidget(UiButton.normal(buttonRect(row, x, details), details, b -> {
            lastTab = TAB_VILLAGES;
            rebuild();
        }).setDetails(Component.translatable(KEY + "map.details_hint")));
        if (selected.isEmpty()) {
            detailsButton.setDisabled(Component.translatable(KEY + "map.no_selection"));
        }
        Component label = Component.translatable(KEY + "map.world_map");
        int after = detailsButton.getX() + detailsButton.getWidth() + UiTheme.GAP;
        worldMapButton(row, Math.max(after, row.right() - font.width(label) - 16), selected.orElse(null));
    }

    /**
     * Adds a map frame of the Overworld in an area, with the relief below the explored chunks. A kept frame gets the
     * new area, and keeps its state.
     *
     * @param kept   the frame of the last build, or null
     * @param area   the area of the frame, with its border
     * @param layers the layers of the frame
     * @param relief gives the relief, or null
     * @return the frame, or null when the area is too small
     */
    @Nullable
    private MapFrame frame(@Nullable MapFrame kept, UiRect area, Predicate<ResourceLocation> layers,
            Supplier<RoutePayload.Relief> relief) {
        if (area.width() <= 2 || area.height() <= 2) {
            return null;
        }
        MapFrame frame = kept != null ? kept : new MapFrame(area.x(), area.y(), area.width(), area.height())
                .dimension(Level.OVERWORLD).layers(layers).showWaypoints(false).backgroundColor(MAP_BACKGROUND)
                .background(ReliefBackground.of(relief));
        // The frame draws its border around its area.
        frame.setRectangle(area.width() - 2, area.height() - 2, area.x() + 1, area.y() + 1);
        return addRenderableWidget(frame);
    }

    /** A click on a village of the frame of the tab "Map" selects it. */
    private void selectOnMap(ResourceLocation layer, String marker) {
        if (layer.equals(CartofoldMaps.VILLAGES) && !marker.equals(lastVillage)
                && journal.villages().stream().anyMatch(village -> village.id().equals(marker))) {
            lastVillage = marker;
            rebuild();
        }
    }

    /** Shows all the discovered territories and villages. With no village, the frame follows the player. */
    private void showAll(MapFrame frame) {
        Optional<double[]> box = JournalMapViews.territoryBox(ClientTerritoryMap.get().villages(), journal.villages());
        if (box.isPresent()) {
            frame.showBox(box.get()[0], box.get()[1], box.get()[2], box.get()[3]);
        }
        frame.followPlayer(box.isEmpty());
        mapFollow = box.isEmpty();
        mapView = frame.view();
    }

    /** Sets the view of a frame of the journal, and keeps it for the next opening. */
    private void setView(MapFrame frame, MapFrame.View view) {
        frame.view(view);
        if (frame == mapFrame) {
            mapView = view;
            mapFollow = false;
        } else {
            routeView = view;
        }
    }

    /** Adds a button of a row of buttons. Returns the X after the button. */
    private int mapButton(UiRect row, int x, Component label, Component hint, Runnable action) {
        UiButton button = addRenderableWidget(UiButton.normal(buttonRect(row, x, label), label, b -> action.run())
                .setDetails(hint));
        return button.getX() + button.getWidth() + UiTheme.GAP;
    }

    /**
     * Adds the button that opens the map of the world of Cartofold in the Overworld, at a village or else at the
     * center of the frame. Escape shows the journal again.
     */
    private void worldMapButton(UiRect row, int x, @Nullable Village village) {
        Component label = Component.translatable(KEY + "map.world_map");
        addRenderableWidget(UiButton.normal(buttonRect(row, x, label), label, b -> {
            MapFrame.View view = mapFrame != null ? mapFrame.view() : mapView;
            CartofoldWorldMap map = village != null ? CartofoldWorldMap.at(Level.OVERWORLD, village.x(), village.z())
                    .select(CartofoldMaps.VILLAGES, village.id())
                    : view != null ? CartofoldWorldMap.at(Level.OVERWORLD, view.centerX(), view.centerZ())
                    : CartofoldWorldMap.at(Level.OVERWORLD, 0, 0);
            map.parent(this).open();
        }).setDetails(Component.translatable(KEY + "map.world_map_hint")));
    }

    /** Draws the row above the map: the selected village, or a hint, and the state of the relief. */
    private void renderMapHeader(GuiGraphics graphics, UiRect header) {
        int right = header.right();
        int badgeY = header.y() + (header.height() - UiTheme.BADGE) / 2;
        if (ClientJournalMap.pending() && !ClientTerritoryMap.get().villages().isEmpty()) {
            Component state = Component.translatable(KEY + "map.relief_pending");
            right -= UiDraw.badgeWidth(font, state);
            UiDraw.badge(graphics, font, right, badgeY, state, UiTone.INFO);
            right -= UiTheme.GAP;
        }
        int y = UiText.textY(header);
        Optional<Village> selected = selectedVillage();
        if (selected.isEmpty()) {
            UiText.draw(graphics, font, Component.translatable(KEY + "map.hint"), header.x(), y,
                    right - header.x(), UiTheme.TEXT_MUTED);
            return;
        }
        Village village = selected.get();
        Component state = Component.translatable(KEY + (!village.active() ? "ruins"
                : village.visited() ? "visited" : "discovered"));
        right -= UiDraw.badgeWidth(font, state);
        UiDraw.badge(graphics, font, right, badgeY, state, !village.active() ? UiTone.MUTED
                : village.visited() ? UiTone.GOOD : UiTone.INFO);
        right -= UiTheme.GAP;
        UiText.draw(graphics, font, Component.translatable(KEY + "map.selected", village.name(), village.faction()),
                header.x(), y, right - header.x(), village.active() ? UiTheme.TEXT : UiTheme.TEXT_FAINT);
    }

    /** Draws the legend of the map: the color and the name of each faction with a discovered territory. */
    private void renderMapLegend(GuiGraphics graphics, UiRect legend) {
        int x = legend.x();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (TerritoryMapPayload.Village village : ClientTerritoryMap.get().villages()) {
            colors.putIfAbsent(village.faction(), village.color() & 0xFFFFFF);
        }
        for (Map.Entry<String, Integer> faction : colors.entrySet()) {
            if (x >= legend.right() - 10) {
                break;
            }
            UiDraw.mark(graphics, x, legend.y() + 2, 7, 0xFF000000 | faction.getValue());
            int w = UiText.draw(graphics, font, Component.literal(faction.getKey()), x + 10, legend.y() + 1,
                    legend.right() - x - 10, UiTheme.TEXT);
            x += 10 + w + 2 * UiTheme.PADDING;
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
        addRenderableWidget(new UiButton(new UiRect(inner.right() - CHIP, inner.y(), CHIP, CHIP),
                Component.literal("⇅"), UiButton.Style.CHIP, button -> {
                    ClientRouteView.select(ClientRouteView.toId(), ClientRouteView.fromId());
                    rebuild();
                }).setDetails(Component.translatable(KEY + "route.swap")));
        UiButton reset = addRenderableWidget(new UiButton(new UiRect(inner.right() - CHIP,
                inner.y() + CHIP + UiTheme.GAP, CHIP, CHIP), Component.literal("✕"), UiButton.Style.CHIP, button -> {
                    ClientRouteView.select(null, null);
                    lastSlot = RouteSlot.FROM;
                    compactDetails = false;
                    rebuild();
                }).setDetails(Component.translatable(KEY + "route.reset")));
        if (ClientRouteView.fromId() == null && ClientRouteView.toId() == null) {
            reset.disable();
        }
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
        initRouteFrame();
        UiRect row = area.bottomPart(UiTheme.CONTROL);
        int x = row.x();
        Component show = Component.translatable(KEY + (ClientRouteView.inWorld() ? "route.hide" : "route.show"));
        UiButton showButton = addRenderableWidget(UiButton.normal(buttonRect(row, x, show), show, b -> {
            ClientRouteView.setInWorld(!ClientRouteView.inWorld());
            rebuild();
        }).setDetails(Component.translatable(KEY + "route.show_hint")));
        if (shownRoute().isEmpty() && !ClientRouteView.inWorld()) {
            showButton.setDisabled(Component.translatable(KEY + "route.no_line"));
        }
        x = showButton.getX() + showButton.getWidth() + UiTheme.GAP;
        boolean precise = ClientRouteView.lineMode() == ClientRouteView.LineMode.PRECISE;
        Component mode = Component.translatable(KEY + (precise ? "route.mode.precise" : "route.mode.approximate"));
        UiButton modeButton = addRenderableWidget(UiButton.normal(buttonRect(row, x, mode), mode, b -> {
            ClientRouteView.setLineMode(precise ? ClientRouteView.LineMode.APPROXIMATE
                    : ClientRouteView.LineMode.PRECISE);
            rebuild();
        }).setDetails(Component.translatable(KEY + "route.mode_hint")));
        x = modeButton.getX() + modeButton.getWidth() + UiTheme.GAP;
        Component again = Component.translatable(KEY + "route.recalculate");
        UiButton againButton = addRenderableWidget(UiButton.normal(buttonRect(row, x, again), again, b -> {
            ClientRouteView.recalculate();
            rebuild();
        }).setDetails(Component.translatable(KEY + "route.recalculate_hint")));
        RoutePayload payload = ClientRouteView.route();
        if (payload == null || payload.more()) {
            againButton.setDisabled(Component.translatable(KEY + "route.recalculate_busy"));
        }
        x = againButton.getX() + againButton.getWidth() + UiTheme.PADDING;
        lineInfo = new UiRect(x, row.y(), Math.max(0, row.right() - x), row.height());
    }

    /**
     * Adds the frame of the route: the route layer and the villages on the relief of the route. The frame shows all of
     * the route, and keeps the view of the player until the route or its relief changes.
     */
    private void initRouteFrame() {
        Optional<Village> from = routeEnd(ClientRouteView.fromId());
        Optional<Village> to = routeEnd(ClientRouteView.toId());
        MapFrame frame = frame(keptRouteFrame, mapArea, layer -> layer.equals(CartofoldMaps.ROUTE)
                || layer.equals(CartofoldMaps.VILLAGES), ClientRouteView::relief);
        if (frame == null || from.isEmpty() || to.isEmpty()) {
            return;
        }
        keptRouteFrame = frame;
        frame.onViewChange(view -> routeView = view);
        JournalMapViews.RouteState fit = routeState.fit();
        if (fit.equals(routeFit) && routeView != null) {
            frame.view(routeView);
        } else {
            double[] box = JournalMapViews.routeBox(from.get(), to.get(), shownRoute(), ClientRouteView.relief());
            frame.showBox(box[0], box[1], box[2], box[3]);
            routeFit = fit;
            routeView = frame.view();
        }
        routeFrame = frame;
    }

    /** The rectangle of a button of the bottom row of the routes, from a position, in the width of its label. */
    private UiRect buttonRect(UiRect row, int x, Component label) {
        int width = Math.max(0, Math.min(font.width(label) + 16, row.right() - x));
        return new UiRect(Math.min(x, row.right()), row.y(), width, row.height());
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

    /** The color of a surface on the map. A coarse route is grey. */
    private static int mapColor(Route.Surface surface, boolean detailed) {
        if (!detailed) {
            return UiTheme.INK_MUTED;
        }
        return CartofoldMaps.surfaceColor(surface);
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
        if (lastTab == TAB_MAP) {
            renderMapHeader(graphics, mapHeader);
            renderMapLegend(graphics, mapLegend);
            return;
        }
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The focused frame takes the arrow keys and the keys + and - before the tabs. The frames of Cartofold have
        // no keys.
        if (getFocused() instanceof MapFrame frame) {
            Optional<MapFrame.View> view = JournalMapViews.onKey(frame.view(), keyCode);
            if (view.isPresent()) {
                setView(frame, view.get());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (mapFrame != null && journal.villages().isEmpty()) {
            UiText.drawWrapped(graphics, font, Component.translatable(KEY + "map.empty"),
                    mapArea.inset(UiTheme.PADDING * 4, mapArea.height() / 3), UiTheme.INK_MUTED);
        }
        RoutePayload payload = ClientRouteView.route();
        if (routeFrame != null && shownRoute().isEmpty() && payload != null
                && payload.status() != RoutePayload.Status.PENDING) {
            UiText.drawCentered(graphics, font, Component.translatable(KEY + (payload.status()
                    == RoutePayload.Status.NONE ? "route.no_route" : "route.refused")), mapArea.inset(4), UiTheme.INK);
        }
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
