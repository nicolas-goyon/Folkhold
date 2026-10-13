package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDialog;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiMenu;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTabs;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Active;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Offer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen of a quest board: the offers of the village as papers pinned on a wood board, and the active quests of the
 * player in a column (see {@link QuestBoardPayload}).
 *
 * <ul>
 *   <li>Each offer has one button: accept, or the reason why the player cannot accept it.</li>
 *   <li>Each active quest has the deliver button (or the reason why the player cannot deliver now) and the abandon
 *       button. Abandon needs a confirmation.</li>
 *   <li>The offers can be sorted by reward, time or kind. The board shows pages when the offers do not fit.</li>
 *   <li>The times count down from the moment the payload comes.</li>
 *   <li>A small panel shows tabs: the offers, or the active quests.</li>
 *   <li>A button sends the action to the server (see {@link QuestActionPayload}) and waits for the answer. The
 *       result shows in the status line.</li>
 * </ul>
 */
public class QuestBoardScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.quest_board.";
    private static final int BOARD_COLOR = 0xFF6B4A2C;
    private static final int BOARD_TEXT = 0xFFE5D5B5;
    private static final int COLUMN_WIDTH = 150;
    private static final int CARD_HEIGHT = 64;
    private static final int ACTIVE_HEIGHT = 46;
    private static final int URGENT_SECONDS = 120;

    /** The sort order of the offers. */
    private enum Sort {
        REWARD(Comparator.comparingInt(Offer::emeralds).reversed()),
        TIME(Comparator.comparingInt(Offer::minutes).thenComparing(Comparator.comparingInt(Offer::emeralds)
                .reversed())),
        KIND(Comparator.comparing(Offer::kind).thenComparing(Comparator.comparingInt(Offer::emeralds).reversed()));

        private final Comparator<Offer> comparator;

        Sort(Comparator<Offer> comparator) {
            this.comparator = comparator;
        }

        Component label() {
            return Component.translatable(KEY + "sort." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** One offer on the current page, with its rectangle. */
    private record Card(Offer offer, UiRect rect) {
    }

    /** One active quest on the current page, with its rectangle. */
    private record Entry(Active quest, UiRect rect) {
    }

    private static Sort lastSort = Sort.REWARD;

    private QuestBoardPayload board;
    private long receivedAt;
    private int compactTab;
    private int offerPage;
    private int activePage;
    private final List<Card> cards = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private UiRect boardRect = new UiRect(0, 0, 0, 0);
    private UiRect columnRect = new UiRect(0, 0, 0, 0);
    private int offerPages = 1;
    private int activePages = 1;

    public QuestBoardScreen(QuestBoardPayload board) {
        super(Component.translatable(KEY + "title", board.title()));
        this.board = board;
        this.receivedAt = System.currentTimeMillis();
    }

    /**
     * Shows a payload: updates the open screen of the same village, or opens a new screen. Called by the network
     * handler on the client thread.
     */
    public static void open(QuestBoardPayload board) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestBoardScreen screen
                && screen.board.settlementId().equals(board.settlementId())) {
            screen.update(board);
            return;
        }
        QuestBoardScreen screen = new QuestBoardScreen(board);
        if (minecraft.screen instanceof VillageHeartScreen heart) {
            // Opened from the heart screen: the back button opens it again.
            screen.setPrevious(heart);
        }
        minecraft.setScreen(screen);
        screen.showResult(board);
    }

    private void update(QuestBoardPayload payload) {
        this.board = payload;
        this.receivedAt = System.currentTimeMillis();
        rebuildWidgets();
        showResult(payload);
    }

    private void showResult(QuestBoardPayload payload) {
        payload.status().ifPresent(text -> showStatus(text, payload.statusGood() ? UiTone.GOOD : UiTone.BAD));
    }

    private ReputationTier tier() {
        return ReputationTier.bySerializedName(board.tier());
    }

    @Override
    protected List<Badge> badges() {
        ReputationTier tier = tier();
        Component text = Component.literal(UiReputation.glyph(tier) + " ")
                .append(Component.translatable("folkhold_world.reputation_tier_title." + tier.serializedName()));
        return List.of(Badge.chip(text, UiReputation.background(tier), UiReputation.text(tier)));
    }

    @Override
    protected Component footerHint() {
        return Component.translatable(KEY + "footer", board.offerMinutes());
    }

    // ------------------------------------------------------------------ layout and widgets

    @Override
    protected void initBody(UiRect body) {
        cards.clear();
        entries.clear();
        if (isCompact()) {
            UiRect tabsRect = body.inset(UiTheme.PADDING, 4).top(UiTheme.CONTROL);
            UiTabs tabs = addRenderableWidget(new UiTabs(tabsRect, List.of(
                    Component.translatable(KEY + "offers_count", board.offers().size()),
                    Component.translatable(KEY + "mine", board.active().size(), board.maxActive())),
                    compactTab, index -> {
                        compactTab = index;
                        rebuildWidgets();
                    }));
            setSectionTabs(tabs);
            UiRect content = body.belowTop(4 + UiTheme.CONTROL, 2);
            if (compactTab == 0) {
                boardRect = content;
                columnRect = new UiRect(content.right(), content.y(), 0, 0);
                initBoard();
            } else {
                boardRect = new UiRect(content.x(), content.y(), 0, 0);
                columnRect = content;
                initColumn();
            }
            return;
        }
        boardRect = body.inset(1, 0).beforeRight(COLUMN_WIDTH, 0);
        columnRect = body.inset(1, 0).rightPart(COLUMN_WIDTH);
        initBoard();
        initColumn();
    }

    private UiRect boardHeader() {
        return new UiRect(boardRect.x() + UiTheme.PADDING, boardRect.y() + 4, boardRect.width() - 2 * UiTheme.PADDING,
                UiTheme.CONTROL - 2);
    }

    private void initBoard() {
        UiRect header = boardHeader();
        List<Offer> offers = new ArrayList<>(board.offers());
        offers.sort(lastSort.comparator);
        UiRect grid = new UiRect(boardRect.x() + UiTheme.PADDING, header.bottom() + 6,
                boardRect.width() - 2 * UiTheme.PADDING, boardRect.bottom() - header.bottom() - 10);
        int columns = grid.width() >= 260 ? 2 : 1;
        int rows = Math.max(1, (grid.height() + UiTheme.GAP + 2) / (CARD_HEIGHT + UiTheme.GAP + 2));
        int perPage = columns * rows;
        offerPages = Math.max(1, (offers.size() + perPage - 1) / perPage);
        offerPage = Mth.clamp(offerPage, 0, offerPages - 1);

        Component sortLabel = Component.translatable(KEY + "sort", lastSort.label());
        int sortWidth = Math.min(font.width(sortLabel) + 10, header.width() / 2);
        UiRect sortRect = header.rightPart(sortWidth);
        addRenderableWidget(new UiButton(sortRect, sortLabel, UiButton.Style.MENU, button -> openSortMenu(sortRect))
                .setTextColor(BOARD_TEXT));
        if (offerPages > 1) {
            UiRect pager = header.beforeRight(sortWidth, UiTheme.GAP).rightPart(56);
            addPager(pager, offerPage, offerPages, delta -> {
                offerPage += delta;
                rebuildWidgets();
            });
        }

        int first = offerPage * perPage;
        for (int i = first; i < Math.min(offers.size(), first + perPage); i++) {
            int index = i - first;
            UiRect cell = grid.column(index % columns, columns, UiTheme.GAP + 2);
            UiRect rect = new UiRect(cell.x(), grid.y() + (index / columns) * (CARD_HEIGHT + UiTheme.GAP + 2),
                    cell.width(), CARD_HEIGHT);
            Offer offer = offers.get(i);
            cards.add(new Card(offer, rect));
            UiRect buttonRect = new UiRect(rect.x() + 5, rect.bottom() - 5 - UiTheme.CONTROL, rect.width() - 10,
                    UiTheme.CONTROL);
            UiButton button;
            if (offer.blocked().isPresent()) {
                button = UiButton.normal(buttonRect, offer.blocked().get(), b -> {
                }).onParchment().setDisabled(offer.blocked().get());
            } else {
                button = UiButton.primary(buttonRect, Component.translatable(KEY + "accept"),
                        b -> send(b, QuestBoardPayload.ACTION_ACCEPT, offer.questId())).onParchment();
            }
            addRenderableWidget(button);
        }
    }

    private void initColumn() {
        if (columnRect.width() <= 0) {
            return;
        }
        UiRect inner = columnRect.inset(UiTheme.PADDING, 0);
        UiRect header = new UiRect(inner.x(), columnRect.y() + 4, inner.width(), UiTheme.CONTROL - 2);
        UiRect list = new UiRect(inner.x(), header.bottom() + 6, inner.width(), columnRect.bottom() - header.bottom()
                - 10);
        int perPage = Math.max(1, (list.height() + UiTheme.GAP) / (ACTIVE_HEIGHT + UiTheme.GAP));
        List<Active> active = board.active();
        activePages = Math.max(1, (active.size() + perPage - 1) / perPage);
        activePage = Mth.clamp(activePage, 0, activePages - 1);
        if (activePages > 1) {
            addPager(header.rightPart(56), activePage, activePages, delta -> {
                activePage += delta;
                rebuildWidgets();
            });
        }
        int first = activePage * perPage;
        for (int i = first; i < Math.min(active.size(), first + perPage); i++) {
            Active quest = active.get(i);
            UiRect rect = new UiRect(list.x(), list.y() + (i - first) * (ACTIVE_HEIGHT + UiTheme.GAP), list.width(),
                    ACTIVE_HEIGHT);
            entries.add(new Entry(quest, rect));
            UiRect row = new UiRect(rect.x() + 4, rect.bottom() - 4 - UiTheme.CONTROL, rect.width() - 8,
                    UiTheme.CONTROL);
            UiRect abandon = row.rightPart(UiTheme.CONTROL);
            UiRect main = row.beforeRight(UiTheme.CONTROL, UiTheme.GAP);
            if (quest.action() == QuestBoardPayload.ACTION_DELIVER && quest.ready()) {
                addRenderableWidget(UiButton.primary(main, Component.translatable(KEY + "deliver"),
                        b -> send(b, QuestBoardPayload.ACTION_DELIVER, quest.questId())));
            } else if (quest.blocked().isPresent()) {
                addRenderableWidget(UiButton.normal(main, quest.blocked().get(), b -> {
                }).setDisabled(quest.blocked().get()));
            }
            addRenderableWidget(UiButton.normal(abandon, Component.literal("✕"), b -> confirmAbandon(quest))
                    .setTextColor(UiTheme.TEXT_BAD)
                    .setDetails(Component.translatable(KEY + "abandon")));
        }
    }

    private void addPager(UiRect rect, int page, int pages, IntConsumer move) {
        UiRect previous = rect.left(14);
        UiRect next = rect.rightPart(14);
        UiButton back = UiButton.normal(previous, Component.literal("◀"), b -> move.accept(-1));
        UiButton forward = UiButton.normal(next, Component.literal("▶"), b -> move.accept(1));
        if (page <= 0) {
            back.disable();
        }
        if (page >= pages - 1) {
            forward.disable();
        }
        addRenderableWidget(back);
        addRenderableWidget(forward);
    }

    private void openSortMenu(UiRect anchor) {
        List<UiMenu.Item> items = new ArrayList<>();
        for (Sort sort : Sort.values()) {
            items.add(UiMenu.Action.of(sort.label(), () -> {
                lastSort = sort;
                offerPage = 0;
                rebuildWidgets();
            }));
        }
        UiMenu.open(this, anchor, Component.translatable(KEY + "sort", lastSort.label()), items);
    }

    private void confirmAbandon(Active quest) {
        UiDialog.builder(this, Component.translatable(KEY + "abandon.title"))
                .danger()
                .message(Component.translatable(KEY + "abandon.message", quest.name()))
                .cancel(Component.translatable(KEY + "abandon.keep"))
                .confirm(Component.translatable(KEY + "abandon"),
                        () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestBoardPayload.ACTION_ABANDON,
                                quest.questId(), board.settlementId())))
                .open();
    }

    private void send(UiButton button, int action, String questId) {
        button.setPending(true);
        PacketDistributor.sendToServer(new QuestActionPayload(action, questId, board.settlementId()));
    }

    private int secondsNow(int seconds) {
        return (int) Math.max(0, seconds - (System.currentTimeMillis() - receivedAt) / 1000L);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        if (boardRect.width() > 0) {
            renderBoard(graphics);
        }
        if (columnRect.width() > 0) {
            renderColumn(graphics);
        }
    }

    private void renderBoard(GuiGraphics graphics) {
        UiDraw.fill(graphics, boardRect, BOARD_COLOR);
        UiRect header = boardHeader();
        UiText.drawLeft(graphics, font, Component.translatable(KEY + "offers_count", board.offers().size()),
                header.left(header.width() / 2), UiTheme.TEXT);
        if (cards.isEmpty()) {
            UiRect empty = new UiRect(header.x(), header.bottom() + 8, header.width(), UiTheme.CONTROL + 4);
            UiDraw.dashedOutline(graphics, empty, BOARD_TEXT);
            UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_offers"), empty, BOARD_TEXT);
            return;
        }
        for (Card card : cards) {
            renderCard(graphics, card);
        }
    }

    private void renderCard(GuiGraphics graphics, Card card) {
        Offer offer = card.offer();
        UiRect rect = card.rect();
        boolean off = offer.blocked().isPresent();
        UiDraw.fill(graphics, new UiRect(rect.x() + 2, rect.y() + 2, rect.width(), rect.height()), 0x50000000);
        UiDraw.fill(graphics, rect, off ? UiTheme.PARCHMENT_EDGE : UiTheme.PARCHMENT);
        UiDraw.mark(graphics, rect.centerX() - 2, rect.y() - 2, 5, off ? UiTheme.WOOD_EDGE : UiTheme.WAX);
        UiDraw.itemSlot(graphics, UiDraw.stack(offer.itemId()), rect.x() + 5, rect.y() + 6);
        int textX = rect.x() + 28;
        int textWidth = rect.right() - 5 - textX;
        UiText.draw(graphics, font, offer.name(), textX, rect.y() + 6, textWidth,
                off ? UiTheme.INK_MUTED : UiTheme.INK);
        UiText.draw(graphics, font, offer.info(), textX, rect.y() + 16, textWidth, UiTheme.INK_MUTED);
        Component right = offer.progress();
        if (offer.countdown() >= 0) {
            right = Component.translatable(KEY + "leaves_in", UiText.clock(secondsNow(offer.countdown())))
                    .withColor(UiTheme.WAX & 0xFFFFFF);
        }
        int rightWidth = font.width(right);
        int y = rect.y() + 28;
        UiText.draw(graphics, font, offer.reward(), rect.x() + 5, y, rect.width() - 14 - rightWidth,
                off ? UiTheme.INK_MUTED : UiTheme.INK);
        UiText.drawRight(graphics, font, right, rect.right() - 5, y, rect.width() / 2, UiTheme.INK_MUTED);
    }

    private void renderColumn(GuiGraphics graphics) {
        UiRect inner = columnRect.inset(UiTheme.PADDING, 0);
        UiText.drawLeft(graphics, font, Component.translatable(KEY + "mine", board.active().size(),
                board.maxActive()), new UiRect(inner.x(), columnRect.y() + 4, inner.width() - 60, UiTheme.CONTROL - 2),
                UiTheme.GOLD);
        UiDraw.hLine(graphics, columnRect.x(), columnRect.y() + UiTheme.CONTROL + 6, columnRect.width(),
                UiTheme.WOOD_RAISED);
        if (entries.isEmpty()) {
            UiRect empty = new UiRect(inner.x(), columnRect.y() + UiTheme.CONTROL + 12, inner.width(),
                    UiTheme.CONTROL + 4);
            UiDraw.dashedOutline(graphics, empty, UiTheme.WOOD_EDGE);
            UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_quests"), empty.inset(3, 0),
                    UiTheme.TEXT_MUTED);
            return;
        }
        for (Entry entry : entries) {
            Active quest = entry.quest();
            UiRect rect = entry.rect();
            UiDraw.box(graphics, rect, UiTheme.WOOD_ROW, quest.ready() ? 0xFF6E9C4A : UiTheme.WOOD_EDGE, false);
            int seconds = secondsNow(quest.secondsLeft());
            Component time = Component.literal(UiText.clock(seconds));
            int timeWidth = UiText.drawRight(graphics, font, time, rect.right() - 4, rect.y() + 4, 40,
                    seconds < URGENT_SECONDS ? UiTheme.TEXT_URGENT : UiTheme.TEXT);
            UiText.draw(graphics, font, quest.name(), rect.x() + 4, rect.y() + 4, rect.width() - 12 - timeWidth,
                    UiTheme.TEXT);
            UiText.draw(graphics, font, quest.info(), rect.x() + 4, rect.y() + 14, rect.width() - 8,
                    UiTheme.TEXT_MUTED);
        }
    }
}
