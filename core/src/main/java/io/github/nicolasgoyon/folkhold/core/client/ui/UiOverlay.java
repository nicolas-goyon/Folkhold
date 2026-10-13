package io.github.nicolasgoyon.folkhold.core.client.ui;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen above another screen: a drop-down menu or a dialog. The parent screen stays visible behind the overlay.
 * Escape, and the end of the overlay, go back to the parent screen.
 *
 * <p>The game calls {@code init} of the parent again when the overlay closes, so the parent makes its widgets again
 * from its state.
 */
public abstract class UiOverlay extends Screen {
    @Nullable
    private final Screen parent;
    private final int dimColor;

    /**
     * Makes an overlay.
     *
     * @param title    the title for the narration
     * @param parent   the screen behind the overlay, or null
     * @param dimColor the color over the parent screen
     */
    protected UiOverlay(Component title, @Nullable Screen parent, int dimColor) {
        super(title);
        this.parent = parent;
        this.dimColor = dimColor;
    }

    /** The screen behind the overlay, or null. */
    @Nullable
    public Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        if (parent != null && (parent.width != width || parent.height != height)) {
            parent.init(minecraft, width, height);
        }
        initOverlay();
    }

    /** Makes the widgets of the overlay. */
    protected abstract void initOverlay();

    /** Goes back to the parent screen. */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            UiScreen.renderBehind(parent, graphics, partialTick);
        }
        graphics.pose().pushPose();
        // Above the items of the parent screen.
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(0, 0, width, height, dimColor);
        renderPanel(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        for (GuiEventListener child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        renderForeground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    /** Draws the panel of the overlay, behind its widgets. */
    protected abstract void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Draws above the widgets of the overlay. */
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
