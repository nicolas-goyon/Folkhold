package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload.Entry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Screen of a quest board: the offers of the village and the active quests of the player (see
 * {@link QuestBoardPayload}).
 *
 * <ul>
 *   <li>Two tabs: the offers of the village, and the quests of the player. A click on a tab, or the left and right
 *       arrow keys, change the tab.</li>
 *   <li>Each quest has two lines (what to do, then the reward and the time) and its buttons on the right.</li>
 *   <li>A button sends the action to the server (see {@link QuestActionPayload}). The server answers with a new
 *       payload, and the screen shows it.</li>
 *   <li>The quests scroll with the mouse wheel.</li>
 * </ul>
 */
public class QuestBoardScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 210;
    private static final int MARGIN = 10;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 22;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_GAP = 2;
    private static final int ENTRY_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 26;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 18;
    private static final int ACTION_WIDTH = 56;
    private static final int ACTION_HEIGHT = 12;
    private static final int ACTION_GAP = 3;
    private static final int SCROLL_BAR_WIDTH = 3;

    private static final int FRAME_OUTER = 0xFF2B1F12;
    private static final int FRAME_GOLD = 0xFFB8913F;
    private static final int PANEL_COLOR = 0xF0211B14;
    private static final int HEADER_COLOR = 0xF0302619;
    private static final int TAB_COLOR = 0xFF1A1510;
    private static final int TAB_HOVER_COLOR = 0xFF2A2219;
    private static final int TAB_ACTIVE_COLOR = 0xFF3A2F21;
    private static final int ENTRY_STRIPE_COLOR = 0x14FFFFFF;
    private static final int ACTION_COLOR = 0xFF3A2F21;
    private static final int ACTION_HOVER_COLOR = 0xFF55452F;
    private static final int SCROLL_TRACK_COLOR = 0x40FFFFFF;
    private static final int SCROLL_THUMB_COLOR = 0xFFB8913F;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int TAB_TEXT_COLOR = 0x9A8F7A;
    private static final int TAB_ACTIVE_TEXT_COLOR = 0xFFD37F;
    private static final int NAME_COLOR = 0xFFFFFF;
    private static final int DETAILS_COLOR = 0xA89F8C;
    private static final int ACTION_TEXT_COLOR = 0xFFD37F;
    private static final int EMPTY_COLOR = 0x7A7262;

    private static final Component[] TABS = {
        Component.translatable("screen.folkhold_world.quest_board.offers"),
        Component.translatable("screen.folkhold_world.quest_board.active")
    };

    private QuestBoardPayload board;
    private int tab;
    private int scroll;
    private int left;
    private int top;
    private int panelHeight;

    public QuestBoardScreen(QuestBoardPayload board) {
        super(board.title());
        this.board = board;
    }

    /**
     * Shows a payload: updates the open screen of the same village, or opens a new screen. Called by the network
     * handler on the client thread.
     */
    public static void open(QuestBoardPayload board) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestBoardScreen screen
                && screen.board.settlementId().equals(board.settlementId())) {
            screen.board = board;
            screen.setScroll(screen.scroll);
            return;
        }
        minecraft.setScreen(new QuestBoardScreen(board));
    }

    @Override
    protected void init() {
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 2 * MARGIN);
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;
        int buttonTop = top + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - BUTTON_WIDTH / 2, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        setScroll(scroll);
    }

    // ------------------------------------------------------------------ layout

    private int tabsTop() {
        return top + HEADER_HEIGHT + 2;
    }

    private int contentLeft() {
        return left + PADDING;
    }

    private int contentTop() {
        return tabsTop() + TAB_HEIGHT + 4;
    }

    private int contentRight() {
        return left + PANEL_WIDTH - PADDING - SCROLL_BAR_WIDTH - 2;
    }

    private int contentBottom() {
        return top + panelHeight - FOOTER_HEIGHT;
    }

    private int tabWidth() {
        return (PANEL_WIDTH - 2 * PADDING - (TABS.length - 1) * TAB_GAP) / TABS.length;
    }

    private int tabLeft(int index) {
        return left + PADDING + index * (tabWidth() + TAB_GAP);
    }

    private List<Entry> entries() {
        return tab == 0 ? board.offers() : board.active();
    }

    private int maxScroll() {
        return Math.max(0, entries().size() * ENTRY_HEIGHT - (contentBottom() - contentTop()));
    }

    private void setScroll(int value) {
        scroll = Mth.clamp(value, 0, maxScroll());
    }

    private void selectTab(int index) {
        if (index >= 0 && index < TABS.length && index != tab) {
            tab = index;
            scroll = 0;
        }
    }

    /** The label of the main button of an action, or null for no button. */
    private static Component actionLabel(int action) {
        return switch (action) {
            case QuestBoardPayload.ACTION_ACCEPT -> Component.translatable("screen.folkhold_world.quest_board.accept");
            case QuestBoardPayload.ACTION_DELIVER -> Component.translatable(
                    "screen.folkhold_world.quest_board.deliver");
            default -> null;
        };
    }

    /** Left X of the main button of an entry. */
    private int mainButtonLeft() {
        return contentRight() - 3 - ACTION_WIDTH;
    }

    /** Top Y of the main button of an entry, and of the abandon button below it. */
    private int buttonTop(int entryTop, boolean second) {
        int top2 = entryTop + (ENTRY_HEIGHT - 2 * ACTION_HEIGHT - 1) / 2;
        return second ? top2 + ACTION_HEIGHT + 1 : top2;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= tabsTop() && mouseY < tabsTop() + TAB_HEIGHT) {
            for (int i = 0; i < TABS.length; i++) {
                if (mouseX >= tabLeft(i) && mouseX < tabLeft(i) + tabWidth()) {
                    selectTab(i);
                    return true;
                }
            }
        }
        if (button == 0 && mouseY >= contentTop() && mouseY < contentBottom()) {
            List<Entry> entries = entries();
            int x = mainButtonLeft();
            for (int i = 0; i < entries.size(); i++) {
                int entryTop = contentTop() - scroll + i * ENTRY_HEIGHT;
                Entry entry = entries.get(i);
                boolean hasMain = actionLabel(entry.action()) != null;
                if (hasMain && inside(mouseX, mouseY, x, buttonTop(entryTop, false))) {
                    send(entry.action(), entry);
                    return true;
                }
                if (entry.abandon() && inside(mouseX, mouseY, x, buttonTop(entryTop, hasMain))) {
                    send(QuestBoardPayload.ACTION_ABANDON, entry);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + ACTION_WIDTH && mouseY >= y && mouseY < y + ACTION_HEIGHT;
    }

    private void send(int action, Entry entry) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PacketDistributor.sendToServer(new QuestActionPayload(action, entry.questId(), board.settlementId()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            setScroll(scroll - (int) Math.round(scrollY * ENTRY_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            selectTab(tab - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            selectTab(tab + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight;
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, FRAME_OUTER);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, FRAME_GOLD);
        graphics.fill(left, top, right, bottom, PANEL_COLOR);
        graphics.fill(left, top, right, top + HEADER_HEIGHT, HEADER_COLOR);
        graphics.fill(left + PADDING, top + HEADER_HEIGHT - 1, right - PADDING, top + HEADER_HEIGHT, FRAME_GOLD);
        graphics.fill(left + PADDING, contentBottom() + 2, right - PADDING, contentBottom() + 3, FRAME_GOLD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = Component.translatable("screen.folkhold_world.quest_board.title", title);
        FormattedCharSequence titleText = ellipsize(heading, PANEL_WIDTH - 2 * PADDING);
        graphics.drawString(font, titleText, width / 2 - font.width(titleText) / 2,
                top + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, TITLE_COLOR);
        renderTabs(graphics, mouseX, mouseY);
        renderEntries(graphics, mouseX, mouseY);
        renderScrollBar(graphics);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabTop = tabsTop();
        for (int i = 0; i < TABS.length; i++) {
            int x = tabLeft(i);
            int w = tabWidth();
            boolean active = i == tab;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= tabTop && mouseY < tabTop + TAB_HEIGHT;
            graphics.fill(x, tabTop, x + w, tabTop + TAB_HEIGHT,
                    active ? TAB_ACTIVE_COLOR : hover ? TAB_HOVER_COLOR : TAB_COLOR);
            if (active) {
                graphics.fill(x, tabTop, x + w, tabTop + 1, FRAME_GOLD);
            }
            int count = i == 0 ? board.offers().size() : board.active().size();
            Component text = Component.translatable("screen.folkhold_world.quest_board.tab", TABS[i], count);
            FormattedCharSequence label = ellipsize(text, w - 2);
            graphics.drawString(font, label, x + (w - font.width(label)) / 2,
                    tabTop + (TAB_HEIGHT - font.lineHeight) / 2 + 1,
                    active ? TAB_ACTIVE_TEXT_COLOR : TAB_TEXT_COLOR, false);
        }
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Entry> entries = entries();
        int x = contentLeft();
        int right = contentRight();
        if (entries.isEmpty()) {
            Component empty = Component.translatable(tab == 0 ? "screen.folkhold_world.quest_board.no_offers"
                    : "screen.folkhold_world.quest_board.no_quests");
            graphics.drawString(font, empty, x + (right - x - font.width(empty)) / 2, contentTop() + 4, EMPTY_COLOR,
                    false);
            return;
        }
        graphics.enableScissor(x, contentTop(), right, contentBottom());
        int buttonLeft = mainButtonLeft();
        int textWidth = buttonLeft - ACTION_GAP - x - 3;
        for (int i = 0; i < entries.size(); i++) {
            int y = contentTop() - scroll + i * ENTRY_HEIGHT;
            if (y + ENTRY_HEIGHT < contentTop() || y > contentBottom()) {
                continue;
            }
            Entry entry = entries.get(i);
            if (i % 2 == 0) {
                graphics.fill(x, y, right, y + ENTRY_HEIGHT, ENTRY_STRIPE_COLOR);
            }
            graphics.drawString(font, ellipsize(entry.name(), textWidth), x + 3, y + 3, NAME_COLOR, false);
            graphics.drawString(font, ellipsize(entry.details(), textWidth), x + 3, y + 14, DETAILS_COLOR, false);
            Component main = actionLabel(entry.action());
            if (main != null) {
                renderAction(graphics, main, buttonLeft, buttonTop(y, false), mouseX, mouseY);
            }
            if (entry.abandon()) {
                renderAction(graphics, Component.translatable("screen.folkhold_world.quest_board.abandon"),
                        buttonLeft, buttonTop(y, main != null), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
    }

    private void renderAction(GuiGraphics graphics, Component label, int x, int y, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y) && mouseY >= contentTop() && mouseY < contentBottom();
        graphics.fill(x, y, x + ACTION_WIDTH, y + ACTION_HEIGHT, hover ? ACTION_HOVER_COLOR : ACTION_COLOR);
        graphics.fill(x, y + ACTION_HEIGHT - 1, x + ACTION_WIDTH, y + ACTION_HEIGHT, FRAME_GOLD);
        FormattedCharSequence text = ellipsize(label, ACTION_WIDTH - 4);
        graphics.drawString(font, text, x + (ACTION_WIDTH - font.width(text)) / 2,
                y + (ACTION_HEIGHT - font.lineHeight) / 2 + 1, ACTION_TEXT_COLOR, false);
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int x = left + PANEL_WIDTH - PADDING - SCROLL_BAR_WIDTH;
        int trackTop = contentTop();
        int trackHeight = contentBottom() - trackTop;
        int contentHeight = entries().size() * ENTRY_HEIGHT;
        int thumbHeight = Math.max(8, trackHeight * trackHeight / contentHeight);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll / max;
        graphics.fill(x, trackTop, x + SCROLL_BAR_WIDTH, trackTop + trackHeight, SCROLL_TRACK_COLOR);
        graphics.fill(x, thumbTop, x + SCROLL_BAR_WIDTH, thumbTop + thumbHeight, SCROLL_THUMB_COLOR);
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
