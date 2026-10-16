package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A button of the Folkhold screens.
 *
 * <ul>
 *   <li>A {@link Style}: normal, primary (the main action), dangerous, or a row of a menu.</li>
 *   <li>Disabled with a reason: the button has a dashed border, and the reason is its tooltip.</li>
 *   <li>Waiting: after a click that sends an action to the server, the button shows "…" until the screen gets the
 *       answer (see {@link #setPending(boolean)}), or for {@link #PENDING_TIMEOUT_MILLIS} at most.</li>
 *   <li>Keyboard focus (Tab key): a dashed gold line around the button. Enter or Space clicks the button.</li>
 *   <li>A label that does not fit ends with "…", and the tooltip shows the complete label.</li>
 * </ul>
 */
public class UiButton extends AbstractButton {
    /** The waiting state ends after this time with no answer of the server, in milliseconds. */
    public static final long PENDING_TIMEOUT_MILLIS = 5000L;

    /** The look of a button. */
    public enum Style {
        /** A normal action on wood. */
        NORMAL,
        /** The main action of a screen or of a card. */
        PRIMARY,
        /** An action that the player cannot undo: pillage, abandon, ruin. */
        DANGER,
        /** A row of a drop-down menu: a text with no border. */
        MENU,
        /** A small filter chip. */
        CHIP,
        /** A selected filter chip. */
        CHIP_SELECTED
    }

    private static final String[] SPINNER = {"◴", "◷", "◶", "◵"};

    private final Consumer<UiButton> action;
    private Style style;
    @Nullable
    private Component disabledReason;
    @Nullable
    private Component tooltipText;
    private boolean pending;
    private long pendingSince;
    private boolean parchment;
    private int textColor;

    /**
     * Makes a button.
     *
     * @param rect   the position and the size
     * @param label  the label
     * @param style  the look
     * @param action the action of a click
     */
    public UiButton(UiRect rect, Component label, Style style, Consumer<UiButton> action) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), label);
        this.style = style;
        this.action = action;
        updateTooltip();
    }

    /** A normal button. */
    public static UiButton normal(UiRect rect, Component label, Consumer<UiButton> action) {
        return new UiButton(rect, label, Style.NORMAL, action);
    }

    /** A primary button: the main action. */
    public static UiButton primary(UiRect rect, Component label, Consumer<UiButton> action) {
        return new UiButton(rect, label, Style.PRIMARY, action);
    }

    /** A dangerous button. */
    public static UiButton danger(UiRect rect, Component label, Consumer<UiButton> action) {
        return new UiButton(rect, label, Style.DANGER, action);
    }

    public Style style() {
        return style;
    }

    /** Changes the look. */
    public UiButton setStyle(Style style) {
        this.style = style;
        return this;
    }

    /** The button is on parchment: the primary style has a darker border, and a disabled button is lighter. */
    public UiButton onParchment() {
        this.parchment = true;
        return this;
    }

    /**
     * Gives the label another color (for example the faction color, or a danger in a menu). The value 0 keeps the
     * color of the style.
     */
    public UiButton setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    /** Sets a tooltip with more details. */
    public UiButton setDetails(@Nullable Component details) {
        this.tooltipText = details;
        updateTooltip();
        return this;
    }

    /**
     * Disables the button with a reason, or enables it.
     *
     * @param reason the reason that the tooltip shows, or null to enable the button
     * @return this button
     */
    public UiButton setDisabled(@Nullable Component reason) {
        this.disabledReason = reason;
        this.active = reason == null;
        updateTooltip();
        return this;
    }

    /** Disables the button with no reason (the screen shows the reason in another place). */
    public UiButton disable() {
        this.disabledReason = null;
        this.active = false;
        updateTooltip();
        return this;
    }

    /**
     * True while the screen waits for the answer of the server. The waiting state ends by itself after
     * {@link #PENDING_TIMEOUT_MILLIS}, when the server does not answer.
     */
    public boolean isPending() {
        if (pending && System.currentTimeMillis() - pendingSince > PENDING_TIMEOUT_MILLIS) {
            pending = false;
        }
        return pending;
    }

    /** Shows the waiting state. A waiting button does not accept clicks. */
    public UiButton setPending(boolean pending) {
        this.pending = pending;
        this.pendingSince = System.currentTimeMillis();
        return this;
    }

    private void updateTooltip() {
        Component text = disabledReason != null ? disabledReason : tooltipText;
        if (text == null && UiText.isCut(Minecraft.getInstance().font, getMessage(), width - 6)) {
            text = getMessage();
        }
        setTooltip(text == null ? null : Tooltip.create(text));
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        updateTooltip();
    }

    @Override
    public void onPress() {
        if (!isPending()) {
            action.accept(this);
        }
    }

    /** True if the button shows the keyboard focus. */
    protected boolean showsKeyboardFocus() {
        return isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        UiRect rect = new UiRect(getX(), getY(), width, height);
        boolean hovered = isHovered() && active && !isPending();
        int text;
        if (!active) {
            if (style != Style.MENU) {
                UiDraw.box(graphics, rect, parchment ? UiTheme.PARCHMENT_OFF_EDGE : UiTheme.DISABLED,
                        parchment ? 0xFF8A7A58 : UiTheme.DISABLED_EDGE, true);
            }
            text = parchment ? 0xFF5C4E38 : UiTheme.TEXT_FAINT;
        } else {
            text = switch (style) {
                case NORMAL -> {
                    UiDraw.box(graphics, rect, hovered ? UiTheme.WOOD_HOVER : UiTheme.WOOD_RAISED,
                            hovered ? UiTheme.GOLD : UiTheme.WOOD_EDGE, false);
                    yield hovered ? UiTheme.GOLD : UiTheme.TEXT;
                }
                case PRIMARY -> {
                    UiDraw.box(graphics, rect, hovered ? 0xFFCDA651 : UiTheme.GILT,
                            parchment && !hovered ? UiTheme.GILT_DARK : UiTheme.GOLD, false);
                    yield UiTheme.TEXT_ON_GILT;
                }
                case DANGER -> {
                    UiDraw.box(graphics, rect, hovered ? 0xFF6E2820 : UiTheme.DANGER,
                            hovered ? 0xFFE06A55 : UiTheme.DANGER_EDGE, false);
                    yield UiTheme.DANGER_TEXT;
                }
                case MENU -> {
                    if (hovered) {
                        UiDraw.fill(graphics, rect, UiTheme.WOOD_TAB);
                    }
                    yield hovered ? UiTheme.GOLD : UiTheme.TEXT;
                }
                case CHIP -> {
                    UiDraw.box(graphics, rect, hovered ? UiTheme.WOOD_HOVER : UiTheme.WOOD_RAISED,
                            hovered ? UiTheme.GOLD : UiTheme.WOOD_EDGE, false);
                    yield UiTheme.TEXT;
                }
                case CHIP_SELECTED -> {
                    UiDraw.box(graphics, rect, UiTheme.WOOD_TAB, UiTheme.GOLD, false);
                    yield UiTheme.GOLD;
                }
            };
            if (textColor != 0 && style != Style.PRIMARY) {
                text = textColor;
            }
        }
        if (showsKeyboardFocus()) {
            UiDraw.dashedOutline(graphics, rect.inset(-2), UiTheme.GOLD);
        }
        Component label = getMessage();
        if (isPending()) {
            int frame = (int) (System.currentTimeMillis() / 150L % SPINNER.length);
            label = Component.literal(SPINNER[frame] + " ").append(UiStrings.WAITING);
            text = UiTheme.TEXT_MUTED;
        }
        UiRect inner = rect.inset(3, 0);
        if (style == Style.MENU) {
            UiText.drawLeft(graphics, font, label, rect.inset(5, 0), text);
        } else {
            UiText.drawCentered(graphics, font, label, inner, text);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
