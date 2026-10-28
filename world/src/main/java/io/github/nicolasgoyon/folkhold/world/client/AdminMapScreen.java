package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiOverlay;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Mark;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The map of the admin screen: all the settlements with their territories, the caravans and the traveling merchants
 * (see {@link AdminMapWidget}). The button "Map" of the admin screen opens it.
 *
 * <ul>
 *   <li>Left: the map. Right: the selected mark, with its name, its kind and its position.</li>
 *   <li>"Teleport" teleports the operator to the selected mark: the teleport of the admin screen for a settlement,
 *       the surface at the position for a caravan or a traveling merchant.</li>
 *   <li>"Details" opens the selected settlement in the admin screen. The header has the buttons "All", "Me" (the map
 *       at the player) and "Refresh".</li>
 * </ul>
 */
public class AdminMapScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.admin.map.";
    private static final int SIDE = 150;
    private static final int HEADER_BUTTON = 14;

    @Nullable
    private static String lastSelected;

    private AdminMapPayload map;
    @Nullable
    private AdminMapWidget widget;
    private UiRect side = new UiRect(0, 0, 0, 0);

    public AdminMapScreen(AdminMapPayload map) {
        super(Component.translatable(KEY + "title"));
        this.map = map;
    }

    /** Opens the screen, or updates the open screen. Called by the network handler. */
    public static void open(AdminMapPayload payload) {
        if (UiOverlay.rootScreen() instanceof AdminMapScreen screen) {
            screen.map = payload;
            screen.rebuildWidgets();
            return;
        }
        AdminMapScreen screen = new AdminMapScreen(payload);
        if (UiOverlay.rootScreen() instanceof UiScreen current) {
            screen.setPrevious(current);
        }
        Minecraft.getInstance().setScreen(screen);
    }

    private static void send(String action, String id) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, id, 0));
    }

    private Optional<Mark> selected() {
        return map.marks().stream().filter(mark -> mark.id().equals(lastSelected)).findFirst();
    }

    @Override
    @Nullable
    protected Component footerHint() {
        return Component.translatable(KEY + "hint");
    }

    @Override
    protected void initBody(UiRect body) {
        initHeaderButtons();
        UiRect inner = body.inset(UiTheme.PADDING);
        int sideWidth = Math.min(SIDE, inner.width() / 3);
        side = inner.rightPart(sideWidth);
        UiRect mapArea = inner.beforeRight(sideWidth, UiTheme.PADDING);
        widget = addRenderableWidget(new AdminMapWidget(mapArea, font, map, lastSelected, mark -> {
            lastSelected = mark.id();
            rebuildWidgets();
        }));
        Optional<Mark> selected = selected();
        if (selected.isPresent()) {
            Mark mark = selected.get();
            UiRect buttons = side.bottomPart(UiTheme.CONTROL * 2 + UiTheme.GAP);
            addRenderableWidget(UiButton.primary(buttons.top(UiTheme.CONTROL), Component.translatable(KEY
                    + "teleport"), button -> {
                        if (mark.settlement()) {
                            send("teleport", mark.id());
                        } else {
                            send("teleport_point", mark.x() + " " + mark.z());
                        }
                    }));
            if (mark.settlement()) {
                addRenderableWidget(UiButton.normal(buttons.bottomPart(UiTheme.CONTROL), Component.translatable(KEY
                        + "details"), button -> {
                            AdminScreen.select(mark.id());
                            send("refresh", "");
                        }));
            }
        }
    }

    private void initHeaderButtons() {
        UiRect header = panel().top(HEADER);
        int y = header.y() + (header.height() - HEADER_BUTTON) / 2;
        int x = header.right() - UiTheme.PADDING - 14 - UiTheme.GAP;
        x -= headerButton(x, y, Component.translatable(KEY + "refresh"), button -> send("map", "")).getWidth()
                + UiTheme.GAP;
        x -= headerButton(x, y, Component.translatable(KEY + "me"), button -> {
            if (widget != null) {
                widget.centerOnPlayer();
            }
        }).getWidth() + UiTheme.GAP;
        headerButton(x, y, Component.translatable(KEY + "all"), button -> {
            if (widget != null) {
                widget.showAll();
            }
        });
    }

    private UiButton headerButton(int right, int y, Component label, java.util.function.Consumer<UiButton> action) {
        int w = font.width(label) + 10;
        return addRenderableWidget(UiButton.normal(new UiRect(right - w, y, w, HEADER_BUTTON), label, action));
    }

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        Optional<Mark> selected = selected();
        UiRect text = side.top(Math.max(0, side.height() - UiTheme.CONTROL * 2 - UiTheme.GAP * 2));
        if (selected.isEmpty()) {
            UiText.drawWrapped(graphics, font, Component.translatable(KEY + "select"), text, UiTheme.INK_MUTED);
            return;
        }
        Mark mark = selected.get();
        int y = text.y();
        UiDraw.mark(graphics, text.x() + 4, y + 4, 7, 0xFF000000 | mark.color());
        graphics.drawString(font, Component.literal(mark.name()), text.x() + 12, y, UiTheme.INK, false);
        y += 12;
        graphics.drawString(font, Component.translatable(KEY + "kind." + mark.kind()), text.x(), y,
                UiTheme.INK_MUTED, false);
        y += 12;
        graphics.drawString(font, Component.translatable(KEY + "position", mark.x(), mark.z()), text.x(), y,
                UiTheme.INK_MUTED, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (widget != null && getFocused() == widget && widget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (widget != null) {
            List<Component> lines = widget.tooltip(mouseX, mouseY);
            if (!lines.isEmpty()) {
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }
}
