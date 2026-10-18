package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.TerritoryMessages;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.NoticePayload;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import io.github.nicolasgoyon.folkhold.world.village.VillageInfo;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the payloads of the server. A mock player (see {@link TestSupport#mockPlayer}) has no channel
 * for the payloads of the mod, like a vanilla client.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos CHEST = new BlockPos(10, 1, 8);

    private NetworkGameTests() {
    }

    /** A player with no channel for a payload gets nothing, and the senders fall back or stay silent. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "network")
    public static void playerWithNoChannelGetsNoPayload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(CHEST, ModBlocks.BANK_CHEST.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        ServerPlayer player = TestSupport.mockPlayer(helper);

        try {
            boolean sent = ModNetwork.send(player,
                    new NoticePayload(Component.literal("test"), Optional.empty(), "info"));
            helper.assertFalse(sent, "A mock player has no channel");
            Notices.send(player, Component.literal("test"), null, Notices.Tone.INFO);
            ModNetwork.send(player, VillageInfo.build(heart, player));
            AdminActions.sendList(player);
            AdminActions.handle(player, new AdminActionPayload("details", settlement.id(), 0));
            QuestBoards.open(player, settlement);
            QuestBoards.sendLog(player);
            BankChests.onUse(player, helper.absolutePos(CHEST));
            TerritoryMessages.onPlayerTick(new PlayerTickEvent.Post(player));
        } finally {
            server.getPlayerList().remove(player);
            settlement.setState(SettlementState.RUINS);
            SettlementData.get(server).setDirty();
        }
        helper.succeed();
    }
}
