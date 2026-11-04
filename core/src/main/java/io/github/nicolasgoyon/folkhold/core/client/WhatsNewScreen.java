package io.github.nicolasgoyon.folkhold.core.client;

import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Category;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Entry;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog.Release;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTabs;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The "What's new" screen: the player-facing changes of the last versions, one tab for each version, grouped by
 * category. The texts are in the language of the client, or in English when the entry has no text in this language.
 *
 * <p>The close button and Escape open the previous screen again (the title screen or the mod list).
 */
public class WhatsNewScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_core.whats_new.";

    private final List<Release> versions;
    @Nullable
    private final Screen parent;
    private int tab;

    public WhatsNewScreen(List<Release> versions, @Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.versions = List.copyOf(versions);
        this.parent = parent;
    }

    @Override
    protected int preferredWidth() {
        return 380;
    }

    @Override
    @Nullable
    protected Component subtitle() {
        return versions.isEmpty() ? null : Component.translatable(KEY + "subtitle", versions.get(0).version());
    }

    @Override
    protected void initBody(UiRect body) {
        UiRect inner = body.inset(UiTheme.PADDING, 4);
        UiRect list = inner;
        if (versions.size() > 1) {
            UiTabs tabs = addRenderableWidget(new UiTabs(inner.top(UiTheme.CONTROL),
                    versions.stream().map(release -> (Component) Component.literal(release.version())).toList(), tab,
                    index -> {
                        tab = index;
                        rebuildWidgets();
                    }));
            setSectionTabs(tabs);
            list = inner.belowTop(UiTheme.CONTROL, 4);
        }
        Lines lines = addRenderableWidget(new Lines(list));
        lines.setEmptyText(Component.translatable(KEY + "empty"));
        if (!versions.isEmpty()) {
            lines.setItems(lines(versions.get(tab), list.width() - UiTheme.SCROLL_BAR - 12));
        }
    }

    /** The lines of a version: a heading for each category, then its entries, wrapped to the width. */
    private List<Line> lines(Release release, int width) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        List<Line> result = new ArrayList<>();
        for (Category category : Category.values()) {
            List<Entry> entries = release.entries(category);
            if (entries.isEmpty()) {
                continue;
            }
            result.add(new Line(Component.translatable(KEY + "category." + category.id()).getVisualOrderText(),
                    true, false));
            for (Entry entry : entries) {
                List<FormattedCharSequence> wrapped = UiText.wrap(font, Component.literal(entry.text(language)),
                        width, 20);
                for (int i = 0; i < wrapped.size(); i++) {
                    result.add(new Line(wrapped.get(i), false, i == 0));
                }
            }
        }
        return result;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** One line of the list: the heading of a category, or a line of an entry (the first line has a bullet). */
    private record Line(FormattedCharSequence text, boolean heading, boolean first) {
    }

    private static final class Lines extends UiList<Line> {
        Lines(UiRect rect) {
            super(rect);
        }

        @Override
        protected int rowHeight(Line line, int index) {
            return line.heading() ? UiTheme.ROW + (index == 0 ? 0 : 4) : UiTheme.LINE + (line.first() ? 2 : 0);
        }

        @Override
        protected void renderRow(GuiGraphics graphics, Line line, int index, UiRect rect, boolean hovered,
                boolean selected, int mouseX, int mouseY) {
            Font font = Minecraft.getInstance().font;
            int y = rect.bottom() - UiTheme.LINE;
            if (line.heading()) {
                y = rect.bottom() - UiTheme.ROW + 2;
                graphics.drawString(font, line.text(), rect.x() + 3, y, UiTheme.GOLD, false);
                UiDraw.hLine(graphics, rect.x() + 3, rect.bottom() - 1, rect.width() - 6, UiTheme.GILT_DARK);
                return;
            }
            if (line.first()) {
                graphics.drawString(font, "•", rect.x() + 4, y, UiTheme.GILT, false);
            }
            graphics.drawString(font, line.text(), rect.x() + 12, y, UiTheme.TEXT, false);
        }
    }
}
