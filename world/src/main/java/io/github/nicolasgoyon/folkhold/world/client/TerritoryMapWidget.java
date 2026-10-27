package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload.Village;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;

/**
 * The map of the journal: the relief, the discovered territories, the discovered villages and the player.
 *
 * <ul>
 *   <li>The relief comes from the server ({@link ClientJournalMap}). A territory has a light fill and a strong border
 *       in the color of its faction ({@link TerritoryRaster}).</li>
 *   <li>A village has a mark in the color of its faction. The mark is a full square when the player visited the
 *       village, and a ring when the player only discovered it. Ruins have a cross.</li>
 *   <li>A name shows when it does not cover a name that shows before it. The name of the selected village always
 *       shows.</li>
 *   <li>The player moves the map with the mouse or the arrow keys, and zooms with the wheel or the keys + and -
 *       ({@link MapViewport}). A click on a village selects it.</li>
 *   <li>The map keeps its position and its scale between two openings of the journal.</li>
 * </ul>
 */
final class TerritoryMapWidget extends AbstractWidget {
    private static final String KEY = "screen.folkhold_world.journal.";
    /** The background of the map. */
    private static final int BACKGROUND = 0xFFFFFFFF;
    /** The opacity of the fill of a territory, from 0 to 255. */
    private static final int FILL_ALPHA = 0x50;
    /** The background of a name of a village. */
    private static final int LABEL_BACKGROUND = 0xC8FFFFFF;
    /** The color of the mark of the player. */
    private static final int PLAYER = 0xFF5F95C4;
    /** The distance in pixels from a mark to the mouse, for a click or a tooltip. */
    private static final int HIT = 6;
    /** A movement of the mouse of this number of pixels or more is a drag, not a click. */
    private static final int CLICK_MOVE = 3;
    /** The movement of the map for a key, in pixels. */
    private static final int KEY_MOVE = 32;
    /** The scale after the journal shows a village on the map, if the scale was smaller. */
    private static final double FOCUS_SCALE = 0.5;

    private static final MapViewport VIEWPORT = new MapViewport();
    /** False until the map has a position: then the map shows all the discovered territories. */
    private static boolean placed;

    private final Font font;
    private final List<Village> villages;
    @Nullable
    private final String selectedId;
    private final Consumer<Village> onSelect;
    private double lastX;
    private double lastY;
    private double moved;

    /**
     * Makes the map.
     *
     * @param rect       the area of the map
     * @param font       the font of the names
     * @param villages   the discovered villages, the most important first
     * @param selectedId the id of the selected village, or null
     * @param onSelect   receives the village of a click
     */
    TerritoryMapWidget(UiRect rect, Font font, List<Village> villages, @Nullable String selectedId,
            Consumer<Village> onSelect) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), Component.translatable(KEY + "tab.map"));
        this.font = font;
        this.villages = List.copyOf(villages);
        this.selectedId = selectedId;
        this.onSelect = onSelect;
    }

    // ------------------------------------------------------------------ position of the map

    /** The map shows all the discovered territories at the next opening. */
    static void forget() {
        placed = false;
    }

    /** Puts a block at the center of the map, with a scale that shows the details of a village. */
    static void focus(double blockX, double blockZ) {
        VIEWPORT.center(blockX, blockZ);
        VIEWPORT.setScale(Math.max(FOCUS_SCALE, VIEWPORT.scale()));
        placed = true;
    }

    /** The block X at the center of the map. */
    static int centerX() {
        return (int) Math.floor(VIEWPORT.centerX());
    }

    /** The block Z at the center of the map. */
    static int centerZ() {
        return (int) Math.floor(VIEWPORT.centerZ());
    }

    /** Shows all the discovered territories and villages. With no village, the map shows the player. */
    void showAll() {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (TerritoryRaster.Cell cell : ClientJournalMap.raster().cells()) {
            minX = Math.min(minX, cell.chunkX() * 16.0);
            minZ = Math.min(minZ, cell.chunkZ() * 16.0);
            maxX = Math.max(maxX, cell.chunkX() * 16.0 + 16.0);
            maxZ = Math.max(maxZ, cell.chunkZ() * 16.0 + 16.0);
        }
        for (Village village : villages) {
            minX = Math.min(minX, village.x());
            minZ = Math.min(minZ, village.z());
            maxX = Math.max(maxX, village.x() + 1.0);
            maxZ = Math.max(maxZ, village.z() + 1.0);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (minX > maxX) {
            if (player != null) {
                VIEWPORT.center(player.getX(), player.getZ());
            }
        } else {
            VIEWPORT.fit(minX, minZ, maxX, maxZ, inner());
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

    /** Changes the scale by a number of steps, around the center of the map. More than 0 shows more details. */
    void zoom(int steps) {
        UiRect inner = inner();
        VIEWPORT.zoom(Math.pow(MapViewport.ZOOM_STEP, steps), inner, inner.centerX(), inner.centerY());
        placed = true;
    }

    /** The area of the map inside its border. */
    private UiRect inner() {
        return new UiRect(getX() + 1, getY() + 1, Math.max(0, width - 2), Math.max(0, height - 2));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!placed) {
            showAll();
        }
        UiDraw.box(graphics, new UiRect(getX(), getY(), width, height), BACKGROUND, UiTheme.PARCHMENT_EDGE, false);
        UiRect inner = inner();
        MapProjection map = VIEWPORT.projection(inner);
        graphics.enableScissor(inner.x(), inner.y(), inner.right(), inner.bottom());
        RoutePayload.Relief relief = ClientJournalMap.relief();
        if (relief != null) {
            MapDrawing.relief(graphics, map, relief);
        }
        renderTerritories(graphics, map);
        renderVillages(graphics, map, mouseX, mouseY);
        renderPlayer(graphics, map);
        if (villages.isEmpty()) {
            UiText.drawWrapped(graphics, font, Component.translatable(KEY + "map.empty"),
                    inner.inset(UiTheme.PADDING * 4, inner.height() / 3), UiTheme.INK_MUTED);
        }
        graphics.disableScissor();
        if (isFocused()) {
            UiDraw.outline(graphics, new UiRect(getX(), getY(), width, height), UiTheme.GILT);
        }
    }

    /** Draws the fill of the territories, then their borders. */
    private void renderTerritories(GuiGraphics graphics, MapProjection map) {
        List<TerritoryMapPayload.Village> owners = ClientTerritoryMap.get().villages();
        List<TerritoryRaster.Cell> cells = ClientJournalMap.raster().cells();
        UiRect area = map.area();
        // One rectangle for the chunks of a row with the same village.
        int start = 0;
        while (start < cells.size()) {
            TerritoryRaster.Cell first = cells.get(start);
            int end = start;
            while (end + 1 < cells.size() && sameRun(cells.get(end), cells.get(end + 1))) {
                end++;
            }
            int x0 = map.x(first.chunkX() * 16);
            int x1 = map.x(cells.get(end).chunkX() * 16 + 16);
            int y0 = map.y(first.chunkZ() * 16);
            int y1 = map.y(first.chunkZ() * 16 + 16);
            if (visible(area, x0, y0, x1, y1) && first.village() < owners.size()) {
                graphics.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1),
                        (FILL_ALPHA << 24) | (owners.get(first.village()).color() & 0xFFFFFF));
            }
            start = end + 1;
        }
        int thickness = map.scale() * 16 >= 24 ? 2 : 1;
        for (TerritoryRaster.Cell cell : cells) {
            if (cell.borders() == 0 || cell.village() >= owners.size()) {
                continue;
            }
            int x0 = map.x(cell.chunkX() * 16);
            int y0 = map.y(cell.chunkZ() * 16);
            int x1 = Math.max(x0 + 1, map.x(cell.chunkX() * 16 + 16));
            int y1 = Math.max(y0 + 1, map.y(cell.chunkZ() * 16 + 16));
            if (!visible(area, x0, y0, x1, y1)) {
                continue;
            }
            int color = 0xFF000000 | darker(owners.get(cell.village()).color());
            if ((cell.borders() & TerritoryRaster.NORTH) != 0) {
                graphics.fill(x0, y0, x1, y0 + thickness, color);
            }
            if ((cell.borders() & TerritoryRaster.SOUTH) != 0) {
                graphics.fill(x0, y1 - thickness, x1, y1, color);
            }
            if ((cell.borders() & TerritoryRaster.WEST) != 0) {
                graphics.fill(x0, y0, x0 + thickness, y1, color);
            }
            if ((cell.borders() & TerritoryRaster.EAST) != 0) {
                graphics.fill(x1 - thickness, y0, x1, y1, color);
            }
        }
    }

    /** True if two chunks are next to each other in a row, with the same village. */
    private static boolean sameRun(TerritoryRaster.Cell a, TerritoryRaster.Cell b) {
        return a.chunkZ() == b.chunkZ() && a.chunkX() + 1 == b.chunkX() && a.village() == b.village();
    }

    private static boolean visible(UiRect area, int x0, int y0, int x1, int y1) {
        return x1 >= area.x() && x0 <= area.right() && y1 >= area.y() && y0 <= area.bottom();
    }

    /** Draws the marks of the villages, then their names. */
    private void renderVillages(GuiGraphics graphics, MapProjection map, int mouseX, int mouseY) {
        UiRect area = map.area();
        Village hovered = isMouseOver(mouseX, mouseY) ? villageAt(map, mouseX, mouseY) : null;
        for (Village village : villages) {
            int x = map.x(village.x() + 0.5);
            int y = map.y(village.z() + 0.5);
            if (visible(area, x - HIT, y - HIT, x + HIT, y + HIT)) {
                mark(graphics, village, x, y);
            }
        }
        List<UiRect> names = new ArrayList<>();
        for (Village village : labelOrder(hovered)) {
            int x = map.x(village.x() + 0.5);
            int y = map.y(village.z() + 0.5);
            Component name = Component.literal(village.name());
            UiRect label = new UiRect(x + 5, y - 5, font.width(name) + 3, 10);
            boolean important = village.id().equals(selectedId) || village == hovered;
            if (!visible(area, label.x(), label.y(), label.right(), label.bottom())
                    || (!important && names.stream().anyMatch(other -> overlaps(other, label)))) {
                continue;
            }
            names.add(label);
            UiDraw.fill(graphics, label, LABEL_BACKGROUND);
            graphics.drawString(font, name, label.x() + 2, label.y() + 1,
                    village.active() ? UiTheme.INK : UiTheme.INK_FAINT, false);
        }
    }

    /** The villages in the order of their names: the selected village, the village under the mouse, the others. */
    private List<Village> labelOrder(@Nullable Village hovered) {
        List<Village> order = new ArrayList<>(villages.size());
        for (Village village : villages) {
            if (village.id().equals(selectedId)) {
                order.add(village);
            }
        }
        if (hovered != null && !hovered.id().equals(selectedId)) {
            order.add(hovered);
        }
        for (Village village : villages) {
            if (!village.id().equals(selectedId) && village != hovered) {
                order.add(village);
            }
        }
        return order;
    }

    private static boolean overlaps(UiRect a, UiRect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    /** Draws the mark of a village. */
    private void mark(GuiGraphics graphics, Village village, int x, int y) {
        if (village.id().equals(selectedId)) {
            UiDraw.outline(graphics, new UiRect(x - 6, y - 6, 13, 13), UiTheme.GILT);
            UiDraw.outline(graphics, new UiRect(x - 5, y - 5, 11, 11), UiTheme.GILT);
        }
        if (!village.active()) {
            for (int d = -3; d <= 3; d++) {
                graphics.fill(x + d, y + d, x + d + 1, y + d + 1, UiTheme.INK_FAINT);
                graphics.fill(x + d, y - d, x + d + 1, y - d + 1, UiTheme.INK_FAINT);
            }
            return;
        }
        int color = 0xFF000000 | factionColor(village);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, UiTheme.INK);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        if (!village.visited()) {
            graphics.fill(x - 1, y - 1, x + 2, y + 2, UiTheme.PARCHMENT_LIGHT);
        }
    }

    /** Draws the player: a dot, and a line in the direction of the view. */
    private static void renderPlayer(GuiGraphics graphics, MapProjection map) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int x = map.x(player.getX());
        int y = map.y(player.getZ());
        double yaw = Math.toRadians(player.getYRot());
        MapDrawing.line(graphics, x - 1, y - 1, x - 1 + (int) Math.round(-Math.sin(yaw) * 8),
                y - 1 + (int) Math.round(Math.cos(yaw) * 8), UiTheme.INK);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, UiTheme.INK);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, PLAYER);
    }

    /** The color of the faction of a village, as 0xRRGGBB. */
    private static int factionColor(Village village) {
        for (TerritoryMapPayload.Village owner : ClientTerritoryMap.get().villages()) {
            if (owner.id().equals(village.id())) {
                return owner.color() & 0xFFFFFF;
            }
        }
        TextColor color = village.faction().getStyle().getColor();
        return color == null ? UiTheme.INK_MUTED & 0xFFFFFF : color.getValue();
    }

    /** A darker color, for the borders of the territories on the light map. */
    private static int darker(int rgb) {
        int red = ((rgb >> 16) & 0xFF) * 3 / 4;
        int green = ((rgb >> 8) & 0xFF) * 3 / 4;
        int blue = (rgb & 0xFF) * 3 / 4;
        return (red << 16) | (green << 8) | blue;
    }

    // ------------------------------------------------------------------ mouse and keys

    /** The village with its mark nearest to a point of the screen, at {@link #HIT} pixels or less. */
    @Nullable
    private Village villageAt(MapProjection map, double mouseX, double mouseY) {
        Village nearest = null;
        double best = HIT * HIT;
        for (Village village : villages) {
            double dx = map.x(village.x() + 0.5) - mouseX;
            double dy = map.y(village.z() + 0.5) - mouseY;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                nearest = village;
            }
        }
        return nearest;
    }

    /**
     * The tooltip of the map at a point of the screen: the village under the mouse, or the territory under the
     * mouse.
     *
     * @param mouseX the screen X of the mouse
     * @param mouseY the screen Y of the mouse
     * @return the lines of the tooltip, or an empty list
     */
    List<Component> tooltip(double mouseX, double mouseY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        MapProjection map = VIEWPORT.projection(inner());
        Village village = villageAt(map, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (village != null) {
            lines.add(village.active() ? Component.literal(village.name())
                    : Component.translatable(KEY + "ruins_suffix", village.name()));
            lines.add(village.faction());
            lines.add(Component.translatable(KEY + (!village.active() ? "ruins"
                    : village.visited() ? "visited" : "discovered")));
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                lines.add(HudDirections.toward(player, village.x() + 0.5, village.z() + 0.5));
            }
            return lines;
        }
        int chunkX = Math.floorDiv((int) Math.floor(map.blockX(mouseX)), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(map.blockZ(mouseY)), 16);
        List<TerritoryMapPayload.Village> owners = ClientTerritoryMap.get().villages();
        ClientJournalMap.raster().at(chunkX, chunkZ).filter(cell -> cell.village() < owners.size())
                .ifPresent(cell -> {
                    TerritoryMapPayload.Village owner = owners.get(cell.village());
                    MutableComponent faction = Component.literal(owner.faction())
                            .withStyle(style -> style.withColor(TextColor.fromRgb(owner.color())));
                    lines.add(Component.translatable(KEY + "map.territory", owner.name()));
                    lines.add(faction);
                });
        return lines;
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
            Village village = villageAt(VIEWPORT.projection(inner()), mouseX, mouseY);
            if (village != null && !Objects.equals(village.id(), selectedId)) {
                onSelect.accept(village);
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
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> VIEWPORT.drag(KEY_MOVE, 0);
            case GLFW.GLFW_KEY_RIGHT -> VIEWPORT.drag(-KEY_MOVE, 0);
            case GLFW.GLFW_KEY_UP -> VIEWPORT.drag(0, KEY_MOVE);
            case GLFW.GLFW_KEY_DOWN -> VIEWPORT.drag(0, -KEY_MOVE);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoom(1);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoom(-1);
            default -> {
                return false;
            }
        }
        placed = true;
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // A click on the map makes no sound: the player drags the map often.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }

    /** The colors of the factions of the discovered territories, by faction name, in the order of the villages. */
    static Map<String, Integer> factionColors() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (TerritoryMapPayload.Village village : ClientTerritoryMap.get().villages()) {
            colors.putIfAbsent(village.faction(), village.color() & 0xFFFFFF);
        }
        return colors;
    }
}
