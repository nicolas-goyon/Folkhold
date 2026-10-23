package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.network.BankPillageRequestPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village bank. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BankGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos CHEST = new BlockPos(3, 1, 3);

    private BankGameTests() {
    }

    /**
     * The emeralds of the treasury in the bank chest during a test: they fit in one slot next to the bread. The
     * scene sets the treasury of the faction from this share and from the number of villages of the faction, which
     * changes with the other tests.
     */
    private static final int SHARE = 20;

    /**
     * @param treasury the treasury of the faction before the test: the scene puts it back at the end, because the
     *                 other tests use the same factions
     */
    private record Scene(VillageHeartBlockEntity heart, Settlement settlement, BankChestBlockEntity chest,
            ServerPlayer player, long treasury) {
        void remove(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            server.getPlayerList().remove(player);
            SettlementData data = SettlementData.get(server);
            data.addTreasury(settlement.factionId(), treasury - data.treasury(settlement.factionId()));
        }
    }

    /**
     * A heart with its settlement, a bank chest, only 10 bread in the stock, a treasury that puts {@link #SHARE}
     * emeralds in the chest, and a player next to the chest. The start stock does not stay: with a high start level,
     * its items fill the chest before the bread. The bank chests of earlier tests near the test area go away: in the
     * scan area of the heart, such a chest is a page of the bank, and the chest of the test then shows another part
     * of the stock.
     */
    private static Scene scene(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        TestSupport.removeEarlierBankChests(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(CHEST, ModBlocks.BANK_CHEST.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.getLayout().scan(helper.getLevel(), helper.absolutePos(HEART));
        // The heart registers itself at its next tick. The test needs it now.
        VillageHearts.register(helper.getLevel(), helper.absolutePos(HEART));
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        TestSupport.emptyStock(settlement);
        settlement.economy().stock().set("minecraft:bread", 10);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(settlement.factionId());
        data.addTreasury(settlement.factionId(), -treasury);
        for (int i = 0; i < 1000 && BankChests.emeraldShare(server, settlement) < SHARE; i++) {
            data.addTreasury(settlement.factionId(), SHARE);
        }
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos chestPos = helper.absolutePos(CHEST);
        player.setPos(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 1.5);
        return new Scene(heart, settlement, helper.getBlockEntity(CHEST), player, treasury);
    }

    /** A confirmed opening is a pillage: very negative reputation, alert, and the chest shows the stocks. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_pillage")
    public static void confirmedOpeningIsAPillage(GameTestHelper helper) {
        Scene scene = scene(helper);
        MinecraftServer server = helper.getLevel().getServer();
        FactionSpec faction = FactionData.get(server).byId(scene.settlement().factionId()).orElseThrow();

        BankChests.onConfirm(scene.player(), helper.absolutePos(CHEST));

        PlayerReputation reputation = ReputationData.get(server).get(scene.player().getUUID());
        int value = WorldConfig.BANK_PILLAGE_VALUE.get();
        boolean hasBread = scene.chest().container().countItem(Items.BREAD) == 10;
        boolean hasEmeralds = scene.chest().container().countItem(Items.EMERALD)
                == BankChests.emeraldShare(server, scene.settlement());
        AlertState alert = scene.heart().getAlertState();
        boolean pillaged = scene.heart().hasPillaged(scene.player());
        scene.remove(helper);

        helper.assertTrue(reputation.faction(faction) <= value, "The faction value must be " + value + " or lower");
        helper.assertTrue(reputation.village(faction, scene.settlement().id()) <= value,
                "The village value must be " + value + " or lower");
        helper.assertTrue(alert != AlertState.CALM, "The village must be in alert");
        helper.assertTrue(pillaged, "The heart must record the pillager");
        helper.assertTrue(hasBread, "The chest must show the 10 bread of the stock");
        helper.assertTrue(hasEmeralds, "The chest must show the emerald share of the treasury");
        helper.succeed();
    }

    /**
     * The confirmation request shows the village, the reputation of the player before and after the pillage, and
     * the garrison.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_request")
    public static void requestShowsTheReputationBeforeAndAfter(GameTestHelper helper) {
        Scene scene = scene(helper);
        MinecraftServer server = helper.getLevel().getServer();
        FactionSpec faction = FactionData.get(server).byId(scene.settlement().factionId()).orElseThrow();
        ReputationData.get(server).get(scene.player().getUUID()).setFaction(faction.id(), 300);
        ReputationData.get(server).get(scene.player().getUUID()).setVillage(scene.settlement().id(), 300);

        BankPillageRequestPayload request = BankChests.request(scene.player(), helper.absolutePos(CHEST),
                scene.heart(), scene.settlement());

        scene.remove(helper);
        helper.assertTrue(request.village().getString().equals(scene.settlement().name()),
                "The request must name the village");
        helper.assertTrue(request.valueBefore() == 300 && request.tierBefore().equals("friend"),
                "The reputation before must be the reputation now: " + request.valueBefore());
        helper.assertTrue(request.valueAfter() == WorldConfig.BANK_PILLAGE_VALUE.get()
                && request.tierAfter().equals(ReputationTier.of(WorldConfig.BANK_PILLAGE_VALUE.get()).serializedName()),
                "The reputation after must be the pillage value: " + request.valueAfter());
        helper.assertTrue(request.guards() >= 0 && request.archers() >= 0, "The garrison counts are not negative");
        helper.succeed();
    }

    /** The items that the player takes leave the stock and the treasury. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_take")
    public static void takenItemsLeaveTheStockAndTheTreasury(GameTestHelper helper) {
        Scene scene = scene(helper);
        MinecraftServer server = helper.getLevel().getServer();
        SettlementData data = SettlementData.get(server);
        long treasuryBefore = data.treasury(scene.settlement().factionId());
        BankChests.onConfirm(scene.player(), helper.absolutePos(CHEST));

        removeFromChest(scene.chest(), new ItemStack(Items.BREAD, 4));
        removeFromChest(scene.chest(), new ItemStack(Items.EMERALD, 5));
        BankChests.onClose(scene.chest(), scene.player());

        int bread = scene.settlement().economy().stock().count("minecraft:bread");
        long treasuryAfter = data.treasury(scene.settlement().factionId());
        scene.remove(helper);

        helper.assertTrue(bread == 6, "Expected 6 bread in the stock, not " + bread);
        helper.assertTrue(treasuryAfter == treasuryBefore - 5,
                "Expected " + (treasuryBefore - 5) + " emeralds in the treasury, not " + treasuryAfter);
        helper.succeed();
    }

    /**
     * A second player cannot open a chest that another player has open. The second player opens the chest after the
     * close of the first player, and sees the stock after the changes of the first player. The stock loses the
     * taken items one time.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_two_players")
    public static void secondOpenerWaitsForTheFirstClose(GameTestHelper helper) {
        Scene scene = scene(helper);
        ServerPlayer first = scene.player();
        ServerPlayer second = TestSupport.mockPlayer(helper);
        second.setGameMode(GameType.SURVIVAL);
        second.setPos(first.getX(), first.getY(), first.getZ());
        BlockPos chestPos = helper.absolutePos(CHEST);
        BankChests.onConfirm(first, chestPos);
        removeFromChest(scene.chest(), new ItemStack(Items.BREAD, 4));

        BankChests.onConfirm(second, chestPos);
        boolean secondRefused = !(second.containerMenu instanceof ChestMenu);
        int breadWhileOpen = scene.chest().container().countItem(Items.BREAD);
        first.closeContainer();
        int stockAfterFirst = scene.settlement().economy().stock().count("minecraft:bread");

        BankChests.onConfirm(second, chestPos);
        boolean secondOpened = second.containerMenu instanceof ChestMenu;
        int breadForSecond = scene.chest().container().countItem(Items.BREAD);
        second.closeContainer();
        int stockAfterSecond = scene.settlement().economy().stock().count("minecraft:bread");
        helper.getLevel().getServer().getPlayerList().remove(second);
        scene.remove(helper);

        helper.assertTrue(secondRefused, "The second player must not open the chest while the first has it open");
        helper.assertTrue(breadWhileOpen == 6, "The chest must keep the content of the first player, found "
                + breadWhileOpen + " bread");
        helper.assertTrue(stockAfterFirst == 6, "Expected 6 bread in the stock after the first close, not "
                + stockAfterFirst);
        helper.assertTrue(secondOpened, "The second player must open the chest after the first close");
        helper.assertTrue(breadForSecond == 6, "The second player must see 6 bread, found " + breadForSecond);
        helper.assertTrue(stockAfterSecond == 6, "Expected 6 bread in the stock after the second close, not "
                + stockAfterSecond);
        helper.succeed();
    }

    /** A player who leaves the game with the chest open: the items that the player took leave the stock. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_logout")
    public static void leavingPlayerSettlesTheChest(GameTestHelper helper) {
        Scene scene = scene(helper);
        BankChests.onConfirm(scene.player(), helper.absolutePos(CHEST));
        removeFromChest(scene.chest(), new ItemStack(Items.BREAD, 4));

        scene.remove(helper);

        int bread = scene.settlement().economy().stock().count("minecraft:bread");
        boolean empty = scene.chest().container().isEmpty() && scene.chest().snapshot().isEmpty();
        helper.assertTrue(bread == 6, "Expected 6 bread in the stock after the logout, not " + bread);
        helper.assertTrue(empty, "The chest must be empty after the logout");
        helper.assertTrue(scene.chest().viewer() == null, "The chest must forget the viewer after the logout");
        helper.succeed();
    }

    private static void removeFromChest(BankChestBlockEntity chest, ItemStack wanted) {
        int left = wanted.getCount();
        for (int slot = 0; slot < chest.container().getContainerSize() && left > 0; slot++) {
            ItemStack stack = chest.container().getItem(slot);
            if (stack.is(wanted.getItem())) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** TNT next to a bank chest does not break it. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_explosion")
    public static void explosionDoesNotBreakABankChest(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(CHEST, ModBlocks.BANK_CHEST.get());
        BlockPos pos = helper.absolutePos(CHEST);

        helper.getLevel().explode(null, pos.getX() + 1.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F,
                Level.ExplosionInteraction.TNT);

        helper.assertBlockPresent(ModBlocks.BANK_CHEST.get(), CHEST);
        helper.succeed();
    }

    /** The cause of an explosion is the player who made it. An explosion with no source has no cause. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "bank_explosion_cause")
    public static void explosionCauseIsThePlayer(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(12, 1, 12));

        Explosion byPlayer = helper.getLevel().explode(player, pos.getX(), pos.getY(), pos.getZ(), 0.5F,
                Level.ExplosionInteraction.NONE);
        Explosion bySomething = helper.getLevel().explode(null, pos.getX(), pos.getY(), pos.getZ(), 0.5F,
                Level.ExplosionInteraction.NONE);
        ServerPlayer cause = BankChests.causeOf(byPlayer);
        ServerPlayer noCause = BankChests.causeOf(bySomething);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(cause == player, "The player must be the cause of its explosion");
        helper.assertTrue(noCause == null, "An explosion with no source must have no cause");
        helper.succeed();
    }
}
