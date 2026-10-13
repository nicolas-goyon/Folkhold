package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiKeyValueList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTabs;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * A screen of information that the server sent (see {@link VillageInfoPayload}): one tab for each section, and the
 * label-value rows of the selected section. For example the caravans and quests overview of the admin screen.
 *
 * <p>The screen keeps the last tab for the next opening. When it opens from another Folkhold screen (the admin
 * screen), its back button opens this screen again.
 */
public class InfoScreen extends UiScreen {
    private static int lastTab;

    private final VillageInfoPayload info;
    private int tab;

    public InfoScreen(VillageInfoPayload info) {
        super(info.title());
        this.info = info;
        this.tab = info.sections().isEmpty() ? 0 : Mth.clamp(lastTab, 0, info.sections().size() - 1);
    }

    /** Opens the screen. Called by the network handler on the client thread. */
    public static void open(VillageInfoPayload info) {
        Minecraft minecraft = Minecraft.getInstance();
        InfoScreen screen = new InfoScreen(info);
        if (minecraft.screen instanceof UiScreen current && !(current instanceof InfoScreen)) {
            screen.setPrevious(current);
        }
        minecraft.setScreen(screen);
    }

    @Override
    protected void initBody(UiRect body) {
        UiRect inner = body.inset(UiTheme.PADDING, 4);
        UiRect list = inner;
        if (!info.sections().isEmpty()) {
            UiTabs tabs = addRenderableWidget(new UiTabs(inner.top(UiTheme.CONTROL),
                    info.sections().stream().map(Section::heading).toList(), tab, index -> {
                        tab = index;
                        lastTab = index;
                        rebuildWidgets();
                    }));
            setSectionTabs(tabs);
            list = inner.belowTop(UiTheme.CONTROL, 4);
        }
        UiKeyValueList rows = addRenderableWidget(new UiKeyValueList(list));
        if (!info.sections().isEmpty()) {
            rows.setItems(InfoRows.of(info.sections().get(tab).rows()));
        }
    }
}
