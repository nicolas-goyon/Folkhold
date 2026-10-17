package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryStatusPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The territory panel at the top right of the screen (client option {@link ClientConfig#SHOW_TERRITORY_HUD}).
 *
 * <ul>
 *   <li>The banner of the faction that owns the territory.</li>
 *   <li>Line 1: the faction name in its color, and the reputation tier of the player with its glyph and color. Out
 *       of the territories: "Unclaimed territory".</li>
 *   <li>Line 2: the village near the player, its distance and an arrow to it from the view of the player.</li>
 * </ul>
 * The panel goes below the vanilla icons of the status effects.
 */
public final class TerritoryHud {
    private static final String KEY = "hud.folkhold_world.territory.";
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int BANNER_WIDTH = 10;
    /** The height of a row of vanilla effect icons, with its gap. */
    private static final int EFFECT_ROW = 26;

    private TerritoryHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!ClientConfig.SHOW_TERRITORY_HUD.get() || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen() || player == null) {
            return;
        }
        TerritoryStatusPayload status = ClientTerritory.get();
        if (status.faction().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(status.tier().map(name -> factionLine(status.faction().get(), name))
                .orElse(status.faction().get()));
        status.village().ifPresent(village -> lines.add(villageLine(player, village, status)));

        Font font = minecraft.font;
        boolean banner = status.factionColor() >= 0;
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int width = PAD + (banner ? BANNER_WIDTH + PAD : 0) + textWidth + PAD;
        int height = PAD + Math.max(lines.size() * (UiTheme.LINE + 2) - 2, banner ? 14 : 0) + PAD;
        UiRect panel = new UiRect(graphics.guiWidth() - MARGIN - width, MARGIN + effectsHeight(player), width,
                height);
        UiDraw.fill(graphics, panel, UiTheme.HUD);
        int x = panel.x() + PAD;
        if (banner) {
            UiDraw.banner(graphics, x, panel.y() + PAD, status.factionColor());
            x += BANNER_WIDTH + PAD;
        }
        int y = panel.y() + PAD;
        for (int i = 0; i < lines.size(); i++) {
            UiText.drawRight(graphics, font, lines.get(i), panel.right() - PAD, y, panel.right() - PAD - x,
                    i == 0 ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            y += UiTheme.LINE + 2;
        }
    }

    /** The faction and the tier of the player: "Ashmark · ✓ Friend". */
    private static Component factionLine(Component faction, String tierName) {
        ReputationTier tier = ReputationTier.fromSerializedName(tierName).orElse(ReputationTier.NEUTRAL);
        Component title = Component.literal(UiReputation.glyph(tier) + " ")
                .append(Component.translatable("folkhold_world.reputation_tier_title." + tierName))
                .withColor(UiReputation.textOnWood(tier));
        return Component.empty().append(faction).append(Component.literal(" · ").withColor(UiTheme.TEXT_FAINT))
                .append(title);
    }

    /** The village, its distance and the arrow to it: "Holmstead · 120 m ↗". */
    private static Component villageLine(LocalPlayer player, Component village, TerritoryStatusPayload status) {
        if (status.villagePos().isEmpty()) {
            return village;
        }
        BlockPos pos = status.villagePos().get();
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        return Component.translatable(KEY + "village", village,
                HudDirections.distanceText(HudDirections.distance(player, x, z)), HudDirections.arrow(player, x, z));
    }

    /** The height of the vanilla effect icons at the top right: one row for good effects, one for bad effects. */
    private static int effectsHeight(LocalPlayer player) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return harmful ? 2 * EFFECT_ROW : beneficial ? EFFECT_ROW : 0;
    }
}
