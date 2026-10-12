package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;

/**
 * Screen of the village heart: the village information that the server sent (see {@link VillageInfoPayload}).
 * Each section has a heading and rows with a label on the left and a value on the right.
 */
public class VillageHeartScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int BORDER_COLOR = 0xFF8B6B3D;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int HEADING_COLOR = 0xFFD37F;
    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFF;

    private final VillageInfoPayload info;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public VillageHeartScreen(VillageInfoPayload info) {
        super(info.title());
        this.info = info;
    }

    /** Opens the screen. Called by the network handler on the client thread. */
    public static void open(VillageInfoPayload info) {
        Minecraft.getInstance().setScreen(new VillageHeartScreen(info));
    }

    private int contentHeight() {
        int height = LINE_HEIGHT + SECTION_GAP;
        for (Section section : info.sections()) {
            height += LINE_HEIGHT * (1 + section.rows().size()) + SECTION_GAP;
        }
        return height;
    }

    @Override
    protected void init() {
        panelHeight = PADDING * 2 + contentHeight() + BUTTON_HEIGHT;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = Math.max(PADDING, (height - panelHeight) / 2);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 50, panelTop + panelHeight - PADDING - BUTTON_HEIGHT, 100, BUTTON_HEIGHT)
                .build());
    }

    /** The vanilla background, then the panel. The widgets and the texts come on top. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int right = panelLeft + PANEL_WIDTH;
        graphics.fill(panelLeft - 1, panelTop - 1, right + 1, panelTop + panelHeight + 1, BORDER_COLOR);
        graphics.fill(panelLeft, panelTop, right, panelTop + panelHeight, PANEL_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int right = panelLeft + PANEL_WIDTH;
        int y = panelTop + PADDING;
        graphics.drawCenteredString(font, title, width / 2, y, TITLE_COLOR);
        y += LINE_HEIGHT + SECTION_GAP;
        for (Section section : info.sections()) {
            graphics.drawString(font, section.heading(), panelLeft + PADDING, y, HEADING_COLOR);
            y += LINE_HEIGHT;
            for (Row row : section.rows()) {
                graphics.drawString(font, row.label(), panelLeft + PADDING * 2, y, LABEL_COLOR);
                int valueWidth = font.width(row.value());
                graphics.drawString(font, row.value(), right - PADDING - valueWidth, y, VALUE_COLOR);
                y += LINE_HEIGHT;
            }
            y += SECTION_GAP;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
