package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the destruction of a village. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuinsGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private RuinsGameTests() {
    }

    private static final BlockPos CHEST = new BlockPos(3, 1, 12);

    private record Village(Settlement settlement, CaptainEntity captain) {
    }

    private static Village village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        CaptainEntity captain = TestSupport.spawnUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        captain.setVillagePos(helper.absolutePos(HEART));
        return new Village(settlement, captain);
    }

    private static int claims(GameTestHelper helper, Settlement settlement) {
        return SettlementData.get(helper.getLevel().getServer()).claims().cellCount(settlement.id());
    }

    /** Kill the captain, then destroy the heart: the village becomes ruins and claims nothing. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_captain_first")
    public static void captainThenHeartMakesRuins(GameTestHelper helper) {
        Village village = village(helper);

        village.captain().kill();
        boolean activeAfterCaptain = village.settlement().isActive();
        helper.setBlock(HEART, Blocks.AIR);

        helper.assertTrue(activeAfterCaptain, "The village must stay active while its heart exists");
        helper.assertTrue(village.settlement().state() == SettlementState.RUINS, "The village must be ruins");
        helper.assertTrue(claims(helper, village.settlement()) == 0, "Ruins must claim no cell");
        helper.succeed();
    }

    /** Destroy the heart, then kill the captain: the village becomes ruins when the captain dies. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_heart_first")
    public static void heartThenCaptainMakesRuins(GameTestHelper helper) {
        Village village = village(helper);

        helper.setBlock(HEART, Blocks.AIR);
        boolean activeAfterHeart = village.settlement().isActive();
        village.captain().kill();

        helper.assertTrue(activeAfterHeart, "The village must stay active while its captain lives");
        helper.assertTrue(village.settlement().state() == SettlementState.RUINS, "The village must be ruins");
        helper.succeed();
    }

    /** Counts the items of the bank chest of the test. */
    private static int count(GameTestHelper helper, Item item) {
        BankChestBlockEntity chest = helper.getBlockEntity(CHEST);
        int total = 0;
        for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
            ItemStack stack = chest.container().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * A village with a bank chest, 10 bread in the stock and 100 emeralds in the treasury. The captain dies, then
     * the heart breaks: the village becomes ruins.
     */
    /**
     * @param village  the village
     * @param share    the emeralds of the treasury in the bank before the ruin
     * @param treasury the treasury of the faction before the test: each test puts it back at the end, because the
     *                 other tests use the same factions
     */
    private record RuinedBank(Village village, int share, long treasury) {
        void restoreTreasury(GameTestHelper helper) {
            SettlementData data = SettlementData.get(helper.getLevel().getServer());
            String faction = village.settlement().factionId();
            data.addTreasury(faction, treasury - data.treasury(faction));
        }
    }

    private static RuinedBank ruinedVillageWithBank(GameTestHelper helper) {
        Village village = village(helper);
        helper.setBlock(CHEST, ModBlocks.BANK_CHEST.get());
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        String faction = village.settlement().factionId();
        long before = data.treasury(faction);
        data.addTreasury(faction, 100 - before);
        village.settlement().economy().stock().set("minecraft:bread", 10);
        int share = BankChests.emeraldShare(helper.getLevel().getServer(), village.settlement());
        village.captain().kill();
        helper.setBlock(HEART, Blocks.AIR);
        return new RuinedBank(village, share, before);
    }

    /** The stock and the share of the treasury go into the bank chest. In ruins, the chest keeps its items. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_bank")
    public static void ruinsKeepTheBankContent(GameTestHelper helper) {
        RuinedBank ruined = ruinedVillageWithBank(helper);
        Village village = ruined.village();
        int share = ruined.share();
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        int bread = count(helper, Items.BREAD);
        int emeralds = count(helper, Items.EMERALD);
        int stock = village.settlement().economy().stock().count("minecraft:bread");
        long treasury = data.treasury(village.settlement().factionId());

        ServerPlayer player = TestSupport.mockPlayer(helper);
        BankChests.onClose(helper.getBlockEntity(CHEST), player);
        int afterClose = count(helper, Items.BREAD);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.setBlock(CHEST, Blocks.AIR);
        ruined.restoreTreasury(helper);

        helper.assertTrue(village.settlement().state() == SettlementState.RUINS, "The village must be ruins");
        helper.assertTrue(bread == 10 && stock == 0, "The 10 bread must go from the stock into the chest, found "
                + bread + " in the chest and " + stock + " in the stock");
        helper.assertTrue(emeralds == share && treasury == 100 - share, "Expected " + share
                + " emeralds in the chest, found " + emeralds + " (treasury " + treasury + ")");
        helper.assertTrue(afterClose == 10, "In ruins, the chest must keep its items when it closes");
        helper.assertItemEntityPresent(Items.BREAD, CHEST, 2.0);
        helper.killAllEntitiesOfClass(ItemEntity.class);
        helper.succeed();
    }

    /** Restore: the settlement is active, a heart of the same settlement is back, the bank items go back. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "ruins_restore")
    public static void restoreBringsBackHeartAndBank(GameTestHelper helper) {
        RuinedBank ruined = ruinedVillageWithBank(helper);
        Village village = ruined.village();
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        String faction = village.settlement().factionId();

        BlockPos heartPos = Settlements.restore(helper.getLevel(), village.settlement());

        boolean heart = helper.getLevel().getBlockEntity(heartPos) instanceof VillageHeartBlockEntity found
                && found.getVillageId().toString().equals(village.settlement().id());
        int stock = village.settlement().economy().stock().count("minecraft:bread");
        int inChest = count(helper, Items.BREAD) + count(helper, Items.EMERALD);
        long treasury = data.treasury(faction);
        helper.setBlock(CHEST, Blocks.AIR);
        ruined.restoreTreasury(helper);

        helper.assertTrue(village.settlement().isActive(), "The village must be active");
        helper.assertTrue(heartPos.equals(helper.absolutePos(HEART)) && heart,
                "A heart of the same settlement must be at the position of the heart");
        helper.assertTrue(stock == 10, "The bread must go back into the stock, found " + stock);
        helper.assertTrue(treasury == 100, "The emeralds must go back into the treasury, found " + treasury);
        helper.assertTrue(inChest == 0, "The chest must be empty");
        helper.succeed();
    }
}
