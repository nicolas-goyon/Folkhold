package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A toast: a small parchment note at the top right of the screen for some seconds, in place of a chat message for
 * an important event (a warning of a guard, the result of a quest).
 *
 * <p>A colored square at the left gives the tone. The title has at most 2 lines, the second text at most 2 lines.
 */
public class UiToast implements Toast {
    private static final int WIDTH = 190;
    private static final int TEXT_LEFT = 16;
    private static final long DEFAULT_MILLIS = 5000L;

    private final Component title;
    @Nullable
    private final Component details;
    private final UiTone tone;
    private final long millis;

    /**
     * Makes a toast.
     *
     * @param title   the main text
     * @param details the second text, or null
     * @param tone    the tone of the square mark
     * @param millis  the display time, in milliseconds
     */
    public UiToast(Component title, @Nullable Component details, UiTone tone, long millis) {
        this.title = title;
        this.details = details;
        this.tone = tone;
        this.millis = millis;
    }

    /** Shows a toast for 5 seconds. */
    public static void show(Component title, @Nullable Component details, UiTone tone) {
        Minecraft.getInstance().getToasts().addToast(new UiToast(title, details, tone, DEFAULT_MILLIS));
    }

    private List<FormattedCharSequence> titleLines(Font font) {
        return UiText.wrap(font, title, WIDTH - TEXT_LEFT - 6, 2);
    }

    private List<FormattedCharSequence> detailLines(Font font) {
        return details == null ? List.of() : UiText.wrap(font, details, WIDTH - TEXT_LEFT - 6, 2);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        Font font = Minecraft.getInstance().font;
        return 10 + (titleLines(font).size() + detailLines(font).size()) * UiTheme.LINE;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        Font font = toastComponent.getMinecraft().font;
        UiRect rect = new UiRect(0, 0, width(), height());
        UiDraw.box(graphics, rect, UiTheme.PARCHMENT, UiTheme.GILT, false);
        UiDraw.mark(graphics, 5, 6, 7, tone.mark());
        int y = 5;
        for (FormattedCharSequence line : titleLines(font)) {
            graphics.drawString(font, line, TEXT_LEFT, y, UiTheme.INK, false);
            y += UiTheme.LINE;
        }
        for (FormattedCharSequence line : detailLines(font)) {
            graphics.drawString(font, line, TEXT_LEFT, y, UiTheme.INK_MUTED, false);
            y += UiTheme.LINE;
        }
        return timeSinceLastVisible >= millis * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
