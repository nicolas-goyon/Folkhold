package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Quest HUD at the top left of the screen (client option {@link ClientConfig#SHOW_QUEST_HUD}): one line for each
 * active quest, with the minutes before its deadline.
 */
public final class QuestHud {
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BACKGROUND_COLOR = 0x80000000;

    private QuestHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.SHOW_QUEST_HUD.get() || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen() || minecraft.player == null) {
            return;
        }
        List<Component> lines = ClientQuestLog.get().lines();
        Font font = minecraft.font;
        int y = MARGIN;
        for (Component line : lines) {
            int lineWidth = font.width(line);
            graphics.fill(MARGIN - 2, y - 1, MARGIN + lineWidth + 2, y + LINE_HEIGHT - 1, BACKGROUND_COLOR);
            graphics.drawString(font, line, MARGIN, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }
}
