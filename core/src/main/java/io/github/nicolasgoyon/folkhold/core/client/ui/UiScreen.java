package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The base of the Folkhold screens: a wood panel at the center of the screen, with a header, a body and a footer.
 *
 * <ul>
 *   <li>The panel has its preferred size ({@link #preferredWidth()}, {@link #preferredHeight()}), less if the screen
 *       is smaller. A screen must work at the minimum size: 320 × 240 GUI pixels, so a panel of 308 × 228 pixels.
 *       {@link #isCompact()} tells the subclass to use its short layout.</li>
 *   <li>Header: an optional back button, an optional banner, the title, an optional subtitle, the badges and the
 *       close button. The back button shows when the screen has a previous screen ({@link #setPrevious}), for
 *       example a sub-screen of the admin screen.</li>
 *   <li>Footer: an optional hint line, for example the keys.</li>
 *   <li>A status line above the footer shows the result of the last action for some seconds
 *       ({@link #showStatus}). It covers the bottom of the body, above the widgets.</li>
 *   <li>The screen does not pause the game, and the game stays visible behind the panel.</li>
 *   <li>Escape closes the screen. The left and right arrow keys change the section tabs, if the screen has them
 *       ({@link #setSectionTabs}).</li>
 * </ul>
 *
 * <p>The subclass makes its widgets in {@link #initBody(UiRect)} and draws its content behind the widgets in
 * {@link #renderBody}. The game calls {@code init} again after a resize, so the subclass keeps its state in fields
 * and makes its widgets again from this state.
 */
public abstract class UiScreen extends Screen {
    /** The height of the header with one line. */
    protected static final int HEADER = 22;
    /** The height of the header with a subtitle. */
    protected static final int HEADER_TALL = 28;
    /** The height of the footer hint line. */
    protected static final int FOOTER = 12;
    /** The height of the status line. */
    protected static final int STATUS = 14;
    /** The time of a status line, in milliseconds. */
    private static final long STATUS_MILLIS = 6000L;
    /** A panel narrower or lower than this size uses the compact layout. */
    private static final int COMPACT_WIDTH = 400;
    private static final int COMPACT_HEIGHT = 250;

    /** True while an overlay draws its parent screen: the parent then does not dim the game. */
    private static boolean drawnBehind;

    /**
     * A badge of the header.
     *
     * @param text       the text
     * @param background the background color
     * @param border     the border color
     * @param textColor  the text color
     */
    public record Badge(Component text, int background, int border, int textColor) {

        /** A badge with the colors of a tone. */
        public static Badge of(Component text, UiTone tone) {
            return new Badge(text, tone.background(), tone.border(), tone.text());
        }

        /** A chip: a badge with no border, for example the reputation tier. */
        public static Badge chip(Component text, int background, int textColor) {
            return new Badge(text, background, background, textColor);
        }
    }

    private UiRect panel = new UiRect(0, 0, 0, 0);
    private UiRect header = panel;
    private UiRect body = panel;
    private UiRect footer = panel;
    @Nullable
    private UiTabs sectionTabs;
    @Nullable
    private Component status;
    private UiTone statusTone = UiTone.GOOD;
    private long statusUntil;
    @Nullable
    private Screen previous;

    protected UiScreen(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------ to override

    /** The preferred width of the panel. */
    protected int preferredWidth() {
        return 468;
    }

    /** The preferred height of the panel. */
    protected int preferredHeight() {
        return 258;
    }

    /** The second line of the header, or null. */
    @Nullable
    protected Component subtitle() {
        return null;
    }

    /** The badges at the right of the header. */
    protected List<Badge> badges() {
        return List.of();
    }

    /** The color of the banner at the left of the header, or -1 for no banner. */
    protected int bannerColor() {
        return -1;
    }

    /** The hint of the footer, or null for no footer. */
    @Nullable
    protected Component footerHint() {
        return sectionTabs != null ? UiStrings.HINT_SECTIONS : UiStrings.HINT_CLOSE;
    }

    /** Makes the widgets of the body. */
    protected abstract void initBody(UiRect body);

    /** Draws the content of the body, behind the widgets. */
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
    }

    /** Draws above the widgets, for example a tooltip of a drawn element. */
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ layout

    /** The panel. */
    protected UiRect panel() {
        return panel;
    }

    /** The body: the panel less the header, the status line and the footer. */
    protected UiRect body() {
        return body;
    }

    /** True if the panel is small: the subclass shows a shorter layout. */
    protected boolean isCompact() {
        return panel.width() < COMPACT_WIDTH || panel.height() < COMPACT_HEIGHT;
    }

    /**
     * Sets the screen that the back button opens again. The previous screen keeps its state: the game calls its
     * {@code init} again.
     *
     * @param screen the previous screen, or null for no back button
     */
    public void setPrevious(@Nullable Screen screen) {
        this.previous = screen;
    }

    /** Opens the previous screen again, if there is one. */
    protected void back() {
        if (previous != null && minecraft != null) {
            minecraft.setScreen(previous);
        }
    }

    /** The tabs that the left and right arrow keys change. */
    protected void setSectionTabs(@Nullable UiTabs tabs) {
        this.sectionTabs = tabs;
    }

    @Override
    protected final void init() {
        panel = UiRect.panel(width, height, preferredWidth(), preferredHeight(), UiTheme.SCREEN_MARGIN);
        header = panel.top(subtitle() != null ? HEADER_TALL : HEADER);
        Component hint = footerHint();
        footer = hint != null ? panel.bottomPart(FOOTER) : panel.bottomPart(0);
        body = new UiRect(panel.x(), header.bottom(), panel.width(), footer.y() - header.bottom());
        sectionTabs = null;
        int closeSize = header.height() >= HEADER_TALL ? 16 : 14;
        UiRect close = new UiRect(header.right() - UiTheme.PADDING - closeSize,
                header.y() + (header.height() - closeSize) / 2, closeSize, closeSize);
        addRenderableWidget(UiButton.normal(close, Component.literal("✕"), button -> onClose())
                .setDetails(UiStrings.CLOSE));
        if (previous != null) {
            UiRect backRect = new UiRect(header.x() + UiTheme.PADDING, close.y(), closeSize, closeSize);
            addRenderableWidget(UiButton.normal(backRect, Component.literal("←"), button -> back())
                    .setDetails(UiStrings.BACK));
        }
        initBody(body);
    }

    // ------------------------------------------------------------------ status line

    /**
     * Shows a status line above the footer for some seconds: the result of an action. It replaces a chat message.
     *
     * @param text the text
     * @param tone {@link UiTone#GOOD} for a success, {@link UiTone#BAD} for a refusal
     */
    public void showStatus(Component text, UiTone tone) {
        this.status = text;
        this.statusTone = tone;
        this.statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
    }

    // ------------------------------------------------------------------ input

    /** Advances the hold buttons of the screen. A screen with its own tick calls this method. */
    @Override
    public void tick() {
        super.tick();
        UiHoldButton.tickAll(children());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sectionTabs != null && !(getFocused() instanceof UiTextField)
                && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            sectionTabs.move(keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!drawnBehind) {
            graphics.fill(0, 0, width, height, UiTheme.SCREEN_DIM);
        }
        UiDraw.panel(graphics, panel);
        UiDraw.fill(graphics, header.inset(1, 1).top(header.height() - 1), UiTheme.WOOD_DARK);
        UiDraw.hLine(graphics, header.x() + 1, header.bottom() - 1, header.width() - 2, UiTheme.GILT);
        renderHeader(graphics);
        renderBody(graphics, body, mouseX, mouseY, partialTick);
        renderFooter(graphics);
    }

    private void renderHeader(GuiGraphics graphics) {
        int left = header.x() + UiTheme.PADDING;
        if (previous != null) {
            left += (header.height() >= HEADER_TALL ? 16 : 14) + UiTheme.GAP + 1;
        }
        int banner = bannerColor();
        if (banner >= 0) {
            UiDraw.banner(graphics, left, header.y() + (header.height() - 14) / 2, banner);
            left += 15;
        }
        int right = header.right() - UiTheme.PADDING - 16 - UiTheme.GAP;
        for (int i = badges().size() - 1; i >= 0; i--) {
            Badge badge = badges().get(i);
            int badgeWidth = UiDraw.badgeWidth(font, badge.text());
            if (right - badgeWidth < left + 60) {
                break;
            }
            right -= badgeWidth;
            UiRect rect = new UiRect(right, header.y() + (header.height() - UiTheme.BADGE) / 2, badgeWidth,
                    UiTheme.BADGE);
            UiDraw.box(graphics, rect, badge.background(), badge.border(), false);
            graphics.drawString(font, badge.text(), rect.x() + 4, UiText.textY(rect), badge.textColor(), false);
            right -= UiTheme.GAP;
        }
        Component sub = subtitle();
        int textWidth = right - left - UiTheme.GAP;
        if (sub != null) {
            UiText.draw(graphics, font, title, left, header.y() + 5, textWidth, UiTheme.TEXT);
            UiText.draw(graphics, font, sub, left, header.y() + 16, textWidth, UiTheme.TEXT_MUTED);
        } else {
            UiText.draw(graphics, font, title, left, UiText.textY(header), textWidth, UiTheme.TEXT);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        if (status == null || System.currentTimeMillis() >= statusUntil) {
            return;
        }
        UiRect line = new UiRect(body.x() + 1, footer.y() - STATUS, body.width() - 2, STATUS);
        boolean good = statusTone == UiTone.GOOD;
        graphics.pose().pushPose();
        // Above the widgets and their items.
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        UiDraw.box(graphics, line, good ? UiTheme.STATUS_GOOD : UiTheme.STATUS_BAD,
                good ? UiTheme.STATUS_GOOD_EDGE : UiTheme.STATUS_BAD_EDGE, false);
        UiText.drawLeft(graphics, font, status, line.inset(UiTheme.PADDING, 0),
                good ? UiTheme.STATUS_GOOD_TEXT : UiTheme.STATUS_BAD_TEXT);
        graphics.pose().popPose();
    }

    private void renderFooter(GuiGraphics graphics) {
        Component hint = footerHint();
        if (hint != null && footer.height() > 0) {
            UiRect inner = footer.inset(1, 0).top(footer.height() - 1);
            UiDraw.fill(graphics, inner, UiTheme.WOOD_DARK);
            UiText.drawLeft(graphics, font, hint, inner.inset(UiTheme.PADDING, 0), UiTheme.TEXT_MUTED);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderStatus(graphics);
        renderOverlay(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Draws a screen behind an overlay (a menu or a dialog). A Folkhold screen then does not dim the game: the
     * overlay dims the game and its parent once.
     */
    static void renderBehind(Screen screen, GuiGraphics graphics, float partialTick) {
        drawnBehind = true;
        try {
            screen.render(graphics, -1, -1, partialTick);
        } finally {
            drawnBehind = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
