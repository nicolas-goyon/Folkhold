package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiKeyValueList;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiScreen;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTabs;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageHeartPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageHeartPayload.Service;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen of the village heart: the player first, then the village (see {@link VillageHeartPayload}).
 *
 * <ul>
 *   <li>Header: banner, village name, faction and culture, level badge, alert badge.</li>
 *   <li>Player part, on parchment: the welcome for the tier of the player, the reputation (chip, bar, village and
 *       faction values), "Here you can" (quests with a link to the quest board, trade, shelter, inn), and an
 *       advice. A hostile player sees the welcome in a red band.</li>
 *   <li>Village part, on wood: one tab for each section, with label-value rows and meters. The left and right
 *       arrow keys change the tab. The screen keeps the last tab for the next opening.</li>
 *   <li>A small panel shows the player part as a short list, and buttons for the village sections. A section opens
 *       in the same panel, with a back button.</li>
 * </ul>
 */
public class VillageHeartScreen extends UiScreen {
    private static final String KEY = "screen.folkhold_world.village_heart.";
    private static final int COLUMN_WIDTH = 184;
    private static final int TALL_CARD = 52;
    private static final int SHORT_CARD = 19;
    private static final int CHIP_AREA = 100;
    private static final int BAND_COLOR = 0xFF5A1E18;
    private static final int ADVICE_BACKGROUND = 0xFFF1DCD3;
    private static final int ADVICE_TEXT = 0xFF5A1E18;
    private static final int ICON = 11;

    /** One service on the current layout, with its rectangle. */
    private record Card(Service service, UiRect rect) {
    }

    private static int lastTab;

    private final VillageHeartPayload heart;
    private int tab;
    private boolean detailsView;
    private final List<Card> cards = new ArrayList<>();
    private UiRect playerRect = new UiRect(0, 0, 0, 0);
    private UiRect overviewRect = new UiRect(0, 0, 0, 0);

    public VillageHeartScreen(VillageHeartPayload heart) {
        super(heart.title());
        this.heart = heart;
        this.tab = heart.sections().isEmpty() ? 0 : Mth.clamp(lastTab, 0, heart.sections().size() - 1);
    }

    /** Opens the screen. Called by the network handler on the client thread. */
    public static void open(VillageHeartPayload heart) {
        Minecraft.getInstance().setScreen(new VillageHeartScreen(heart));
    }

    private ReputationTier tier() {
        return ReputationTier.fromSerializedName(heart.playerTier()).orElse(ReputationTier.NEUTRAL);
    }

    private boolean hostile() {
        return !heart.services().isEmpty() && tier().isHostile();
    }

    // ------------------------------------------------------------------ header

    @Override
    @Nullable
    protected Component subtitle() {
        boolean hasFaction = !heart.faction().getString().isEmpty();
        boolean hasCulture = !heart.culture().getString().isEmpty();
        if (hasFaction && hasCulture) {
            return Component.translatable(KEY + "subtitle", heart.faction(), heart.culture());
        }
        return hasFaction ? heart.faction() : null;
    }

    @Override
    protected int bannerColor() {
        return heart.bannerColor();
    }

    @Override
    protected List<Badge> badges() {
        List<Badge> badges = new ArrayList<>();
        if (heart.level() > 0) {
            badges.add(Badge.of(Component.translatable(KEY + "level_badge", heart.level(), heart.tier()),
                    UiTone.GOLD));
        }
        AlertState alert = AlertState.fromSerializedName(heart.alert()).orElse(AlertState.CALM);
        badges.add(Badge.of(Component.translatable(KEY + "alert." + alert.serializedName()),
                AdminScreen.alertTone(alert)));
        return badges;
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void initBody(UiRect body) {
        cards.clear();
        playerRect = new UiRect(body.x(), body.y(), 0, 0);
        overviewRect = playerRect;
        if (isCompact()) {
            if (detailsView) {
                initDetails(body.inset(UiTheme.PADDING, 4), true);
            } else {
                initOverview(body);
            }
            return;
        }
        playerRect = body.inset(1, 0).beforeRight(COLUMN_WIDTH, 0);
        initPlayer(playerRect);
        initDetails(body.inset(1, 0).rightPart(COLUMN_WIDTH).inset(0, 0), false);
    }

    /** The tabs and the rows of the village sections. */
    private void initDetails(UiRect area, boolean withBack) {
        UiRect top = area.top(UiTheme.CONTROL);
        UiRect tabsRect = top;
        if (withBack) {
            Component back = Component.translatable(KEY + "back");
            int backWidth = font.width(back) + 12;
            addRenderableWidget(UiButton.normal(top.left(backWidth), back, button -> {
                detailsView = false;
                rebuildWidgets();
            }));
            tabsRect = top.afterLeft(backWidth, UiTheme.GAP);
        }
        List<Section> sections = heart.sections();
        if (!sections.isEmpty()) {
            UiTabs tabs = addRenderableWidget(new UiTabs(tabsRect, sections.stream().map(Section::heading).toList(),
                    tab, index -> {
                        tab = index;
                        lastTab = index;
                        rebuildWidgets();
                    }).setPadding(3));
            setSectionTabs(tabs);
        }
        UiKeyValueList rows = addRenderableWidget(new UiKeyValueList(area.belowTop(UiTheme.CONTROL, 4)
                .inset(withBack ? 0 : UiTheme.PADDING, 0)));
        if (!sections.isEmpty()) {
            rows.setItems(InfoRows.of(sections.get(tab).rows()));
        }
    }

    /** The cards of the services, in two columns. */
    private void initPlayer(UiRect area) {
        UiRect inner = area.inset(7, 6);
        int y = inner.y() + 36 + (hostile() ? 4 : 0);
        List<Service> services = heart.services();
        for (int i = 0; i < services.size(); i += 2) {
            Service left = services.get(i);
            Service right = i + 1 < services.size() ? services.get(i + 1) : null;
            int height = Math.max(cardHeight(left), right == null ? 0 : cardHeight(right));
            UiRect row = new UiRect(inner.x(), y, inner.width(), height);
            addCard(left, row.column(0, 2, 4));
            if (right != null) {
                addCard(right, row.column(1, 2, 4));
            }
            y += height + 4;
        }
    }

    private static int cardHeight(Service service) {
        boolean extra = !service.details().getString().isEmpty() || !service.items().isEmpty()
                || service.action() != VillageHeartPayload.ACTION_NONE;
        return extra ? TALL_CARD : SHORT_CARD;
    }

    private void addCard(Service service, UiRect rect) {
        cards.add(new Card(service, rect));
        if (service.action() == VillageHeartPayload.ACTION_QUEST_BOARD
                && service.status() != VillageHeartPayload.REFUSED) {
            Component label = Component.translatable(KEY + "quest_board");
            UiRect button = new UiRect(rect.x() + 5, rect.bottom() - 5 - UiTheme.CONTROL,
                    Math.min(font.width(label) + 14, rect.width() - 10), UiTheme.CONTROL);
            addRenderableWidget(UiButton.primary(button, label, this::openQuestBoard).onParchment());
        }
    }

    /** The short layout: the services as a list, and buttons for the village sections. */
    private void initOverview(UiRect body) {
        overviewRect = body;
        UiRect inner = body.inset(UiTheme.PADDING, 0);
        int rowsTop = inner.y() + 32 + 12;
        boolean questButton = false;
        for (int i = 0; i < heart.services().size(); i++) {
            Service service = heart.services().get(i);
            UiRect row = new UiRect(inner.x(), rowsTop + i * 14, inner.width(), 14);
            cards.add(new Card(service, row));
            questButton |= service.action() == VillageHeartPayload.ACTION_QUEST_BOARD
                    && service.status() != VillageHeartPayload.REFUSED;
        }
        int after = rowsTop + heart.services().size() * 14 + 4;
        if (questButton) {
            Component label = Component.translatable(KEY + "quest_board");
            addRenderableWidget(UiButton.primary(new UiRect(inner.x(), after, font.width(label) + 14,
                    UiTheme.CONTROL), label, this::openQuestBoard));
        }
        List<Section> sections = heart.sections();
        if (sections.isEmpty()) {
            return;
        }
        UiRect buttons = new UiRect(inner.x(), body.bottom() - 4 - UiTheme.CONTROL, inner.width(), UiTheme.CONTROL);
        for (int i = 0; i < sections.size(); i++) {
            int index = i;
            addRenderableWidget(UiButton.normal(buttons.column(i, sections.size(), 2), sections.get(i).heading(),
                    button -> {
                        tab = index;
                        lastTab = index;
                        detailsView = true;
                        rebuildWidgets();
                    }));
        }
    }

    private void openQuestBoard(UiButton button) {
        button.setPending(true);
        PacketDistributor.sendToServer(new QuestBoardRequestPayload(heart.settlementId()));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBody(GuiGraphics graphics, UiRect body, int mouseX, int mouseY, float partialTick) {
        if (playerRect.width() > 0) {
            renderPlayer(graphics);
            UiDraw.vLine(graphics, playerRect.right(), body.y(), body.height(), UiTheme.GILT);
        } else if (overviewRect.width() > 0) {
            renderOverview(graphics);
        }
    }

    private void renderPlayer(GuiGraphics graphics) {
        UiDraw.fill(graphics, playerRect, UiTheme.PARCHMENT);
        UiRect inner = playerRect.inset(7, 6);
        if (heart.services().isEmpty()) {
            UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_faction"), inner.top(20),
                    UiTheme.INK_MUTED);
            return;
        }
        ReputationTier tier = tier();
        if (hostile()) {
            UiRect band = new UiRect(playerRect.x(), playerRect.y(), playerRect.width(), 36);
            UiDraw.fill(graphics, band, BAND_COLOR);
            UiRect text = band.inset(7, 5).beforeRight(CHIP_AREA, 4);
            UiText.draw(graphics, font, heart.welcome(), text.x(), text.y(), text.width(), UiTheme.DANGER_TEXT);
            UiText.draw(graphics, font, Component.translatable(KEY + "hostile_warning"), text.x(), text.y() + 12,
                    text.width(), 0xFFF2B8AC);
            renderChip(graphics, tier, band.inset(7, 5).rightPart(CHIP_AREA).x(), band.y() + 5, false);
        } else {
            UiRect welcome = new UiRect(inner.x(), inner.y(), inner.width() - CHIP_AREA - 4, 2 * UiTheme.LINE);
            UiText.drawWrapped(graphics, font, heart.welcome(), welcome, UiTheme.INK);
            renderChip(graphics, tier, inner.right() - CHIP_AREA, inner.y(), true);
            Component line = Component.translatable(KEY + "reputation_line", heart.villageValue(),
                    heart.villageWeight(), heart.factionValue(), 100 - heart.villageWeight());
            UiText.drawRight(graphics, font, line, inner.right(), inner.y() + 21, inner.width(), UiTheme.INK_MUTED);
        }
        int labelY = inner.y() + 34 + (hostile() ? 4 : 0) - UiTheme.LINE - 1;
        if (!hostile()) {
            graphics.drawString(font, Component.translatable(KEY + "here_you_can"), inner.x(), labelY,
                    UiTheme.INK_MUTED, false);
        }
        for (Card card : cards) {
            renderCard(graphics, card);
        }
        renderAdvice(graphics, inner);
    }

    /** The reputation chip of the player, with a bar below on parchment. */
    private void renderChip(GuiGraphics graphics, ReputationTier tier, int x, int y, boolean withBar) {
        Component text = Component.translatable(KEY + "chip", FactionVisuals.tierTitle(tier), heart.reputation());
        int width = Math.min(font.width(text) + 8, CHIP_AREA);
        int chipX = x + CHIP_AREA - width;
        UiRect chip = new UiRect(chipX, y, width, UiTheme.BADGE);
        UiDraw.fill(graphics, chip, UiReputation.background(tier));
        UiText.drawCentered(graphics, font, text, chip.inset(3, 0), UiReputation.text(tier));
        if (withBar) {
            double fraction = (heart.reputation() + 1000) / 2000.0;
            UiDraw.bar(graphics, new UiRect(x, y + UiTheme.BADGE + 3, CHIP_AREA, 3), fraction, UiTheme.PARCHMENT_EDGE,
                    UiReputation.background(tier));
        }
    }

    private void renderCard(GuiGraphics graphics, Card card) {
        Service service = card.service();
        UiRect rect = card.rect();
        boolean later = service.status() == VillageHeartPayload.LATER;
        UiDraw.box(graphics, rect, later ? UiTheme.PARCHMENT_OFF : UiTheme.PARCHMENT_LIGHT,
                later ? UiTheme.PARCHMENT_OFF_EDGE : UiTheme.PARCHMENT_EDGE, later);
        UiRect top = new UiRect(rect.x() + 5, rect.y() + 5, rect.width() - 10, UiTheme.LINE);
        int stateWidth = UiText.drawRight(graphics, font, service.state(), top.right(), top.y(),
                top.width() * 3 / 5, UiTheme.INK);
        UiText.draw(graphics, font, service.name(), top.x(), top.y(), top.width() - stateWidth - 4,
                later ? UiTheme.INK_FAINT : UiTheme.INK);
        if (rect.height() <= SHORT_CARD) {
            return;
        }
        int y = top.y() + 12;
        if (!service.details().getString().isEmpty()) {
            int lines = service.items().isEmpty() && service.action() == VillageHeartPayload.ACTION_NONE ? 3 : 1;
            List<FormattedCharSequence> wrapped = UiText.wrap(font, service.details(), top.width(), lines);
            for (FormattedCharSequence line : wrapped) {
                graphics.drawString(font, line, top.x(), y, UiTheme.INK_MUTED, false);
                y += UiTheme.LINE;
            }
            y += 2;
        }
        if (!service.items().isEmpty()) {
            Component label = Component.translatable(KEY + "looks_for");
            graphics.drawString(font, label, top.x(), y + 1, UiTheme.INK_MUTED, false);
            int x = top.x() + font.width(label) + 3;
            for (String item : service.items()) {
                if (x + ICON > top.right()) {
                    break;
                }
                UiDraw.box(graphics, new UiRect(x, y, ICON, ICON), 0xFF8B8B8B, 0xFF4E4E4E, false);
                UiDraw.item(graphics, UiDraw.stack(item), x + 1, y + 1, 9);
                x += ICON + 2;
            }
        }
    }

    private void renderAdvice(GuiGraphics graphics, UiRect inner) {
        if (heart.advice().getString().isEmpty()) {
            return;
        }
        UiRect box = inner.bottomPart(24);
        if (heart.adviceDanger()) {
            UiDraw.box(graphics, box, ADVICE_BACKGROUND, UiTheme.WAX, false);
            UiDraw.mark(graphics, box.x() + 5, box.y() + 8, 7, UiTheme.WAX);
            UiText.drawWrapped(graphics, font, heart.advice(), new UiRect(box.x() + 16, box.y() + 3,
                    box.width() - 21, 2 * UiTheme.LINE), ADVICE_TEXT);
        } else {
            UiText.drawWrapped(graphics, font, heart.advice(), box.inset(0, 3), UiTheme.INK_MUTED);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        UiRect inner = overviewRect.inset(UiTheme.PADDING, 0);
        if (heart.services().isEmpty()) {
            UiText.drawCentered(graphics, font, Component.translatable(KEY + "no_faction"),
                    new UiRect(inner.x(), inner.y() + 8, inner.width(), 12), UiTheme.TEXT_MUTED);
        } else {
            ReputationTier tier = tier();
            UiRect band = new UiRect(overviewRect.x() + 1, overviewRect.y(), overviewRect.width() - 2, 32);
            boolean hostile = hostile();
            UiDraw.fill(graphics, band, hostile ? BAND_COLOR : UiTheme.PARCHMENT);
            UiRect text = band.inset(UiTheme.PADDING, 4).beforeRight(CHIP_AREA, 4);
            UiText.draw(graphics, font, heart.welcome(), text.x(), text.y(), text.width(),
                    hostile ? UiTheme.DANGER_TEXT : UiTheme.INK);
            Component second = hostile ? Component.translatable(KEY + "hostile_warning")
                    : Component.translatable(KEY + "reputation_line", heart.villageValue(), heart.villageWeight(),
                            heart.factionValue(), 100 - heart.villageWeight());
            UiText.draw(graphics, font, second, text.x(), text.y() + 12, text.width(),
                    hostile ? 0xFFF2B8AC : UiTheme.INK_MUTED);
            renderChip(graphics, tier, band.inset(UiTheme.PADDING, 4).rightPart(CHIP_AREA).x(), band.y() + 9, false);
            graphics.drawString(font, Component.translatable(KEY + "here_for_you"), inner.x(), band.bottom() + 3,
                    UiTheme.TEXT_MUTED, false);
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.get(i);
                UiRect row = card.rect();
                if (i % 2 == 0) {
                    UiDraw.fill(graphics, row, UiTheme.WOOD_ROW);
                }
                Service service = card.service();
                int color = switch (service.status()) {
                    case VillageHeartPayload.AVAILABLE -> UiTheme.TEXT_GOOD;
                    case VillageHeartPayload.REFUSED -> UiTheme.TEXT_BAD;
                    case VillageHeartPayload.LATER -> UiTheme.TEXT_FAINT;
                    default -> UiTheme.TEXT_MUTED;
                };
                int y = UiText.textY(row);
                int stateWidth = UiText.drawRight(graphics, font, Component.literal(service.state().getString()),
                        row.right() - 3, y, row.width() * 3 / 5, color);
                UiText.draw(graphics, font, service.name(), row.x() + 3, y, row.width() - stateWidth - 10,
                        UiTheme.TEXT);
            }
            int adviceY = cards.isEmpty() ? band.bottom() + 16 : cards.get(cards.size() - 1).rect().bottom() + 4;
            boolean questButton = heart.services().stream().anyMatch(service -> service.action()
                    == VillageHeartPayload.ACTION_QUEST_BOARD && service.status() != VillageHeartPayload.REFUSED);
            if (questButton) {
                adviceY += UiTheme.CONTROL + 4;
            }
            UiText.drawWrapped(graphics, font, heart.advice(), new UiRect(inner.x(), adviceY, inner.width(),
                    2 * UiTheme.LINE), 0xFFD8CFBD);
        }
        if (!heart.sections().isEmpty()) {
            UiRect bottom = new UiRect(overviewRect.x() + 1, overviewRect.bottom() - 4 - UiTheme.CONTROL - 13,
                    overviewRect.width() - 2, 13 + UiTheme.CONTROL + 4);
            UiDraw.hLine(graphics, bottom.x(), bottom.y(), bottom.width(), UiTheme.WOOD_RAISED);
            graphics.drawString(font, Component.translatable(KEY + "details_title"), inner.x(), bottom.y() + 3,
                    UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (playerRect.width() <= 0) {
            return;
        }
        for (Card card : cards) {
            if (card.service().items().isEmpty() || card.rect().height() <= SHORT_CARD) {
                continue;
            }
            int x = card.rect().x() + 5 + font.width(Component.translatable(KEY + "looks_for")) + 3;
            int y = itemsY(card);
            for (String item : card.service().items()) {
                UiRect icon = new UiRect(x, y, ICON, ICON);
                if (icon.contains(mouseX, mouseY)) {
                    ItemStack stack = UiDraw.stack(item);
                    if (!stack.isEmpty()) {
                        graphics.renderTooltip(font, stack, mouseX, mouseY);
                    }
                    return;
                }
                x += ICON + 2;
            }
        }
    }

    /** The Y of the item icons of a card: the same computation as {@link #renderCard}. */
    private int itemsY(Card card) {
        Service service = card.service();
        int y = card.rect().y() + 5 + 12;
        if (!service.details().getString().isEmpty()) {
            y += UiTheme.LINE + 2;
        }
        return y;
    }
}
