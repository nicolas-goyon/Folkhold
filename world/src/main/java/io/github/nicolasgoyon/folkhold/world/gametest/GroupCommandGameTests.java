package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.GroupOverview;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the commands of the caravans and the quests, and for the overview of the admin screen. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroupCommandGameTests {
    private static final int FAR_X = 25_000_000;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private GroupCommandGameTests() {
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static Settlement market(MinecraftServer server, int x, int bread) {
        String faction = FactionData.get(server).factions().get(0).id();
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, 0, faction, "Commandton", x, 0);
        settlement.economy().setPopulation(10);
        settlement.economy().stock().set("minecraft:bread", bread);
        settlement.economy().setInitialized(true);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** The caravan commands make a caravan, show it in the overview, and remove it. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "commands_caravan")
    public static void caravanCommandsSpawnShowAndRemove(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement from = market(server, FAR_X, 100);
        Settlement to = market(server, FAR_X + 500, 0);

        run(server, "folkhold caravan spawn " + from.id() + " " + to.id());
        Optional<MobileGroup> caravan = MobileGroupData.get(server).groups().bySettlement(from.id()).stream()
                .findFirst();
        VillageInfoPayload overview = GroupOverview.build(server);
        String shortId = caravan.map(group -> group.id().substring(0, 8)).orElse("");
        boolean listed = caravan.isPresent() && overview.sections().get(0).rows().stream()
                .anyMatch(row -> row.label().getContents() instanceof TranslatableContents contents
                        && Arrays.asList(contents.getArgs()).contains(shortId));
        caravan.ifPresent(group -> run(server, "folkhold caravan remove " + group.id()));
        boolean removed = caravan.isPresent()
                && MobileGroupData.get(server).groups().byId(caravan.get().id()).isEmpty();

        from.setState(SettlementState.RUINS);
        to.setState(SettlementState.RUINS);
        SettlementData.get(server).setDirty();
        helper.assertTrue(caravan.isPresent(), "The spawn command must make a caravan");
        helper.assertTrue(caravan.get().destinationId().equals(to.id())
                && caravan.get().cargo().count("minecraft:bread") == 32
                && from.economy().stock().count("minecraft:bread") == 68,
                "The caravan must carry 32 bread of the origin to the destination");
        helper.assertTrue(overview.sections().size() == 3 && listed, "The overview must list the caravan");
        helper.assertTrue(removed, "The remove command must remove the caravan");
        helper.succeed();
    }

    /** The quest commands give a delivery to a player and complete it. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "commands_quest")
    public static void questCommandsGiveAndComplete(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.retireEarlierSettlements(helper);
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement village = Settlements.of(server, heart).orElseThrow();
        village.economy().setPopulation(10);
        village.economy().setInitialized(true);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        BlockPos pos = helper.absolutePos(HEART.east(2));
        player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
        long treasury = SettlementData.get(server).treasury(village.factionId());

        run(server, "folkhold quest give " + player.getGameProfile().getName() + " delivery");
        List<Quest> active = QuestData.get(server).quests().active(player.getUUID());
        Quest quest = active.isEmpty() ? null : active.get(0);
        if (quest != null) {
            run(server, "folkhold quest complete " + quest.id());
        }

        server.getPlayerList().remove(player);
        SettlementData settlements = SettlementData.get(server);
        settlements.addTreasury(village.factionId(), treasury - settlements.treasury(village.factionId()));
        QuestData.get(server).quests().removeFinished();
        helper.assertTrue(quest != null && quest.kind() == QuestKind.DELIVERY
                && quest.settlementId().equals(village.id()), "The player must get a delivery of the village");
        helper.assertTrue(quest.state() == QuestState.COMPLETED, "The complete command must complete the quest");
        helper.succeed();
    }

    /** The overview of the admin screen has the three tabs, also with no group. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "commands_overview")
    public static void overviewHasThreeTabs(GameTestHelper helper) {
        VillageInfoPayload overview = GroupOverview.build(helper.getLevel().getServer());
        helper.assertTrue(overview.sections().size() == 3, "The overview must have 3 tabs");
        helper.assertTrue(overview.sections().get(1).rows().stream().noneMatch(row -> row.label() == null),
                "Each row must have a label");
        helper.succeed();
    }
}
