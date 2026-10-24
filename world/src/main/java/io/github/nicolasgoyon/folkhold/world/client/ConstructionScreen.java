package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiOverlay;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTextField;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionStatusPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionTemplatesPayload;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The construction menu of an operator: the structure templates of the server, with a text filter, and the speed of
 * the construction. With the speed "builders", the builders of the nearest village build the structure. The button
 * "Place" asks the server for the blocks of the selected template. When the blocks arrive, the menu closes and the
 * ghost of the template follows the view of the player (see {@link ConstructionGhost}).
 *
 * <p>The menu keeps the filter, the selected template and the speed between two openings.
 */
public final class ConstructionScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.construction.";
    private static final int LIST_ROW = UiTheme.ROW + 2;
    private static final int CHIP = 14;
    /**
     * The speeds of the construction, in steps for each tick. {@link ConstructionPlacement#BUILDERS}: the builders of
     * the nearest village build the structure.
     */
    private static final int[] SPEEDS = {1, 4, 20, ConstructionPlacement.BUILDERS};
    private static final String[] SPEED_NAMES = {"slow", "normal", "fast", "builders"};
    private static final int DEFAULT_SPEED = 1;

    private static String lastFilter = "";
    @Nullable
    private static ResourceLocation lastSelected;
    private static int lastSpeed = DEFAULT_SPEED;

    private List<ResourceLocation> templates;
    private List<ResourceLocation> visible = List.of();
    @Nullable
    private TemplateList list;
    @Nullable
    private UiButton placeButton;
    private int listScroll;
    private UiRect countArea = new UiRect(0, 0, 0, 0);
    private UiRect speedLabelArea = new UiRect(0, 0, 0, 0);

    private ConstructionScreen(List<ResourceLocation> templates) {
        super(Component.translatable(KEY + "title"));
        this.templates = templates;
    }

    /** Opens the menu, or updates the open menu. Called by the network handler. */
    public static void open(ConstructionTemplatesPayload payload) {
        ConstructionScreen screen = current();
        if (screen != null) {
            screen.templates = payload.templates();
            screen.applyFilter();
            return;
        }
        Minecraft.getInstance().setScreen(new ConstructionScreen(payload.templates()));
    }

    /**
     * Shows an answer of the server in the status line of the open menu.
     *
     * @return false if the menu is not open
     */
    static boolean showStatus(ConstructionStatusPayload payload) {
        ConstructionScreen screen = current();
        if (screen == null) {
            return false;
        }
        if (screen.placeButton != null) {
            screen.placeButton.setPending(false);
        }
        screen.showStatus(payload.text(), payload.success() ? UiTone.GOOD : UiTone.BAD);
        return true;
    }

    /** Closes the open menu: the ghost of the selected template is in the level. */
    static void closeForGhost() {
        if (current() != null) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** The speed that the player selected, in steps for each tick. */
    static int stepsPerTick() {
        return SPEEDS[lastSpeed];
    }

    /** Forgets the state kept between two openings. Called when the player leaves a world or a server. */
    public static void clear() {
        lastFilter = "";
        lastSelected = null;
        lastSpeed = DEFAULT_SPEED;
    }

    /** The open construction menu, also behind a menu or a dialog, or null. */
    @Nullable
    private static ConstructionScreen current() {
        return UiOverlay.rootScreen() instanceof ConstructionScreen screen ? screen : null;
    }

    @Override
    protected Component subtitle() {
        return Component.translatable(KEY + "subtitle");
    }

    @Override
    protected Component footerHint() {
        return Component.translatable(KEY + "hint");
    }

    @Override
    protected void initBody(UiRect body) {
        if (list != null) {
            listScroll = list.scroll();
        }
        UiRect inner = body.inset(UiTheme.PADDING);
        UiRect searchRow = inner.top(UiTheme.CONTROL);
        int countWidth = font.width(Component.translatable(KEY + "count", 9999)) + UiTheme.GAP;
        UiTextField search = new UiTextField(searchRow.beforeRight(countWidth, 0),
                Component.translatable(KEY + "filter"), Component.translatable(KEY + "filter"));
        search.setValue(lastFilter);
        search.setResponder(text -> {
            lastFilter = text;
            applyFilter();
        });
        addRenderableWidget(search);
        countArea = searchRow.rightPart(countWidth);

        UiRect footer = inner.bottomPart(UiTheme.CONTROL);
        int listTop = searchRow.bottom() + UiTheme.GAP;
        TemplateList templateList = addRenderableWidget(new TemplateList(
                new UiRect(inner.x(), listTop, inner.width(), footer.y() - UiTheme.GAP - listTop)));
        templateList.setSelectable(true).setEmptyText(Component.translatable(KEY + "empty"));
        list = templateList;
        applyFilter();
        templateList.setScroll(listScroll);

        Component speed = Component.translatable(KEY + "speed");
        speedLabelArea = new UiRect(footer.x(), footer.y(), font.width(speed), footer.height());
        int x = speedLabelArea.right() + UiTheme.GAP;
        int chipY = footer.y() + (footer.height() - CHIP) / 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            int index = i;
            Component label = Component.translatable(KEY + "speed." + SPEED_NAMES[i]);
            int w = font.width(label) + 10;
            UiButton chip = addRenderableWidget(new UiButton(new UiRect(x, chipY, w, CHIP), label,
                    i == lastSpeed ? UiButton.Style.CHIP_SELECTED : UiButton.Style.CHIP, button -> {
                        lastSpeed = index;
                        rebuildWidgets();
                    }));
            chip.setDetails(SPEEDS[i] == ConstructionPlacement.BUILDERS
                    ? Component.translatable(KEY + "speed.builders.details")
                    : Component.translatable(KEY + "speed.details", SPEEDS[i]));
            x += w + UiTheme.GAP;
        }
        Component place = Component.translatable(KEY + "place");
        int placeWidth = Math.max(70, font.width(place) + 16);
        placeButton = addRenderableWidget(UiButton.primary(footer.rightPart(placeWidth), place, this::place));
        updatePlaceButton();
    }

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        UiText.drawRight(graphics, font, Component.translatable(KEY + "count", visible.size()), countArea.right(),
                UiText.textY(countArea), countArea.width(), UiTheme.TEXT_MUTED);
        UiText.draw(graphics, font, Component.translatable(KEY + "speed"), speedLabelArea.x(),
                UiText.textY(speedLabelArea), speedLabelArea.width(), UiTheme.TEXT_MUTED);
    }

    private void applyFilter() {
        String text = lastFilter.trim().toLowerCase(Locale.ROOT);
        visible = templates.stream().filter(id -> text.isEmpty() || id.toString().contains(text)).toList();
        if (list != null) {
            list.setItems(visible);
            list.setSelectedIndex(lastSelected == null ? -1 : visible.indexOf(lastSelected));
        }
        updatePlaceButton();
    }

    private void updatePlaceButton() {
        if (placeButton != null) {
            boolean selected = lastSelected != null && visible.contains(lastSelected);
            placeButton.setDisabled(selected ? null : Component.translatable(KEY + "select_first"));
        }
    }

    private void place(UiButton button) {
        ResourceLocation selected = lastSelected;
        if (selected == null) {
            return;
        }
        button.setPending(true);
        PacketDistributor.sendToServer(new ConstructionPreviewRequestPayload(selected));
    }

    /** The list of the templates: the path, and the namespace at the right. */
    private final class TemplateList extends UiList<ResourceLocation> {
        TemplateList(UiRect rect) {
            super(rect);
        }

        @Override
        protected int rowHeight(ResourceLocation item, int index) {
            return LIST_ROW;
        }

        @Override
        protected void renderRow(GuiGraphics graphics, ResourceLocation item, int index, UiRect rect,
                boolean hovered, boolean selected, int mouseX, int mouseY) {
            if (selected) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_RAISED);
                UiDraw.fill(graphics, rect.left(2), UiTheme.GILT);
            } else if (hovered) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_HOVER);
            } else if (index % 2 == 0) {
                UiDraw.fill(graphics, rect, UiTheme.WOOD_ROW);
            }
            UiRect text = rect.inset(UiTheme.PADDING, 0);
            int y = UiText.textY(text);
            int namespaceWidth = UiText.drawRight(graphics, font, Component.literal(item.getNamespace()),
                    text.right(), y, text.width() / 3, UiTheme.TEXT_MUTED);
            UiText.draw(graphics, font, Component.literal(item.getPath()), text.x(), y,
                    text.width() - namespaceWidth - UiTheme.PADDING, selected ? UiTheme.GOLD : UiTheme.TEXT);
        }

        @Override
        protected void onSelect(ResourceLocation item, int index) {
            lastSelected = item;
            updatePlaceButton();
        }

        @Override
        protected Component narration(ResourceLocation item) {
            return Component.literal(item.toString());
        }
    }
}
