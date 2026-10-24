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
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Faction;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Village;
import io.github.nicolasgoyon.folkhold.world.network.JournalRequestPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    /** The index of the tab of the villages. The tab of the factions follows it. */
    private static final int TAB_VILLAGES = 0;

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

    public JournalScreen(JournalPayload journal) {
        super(Component.translatable(KEY + "title"));
        this.journal = journal;
    }

    // ------------------------------------------------------------------ network and key

    /** Opens the journal, or updates the open journal. Called by the network handler. */
    public static void open(JournalPayload payload) {
        JournalScreen screen = current();
        if (screen != null) {
            screen.journal = payload;
            screen.rebuild();
            return;
        }
        Minecraft.getInstance().setScreen(new JournalScreen(payload));
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
        return lastTab == TAB_VILLAGES ? selectedVillage().isPresent() : selectedFaction().isPresent();
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
                Component.translatable(KEY + "tab.factions", journal.factions().size())), lastTab, index -> {
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
        if (showsList()) {
            if (lastTab == TAB_VILLAGES) {
                initVillageList(left);
            } else {
                initFactionList(left);
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
            if (lastTab == TAB_VILLAGES) {
                selectedVillage().ifPresent(village -> addRows(rows, villageRows(village)));
            } else {
                selectedFaction().ifPresent(faction -> addRows(rows, factionRows(faction)));
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
        searchRow = inner.top(UiTheme.CONTROL);
        UiTextField search = new UiTextField(searchRow, Component.translatable(KEY + "filter"),
                Component.translatable(KEY + "filter"));
        search.setValue(lastText);
        search.setResponder(text -> {
            lastText = text;
            applyVillageFilters();
        });
        addRenderableWidget(search);
        int chipY = searchRow.bottom() + UiTheme.GAP;
        int x = inner.x();
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
        int listTop = chipY + CHIP + UiTheme.PADDING;
        VillageList villages = addRenderableWidget(new VillageList(new UiRect(area.x() + 1, listTop,
                area.width() - (isCompact() ? 2 : 1), area.bottom() - listTop - 1)));
        villages.setSelectable(true);
        villages.setEmptyText(Component.translatable(KEY + "empty_villages"));
        list = villages;
        applyVillageFilters();
        villages.setScroll(listScroll);
    }

    private void applyVillageFilters() {
        String text = lastText.trim().toLowerCase(Locale.ROOT);
        visibleVillages = journal.villages().stream()
                .filter(lastFilter::accepts)
                .filter(village -> text.isEmpty() || village.name().toLowerCase(Locale.ROOT).contains(text)
                        || village.faction().getString().toLowerCase(Locale.ROOT).contains(text))
                .sorted(lastSort.comparator)
                .toList();
        if (list instanceof VillageList villages) {
            villages.setItems(visibleVillages);
            int index = -1;
            for (int i = 0; i < visibleVillages.size(); i++) {
                if (visibleVillages.get(i).id().equals(lastVillage)) {
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
            int infoWidth = UiText.drawRight(graphics, font, info, text.right(), y, text.width() / 2,
                    UiTheme.TEXT_MUTED);
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
        if (lastTab == TAB_VILLAGES) {
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
