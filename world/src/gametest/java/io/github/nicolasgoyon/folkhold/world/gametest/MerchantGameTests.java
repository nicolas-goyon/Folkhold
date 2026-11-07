package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
 * In-game tests for the villagers of the villages and the trades of the workers. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MerchantGameTests {
    /** The treasury of the faction in the test of a purchase: the workers can pay. */
    private static final long BUYER_TREASURY = 1_000L;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final String BAKER = "folkhold_world:baker";
    private static final String BLACKSMITH = "folkhold_world:blacksmith";
    private static final String MINER = "folkhold_world:miner";
    /** The test job of the GameTest data: it needs its building and shows the weaponsmith profession. */
    private static final String TEST_SMITH = "folkhold_world:test_smith";
    /** The tests call the checks of the heart this number of times to create all the villagers. */
    private static final int FILL_CHECKS = 30;

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

    /**
     * The heart creates the inhabitants of the village, in groups: one check creates
     * {@link VillageHeartBlockEntity#VILLAGER_GROUP} villagers at most, and the next checks create the others. No
     * villager is a merchant.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "merchant_spawn")
    public static void heartCreatesItsInhabitants(GameTestHelper helper) {
        Village village = village(helper);
        village.heart().ensureGarrison(helper.getLevel().getGameTime());
        int firstGroup = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos()).size();
        fill(helper, village);

        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos());
        int expectedInhabitants = VillageFolk.inhabitantCount(village.settlement());
        List<VillagerProfession> found = inhabitants.stream()
                .map(villager -> villager.getVillagerData().getProfession()).sorted(BY_ID).toList();
        List<VillagerProfession> wanted = VillageFolk.professions(village.settlement(), expectedInhabitants).stream()
                .sorted(BY_ID).toList();
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        helper.assertTrue(firstGroup > 0 && firstGroup <= VillageHeartBlockEntity.VILLAGER_GROUP,
                "The first check must create a group of villagers, not " + firstGroup);
        helper.assertTrue(!found.contains(ModProfessions.MERCHANT.get()), "No villager must be a merchant");
        helper.assertTrue(inhabitants.size() == expectedInhabitants && expectedInhabitants > 0,
                "Expected " + expectedInhabitants + " inhabitants, found " + inhabitants.size());
        helper.assertTrue(found.equals(wanted), "Expected the professions " + wanted + ", found " + found);
        helper.succeed();
    }

    /**
     * A village with a large population and a bed for each inhabitant has all its inhabitants. The workers show the job
     * of the settlement. Of the other inhabitants, some are builders and handymen, and the rest have the inhabitant
     * profession.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_folk_large")
    public static void largeVillageHasAllItsInhabitants(GameTestHelper helper) {
        Village village = village(helper);
        int population = 40;
        int jobSlots = 3;
        village.settlement().economy().setPopulation(population);
        village.settlement().economy().setJobs(Collections.nCopies(jobSlots, "folkhold_world:farmer"));
        // The hall gives places to the builders and the handymen. The beds give the other places.
        TestSupport.placeBeds(helper, population - VillageBeds.staff());
        fill(helper, village);

        BlockPos heartPos = village.heart().getBlockPos();
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), heartPos);
        long farmers = inhabitants.stream()
                .filter(villager -> villager.getVillagerData().getProfession() == VillagerProfession.FARMER).count();
        long others = inhabitants.stream().filter(villager -> villager.getVillagerData().getProfession()
                == ModProfessions.INHABITANT.get()).count();
        long builders = inhabitants.stream().filter(villager -> villager.getVillagerData().getProfession()
                == ModProfessions.BUILDER.get()).count();
        long handymen = inhabitants.stream().filter(villager -> villager.getVillagerData().getProfession()
                == ModProfessions.HANDYMAN.get()).count();
        int expected = population;
        int workers = VillageFolk.workerCount(jobSlots, population, WorldConfig.ECONOMY_WORKERS_PER_JOB.get());
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        helper.assertTrue(inhabitants.size() == expected,
                "Expected " + expected + " inhabitants, found " + inhabitants.size());
        int expectedBuilders = WorldConfig.BUILDERS.get();
        int expectedHandymen = WorldConfig.HANDYMEN.get();
        int expectedOthers = expected - workers - expectedBuilders - expectedHandymen;
        helper.assertTrue(farmers == workers, "Expected " + workers + " farmers, found " + farmers);
        helper.assertTrue(builders == expectedBuilders && handymen == expectedHandymen,
                "Expected " + expectedBuilders + " builders and " + expectedHandymen + " handymen, found " + builders
                        + " and " + handymen);
        helper.assertTrue(others == expectedOthers,
                "Expected " + expectedOthers + " inhabitants with no job, found " + others);
        helper.succeed();
    }

    /** Calls the checks of the heart until the village has all its villagers: the fill uses groups. */
    private static void fill(GameTestHelper helper, Village village) {
        long now = helper.getLevel().getGameTime();
        for (int i = 0; i < FILL_CHECKS; i++) {
            village.heart().ensureGarrison(now + i);
        }
    }

    /** The player buys bread from the baker: the bread leaves the stock and the emeralds go into the treasury. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_sell")
    public static void playerBuysFromTheStock(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().economy().setJobs(List.of(BAKER));
        village.settlement().economy().stock().set("minecraft:bread", 64);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(village.settlement().factionId());

        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                ModProfessions.BAKER.get());
        MerchantOffer bread = offers.stream().filter(offer -> offer.getResult().is(Items.BREAD)).findFirst()
                .orElse(null);
        helper.assertTrue(bread != null, "The baker must sell the bread of the stock");
        Merchants.applyTrade(server, village.settlement(), bread);

        int stock = village.settlement().economy().stock().count("minecraft:bread");
        long after = data.treasury(village.settlement().factionId());
        helper.assertTrue(stock == 64 - bread.getResult().getCount(), "The bread must leave the stock, not " + stock);
        helper.assertTrue(after == treasury + bread.getCostA().getCount(), "The emeralds must go into the treasury");
        helper.succeed();
    }

    /** The player sells wheat to the baker: the wheat goes into the stock, the treasury pays. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "merchant_buy")
    public static void playerSellsWhatTheVillageNeeds(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().setProgress(20, 0);
        village.settlement().economy().setJobs(List.of(BAKER));
        SettlementData data = SettlementData.get(server);
        String faction = village.settlement().factionId();
        // The other tests use the same factions: a known treasury, put back at the end.
        long original = data.treasury(faction);
        data.addTreasury(faction, BUYER_TREASURY - original);
        long treasury = data.treasury(faction);

        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                ModProfessions.BAKER.get());
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
        helper.assertTrue(purchase != null && purchase.getCostA().is(Items.WHEAT), "The baker must buy wheat");
        helper.assertTrue(stock == before + purchase.getCostA().getCount(), "The item must go into the stock");
        helper.assertTrue(after == treasury - purchase.getResult().getCount(), "The treasury must pay the emeralds");
        helper.succeed();
    }

    /** The offer of an item in the offers of a worker, or null. */
    private static MerchantOffer offerOf(MerchantOffers offers, Item item) {
        return offers.stream().filter(offer -> offer.getResult().is(item)).findFirst().orElse(null);
    }

    /** A miner of the village, with offers, that trades with a player: the player has the trade screen open. */
    private static Villager tradingMerchant(GameTestHelper helper, Village village, ServerPlayer player,
            MerchantOffers offers) {
        Villager merchant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                VillagerProfession.TOOLSMITH);
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
        village.settlement().economy().setJobs(List.of(MINER));
        village.settlement().economy().stock().set("minecraft:diamond", 1);
        SettlementData data = SettlementData.get(server);
        long treasury = data.treasury(village.settlement().factionId());
        ServerPlayer first = TestSupport.mockPlayer(helper);
        ServerPlayer second = TestSupport.mockPlayer(helper);
        Villager firstMerchant = tradingMerchant(helper, village, first, Merchants.offers(server,
                village.settlement(), ReputationTier.NEUTRAL, VillagerProfession.TOOLSMITH));
        Villager secondMerchant = tradingMerchant(helper, village, second, Merchants.offers(server,
                village.settlement(), ReputationTier.NEUTRAL, VillagerProfession.TOOLSMITH));
        MerchantOffer firstDiamond = offerOf(firstMerchant.getOffers(), Items.DIAMOND);
        MerchantOffer secondDiamond = offerOf(secondMerchant.getOffers(), Items.DIAMOND);
        helper.assertTrue(firstDiamond != null && secondDiamond != null, "The two miners must sell the diamond");

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
        TestSupport.buildFloor(helper);
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

    /** The jobs of the data pack show their professions. An unknown job shows the inhabitant profession. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_folk_jobs")
    public static void jobsShowTheirProfessions(GameTestHelper helper) {
        helper.assertTrue(VillageFolk.professionOf("folkhold_world:farmer") == VillagerProfession.FARMER,
                "The farmer job must show the farmer profession");
        helper.assertTrue(VillageFolk.professionOf("folkhold_world:lumberjack") == ModProfessions.LUMBERJACK.get(),
                "The lumberjack job must show the lumberjack profession");
        helper.assertTrue(VillageFolk.professionOf("unknown:job") == ModProfessions.INHABITANT.get(),
                "An unknown job must show the inhabitant profession");
        helper.succeed();
    }

    /**
     * When the jobs of the settlement change, the heart gives the new professions to the inhabitants. The builders and
     * the handymen come first, then the workers of the jobs.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_folk_reassign")
    public static void heartGivesTheJobProfessions(GameTestHelper helper) {
        Village village = village(helper);
        village.settlement().economy().setJobs(Collections.nCopies(10, "folkhold_world:farmer"));
        Villager inhabitant = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(),
                ModProfessions.INHABITANT.get());

        village.heart().ensureGarrison(helper.getLevel().getGameTime());

        VillagerProfession profession = inhabitant.getVillagerData().getProfession();
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos());
        List<VillagerProfession> working = List.of(ModProfessions.BUILDER.get(), ModProfessions.HANDYMAN.get(),
                VillagerProfession.FARMER);
        boolean allWorking = inhabitants.stream()
                .allMatch(villager -> working.contains(villager.getVillagerData().getProfession()));
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        helper.assertTrue(profession != ModProfessions.INHABITANT.get(),
                "The inhabitant must get a profession of the settlement, not " + profession);
        helper.assertTrue(allWorking, "Each inhabitant must be a builder, a handyman or a farmer");
        helper.succeed();
    }

    /**
     * A player can trade with the worker of an active job. A player cannot trade with an inhabitant with no job,
     * with the worker of a job that the village does not have, or with a villager with the merchant profession.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_folk_trade")
    public static void inhabitantDoesNotTrade(GameTestHelper helper) {
        Village village = village(helper);
        village.settlement().economy().setJobs(List.of(BAKER));
        ServerPlayer player = TestSupport.mockPlayer(helper);
        // A mock player has a new UUID and the start reputation of the faction: a hostile faction refuses the
        // trade. The test sets a neutral reputation, so the baker trades.
        ReputationData reputation = ReputationData.get(helper.getLevel().getServer());
        reputation.get(player.getUUID()).setFaction(village.settlement().factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.settlement().id(), 0);
        List<VillagerProfession> professions = List.of(ModProfessions.BAKER.get(), ModProfessions.INHABITANT.get(),
                VillagerProfession.FARMER, ModProfessions.MERCHANT.get());
        List<Boolean> canceled = professions.stream().map(profession -> {
            Villager villager = VillageFolk.spawn(helper.getLevel(), village.heart().getBlockPos(), profession);
            PlayerInteractEvent.EntityInteract event =
                    new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, villager);
            NeoForge.EVENT_BUS.post(event);
            villager.discard();
            return event.isCanceled();
        }).toList();

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(canceled.equals(List.of(false, true, true, true)),
                "Only the baker must trade: canceled " + canceled + " for " + professions);
        helper.succeed();
    }

    /**
     * A blacksmith sells only the outputs of the blacksmith job in stock, and buys only its inputs. A trade with
     * the blacksmith changes the stock and the treasury.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "worker_offers")
    public static void workerTradesTheGoodsOfItsJob(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().setProgress(20, 0);
        village.settlement().economy().setJobs(List.of(BLACKSMITH, BAKER));
        village.settlement().economy().stock().set("minecraft:iron_sword", 5);
        village.settlement().economy().stock().set("minecraft:bread", 64);
        SettlementData data = SettlementData.get(server);
        String faction = village.settlement().factionId();
        long original = data.treasury(faction);
        data.addTreasury(faction, BUYER_TREASURY - original);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        MerchantOffers offers = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                VillagerProfession.WEAPONSMITH);
        Villager smith = tradingMerchant(helper, village, player, offers);
        List<Item> sold = offers.stream().map(offer -> offer.getResult().getItem())
                .filter(item -> item != Items.EMERALD).toList();
        List<String> bought = offers.stream().filter(offer -> offer.getResult().is(Items.EMERALD))
                .map(offer -> BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem()).toString()).toList();
        List<String> inputs = Economies.jobs(village.settlement()).get(0).recipe().inputs().stream()
                .map(input -> input.item()).toList();
        MerchantOffer sword = offerOf(offers, Items.IRON_SWORD);
        if (sword != null) {
            sword.increaseUses();
            NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(player, sword, smith));
        }
        int stock = village.settlement().economy().stock().count("minecraft:iron_sword");
        long treasury = data.treasury(faction);

        smith.discard();
        server.getPlayerList().remove(player);
        data.addTreasury(faction, original - treasury);
        helper.assertTrue(sold.equals(List.of(Items.IRON_SWORD)), "The blacksmith must sell only swords: " + sold);
        helper.assertTrue(!bought.isEmpty() && inputs.containsAll(bought),
                "The blacksmith must buy only its inputs: " + bought);
        helper.assertTrue(sword != null && stock == 4, "The sword must leave the stock, found " + stock);
        helper.assertTrue(treasury == BUYER_TREASURY + sword.getCostA().getCount(),
                "The emeralds must go into the treasury, found " + treasury);
        helper.succeed();
    }

    /**
     * The worker of a job with no building offers nothing. A villager with the merchant profession offers nothing. A
     * village with no active job gets no prosperity share for the jobs.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "worker_inactive")
    public static void workerOfAnInactiveJobDoesNotTrade(GameTestHelper helper) {
        Village village = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        village.settlement().economy().setJobs(List.of(TEST_SMITH));
        village.settlement().economy().stock().set("minecraft:iron_sword", 5);
        village.settlement().economy().setInitialized(true);
        village.settlement().economy().setLastCycle(1.0, 1.0);
        boolean unknownEmpty = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                VillagerProfession.WEAPONSMITH).isEmpty();
        double activeProsperity = Economies.prosperity(server, village.settlement());
        village.settlement().setJobBuildings(Optional.of(Map.of()));
        double inactiveProsperity = Economies.prosperity(server, village.settlement());
        MerchantOffers inactive = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                VillagerProfession.WEAPONSMITH);
        MerchantOffers merchant = Merchants.offers(server, village.settlement(), ReputationTier.NEUTRAL,
                ModProfessions.MERCHANT.get());
        village.settlement().setJobBuildings(Optional.empty());

        helper.assertTrue(!unknownEmpty, "With unknown building counts, the smith must sell its swords");
        helper.assertTrue(inactive.isEmpty(), "The smith of an inactive job must offer nothing");
        helper.assertTrue(merchant.isEmpty(), "A villager with the merchant profession must offer nothing");
        helper.assertTrue(Math.abs(activeProsperity - inactiveProsperity - 0.3) < 1e-9,
                "No active job must remove the jobs share: " + activeProsperity + " -> " + inactiveProsperity);
        helper.succeed();
    }

    /** A villager with the merchant profession becomes an inhabitant at the check of the heart. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_folk_merchant")
    public static void merchantBecomesAnInhabitant(GameTestHelper helper) {
        Villager merchant = new Villager(EntityType.VILLAGER, helper.getLevel());
        merchant.setVillagerData(merchant.getVillagerData().setProfession(ModProfessions.MERCHANT.get()));

        List<VillagerProfession> missing = VillageFolk.assign(List.of(merchant), List.of());

        helper.assertTrue(missing.isEmpty(), "No profession must be missing");
        helper.assertTrue(merchant.getVillagerData().getProfession() == ModProfessions.INHABITANT.get(),
                "The merchant must become an inhabitant, not " + merchant.getVillagerData().getProfession());
        helper.succeed();
    }
}
