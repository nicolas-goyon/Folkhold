package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Mark;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The map of the admin screen (see {@link AdminMapScreen}): all the territories, the settlements, the caravans, the
 * traveling merchants and the player.
 *
 * <ul>
 *   <li>A territory has a light fill and a border in the color of its faction.</li>
 *   <li>A village has a square in the color of its faction. A settlement of the simulation has a ring, and a ruin a
 *       cross. A caravan has a brown diamond, and a traveling merchant a purple diamond.</li>
 *   <li>The names of the settlements show when the scale shows the details, and when they do not cover other
 *       names.</li>
 *   <li>The operator moves the map with the mouse or the arrow keys, and zooms with the wheel or the keys + and -. A
 *       click on a mark selects it.</li>
 * </ul>
 */
final class AdminMapWidget extends AbstractWidget {
    private static final String KEY = "screen.folkhold_world.admin.map.";
    private static final int BACKGROUND = 0xFFFFFFFF;
    private static final int FILL_ALPHA = 0x40;
    private static final int LABEL_BACKGROUND = 0xC8FFFFFF;
    private static final int PLAYER = 0xFF5F95C4;
    private static final int CARAVAN = 0xFF8B5A2B;
    private static final int MERCHANT = 0xFF7B4FA0;
    /** The names of the settlements show from this scale, in pixels for each block. */
    private static final double NAME_SCALE = 0.125;
    private static final int HIT = 6;
    /** The smallest margin around the marks when the map shows all of them, in blocks. */
    private static final double MARGIN = 256.0;
    private static final int CLICK_MOVE = 3;
    private static final int KEY_MOVE = 32;

    /** The smallest scale: the map of the admin screen shows all the world. */
    private static final double MIN_SCALE = 1.0 / 512.0;
    private static final MapViewport VIEWPORT = new MapViewport(MIN_SCALE);
    private static boolean placed;

    private final Font font;
    private final AdminMapPayload map;
    private final TerritoryMapView view;
    private final TerritoryRaster raster;
    @Nullable
    private final String selectedId;
    private final Consumer<Mark> onSelect;
    private double lastX;
    private double lastY;
    private double moved;

    AdminMapWidget(UiRect rect, Font font, AdminMapPayload map, @Nullable String selectedId, Consumer<Mark> onSelect) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), Component.translatable(KEY + "title"));
        this.font = font;
        this.map = map;
        this.view = new TerritoryMapView(map.territories(), 0);
        this.raster = TerritoryRaster.of(view);
        this.selectedId = selectedId;
        this.onSelect = onSelect;
    }

    /** Shows all the marks, with a margin. */
    void showAll() {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Mark mark : map.marks()) {
            minX = Math.min(minX, mark.x());
            minZ = Math.min(minZ, mark.z());
            maxX = Math.max(maxX, mark.x() + 1.0);
            maxZ = Math.max(maxZ, mark.z() + 1.0);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (minX > maxX) {
            if (player != null) {
                VIEWPORT.center(player.getX(), player.getZ());
            }
        } else {
            double margin = Math.max(MARGIN, Math.max(maxX - minX, maxZ - minZ) / 10.0);
            VIEWPORT.fit(minX - margin, minZ - margin, maxX + margin, maxZ + margin, inner());
        }
        placed = true;
    }

    /** Puts the player at the center of the map. */
    void centerOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            VIEWPORT.center(player.getX(), player.getZ());
            placed = true;
        }
    }

    /** Puts a mark at the center of the map. */
    static void focus(Mark mark) {
        VIEWPORT.center(mark.x() + 0.5, mark.z() + 0.5);
        placed = true;
    }

    private UiRect inner() {
        return new UiRect(getX() + 1, getY() + 1, Math.max(0, width - 2), Math.max(0, height - 2));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!placed) {
            showAll();
        }
        UiDraw.box(graphics, new UiRect(getX(), getY(), width, height), BACKGROUND, UiTheme.PARCHMENT_EDGE, false);
        UiRect inner = inner();
        MapProjection projection = VIEWPORT.projection(inner);
        graphics.enableScissor(inner.x(), inner.y(), inner.right(), inner.bottom());
        renderTerritories(graphics, projection);
        renderMarks(graphics, projection, mouseX, mouseY);
        renderPlayer(graphics, projection);
        graphics.disableScissor();
        if (isFocused()) {
            UiDraw.outline(graphics, new UiRect(getX(), getY(), width, height), UiTheme.GILT);
        }
    }

    private void renderTerritories(GuiGraphics graphics, MapProjection projection) {
        List<TerritoryMapPayload.Village> owners = view.villages();
        UiRect area = projection.area();
        for (TerritoryRaster.Cell cell : raster.cells()) {
            if (cell.village() >= owners.size()) {
                continue;
            }
            int x0 = projection.x(cell.chunkX() * 16);
            int y0 = projection.y(cell.chunkZ() * 16);
            int x1 = Math.max(x0 + 1, projection.x(cell.chunkX() * 16 + 16));
            int y1 = Math.max(y0 + 1, projection.y(cell.chunkZ() * 16 + 16));
            if (x1 < area.x() || x0 > area.right() || y1 < area.y() || y0 > area.bottom()) {
                continue;
            }
            int rgb = owners.get(cell.village()).color() & 0xFFFFFF;
            graphics.fill(x0, y0, x1, y1, (FILL_ALPHA << 24) | rgb);
            int border = 0xFF000000 | rgb;
            if ((cell.borders() & TerritoryRaster.NORTH) != 0) {
                graphics.fill(x0, y0, x1, y0 + 1, border);
            }
            if ((cell.borders() & TerritoryRaster.SOUTH) != 0) {
                graphics.fill(x0, y1 - 1, x1, y1, border);
            }
            if ((cell.borders() & TerritoryRaster.WEST) != 0) {
                graphics.fill(x0, y0, x0 + 1, y1, border);
            }
            if ((cell.borders() & TerritoryRaster.EAST) != 0) {
                graphics.fill(x1 - 1, y0, x1, y1, border);
            }
        }
    }

    private void renderMarks(GuiGraphics graphics, MapProjection projection, int mouseX, int mouseY) {
        UiRect area = projection.area();
        Mark hovered = isMouseOver(mouseX, mouseY) ? markAt(projection, mouseX, mouseY) : null;
        for (Mark mark : map.marks()) {
            int x = projection.x(mark.x() + 0.5);
            int y = projection.y(mark.z() + 0.5);
            if (x >= area.x() - HIT && x <= area.right() + HIT && y >= area.y() - HIT && y <= area.bottom() + HIT) {
                mark(graphics, mark, x, y);
            }
        }
        boolean names = projection.scale() >= NAME_SCALE;
        List<UiRect> labels = new ArrayList<>();
        for (Mark mark : map.marks()) {
            boolean important = mark.id().equals(selectedId) || mark == hovered;
            if (!important && (!names || !mark.settlement())) {
                continue;
            }
            int x = projection.x(mark.x() + 0.5);
            int y = projection.y(mark.z() + 0.5);
            Component name = Component.literal(mark.name());
            UiRect label = new UiRect(x + 5, y - 5, font.width(name) + 3, 10);
            if (!important && labels.stream().anyMatch(other -> other.x() < label.right()
                    && label.x() < other.right() && other.y() < label.bottom() && label.y() < other.bottom())) {
                continue;
            }
            labels.add(label);
            UiDraw.fill(graphics, label, LABEL_BACKGROUND);
            graphics.drawString(font, name, label.x() + 2, label.y() + 1,
                    mark.kind() == AdminMapPayload.RUIN ? UiTheme.INK_MUTED : UiTheme.INK, false);
        }
    }

    private void mark(GuiGraphics graphics, Mark mark, int x, int y) {
        if (mark.id().equals(selectedId)) {
            UiDraw.outline(graphics, new UiRect(x - 6, y - 6, 13, 13), UiTheme.GILT);
            UiDraw.outline(graphics, new UiRect(x - 5, y - 5, 11, 11), UiTheme.GILT);
        }
        switch (mark.kind()) {
            case AdminMapPayload.RUIN -> {
                for (int d = -3; d <= 3; d++) {
                    graphics.fill(x + d, y + d, x + d + 1, y + d + 1, UiTheme.INK_MUTED);
                    graphics.fill(x + d, y - d, x + d + 1, y - d + 1, UiTheme.INK_MUTED);
                }
            }
            case AdminMapPayload.CARAVAN, AdminMapPayload.MERCHANT -> {
                int color = mark.kind() == AdminMapPayload.CARAVAN ? CARAVAN : MERCHANT;
                for (int d = 0; d <= 3; d++) {
                    graphics.fill(x - 3 + d, y - d, x + 4 - d, y + d + 1, color);
                }
            }
            default -> {
                graphics.fill(x - 3, y - 3, x + 4, y + 4, UiTheme.INK);
                graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000 | mark.color());
                if (mark.kind() == AdminMapPayload.SIMULATED) {
                    graphics.fill(x - 1, y - 1, x + 2, y + 2, UiTheme.PARCHMENT_LIGHT);
                }
            }
        }
    }

    private static void renderPlayer(GuiGraphics graphics, MapProjection projection) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int x = projection.x(player.getX());
        int y = projection.y(player.getZ());
        graphics.fill(x - 3, y - 3, x + 4, y + 4, UiTheme.INK);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, PLAYER);
    }

    @Nullable
    private Mark markAt(MapProjection projection, double mouseX, double mouseY) {
        Mark nearest = null;
        double best = HIT * HIT;
        for (Mark mark : map.marks()) {
            double dx = projection.x(mark.x() + 0.5) - mouseX;
            double dy = projection.y(mark.z() + 0.5) - mouseY;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                nearest = mark;
            }
        }
        return nearest;
    }

    /** The tooltip of the mark under the mouse: its name, its kind and its position. */
    List<Component> tooltip(double mouseX, double mouseY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        Mark mark = markAt(VIEWPORT.projection(inner()), mouseX, mouseY);
        if (mark == null) {
            return List.of();
        }
        return List.of(Component.literal(mark.name()), Component.translatable(KEY + "kind." + mark.kind()),
                Component.translatable(KEY + "position", mark.x(), mark.z()));
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        lastX = mouseX;
        lastY = mouseY;
        moved = 0;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        moved += Math.abs(mouseX - lastX) + Math.abs(mouseY - lastY);
        VIEWPORT.drag(mouseX - lastX, mouseY - lastY);
        lastX = mouseX;
        lastY = mouseY;
        placed = true;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (moved < CLICK_MOVE) {
            Mark mark = markAt(VIEWPORT.projection(inner()), mouseX, mouseY);
            if (mark != null && !Objects.equals(mark.id(), selectedId)) {
                onSelect.accept(mark);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        VIEWPORT.zoom(Math.pow(MapViewport.ZOOM_STEP, Math.signum(scrollY)), inner(), mouseX, mouseY);
        placed = true;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        UiRect inner = inner();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> VIEWPORT.drag(KEY_MOVE, 0);
            case GLFW.GLFW_KEY_RIGHT -> VIEWPORT.drag(-KEY_MOVE, 0);
            case GLFW.GLFW_KEY_UP -> VIEWPORT.drag(0, KEY_MOVE);
            case GLFW.GLFW_KEY_DOWN -> VIEWPORT.drag(0, -KEY_MOVE);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> VIEWPORT.zoom(MapViewport.ZOOM_STEP, inner,
                    inner.centerX(), inner.centerY());
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> VIEWPORT.zoom(1.0 / MapViewport.ZOOM_STEP, inner,
                    inner.centerX(), inner.centerY());
            default -> {
                return false;
            }
        }
        placed = true;
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // A click on the map makes no sound: the operator drags the map often.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
