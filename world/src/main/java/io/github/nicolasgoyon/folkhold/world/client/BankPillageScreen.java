package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.BankPillageConfirmPayload;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The confirmation screen before the pillage of a bank chest. */
public final class BankPillageScreen {

    private BankPillageScreen() {
    }

    public static void open(BankPillageRequestPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(null);
            if (accepted) {
                PacketDistributor.sendToServer(new BankPillageConfirmPayload(payload.pos()));
            }
        }, Component.translatable("screen.folkhold_world.bank_pillage.title"),
                Component.translatable("screen.folkhold_world.bank_pillage.message")));
    }
}
