package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A dangerous button that the player must hold: the action starts only after {@link #holdMillis()}. A bar at the
 * bottom of the button shows the progress. To release the mouse button, to move the mouse away or to release the key
 * stops the hold.
 *
 * <p>The mouse and the keys Enter and Space can hold the button.
 */
public class UiHoldButton extends UiButton {
    /** The default hold time, in milliseconds. */
    public static final long DEFAULT_HOLD_MILLIS = 1000L;

    private final long holdMillis;
    private long holdStart = -1L;
    private boolean byMouse;

    /**
     * Makes a hold button.
     *
     * @param rect       the position and the size
     * @param label      the label
     * @param holdMillis the time to hold, in milliseconds
     * @param action     the action at the end of the hold
     */
    public UiHoldButton(UiRect rect, Component label, long holdMillis, Consumer<UiButton> action) {
        super(rect, label, Style.DANGER, action);
        this.holdMillis = holdMillis;
    }

    /** The time to hold, in milliseconds. */
    public long holdMillis() {
        return holdMillis;
    }

    /** True while the player holds the button. */
    public boolean isHolding() {
        return holdStart >= 0L;
    }

    /** The progress of the hold, 0 to 1. */
    public double progress() {
        return isHolding() ? Math.min(1.0, (System.currentTimeMillis() - holdStart) / (double) holdMillis) : 0.0;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        start(true);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        holdStart = -1L;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active && visible && isActivationKey(keyCode)) {
            if (!isHolding()) {
                start(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isActivationKey(keyCode) && !byMouse) {
            holdStart = -1L;
            return true;
        }
        return false;
    }

    private static boolean isActivationKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }

    private void start(boolean mouse) {
        if (!isPending()) {
            holdStart = System.currentTimeMillis();
            byMouse = mouse;
        }
    }

    /**
     * Advances the hold. The screen calls this method on each game tick (see {@link UiScreen#tick()} and
     * {@link UiOverlay#tick()}). The hold stops when the button is not active or the mouse left the button. The
     * action runs when the hold is complete.
     */
    public void tick() {
        if (!isHolding()) {
            return;
        }
        if (!active || (byMouse && !isHovered())) {
            holdStart = -1L;
            return;
        }
        if (progress() >= 1.0) {
            holdStart = -1L;
            onPress();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (isHolding()) {
            UiDraw.fill(graphics, new UiRect(getX() + 1, getY() + height - 2,
                    (int) Math.round((width - 2) * progress()), 1), UiTheme.GOLD);
        }
    }

    /** Advances the hold of the hold buttons among the children of a screen. */
    static void tickAll(Iterable<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof UiHoldButton hold) {
                hold.tick();
            }
        }
    }
}
