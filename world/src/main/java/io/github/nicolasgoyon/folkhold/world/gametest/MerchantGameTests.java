package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the merchants of the villages. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MerchantGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private MerchantGameTests() {
    }

    private record Village(VillageHeartBlockEntity heart, Settlement settlement) {
    }

    private static Village village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        settlement.economy().setPopulation(10);
        return new Village(heart, settlement);
    }

    /** The heart creates the merchants of the village: villagers with the merchant profession. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "merchant_spawn")
    public static void heartCreatesItsMerchants(GameTestHelper helper) {
        Village village = village(helper);
        village.heart().ensureGarrison(helper.getLevel().getGameTime());

        List<Villager> merchants = Merchants.merchantsOf(helper.getLevel(), village.heart().getBlockPos());
        boolean professions = merchants.stream()
                .allMatch(villager -> villager.getVillagerData().getProfession() == ModProfessions.MERCHANT.get());
        int expected = Merchants.merchantCount(village.settlement());
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        merchants.forEach(Entity::discard);

        helper.assertTrue(merchants.size() == expected, "Expected " + expected + " merchants, found " + merchants.size());
        helper.assertTrue(professions, "Each merchant must have the merchant profession");
        helper.succeed();
    }

    /** The player buys bread: the bread leaves the stock and the emeralds go into the treasury. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_sell")
    public static void playerBuysFromTheStock(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().economy().stock().set("minecraft:bread", 64);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(village.settlement().factionId());

        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL);
        MerchantOffer bread = offers.stream().filter(offer -> offer.getResult().is(Items.BREAD)).findFirst()
                .orElse(null);
        helper.assertTrue(bread != null, "The merchant must sell the bread of the stock");
        Merchants.applyTrade(server, village.settlement(), bread);

        int stock = village.settlement().economy().stock().count("minecraft:bread");
        long after = data.treasury(village.settlement().factionId());
        helper.assertTrue(stock == 64 - bread.getResult().getCount(), "The bread must leave the stock, not " + stock);
        helper.assertTrue(after == treasury + bread.getCostA().getCount(), "The emeralds must go into the treasury");
        helper.succeed();
    }

    /** The player sells an item that the village needs: the item goes into the stock, the treasury pays. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_buy")
    public static void playerSellsWhatTheVillageNeeds(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().setProgress(20, 0);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(village.settlement().factionId());

        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL);
        MerchantOffer purchase = offers.stream().filter(offer -> offer.getResult().is(Items.EMERALD)).findFirst()
                .orElse(null);
        helper.assertTrue(purchase != null, "The merchant must buy an item that the village needs");
        String item = BuiltInRegistries.ITEM.getKey(purchase.getCostA().getItem()).toString();
        int before = village.settlement().economy().stock().count(item);
        Merchants.applyTrade(server, village.settlement(), purchase);

        int stock = village.settlement().economy().stock().count(item);
        long after = data.treasury(village.settlement().factionId());
        helper.assertTrue(stock == before + purchase.getCostA().getCount(), "The item must go into the stock");
        helper.assertTrue(after == treasury - purchase.getResult().getCount(), "The treasury must pay the emeralds");
        helper.succeed();
    }

    /** A hostile player cannot trade. A neutral player can trade. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_reputation")
    public static void hostilePlayerCannotTrade(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(village.settlement().factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.settlement().id(), 0);
        boolean neutral = Merchants.canTrade(server, player, village.heart());
        reputation.get(player.getUUID()).setFaction(village.settlement().factionId(), -700);
        reputation.get(player.getUUID()).setVillage(village.settlement().id(), -700);
        boolean hostile = Merchants.canTrade(server, player, village.heart());
        server.getPlayerList().remove(player);

        helper.assertTrue(neutral, "A neutral player must trade");
        helper.assertTrue(!hostile, "A hostile player must not trade");
        helper.succeed();
    }
}
