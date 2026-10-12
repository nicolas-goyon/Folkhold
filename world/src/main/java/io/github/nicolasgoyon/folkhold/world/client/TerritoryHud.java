package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryStatusPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Territory HUD at the top right of the screen (client option {@link ClientConfig#SHOW_TERRITORY_HUD}):
 * <ul>
 *   <li>line 1: the faction that owns the territory and the reputation tier of the player, or "Unclaimed
 *       territory";</li>
 *   <li>line 2: the village near the player, if there is one.</li>
 * </ul>
 */
public final class TerritoryHud {
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BACKGROUND_COLOR = 0x80000000;

    private TerritoryHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.SHOW_TERRITORY_HUD.get() || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen() || minecraft.player == null) {
            return;
        }
        TerritoryStatusPayload status = ClientTerritory.get();
        if (status.faction().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(status.tier()
                .map(tier -> Component.translatable("hud.folkhold_world.territory", status.faction().get(), tier))
                .orElse(status.faction().get().copy()));
        status.village().ifPresent(village -> lines.add(Component.translatable("hud.folkhold_world.village", village)));

        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int y = MARGIN;
        for (Component line : lines) {
            int lineWidth = font.width(line);
            int x = width - MARGIN - lineWidth;
            graphics.fill(x - 2, y - 1, x + lineWidth + 2, y + LINE_HEIGHT - 1, BACKGROUND_COLOR);
            graphics.drawString(font, line, x, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }
}
