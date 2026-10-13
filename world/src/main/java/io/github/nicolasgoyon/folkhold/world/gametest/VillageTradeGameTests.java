package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupSystem;
import io.github.nicolasgoyon.folkhold.world.group.VillageTrade;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the trade between villages. The test settlements are far from the test area, so they claim no
 * territory near the other tests. Each test puts the treasuries and the relations back at the end. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageTradeGameTests {
    private static final int FAR_X = 22_000_000;
    private static final String BREAD = "minecraft:bread";

    private VillageTradeGameTests() {
    }

    /** Two factions that are not the same. */
    private static List<FactionSpec> twoFactions(MinecraftServer server) {
        List<FactionSpec> factions = FactionData.get(server).factions();
        return List.of(factions.get(0), factions.get(1));
    }

    /** An active settlement with an economy: 10 inhabitants, no jobs, some bread. */
    private static Settlement settlement(MinecraftServer server, String factionId, int x, int z, int bread) {
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, z >> 4, factionId,
                "Test market", x, z);
        settlement.economy().setPopulation(10);
        settlement.economy().stock().set(BREAD, bread);
        settlement.economy().setInitialized(true);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    private static void retire(MinecraftServer server, Settlement... settlements) {
        for (Settlement settlement : settlements) {
            settlement.setState(SettlementState.RUINS);
        }
        SettlementData.get(server).setDirty();
    }

    /**
     * A village with a surplus of bread sends a caravan to a village with a need. At the arrival, the buyer gets the
     * bread, the seller gets the payment, and the relation between the two factions goes up.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "trade_caravan")
    public static void tradeSendsACaravanAndPaysAtTheArrival(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<FactionSpec> factions = twoFactions(server);
        String sellerFaction = factions.get(0).id();
        String buyerFaction = factions.get(1).id();
        SettlementData settlements = SettlementData.get(server);
        Relations relations = FactionData.get(server).relations();
        int relationBefore = relations.get(sellerFaction, buyerFaction);
        long sellerTreasury = settlements.treasury(sellerFaction);
        long buyerTreasury = settlements.treasury(buyerFaction);
        relations.set(sellerFaction, buyerFaction, 0);
        Settlement seller = settlement(server, sellerFaction, FAR_X, 0, 500);
        Settlement buyer = settlement(server, buyerFaction, FAR_X + 600, 200, 0);

        List<MobileGroup> created = VillageTrade.trade(server, settlements.tickNumber(), List.of(seller, buyer));

        MobileGroup caravan = created.isEmpty() ? null : created.get(0);
        int cargo = caravan == null ? 0 : caravan.cargo().count(BREAD);
        int sellerStock = seller.economy().stock().count(BREAD);
        if (caravan != null) {
            // Only this caravan arrives: the other groups of the test world do not change the treasuries.
            MobileGroupSystem.deliver(server, caravan);
            VillageTrade.onArrival(server, caravan);
            MobileGroupData.get(server).groups().remove(caravan.id());
        }
        int buyerStock = buyer.economy().stock().count(BREAD);
        long sellerGain = settlements.treasury(sellerFaction) - sellerTreasury;
        long buyerLoss = buyerTreasury - settlements.treasury(buyerFaction);
        int relationAfter = relations.get(sellerFaction, buyerFaction);

        retire(server, seller, buyer);
        settlements.addTreasury(sellerFaction, sellerTreasury - settlements.treasury(sellerFaction));
        settlements.addTreasury(buyerFaction, buyerTreasury - settlements.treasury(buyerFaction));
        relations.set(sellerFaction, buyerFaction, relationBefore);
        FactionData.get(server).setDirty();

        helper.assertTrue(created.size() == 1, "The trade must make one caravan: " + created.size());
        helper.assertTrue(caravan.kind() == GroupKind.CARAVAN && caravan.originId().equals(seller.id())
                && caravan.destinationId().equals(buyer.id()) && caravan.factionId().equals(sellerFaction),
                "The caravan must go from the seller to the buyer, with the faction of the seller");
        helper.assertTrue(cargo > 0 && sellerStock == 500 - cargo,
                "The bread must leave the stock of the seller: " + cargo + ", " + sellerStock);
        helper.assertTrue(caravan.merchant().isPresent()
                && caravan.members().stream().filter(m -> m.role() == MemberRole.GUARD).count() >= 2,
                "The caravan must have a merchant and 2 or more guards");
        helper.assertTrue(buyerStock == cargo, "The buyer must get the bread: " + buyerStock);
        helper.assertTrue(caravan.payment() > 0 && sellerGain == caravan.payment()
                && buyerLoss == caravan.payment(),
                "The buyer must pay the seller: " + caravan.payment() + ", " + sellerGain + ", " + buyerLoss);
        helper.assertTrue(relationAfter == WorldConfig.TRADE_RELATION_GAIN.get(),
                "The relation must go up: " + relationAfter);
        helper.succeed();
    }

    /** Two factions at war do not trade. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "trade_war")
    public static void factionsAtWarDoNotTrade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<FactionSpec> factions = twoFactions(server);
        String sellerFaction = factions.get(0).id();
        String buyerFaction = factions.get(1).id();
        Relations relations = FactionData.get(server).relations();
        int relationBefore = relations.get(sellerFaction, buyerFaction);
        relations.set(sellerFaction, buyerFaction, Relations.MIN);
        Settlement seller = settlement(server, sellerFaction, FAR_X, 10_000, 500);
        Settlement buyer = settlement(server, buyerFaction, FAR_X + 600, 10_200, 0);

        List<MobileGroup> created = VillageTrade.trade(server, SettlementData.get(server).tickNumber(),
                List.of(seller, buyer));

        created.forEach(group -> MobileGroupData.get(server).groups().remove(group.id()));
        retire(server, seller, buyer);
        relations.set(sellerFaction, buyerFaction, relationBefore);
        helper.assertTrue(created.isEmpty(), "Factions at war must not trade");
        helper.assertTrue(seller.economy().stock().count(BREAD) == 500, "The seller must keep its bread");
        helper.succeed();
    }
}
