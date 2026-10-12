package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Screen of the village heart: the village information that the server sent (see {@link VillageInfoPayload}).
 *
 * <ul>
 *   <li>A panel of fixed size, which fits in the screen at all GUI scales.</li>
 *   <li>One tab for each section of the payload. A click on a tab, or the left and right arrow keys, change the
 *       tab. The screen keeps the last tab for the next opening.</li>
 *   <li>The rows of a tab scroll with the mouse wheel. A scroll bar shows when the rows do not fit.</li>
 *   <li>A row has a label on the left and a value on the right. A value that is too long ends with "…".</li>
 * </ul>
 */
public class VillageHeartScreen extends Screen {
    private static final int PANEL_WIDTH = 276;
    private static final int MAX_PANEL_HEIGHT = 196;
    private static final int MARGIN = 10;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 22;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_GAP = 2;
    private static final int ROW_HEIGHT = 12;
    private static final int FOOTER_HEIGHT = 26;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SCROLL_BAR_WIDTH = 3;
    private static final int SCROLL_STEP = ROW_HEIGHT * 2;

    private static final int FRAME_OUTER = 0xFF2B1F12;
    private static final int FRAME_GOLD = 0xFFB8913F;
    private static final int PANEL_COLOR = 0xF0211B14;
    private static final int HEADER_COLOR = 0xF0302619;
    private static final int TAB_COLOR = 0xFF1A1510;
    private static final int TAB_HOVER_COLOR = 0xFF2A2219;
    private static final int TAB_ACTIVE_COLOR = 0xFF3A2F21;
    private static final int ROW_STRIPE_COLOR = 0x14FFFFFF;
    private static final int SCROLL_TRACK_COLOR = 0x40FFFFFF;
    private static final int SCROLL_THUMB_COLOR = 0xFFB8913F;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int TAB_TEXT_COLOR = 0x9A8F7A;
    private static final int TAB_ACTIVE_TEXT_COLOR = 0xFFD37F;
    private static final int LABEL_COLOR = 0xA89F8C;
    private static final int VALUE_COLOR = 0xFFFFFF;
    private static final int EMPTY_COLOR = 0x7A7262;

    /** The tab of the last opening. */
    private static int lastTab;

    private final VillageInfoPayload info;
    private int tab;
    private int scroll;
    private int left;
    private int top;
    private int panelHeight;

    public VillageHeartScreen(VillageInfoPayload info) {
        super(info.title());
        this.info = info;
        this.tab = info.sections().isEmpty() ? 0 : Mth.clamp(lastTab, 0, info.sections().size() - 1);
    }

    /** Opens the screen. Called by the network handler on the client thread. */
    public static void open(VillageInfoPayload info) {
        Minecraft.getInstance().setScreen(new VillageHeartScreen(info));
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
        int count = Math.max(1, info.sections().size());
        return (PANEL_WIDTH - 2 * PADDING - (count - 1) * TAB_GAP) / count;
    }

    private int tabLeft(int index) {
        return left + PADDING + index * (tabWidth() + TAB_GAP);
    }

    private List<Row> rows() {
        return info.sections().isEmpty() ? List.of() : info.sections().get(tab).rows();
    }

    private int maxScroll() {
        return Math.max(0, rows().size() * ROW_HEIGHT - (contentBottom() - contentTop()));
    }

    private void setScroll(int value) {
        scroll = Mth.clamp(value, 0, maxScroll());
    }

    private void selectTab(int index) {
        if (index >= 0 && index < info.sections().size() && index != tab) {
            tab = index;
            lastTab = index;
            scroll = 0;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= tabsTop() && mouseY < tabsTop() + TAB_HEIGHT) {
            for (int i = 0; i < info.sections().size(); i++) {
                if (mouseX >= tabLeft(i) && mouseX < tabLeft(i) + tabWidth()) {
                    selectTab(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            setScroll(scroll - (int) Math.round(scrollY * SCROLL_STEP));
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

    /** The vanilla background, then the frame and the panel. The widgets and the texts come on top. */
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
        FormattedCharSequence titleText = ellipsize(title, PANEL_WIDTH - 2 * PADDING);
        graphics.drawString(font, titleText, width / 2 - font.width(titleText) / 2,
                top + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, TITLE_COLOR);
        renderTabs(graphics, mouseX, mouseY);
        renderRows(graphics);
        renderScrollBar(graphics);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabTop = tabsTop();
        for (int i = 0; i < info.sections().size(); i++) {
            int x = tabLeft(i);
            int w = tabWidth();
            boolean active = i == tab;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= tabTop && mouseY < tabTop + TAB_HEIGHT;
            graphics.fill(x, tabTop, x + w, tabTop + TAB_HEIGHT,
                    active ? TAB_ACTIVE_COLOR : hover ? TAB_HOVER_COLOR : TAB_COLOR);
            if (active) {
                graphics.fill(x, tabTop, x + w, tabTop + 1, FRAME_GOLD);
            }
            FormattedCharSequence label = ellipsize(info.sections().get(i).heading(), w - 2);
            graphics.drawString(font, label, x + (w - font.width(label)) / 2,
                    tabTop + (TAB_HEIGHT - font.lineHeight) / 2 + 1,
                    active ? TAB_ACTIVE_TEXT_COLOR : TAB_TEXT_COLOR, false);
        }
    }

    private void renderRows(GuiGraphics graphics) {
        List<Row> rows = rows();
        int x = contentLeft();
        int right = contentRight();
        if (rows.isEmpty()) {
            Component empty = Component.translatable("screen.folkhold_world.village_heart.empty");
            graphics.drawString(font, empty, x + (right - x - font.width(empty)) / 2, contentTop() + 4, EMPTY_COLOR,
                    false);
            return;
        }
        graphics.enableScissor(x, contentTop(), right, contentBottom());
        int y = contentTop() - scroll;
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW_HEIGHT >= contentTop() && y <= contentBottom()) {
                Row row = rows.get(i);
                if (i % 2 == 0) {
                    graphics.fill(x, y, right, y + ROW_HEIGHT, ROW_STRIPE_COLOR);
                }
                int textY = y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
                int available = right - x - 6;
                FormattedCharSequence label = ellipsize(row.label(), available * 3 / 5);
                int labelWidth = font.width(label);
                FormattedCharSequence value = ellipsize(row.value(), available - labelWidth - 6);
                graphics.drawString(font, label, x + 3, textY, LABEL_COLOR, false);
                graphics.drawString(font, value, right - 3 - font.width(value), textY, VALUE_COLOR, false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int x = left + PANEL_WIDTH - PADDING - SCROLL_BAR_WIDTH;
        int trackTop = contentTop();
        int trackHeight = contentBottom() - trackTop;
        int contentHeight = rows().size() * ROW_HEIGHT;
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
