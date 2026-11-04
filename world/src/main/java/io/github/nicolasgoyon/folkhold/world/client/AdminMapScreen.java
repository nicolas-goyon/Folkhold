package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.cartofold.api.client.MapFrame;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiOverlay;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.admin.AdminMap;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Kind;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The map of the admin screen: a map frame of Cartofold with the layer {@link AdminMap#LAYER}. The frame shows the
 * chunks that all the players explored ({@code allChunks}). The button "Map" of the admin screen opens it.
 *
 * <ul>
 *   <li>Left: the map. Right: the kind of the selected marker. The map shows the name of the selected marker, and the
 *       tooltip of a marker gives its kind and its position.</li>
 *   <li>"Teleport" teleports the operator to the selected marker: the teleport of the admin screen for a settlement,
 *       the surface at the position of a caravan or a traveling merchant.</li>
 *   <li>"Details" opens the selected settlement in the admin screen. The header has the buttons "All", "Me" (the map
 *       follows the player) and "Refresh".</li>
 *   <li>The frame stays across the rebuilds of the screen, and keeps its view between two openings. The arrow keys
 *       move the focused frame, and the keys + and - zoom.</li>
 *   <li>When the screen closes, the client asks the server to remove the layer.</li>
 * </ul>
 */
public class AdminMapScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.admin.map.";
    private static final int SIDE = 150;
    private static final int HEADER_BUTTON = 14;
    /** The background of the frame where no player explored the chunks. */
    private static final int BACKGROUND = 0xFFFFFFFF;

    /** The identifier of the selected marker, or null. */
    @Nullable
    private static String lastSelected;
    /** The view of the frame, or null before the first opening. */
    @Nullable
    private static MapFrame.View lastView;

    private AdminMapPayload map;
    @Nullable
    private MapFrame frame;
    private UiRect side = new UiRect(0, 0, 0, 0);

    public AdminMapScreen(AdminMapPayload map) {
        super(Component.translatable(KEY + "title"));
        this.map = map;
    }

    /** Opens the screen, or updates the open screen. Called by the network handler. */
    public static void open(AdminMapPayload payload) {
        if (UiOverlay.rootScreen() instanceof AdminMapScreen screen) {
            screen.map = payload;
            return;
        }
        AdminMapScreen screen = new AdminMapScreen(payload);
        if (UiOverlay.rootScreen() instanceof UiScreen current) {
            screen.setPrevious(current);
        }
        Minecraft.getInstance().setScreen(screen);
    }

    /** Forgets the selection and the view, for example when the player leaves the server. */
    public static void clear() {
        lastSelected = null;
        lastView = null;
    }

    private static void send(String action, String id) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, id, 0));
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
        UiRect area = inner.beforeRight(sideWidth, UiTheme.PADDING);
        if (area.width() > 2 && area.height() > 2) {
            if (frame == null) {
                frame = new MapFrame(area.x(), area.y(), area.width(), area.height()).dimension(Level.OVERWORLD)
                        .allChunks(true).backgroundColor(BACKGROUND).layers(AdminMap.LAYER::equals)
                        .showWaypoints(false).onMarkerClick(this::select).onViewChange(view -> lastView = view);
                if (lastSelected != null) {
                    frame.select(AdminMap.LAYER, lastSelected);
                }
                if (lastView != null) {
                    frame.view(lastView);
                } else {
                    showAll();
                }
            }
            // The frame draws its border around its area.
            frame.setRectangle(area.width() - 2, area.height() - 2, area.x() + 1, area.y() + 1);
            addRenderableWidget(frame);
        }
        Optional<Kind> kind = selectedKind();
        if (kind.isEmpty()) {
            return;
        }
        String marker = lastSelected;
        String id = Kind.id(marker);
        UiRect buttons = side.bottomPart(UiTheme.CONTROL * 2 + UiTheme.GAP);
        addRenderableWidget(UiButton.primary(buttons.top(UiTheme.CONTROL), Component.translatable(KEY + "teleport"),
                button -> send(kind.get().settlement() ? "teleport" : "teleport_group", id)));
        if (kind.get().settlement()) {
            addRenderableWidget(UiButton.normal(buttons.bottomPart(UiTheme.CONTROL), Component.translatable(KEY
                    + "details"), button -> {
                        AdminScreen.select(id);
                        send("refresh", "");
                    }));
        }
    }

    private Optional<Kind> selectedKind() {
        return lastSelected == null ? Optional.empty() : Kind.of(lastSelected);
    }

    /** A click on a marker of the admin layer selects it. */
    private void select(ResourceLocation layer, String marker) {
        if (layer.equals(AdminMap.LAYER) && Kind.of(marker).isPresent() && !marker.equals(lastSelected)) {
            lastSelected = marker;
            if (frame != null) {
                frame.select(AdminMap.LAYER, marker);
            }
            rebuild();
        }
    }

    /** Builds the widgets again, with the same frame and the same focus. */
    private void rebuild() {
        boolean focused = frame != null && getFocused() == frame;
        rebuildWidgets();
        if (focused && children().contains(frame)) {
            setFocused(frame);
        }
    }

    /** Shows all the settlements. With no settlement, the frame follows the player. */
    private void showAll() {
        if (frame == null) {
            return;
        }
        if (!map.empty()) {
            frame.showBox(map.minX(), map.minZ(), map.maxX() + 1.0, map.maxZ() + 1.0);
        }
        frame.followPlayer(map.empty());
        lastView = frame.view();
    }

    private void initHeaderButtons() {
        UiRect header = panel().top(HEADER);
        int y = header.y() + (header.height() - HEADER_BUTTON) / 2;
        int x = header.right() - UiTheme.PADDING - 14 - UiTheme.GAP;
        x -= headerButton(x, y, Component.translatable(KEY + "refresh"), button -> send("map", "")).getWidth()
                + UiTheme.GAP;
        x -= headerButton(x, y, Component.translatable(KEY + "me"), button -> {
            if (frame != null) {
                frame.followPlayer(true);
            }
        }).getWidth() + UiTheme.GAP;
        headerButton(x, y, Component.translatable(KEY + "all"), button -> showAll());
    }

    private UiButton headerButton(int right, int y, Component label, Consumer<UiButton> action) {
        int w = font.width(label) + 10;
        return addRenderableWidget(UiButton.normal(new UiRect(right - w, y, w, HEADER_BUTTON), label, action));
    }

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        UiRect text = side.top(Math.max(0, side.height() - UiTheme.CONTROL * 2 - UiTheme.GAP * 2));
        Optional<Kind> kind = selectedKind();
        Component line = kind.isEmpty() ? Component.translatable(KEY + "select")
                : Component.translatable(KEY + "kind." + kind.get().key());
        UiText.drawWrapped(graphics, font, line, text, kind.isEmpty() ? UiTheme.INK_MUTED : UiTheme.INK);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The frames of Cartofold have no keys: the screen moves and zooms the focused frame.
        if (frame != null && getFocused() == frame) {
            Optional<MapFrame.View> view = JournalMapViews.onKey(frame.view(), keyCode);
            if (view.isPresent()) {
                frame.view(view.get());
                lastView = view.get();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        // The server removes the layer of the admin map. When the player leaves the server, the server removes it.
        if (Minecraft.getInstance().getConnection() != null) {
            send("map_close", "");
        }
    }
}
