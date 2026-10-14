package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryTitlePayload;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The territory banner: when the player enters a territory, a small banner at the top of the screen shows the
 * faction banner, the name of the territory and the tier of the player for some seconds. It replaces the vanilla
 * title, which is too large for this information.
 */
public final class TerritoryTitleHud {
    private static final long FADE_IN = 400L;
    private static final long STAY = 3000L;
    private static final long FADE_OUT = 900L;
    private static final float TITLE_SCALE = 1.5F;
    private static final int PAD = 6;
    private static final int BANNER_WIDTH = 10;
    private static final int BANNER_HEIGHT = 14;

    @Nullable
    private static TerritoryTitlePayload current;
    private static long shownAt;

    private TerritoryTitleHud() {
    }

    /** Shows a territory banner. Called by the network handler on the client thread. */
    public static void show(TerritoryTitlePayload payload) {
        current = payload;
        shownAt = System.currentTimeMillis();
    }

    /** Removes the banner. */
    public static void clear() {
        current = null;
    }

    /** The opacity of the banner now, from 0 to 1. */
    private static float opacity(long elapsed) {
        if (elapsed < FADE_IN) {
            return (float) elapsed / FADE_IN;
        }
        if (elapsed < FADE_IN + STAY) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (float) (elapsed - FADE_IN - STAY) / FADE_OUT);
    }

    private static int withOpacity(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        TerritoryTitlePayload payload = current;
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || minecraft.options.hideGui) {
            return;
        }
        long elapsed = System.currentTimeMillis() - shownAt;
        float opacity = opacity(elapsed);
        if (elapsed > FADE_IN + STAY + FADE_OUT) {
            current = null;
            return;
        }
        if (opacity < 0.05F) {
            return;
        }
        Font font = minecraft.font;
        boolean banner = payload.factionColor() >= 0;
        int titleWidth = Math.round(font.width(payload.title()) * TITLE_SCALE);
        int titleHeight = Math.round(UiTheme.LINE * TITLE_SCALE);
        int rowWidth = titleWidth + (banner ? BANNER_WIDTH + PAD : 0);
        int rowHeight = Math.max(titleHeight, banner ? BANNER_HEIGHT : 0);
        Component subtitle = payload.subtitle().orElse(null);
        int width = Math.max(rowWidth, subtitle == null ? 0 : font.width(subtitle)) + 2 * PAD;
        int height = PAD + rowHeight + (subtitle == null ? 0 : 3 + UiTheme.LINE) + PAD;
        UiRect box = new UiRect((graphics.guiWidth() - width) / 2, graphics.guiHeight() / 5, width, height);

        UiDraw.fill(graphics, box, withOpacity(UiTheme.HUD, opacity));
        UiDraw.hLine(graphics, box.x(), box.bottom() - 1, box.width(), withOpacity(UiTheme.GILT, opacity));
        int x = box.x() + (box.width() - rowWidth) / 2;
        int y = box.y() + PAD;
        if (banner && opacity > 0.5F) {
            UiDraw.banner(graphics, x, y + (rowHeight - BANNER_HEIGHT) / 2, payload.factionColor());
        }
        if (banner) {
            x += BANNER_WIDTH + PAD;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (rowHeight - titleHeight) / 2.0F + 1.0F, 0.0F);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
        graphics.drawString(font, payload.title(), 0, 0, withOpacity(0xFFFFFFFF, opacity), true);
        graphics.pose().popPose();
        if (subtitle != null) {
            int subtitleY = y + rowHeight + 3;
            graphics.drawString(font, subtitle, box.x() + (box.width() - font.width(subtitle)) / 2, subtitleY,
                    withOpacity(UiTheme.TEXT, opacity), true);
        }
    }
}
