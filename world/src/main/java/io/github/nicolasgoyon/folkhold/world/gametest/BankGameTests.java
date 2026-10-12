package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestBlockEntity;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
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

    private record Scene(VillageHeartBlockEntity heart, Settlement settlement, BankChestBlockEntity chest,
            ServerPlayer player) {
        void remove(GameTestHelper helper) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** A heart with its settlement, a bank chest, 10 bread in the stock, and a player next to the chest. */
    private static Scene scene(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(CHEST, ModBlocks.BANK_CHEST.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.getLayout().scan(helper.getLevel(), helper.absolutePos(HEART));
        // The heart registers itself at its next tick. The test needs it now.
        VillageHearts.register(helper.getLevel(), helper.absolutePos(HEART));
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        settlement.economy().stock().set("minecraft:bread", 10);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos chestPos = helper.absolutePos(CHEST);
        player.setPos(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 1.5);
        return new Scene(heart, settlement, helper.getBlockEntity(CHEST), player);
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
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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
