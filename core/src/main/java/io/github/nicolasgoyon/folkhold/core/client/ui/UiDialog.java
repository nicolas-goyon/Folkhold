package io.github.nicolasgoyon.folkhold.core.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A confirmation dialog for an action that the player cannot undo: pillage, abandon a quest, ruin a settlement.
 *
 * <ul>
 *   <li>The cancel button has the focus when the dialog opens. Escape cancels.</li>
 *   <li>The dialog shows what the action does: a message, a "before → after" comparison, a list of consequences.</li>
 *   <li>Three ways to confirm: a click, a hold of the button ({@link Builder#hold}), or the name of the target in a
 *       text field ({@link Builder#typeToConfirm}).</li>
 *   <li>Two looks: wood (default) or parchment ({@link Builder#parchment}). A dangerous dialog has a red frame.</li>
 * </ul>
 *
 * <p>Use {@link #builder(Screen, Component)}, then {@link Builder#open()}.
 */
public final class UiDialog extends UiOverlay {
    private static final int MAX_WIDTH = 248;
    private static final int PAD = 8;
    private static final int BAND = 22;
    private static final int COMPARISON = 32;
    private static final int BUTTON = 18;

    /**
     * One side of a "before → after" comparison.
     *
     * @param caption    the small text on top, for example "Before"
     * @param value      the value, for example "✓ Friend 420"
     * @param background the background color
     * @param text       the text color
     */
    public record Chip(Component caption, Component value, int background, int text) {
    }

    private final Builder spec;
    private UiRect box = new UiRect(0, 0, 0, 0);
    @Nullable
    private UiTextField nameField;
    @Nullable
    private UiButton confirmButton;

    private UiDialog(Builder spec) {
        super(spec.title, spec.parent, 0xA0000000);
        this.spec = spec;
    }

    /**
     * Starts a dialog.
     *
     * @param parent the screen behind the dialog, and the screen after the dialog
     * @param title  the question, for example "Abandon the quest?"
     * @return the builder
     */
    public static Builder builder(@Nullable Screen parent, Component title) {
        return new Builder(parent, title);
    }

    private int innerWidth() {
        return box.width() - 2 * PAD;
    }

    private List<FormattedCharSequence> messageLines(int width) {
        return spec.message == null ? List.of() : UiText.wrap(font, spec.message, width, 6);
    }

    @Override
    protected void initOverlay() {
        int w = Math.min(MAX_WIDTH, width - 2 * UiTheme.SCREEN_MARGIN);
        int inner = w - 2 * PAD;
        int h = PAD + (spec.parchment && spec.danger ? BAND + PAD : UiTheme.LINE + 6);
        if (spec.message != null) {
            h += messageLines(inner).size() * UiTheme.LINE + 6;
        }
        if (spec.before != null && spec.after != null) {
            h += COMPARISON + 6;
        }
        h += spec.bullets.size() * UiTheme.LINE + (spec.bullets.isEmpty() ? 0 : 6);
        if (spec.expectedName != null) {
            h += UiTheme.LINE + 2 + UiTheme.CONTROL + 6;
        }
        h += BUTTON + PAD;
        if (spec.note != null) {
            h += UiTheme.LINE + 2;
        }
        box = new UiRect(0, 0, width, height).centered(w, Math.min(h, height - 2 * UiTheme.SCREEN_MARGIN));

        int buttonsTop = box.bottom() - PAD - BUTTON - (spec.note != null ? UiTheme.LINE + 2 : 0);
        UiRect row = new UiRect(box.x() + PAD, buttonsTop, inner, BUTTON);
        UiRect cancelRect = row.column(0, 2, UiTheme.GAP);
        UiRect confirmRect = row.column(1, 2, UiTheme.GAP);
        UiButton cancel = UiButton.normal(cancelRect, spec.cancelLabel, b -> onClose());
        UiButton confirm;
        if (spec.holdMillis > 0) {
            confirm = new UiHoldButton(confirmRect, spec.confirmLabel, spec.holdMillis, b -> confirm());
        } else {
            confirm = new UiButton(confirmRect, spec.confirmLabel,
                    spec.danger ? UiButton.Style.DANGER : UiButton.Style.PRIMARY, b -> confirm());
        }
        if (spec.expectedName != null) {
            UiRect field = new UiRect(box.x() + PAD, buttonsTop - 6 - UiTheme.CONTROL, inner, UiTheme.CONTROL);
            nameField = new UiTextField(field, UiStrings.TYPE_NAME, null);
            nameField.setMaxLength(64);
            nameField.setResponder(text -> updateConfirm());
            addRenderableWidget(nameField);
        }
        addRenderableWidget(cancel);
        confirmButton = addRenderableWidget(confirm);
        updateConfirm();
        setInitialFocus(cancel);
    }

    private boolean nameMatches() {
        return spec.expectedName == null || (nameField != null && nameField.getValue().trim()
                .toLowerCase(Locale.ROOT).equals(spec.expectedName.trim().toLowerCase(Locale.ROOT)));
    }

    private void updateConfirm() {
        if (confirmButton == null) {
            return;
        }
        if (nameMatches()) {
            confirmButton.setDisabled(null);
        } else {
            confirmButton.disable();
        }
    }

    private void confirm() {
        if (!nameMatches()) {
            return;
        }
        onClose();
        spec.onConfirm.run();
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean parchment = spec.parchment;
        int border = spec.danger ? (parchment ? UiTheme.WAX : UiTheme.DANGER_EDGE) : UiTheme.GILT;
        UiDraw.fill(graphics, box.inset(-1), 0xFF0E0A06);
        UiDraw.box(graphics, box, parchment ? UiTheme.PARCHMENT : UiTheme.WOOD, border, false);
        int text = parchment ? UiTheme.INK : UiTheme.TEXT;
        int soft = parchment ? UiTheme.INK : 0xFFD8CFBD;
        int x = box.x() + PAD;
        int y = box.y() + PAD;
        if (parchment && spec.danger) {
            UiRect band = new UiRect(box.x() + 1, box.y() + 1, box.width() - 2, BAND);
            UiDraw.fill(graphics, band, UiTheme.DANGER_STRONG);
            UiRect mark = new UiRect(band.x() + 5, band.y() + 3, 16, 16);
            UiDraw.box(graphics, mark, 0xFFB8413A, UiTheme.DANGER, false);
            UiText.drawCentered(graphics, font, Component.literal("!"), mark, UiTheme.DANGER_TEXT);
            UiText.draw(graphics, font, spec.title, mark.right() + 6, UiText.textY(band),
                    band.right() - mark.right() - 12, UiTheme.DANGER_TEXT);
            y = band.bottom() + PAD;
        } else {
            int titleWidth = innerWidth();
            if (spec.tag != null) {
                UiRect tag = new UiRect(box.right() - PAD - UiDraw.badgeWidth(font, spec.tag), y - 2,
                        UiDraw.badgeWidth(font, spec.tag), UiTheme.BADGE);
                UiDraw.box(graphics, tag, 0, UiTheme.WOOD_EDGE, false);
                UiText.drawCentered(graphics, font, spec.tag, tag, UiTheme.TEXT_MUTED);
                titleWidth -= tag.width() + 4;
            }
            UiText.draw(graphics, font, spec.title, x, y, titleWidth, text);
            y += UiTheme.LINE + 6;
        }
        if (spec.message != null) {
            for (FormattedCharSequence line : messageLines(innerWidth())) {
                graphics.drawString(font, line, x, y, soft, false);
                y += UiTheme.LINE;
            }
            y += 6;
        }
        if (spec.before != null && spec.after != null) {
            UiRect row = new UiRect(x, y, innerWidth(), COMPARISON);
            int side = (row.width() - 20) / 2;
            renderChip(graphics, spec.before, row.left(side));
            UiText.drawCentered(graphics, font, Component.literal("→"), new UiRect(row.x() + side, row.y(), 20,
                    COMPARISON), UiTheme.WAX);
            renderChip(graphics, spec.after, row.rightPart(side));
            y += COMPARISON + 6;
        }
        for (Component bullet : spec.bullets) {
            UiText.draw(graphics, font, Component.literal("• ").append(bullet), x, y, innerWidth(), soft);
            y += UiTheme.LINE;
        }
        if (spec.expectedName != null && nameField != null) {
            graphics.drawString(font, UiStrings.TYPE_NAME, x, nameField.frame().y() - UiTheme.LINE - 2,
                    parchment ? UiTheme.INK_MUTED : UiTheme.TEXT_MUTED, false);
        }
        if (spec.note != null) {
            UiText.draw(graphics, font, spec.note, x, box.bottom() - PAD - UiTheme.LINE + 1, innerWidth(),
                    parchment ? UiTheme.INK_MUTED : UiTheme.TEXT_MUTED);
        }
    }

    private void renderChip(GuiGraphics graphics, Chip chip, UiRect rect) {
        UiDraw.fill(graphics, rect, chip.background());
        UiText.drawCentered(graphics, font, chip.caption(), new UiRect(rect.x(), rect.y() + 3, rect.width(),
                UiTheme.LINE), chip.text());
        UiText.drawCentered(graphics, font, chip.value(), new UiRect(rect.x(), rect.y() + 16, rect.width(),
                UiTheme.LINE), chip.text());
    }

    /** The settings of a dialog. */
    public static final class Builder {
        @Nullable
        private final Screen parent;
        private final Component title;
        @Nullable
        private Component message;
        @Nullable
        private Component tag;
        @Nullable
        private Component note;
        @Nullable
        private Chip before;
        @Nullable
        private Chip after;
        private final List<Component> bullets = new ArrayList<>();
        private boolean danger;
        private boolean parchment;
        private long holdMillis;
        @Nullable
        private String expectedName;
        private Component cancelLabel = UiStrings.CANCEL_KEY;
        private Component confirmLabel = UiStrings.OK;
        private Runnable onConfirm = () -> {
        };

        private Builder(@Nullable Screen parent, Component title) {
            this.parent = parent;
            this.title = title;
        }

        /** The text below the title. */
        public Builder message(Component text) {
            this.message = text;
            return this;
        }

        /** A small tag at the right of the title, for example "ADMIN". */
        public Builder tag(Component text) {
            this.tag = text;
            return this;
        }

        /** A small text below the buttons. */
        public Builder note(Component text) {
            this.note = text;
            return this;
        }

        /** A "before → after" comparison. */
        public Builder comparison(Chip beforeChip, Chip afterChip) {
            this.before = beforeChip;
            this.after = afterChip;
            return this;
        }

        /** One consequence of the action. */
        public Builder bullet(Component text) {
            this.bullets.add(text);
            return this;
        }

        /** A red frame and a dangerous confirm button. */
        public Builder danger() {
            this.danger = true;
            return this;
        }

        /** The parchment look. */
        public Builder parchment() {
            this.parchment = true;
            return this;
        }

        /** The player must hold the confirm button for a time. */
        public Builder hold(long millis) {
            this.holdMillis = millis;
            return this;
        }

        /** The player must type a name (case is not important) before the confirm button is available. */
        public Builder typeToConfirm(String name) {
            this.expectedName = name;
            return this;
        }

        /** The label of the cancel button. */
        public Builder cancel(Component label) {
            this.cancelLabel = label;
            return this;
        }

        /** The label and the action of the confirm button. */
        public Builder confirm(Component label, Runnable action) {
            this.confirmLabel = label;
            this.onConfirm = action;
            return this;
        }

        /** Makes the dialog. */
        public UiDialog build() {
            return new UiDialog(this);
        }

        /** Opens the dialog. */
        public void open() {
            Minecraft.getInstance().setScreen(build());
        }
    }
}
