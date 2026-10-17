package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDialog;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiHoldButton;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiStrings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageConfirmPayload;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageRequestPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The confirmation dialog before the pillage of a bank chest: the village and its faction, the reputation of the
 * player before and after the pillage, and the consequences. The player holds the pillage button for one second.
 * The cancel button has the focus.
 */
public final class BankPillageScreen {
    private static final String KEY = "screen.folkhold_world.bank_pillage.";

    private BankPillageScreen() {
    }

    public static void open(BankPillageRequestPayload payload) {
        ReputationTier before = tier(payload.tierBefore());
        ReputationTier after = tier(payload.tierAfter());
        UiDialog.builder(null, Component.translatable(KEY + "title"))
                .parchment()
                .danger()
                .message(Component.translatable(KEY + "bank", payload.village(), payload.faction()))
                .comparison(chip(Component.translatable(KEY + "before"), before, payload.valueBefore()),
                        chip(Component.translatable(KEY + "after"), after, payload.valueAfter()))
                .bullet(Component.translatable(KEY + "faction", payload.faction()))
                .bullet(Component.translatable(KEY + "garrison", payload.guards(), payload.archers()))
                .bullet(Component.translatable(KEY + "attack"))
                .hold(UiHoldButton.DEFAULT_HOLD_MILLIS)
                .note(UiStrings.HOLD_HINT)
                .confirm(Component.translatable(KEY + "confirm"),
                        () -> PacketDistributor.sendToServer(new BankPillageConfirmPayload(payload.pos())))
                .open();
    }

    private static UiDialog.Chip chip(Component caption, ReputationTier tier, int value) {
        Component text = Component.literal(UiReputation.glyph(tier) + " ")
                .append(Component.translatable("folkhold_world.reputation_tier_title." + tier.serializedName()))
                .append(" " + value);
        return new UiDialog.Chip(caption, text, UiReputation.background(tier), UiReputation.text(tier));
    }

    private static ReputationTier tier(String name) {
        return ReputationTier.fromSerializedName(name).orElse(ReputationTier.NEUTRAL);
    }
}
