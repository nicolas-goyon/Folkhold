package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload.Entry;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload.Escort;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The quest panel at the top left of the screen (client option {@link ClientConfig#SHOW_QUEST_HUD}).
 *
 * <ul>
 *   <li>A header with the number of active quests and the maximum.</li>
 *   <li>A row for each delivery: the item icon, what to do, the items of the player, and the time before the
 *       deadline. Under 2 minutes, the time is on a red badge.</li>
 *   <li>A tracker for each escort: the destination and the time, a progress bar, the blocks, the hearts of the
 *       merchant, and a warning with the time to come back when the player is too far.</li>
 * </ul>
 * The panel shows nothing when the player has no active quest.
 */
public final class QuestHud {
    private static final String KEY = "hud.folkhold_world.quests.";
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int ROW = 13;
    private static final int ICON = 10;
    private static final int MIN_WIDTH = 150;
    private static final int MAX_WIDTH = 210;
    /** Under this time, the time of a quest is on a red badge. */
    private static final int URGENT_SECONDS = 120;
    private static final int HEARTS = 4;

    private QuestHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.SHOW_QUEST_HUD.get() || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen() || minecraft.player == null) {
            return;
        }
        QuestLogPayload log = ClientQuestLog.get();
        if (log.quests().isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        Inventory inventory = minecraft.player.getInventory();
        int width = MIN_WIDTH;
        for (Entry entry : log.quests()) {
            width = Math.max(width, ICON + 4 + font.width(rowText(entry, inventory)) + 8 + font.width("00:00") + 2
                    * PAD);
        }
        width = Math.min(width, Math.min(MAX_WIDTH, graphics.guiWidth() / 2 - MARGIN));
        int height = PAD + UiTheme.LINE + 3;
        for (Entry entry : log.quests()) {
            height += entry.escort().map(escort -> escortHeight(escort) + 2).orElse(ROW);
        }
        height += PAD - 2;

        UiRect panel = new UiRect(MARGIN, MARGIN, width, height);
        UiDraw.fill(graphics, panel, UiTheme.HUD);
        UiRect inner = panel.inset(PAD);
        Component header = Component.translatable(KEY + "header", log.quests().size(), log.maxActive());
        UiText.draw(graphics, font, header, inner.x(), inner.y(), inner.width(), UiTheme.GOLD);
        int y = inner.y() + UiTheme.LINE + 3;
        for (Entry entry : log.quests()) {
            if (entry.escort().isPresent()) {
                int h = escortHeight(entry.escort().get());
                renderEscort(graphics, font, entry, entry.escort().get(), new UiRect(inner.x(), y, inner.width(), h));
                y += h + 2;
            } else {
                renderDelivery(graphics, font, entry, inventory, new UiRect(inner.x(), y, inner.width(), ROW));
                y += ROW;
            }
        }
    }

    private static Component rowText(Entry entry, Inventory inventory) {
        if (entry.escort().isPresent()) {
            return entry.name();
        }
        int have = count(inventory, entry.itemId());
        boolean ready = have >= entry.count();
        MutableComponent progress = Component.translatable(KEY + "progress", Math.min(have, entry.count()),
                entry.count());
        if (ready) {
            progress.append(" ✓");
        }
        return Component.empty().append(entry.name()).append(Component.literal(" · ").withColor(UiTheme.TEXT_FAINT))
                .append(progress.withColor(ready ? UiTheme.TEXT_GOOD : UiTheme.TEXT_MUTED));
    }

    private static void renderDelivery(GuiGraphics graphics, Font font, Entry entry, Inventory inventory, UiRect row) {
        ItemStack stack = UiDraw.stack(entry.itemId());
        if (!stack.isEmpty()) {
            UiDraw.item(graphics, stack, row.x(), row.y() + (row.height() - ICON) / 2, ICON);
        }
        int timeWidth = renderTime(graphics, font, ClientQuestLog.secondsNow(entry.secondsLeft()), row.right(), row);
        int x = row.x() + ICON + 4;
        UiText.draw(graphics, font, rowText(entry, inventory), x, UiText.textY(row), row.right() - timeWidth - 4 - x,
                UiTheme.TEXT);
    }

    /** Draws a time at the right of a row. Returns its width. */
    private static int renderTime(GuiGraphics graphics, Font font, int seconds, int right, UiRect row) {
        String time = UiText.clock(seconds);
        int w = font.width(time);
        if (seconds < URGENT_SECONDS) {
            UiRect badge = new UiRect(right - w - 6, row.y() + (row.height() - UiTheme.BADGE) / 2, w + 6,
                    UiTheme.BADGE);
            UiDraw.box(graphics, badge, UiTheme.DANGER, UiTheme.DANGER, false);
            graphics.drawString(font, time, badge.x() + 3, UiText.textY(badge), UiTheme.DANGER_TEXT, false);
            return w + 6;
        }
        graphics.drawString(font, time, right - w, UiText.textY(row), UiTheme.TEXT, false);
        return w;
    }

    private static int escortHeight(Escort escort) {
        // Padding, title, gap, bar, gap, blocks line, warning line, padding.
        return 4 + ROW + 2 + 3 + 3 + UiTheme.LINE + (escort.away() ? 2 + UiTheme.LINE : 0) + 4;
    }

    private static void renderEscort(GuiGraphics graphics, Font font, Entry entry, Escort escort, UiRect box) {
        boolean warning = escort.away();
        UiDraw.box(graphics, box, warning ? 0x803A1814 : 0x60241C13, warning ? UiTheme.DANGER_EDGE
                : UiTheme.WOOD_EDGE, false);
        UiRect inner = box.inset(4, 4);
        UiRect title = inner.top(ROW);
        ItemStack stack = UiDraw.stack(entry.itemId());
        if (!stack.isEmpty()) {
            UiDraw.item(graphics, stack, title.x(), title.y() + (ROW - ICON) / 2, ICON);
        }
        int timeWidth = renderTime(graphics, font, ClientQuestLog.secondsNow(entry.secondsLeft()), title.right(),
                title);
        int x = title.x() + ICON + 4;
        UiText.draw(graphics, font, entry.name(), x, UiText.textY(title), title.right() - timeWidth - 4 - x,
                UiTheme.TEXT);

        int y = title.bottom() + 2;
        double fraction = escort.length() <= 0 ? 0.0 : (double) escort.progress() / escort.length();
        UiDraw.bar(graphics, new UiRect(inner.x(), y, inner.width(), 3), fraction, UiTheme.WOOD_INPUT,
                UiTheme.GOLD);
        y += 3 + 3;
        Component blocks = Component.translatable(KEY + "blocks", Math.min(escort.progress(), escort.length()),
                escort.length());
        int heartsWidth = 0;
        if (escort.maxHealth() > 0) {
            Component hearts = Component.translatable(KEY + "merchant", hearts(escort));
            heartsWidth = UiText.drawRight(graphics, font, hearts, inner.right(), y, inner.width() / 2,
                    UiTheme.TEXT_MUTED);
        }
        UiText.draw(graphics, font, blocks, inner.x(), y, inner.width() - heartsWidth - 4, UiTheme.TEXT_MUTED);
        if (warning) {
            y += UiTheme.LINE + 2;
            Component text = Component.translatable(KEY + "too_far", escort.distance(), escort.maxDistance(),
                    UiText.clock(ClientQuestLog.secondsNow(escort.awaySeconds())));
            UiText.draw(graphics, font, text, inner.x(), y, inner.width(), UiTheme.TEXT_URGENT);
        }
    }

    /** The hearts of the merchant: full hearts in red, empty hearts in grey. */
    private static Component hearts(Escort escort) {
        int full = (int) Math.ceil((double) escort.health() * HEARTS / escort.maxHealth());
        full = Math.max(0, Math.min(HEARTS, full));
        return Component.literal("♥".repeat(full)).withColor(UiTheme.TEXT_BAD)
                .append(Component.literal("♡".repeat(HEARTS - full)).withColor(UiTheme.TEXT_FAINT));
    }

    /** The number of items with an id in the inventory of the player. */
    private static int count(Inventory inventory, String itemId) {
        ItemStack wanted = UiDraw.stack(itemId);
        if (wanted.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(wanted.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
