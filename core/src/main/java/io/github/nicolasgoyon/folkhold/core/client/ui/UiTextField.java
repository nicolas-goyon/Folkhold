package io.github.nicolasgoyon.folkhold.core.client.ui;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field of the Folkhold screens: a dark box with a wood border, and a gold border when it has the focus.
 *
 * <p>The vanilla text field draws the text at the top of its box when it has no border. So this field keeps the
 * box of the frame, and places the vanilla field at the vertical center of it. A click on the frame also selects
 * the field.
 */
public class UiTextField extends EditBox {
    private final UiRect frame;

    /**
     * Makes a text field.
     *
     * @param rect  the frame of the field
     * @param label the label for the narration
     * @param hint  the grey text in the empty field, or null
     */
    public UiTextField(UiRect rect, Component label, @Nullable Component hint) {
        super(Minecraft.getInstance().font, rect.x() + 4, rect.y() + (rect.height() - 8) / 2, rect.width() - 8, 9,
                label);
        this.frame = rect;
        setBordered(false);
        setTextColor(UiTheme.TEXT);
        setTextColorUneditable(UiTheme.TEXT_FAINT);
        if (hint != null) {
            setHint(hint);
        }
    }

    /** The frame of the field. */
    public UiRect frame() {
        return frame;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return active && visible && frame.contains(mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && frame.contains(mouseX, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        UiDraw.box(graphics, frame, UiTheme.WOOD_INPUT, isFocused() ? UiTheme.GOLD : UiTheme.WOOD_EDGE, false);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
