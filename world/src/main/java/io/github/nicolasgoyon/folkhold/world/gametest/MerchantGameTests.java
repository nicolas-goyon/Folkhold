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
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the merchants of the villages. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MerchantGameTests {
    /** The treasury of the faction in the test of a purchase: the merchants can pay. */
    private static final long BUYER_TREASURY = 1_000L;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private static final Comparator<VillagerProfession> BY_ID =
            Comparator.comparing(profession -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString());

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
        TestSupport.emptyStock(settlement);
        return new Village(heart, settlement);
    }

    /** The heart creates the merchants and the inhabitants of the village. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "merchant_spawn")
    public static void heartCreatesItsMerchants(GameTestHelper helper) {
        Village village = village(helper);
        village.heart().ensureGarrison(helper.getLevel().getGameTime());

        List<Villager> merchants = Merchants.merchantsOf(helper.getLevel(), village.heart().getBlockPos());
        boolean professions = merchants.stream()
                .allMatch(villager -> villager.getVillagerData().getProfession() == ModProfessions.MERCHANT.get());
        int expected = Merchants.merchantCount(village.settlement());
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos());
        int expectedInhabitants = VillageFolk.inhabitantCount(village.settlement());
        List<VillagerProfession> found = inhabitants.stream()
                .map(villager -> villager.getVillagerData().getProfession()).sorted(BY_ID).toList();
        List<VillagerProfession> wanted = VillageFolk.professions(village.settlement(), expectedInhabitants).stream()
                .sorted(BY_ID).toList();
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        merchants.forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        helper.assertTrue(merchants.size() == expected, "Expected " + expected + " merchants, found " + merchants.size());
        helper.assertTrue(professions, "Each merchant must have the merchant profession");
        helper.assertTrue(inhabitants.size() == expectedInhabitants && expectedInhabitants > 0,
                "Expected " + expectedInhabitants + " inhabitants, found " + inhabitants.size());
        helper.assertTrue(found.equals(wanted), "Expected the professions " + wanted + ", found " + found);
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
        String faction = village.settlement().factionId();
        // The other tests use the same factions: a known treasury, put back at the end.
        long original = data.treasury(faction);
        data.addTreasury(faction, BUYER_TREASURY - original);
        long treasury = data.treasury(faction);

        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL);
        MerchantOffer purchase = offers.stream().filter(offer -> offer.getResult().is(Items.EMERALD)).findFirst()
                .orElse(null);
        String item = purchase == null ? "" : BuiltInRegistries.ITEM.getKey(purchase.getCostA().getItem()).toString();
        int before = village.settlement().economy().stock().count(item);
        if (purchase != null) {
            Merchants.applyTrade(server, village.settlement(), purchase);
        }

        int stock = village.settlement().economy().stock().count(item);
        long after = data.treasury(faction);
        data.addTreasury(faction, original - data.treasury(faction));
        helper.assertTrue(purchase != null, "The merchant must buy an item that the village needs");
        helper.assertTrue(stock == before + purchase.getCostA().getCount(), "The item must go into the stock");
        helper.assertTrue(after == treasury - purchase.getResult().getCount(), "The treasury must pay the emeralds");
        helper.succeed();
    }

    /** The offer of an item in the offers of a merchant, or null. */
    private static MerchantOffer offerOf(MerchantOffers offers, Item item) {
        return offers.stream().filter(offer -> offer.getResult().is(item)).findFirst().orElse(null);
    }

    /** A merchant of the village, with offers, that trades with a player: the player has the trade screen open. */
    private static Villager tradingMerchant(GameTestHelper helper, Village village, ServerPlayer player,
            MerchantOffers offers) {
        Villager merchant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                ModProfessions.MERCHANT.get());
        merchant.setOffers(offers);
        merchant.setTradingPlayer(player);
        merchant.openTradingScreen(player, merchant.getDisplayName(), 1);
        return merchant;
    }

    /**
     * Two players have the trade screen open on the same stock with one diamond. The first trade takes the
     * diamond. The second trade does not apply: the stock stays at zero and the treasury gets the emeralds one
     * time. After the first trade, the screen of the second player shows the offer as sold out.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_last_item")
    public static void onlyOneTradeTakesTheLastItem(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().economy().stock().set("minecraft:diamond", 1);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(village.settlement().factionId());
        ServerPlayer first = TestSupport.mockPlayer(helper);
        ServerPlayer second = TestSupport.mockPlayer(helper);
        Villager firstMerchant = tradingMerchant(helper, village, first,
                Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL));
        Villager secondMerchant = tradingMerchant(helper, village, second,
                Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL));
        MerchantOffer firstDiamond = offerOf(firstMerchant.getOffers(), Items.DIAMOND);
        MerchantOffer secondDiamond = offerOf(secondMerchant.getOffers(), Items.DIAMOND);
        helper.assertTrue(firstDiamond != null && secondDiamond != null, "The two merchants must sell the diamond");

        firstDiamond.increaseUses();
        NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(first, firstDiamond, firstMerchant));
        int stockAfterFirst = village.settlement().economy().stock().count("minecraft:diamond");
        boolean secondSoldOut = offerOf(secondMerchant.getOffers(), Items.DIAMOND).isOutOfStock();
        secondDiamond.increaseUses();
        NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(second, secondDiamond, secondMerchant));
        int stockAfterSecond = village.settlement().economy().stock().count("minecraft:diamond");
        long treasuryAfter = data.treasury(village.settlement().factionId());

        firstMerchant.discard();
        secondMerchant.discard();
        server.getPlayerList().remove(first);
        server.getPlayerList().remove(second);
        helper.assertTrue(stockAfterFirst == 0, "The first trade must take the diamond, found " + stockAfterFirst);
        helper.assertTrue(secondSoldOut, "The screen of the second player must show the diamond as sold out");
        helper.assertTrue(stockAfterSecond == 0, "The stock must stay at zero, found " + stockAfterSecond);
        helper.assertTrue(treasuryAfter == treasury + firstDiamond.getCostA().getCount(),
                "The treasury must get the emeralds of one trade, found " + (treasuryAfter - treasury));
        helper.succeed();
    }

    /** A canceled trade: the result goes back from the hand of the player, and the player gets the emeralds. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_cancel")
    public static void canceledTradeGivesThePaymentBack(GameTestHelper helper) {
        Village village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 3), Optional.empty(),
                new ItemStack(Items.DIAMOND, 2), 1, 0, 0.05F);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 2));

        Merchants.cancel(player, offer);

        boolean handEmpty = player.containerMenu.getCarried().isEmpty();
        int emeralds = player.getInventory().countItem(Items.EMERALD);
        int diamonds = player.getInventory().countItem(Items.DIAMOND);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(handEmpty, "The result must leave the hand of the player");
        helper.assertTrue(emeralds == 3, "The player must get the 3 emeralds back, found " + emeralds);
        helper.assertTrue(diamonds == 0, "The player must not keep the diamonds, found " + diamonds);
        helper.succeed();
    }

    /** A hostile player cannot trade. A neutral player can trade. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_reputation")
    public static void hostilePlayerCannotTrade(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestSupport.mockPlayer(helper);
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

    /** A dead villager of the village removes 1 from the population. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_folk_death")
    public static void deadVillagerLowersThePopulation(GameTestHelper helper) {
        Village village = village(helper);
        Villager inhabitant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                ModProfessions.INHABITANT.get());
        int before = village.settlement().economy().population();

        inhabitant.kill();

        int after = village.settlement().economy().population();
        helper.assertTrue(after == before - 1, "The population must be " + (before - 1) + ", not " + after);
        helper.succeed();
    }

    /** Two villagers of a faction village do not breed. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_folk_breed")
    public static void villageFolkDoesNotBreed(GameTestHelper helper) {
        Village village = village(helper);
        Villager a = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(), ModProfessions.INHABITANT.get());
        Villager b = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(), ModProfessions.INHABITANT.get());
        BabyEntitySpawnEvent event = new BabyEntitySpawnEvent(a, b, null);

        NeoForge.EVENT_BUS.post(event);

        a.discard();
        b.discard();
        helper.assertTrue(event.isCanceled(), "The birth must be canceled");
        helper.succeed();
    }

    /** The inhabitants show the jobs in proportion to the workers, the jobs with the most workers first. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_folk_jobs")
    public static void inhabitantsShowTheJobsInProportion(GameTestHelper helper) {
        List<String> jobs = List.of("folkhold_world:miner", "folkhold_world:farmer", "folkhold_world:farmer",
                "folkhold_world:farmer");

        List<String> two = VillageFolk.pickJobs(jobs, 2);
        List<String> four = VillageFolk.pickJobs(jobs, 4);

        helper.assertTrue(two.equals(List.of("folkhold_world:farmer", "folkhold_world:miner")),
                "Expected 1 farmer and 1 miner, found " + two);
        helper.assertTrue(four.equals(List.of("folkhold_world:farmer", "folkhold_world:farmer",
                "folkhold_world:farmer", "folkhold_world:miner")), "Expected 3 farmers and 1 miner, found " + four);
        helper.assertTrue(VillageFolk.pickJobs(List.of(), 3).isEmpty(), "No workers must give no jobs");
        helper.assertTrue(VillageFolk.professionOf("folkhold_world:farmer") == VillagerProfession.FARMER,
                "The farmer job must show the farmer profession");
        helper.assertTrue(VillageFolk.professionOf("folkhold_world:lumberjack") == ModProfessions.LUMBERJACK.get(),
                "The lumberjack job must show the lumberjack profession");
        helper.assertTrue(VillageFolk.professionOf("unknown:job") == ModProfessions.INHABITANT.get(),
                "An unknown job must show the inhabitant profession");
        helper.succeed();
    }

    /** When the jobs of the settlement change, the heart gives the new professions to the inhabitants. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_folk_reassign")
    public static void heartGivesTheJobProfessions(GameTestHelper helper) {
        Village village = village(helper);
        village.settlement().economy().setJobs(Collections.nCopies(10, "folkhold_world:farmer"));
        Villager inhabitant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                ModProfessions.INHABITANT.get());

        village.heart().ensureGarrison(helper.getLevel().getGameTime());

        VillagerProfession profession = inhabitant.getVillagerData().getProfession();
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos());
        boolean allFarmers = inhabitants.stream()
                .allMatch(villager -> villager.getVillagerData().getProfession() == VillagerProfession.FARMER);
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), village.heart().getBlockPos()).forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        helper.assertTrue(profession == VillagerProfession.FARMER,
                "The inhabitant must become a farmer, not " + profession);
        helper.assertTrue(allFarmers, "Each inhabitant must be a farmer");
        helper.succeed();
    }

    /** A player cannot trade with an inhabitant. A player can trade with a merchant. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_folk_trade")
    public static void inhabitantDoesNotTrade(GameTestHelper helper) {
        Village village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        // The mock player can keep the reputation of an earlier test: a neutral player can trade with a merchant.
        ReputationData reputation = ReputationData.get(helper.getLevel().getServer());
        reputation.get(player.getUUID()).setFaction(village.settlement().factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.settlement().id(), 0);
        Villager inhabitant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                VillagerProfession.FARMER);
        Villager merchant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                ModProfessions.MERCHANT.get());

        PlayerInteractEvent.EntityInteract withInhabitant =
                new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, inhabitant);
        PlayerInteractEvent.EntityInteract withMerchant =
                new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, merchant);
        NeoForge.EVENT_BUS.post(withInhabitant);
        NeoForge.EVENT_BUS.post(withMerchant);

        inhabitant.discard();
        merchant.discard();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(withInhabitant.isCanceled(), "The interaction with an inhabitant must be canceled");
        helper.assertTrue(!withMerchant.isCanceled(), "The interaction with a merchant must not be canceled");
        helper.succeed();
    }
}
