package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.group.CaravanInteractions;
import io.github.nicolasgoyon.folkhold.world.group.CaravanStays;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the stay of a caravan at the trade place of its destination: the caravan yard of the market, or
 * the plaza of a hamlet. During the stay, the merchant sells the goods of the stock that no worker sells.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaravanMarketGameTests {
    private static final int SIZE = 32;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** The origin of the market piece: the street side (z = 0 of the piece) is to the north. */
    private static final BlockPos MARKET_ORIGIN = new BlockPos(14, 1, 18);
    /** The route of the caravans: to the north of the market. */
    private static final BlockPos START = new BlockPos(2, 1, 15);
    private static final ResourceLocation MARKET = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/market");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/homes");
    private static final String BAKER = "folkhold_world:baker";
    /** The treasury of the faction during the trade test. */
    private static final long TREASURY = 1000;

    private CaravanMarketGameTests() {
    }

    /** A village heart with its settlement. The end of the test removes it, also after a failure. */
    private static VillageHeartBlockEntity village(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_GROWTH, false);
        TestSupport.buildFloor(helper, SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        TestSupport.atTestEnd(helper, () -> {
            heart.getUnits().forEach(Entity::discard);
            heart.getCats().forEach(Entity::discard);
            VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
            VillageRepairData.get(helper.getLevel()).forget(heart.getBlockPos());
            settlement.setState(SettlementState.RUINS);
            SettlementData.get(helper.getLevel().getServer()).setDirty();
            helper.getLevel().setBlock(heart.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        return heart;
    }

    /** A traveling caravan to a settlement, with a merchant, a mule and a guard. Its entities stay. */
    private static MobileGroup caravan(GameTestHelper helper, Settlement destination) {
        BlockPos start = helper.absolutePos(START);
        Route route = new Route(List.of(new Route.Point(start.getX(), start.getZ()),
                new Route.Point(start.getX() + 3, start.getZ())));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", destination.id(), route);
        group.setState(GroupState.TRAVELING);
        group.cargo().set("minecraft:iron_sword", 2);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        ServerLevel level = helper.getLevel();
        MobileGroupData.get(level.getServer()).groups().add(group);
        GroupMaterializer.keep(group.id(), true);
        TestSupport.atTestEnd(helper, () -> {
            GroupMaterializer.keep(group.id(), false);
            GroupMaterializer.dematerialize(level, group, false);
            MobileGroupData.get(level.getServer()).groups().remove(group.id());
            group.members().forEach(member -> {
                Entity entity = level.getEntity(member.id());
                if (entity != null) {
                    entity.discard();
                }
            });
        });
        GroupMaterializer.materialize(level, group);
        return group;
    }

    /**
     * A caravan arrives at a village with a market. The merchant walks to the market, the caravan arrives near the
     * caravan yard, and the merchant stops in the yard. The stay sells the goods of the destination.
     */
    @GameTest(template = "arena_large", timeoutTicks = 560, batch = "caravan_market_yard")
    public static void caravanStopsInTheMarketYard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        VillageHeartBlockEntity heart = village(helper);
        BlockPos origin = helper.absolutePos(MARKET_ORIGIN);
        StructureTemplate template = level.getStructureManager().get(MARKET).orElseThrow();
        template.placeInWorld(level, origin, origin, GhostPlacement.settings(Rotation.NONE, Mirror.NONE)
                .addProcessor(JigsawReplacementProcessor.INSTANCE), level.getRandom(), Block.UPDATE_CLIENTS);
        heart.setRepairReference(new VillageReference(List.of(new VillageReference.Piece(MARKET, origin,
                Rotation.NONE)), List.of(), Optional.empty(), List.of(), Optional.of(HOMES)));
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());
        helper.assertTrue(heart.getLayout().markets().size() == 1, "The heart must find the market");
        BlockPos yard = origin.offset(2, 1, 2);
        CaravanStays.Stop stop = CaravanStays.stopOf(level, heart);
        helper.assertTrue(stop.market() && stop.pos().equals(yard), "The stop must be the caravan yard " + yard
                + ", found " + stop);
        Settlement destination = Settlements.of(server, heart).orElseThrow();
        MobileGroup group = caravan(helper, destination);
        Entity merchant = helper.getLevel().getEntity(group.members().get(0).id());
        helper.assertTrue(merchant instanceof CaravanMerchant, "The caravan must have its merchant");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GroupMaterializer.update(server);
                    helper.assertTrue(MobileGroupData.get(server).groups().byId(group.id()).isEmpty(),
                            "The caravan must arrive. Merchant at " + merchant.blockPosition());
                })
                .thenExecute(() -> {
                    CaravanMerchant stayer = (CaravanMerchant) merchant;
                    helper.assertTrue(destination.id().equals(stayer.staySettlement()),
                            "The merchant must stay at the destination");
                    helper.assertTrue(stayer.getRestrictCenter().equals(yard),
                            "The merchant must stay at the yard, found " + stayer.getRestrictCenter());
                })
                .thenWaitUntil(() -> helper.assertTrue(merchant.blockPosition().distSqr(yard)
                                <= CaravanStays.YARD_RADIUS * CaravanStays.YARD_RADIUS,
                        "The merchant must stop in the yard " + yard + ", found " + merchant.blockPosition()))
                .thenSucceed();
    }

    /** A caravan arrives at a hamlet with no market: the merchant stops at the plaza, near the heart. */
    @GameTest(template = "arena_large", timeoutTicks = 560, batch = "caravan_market_plaza")
    public static void caravanStopsAtThePlazaOfAHamlet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        VillageHeartBlockEntity heart = village(helper);
        BlockPos heartPos = heart.getBlockPos();
        CaravanStays.Stop stop = CaravanStays.stopOf(level, heart);
        helper.assertTrue(!stop.market() && stop.pos().equals(heartPos), "The stop must be the heart, found " + stop);
        Settlement destination = Settlements.of(server, heart).orElseThrow();
        MobileGroup group = caravan(helper, destination);
        Entity merchant = helper.getLevel().getEntity(group.members().get(0).id());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GroupMaterializer.update(server);
                    helper.assertTrue(MobileGroupData.get(server).groups().byId(group.id()).isEmpty(),
                            "The caravan must arrive. Merchant at " + merchant.blockPosition());
                })
                .thenExecute(() -> helper.assertTrue(merchant instanceof CaravanMerchant stayer
                                && destination.id().equals(stayer.staySettlement())
                                && stayer.getRestrictCenter().equals(heartPos),
                        "The merchant must stay at the plaza"))
                .thenWaitUntil(() -> helper.assertTrue(((CaravanMerchant) merchant).isWithinRestriction(),
                        "The merchant must stop near the heart, found " + merchant.blockPosition()))
                .thenSucceed();
    }

    /**
     * During the stay, the merchant sells the items of the stock that no active job sells: the swords of a village
     * with a baker, not the bread. A sale takes the item from the stock and puts the emeralds into the treasury.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "caravan_market_offers")
    public static void stayOffersTheGoodsThatNoJobSells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        settlement.economy().setPopulation(10);
        settlement.economy().setJobs(List.of(BAKER));
        TestSupport.emptyStock(settlement);
        settlement.economy().stock().set("minecraft:bread", 64);
        settlement.economy().stock().set("minecraft:iron_sword", 5);
        SettlementData data = SettlementData.get(server);
        String faction = settlement.factionId();
        long original = data.treasury(faction);
        TestSupport.atTestEnd(helper, () -> data.addTreasury(faction, original - data.treasury(faction)));
        data.addTreasury(faction, TREASURY - original);
        CaravanMerchant merchant = ModEntities.CARAVAN_MERCHANT.get().create(level);
        helper.assertTrue(merchant != null, "The merchant must exist");
        merchant.stay(settlement.id(), CaravanStays.stopOf(level, heart));
        ServerPlayer player = TestSupport.mockPlayer(helper);
        // A neutral player: the default reputation of some factions is hostile.
        ReputationData.get(server).get(player.getUUID()).setFaction(faction, 0);
        ReputationData.get(server).get(player.getUUID()).setVillage(settlement.id(), 0);

        CaravanInteractions.onEntityInteract(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND,
                merchant));
        List<Item> sold = merchant.getOffers().stream().map(offer -> offer.getResult().getItem()).toList();
        MerchantOffer sword = merchant.getOffers().stream().filter(offer -> offer.getResult().is(Items.IRON_SWORD))
                .findFirst().orElse(null);
        helper.assertTrue(sword != null, "The merchant must sell the swords that no job sells: " + sold);
        helper.assertFalse(sold.contains(Items.BREAD), "The merchant must not sell the bread of the baker: " + sold);
        helper.assertTrue(CaravanStays.offers(settlement, ReputationTier.NEUTRAL).size() == sold.size(),
                "The interaction must show the offers of the stay");
        sword.increaseUses();
        NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(player, sword, merchant));
        int stock = settlement.economy().stock().count("minecraft:iron_sword");
        long treasury = data.treasury(faction);
        merchant.discard();

        helper.assertTrue(stock == 4, "The sword must leave the stock, found " + stock);
        helper.assertTrue(treasury == TREASURY + sword.getCostA().getCount(),
                "The emeralds must go into the treasury, found " + treasury);
        helper.succeed();
    }
}
