package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.journal.JournalData;
import io.github.nicolasgoyon.folkhold.world.journal.Journals;
import io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the journal of the players. See {@link TestSupport} for the templates and the helpers. */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private JournalGameTests() {
    }

    /**
     * The territory of a village discovers it, and the village message is a visit. The journal of the player has the
     * village and its faction.
     */
    @GameTest(template = "arena", batch = "journal_visit")
    public static void territoryDiscoversAndVillageVisits(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        JournalData data = JournalData.get(server);

        Journals.update(player, Optional.of(settlement), Optional.empty());
        JournalData.Visit discovered = data.visits(player.getUUID()).get(settlement.id());
        Journals.update(player, Optional.empty(), Optional.of(settlement.id()));
        JournalData.Visit visited = data.visits(player.getUUID()).get(settlement.id());
        JournalPayload payload = Journals.payload(player);
        server.getPlayerList().remove(player);

        helper.assertTrue(discovered != null && !discovered.visited(), "The territory must discover the village");
        helper.assertTrue(visited != null && visited.visited() && visited.discovered() == discovered.discovered(),
                "The village message must be a visit, with the same day of discovery");
        helper.assertTrue(payload.villages().size() == 1 && payload.villages().get(0).id().equals(settlement.id())
                && payload.villages().get(0).visited(), "The journal must have the visited village");
        helper.assertTrue(payload.factions().size() == 1
                && payload.factions().get(0).id().equals(settlement.factionId())
                && payload.factions().get(0).villages() == 1, "The journal must have the faction of the village");
        helper.succeed();
    }

    /**
     * The map of a player has the territory of a discovered village, with the color of its faction. Before the
     * discovery, the map does not have the village.
     */
    @GameTest(template = "arena", batch = "journal_map")
    public static void mapHasTheDiscoveredTerritory(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        ServerPlayer player = TestSupport.mockPlayer(helper);

        TerritoryMapPayload before = TerritoryMaps.payload(player);
        Journals.update(player, Optional.of(settlement), Optional.empty());
        TerritoryMapPayload after = TerritoryMaps.payload(player);
        int color = FactionData.get(server).byId(settlement.factionId()).orElseThrow().color();
        server.getPlayerList().remove(player);

        helper.assertTrue(before.villages().stream().noneMatch(village -> village.id().equals(settlement.id())),
                "The map must not have the village before the discovery");
        Optional<TerritoryMapPayload.Village> village = after.villages().stream()
                .filter(found -> found.id().equals(settlement.id())).findFirst();
        helper.assertTrue(village.isPresent(), "The map must have the discovered village");
        helper.assertTrue(village.get().name().equals(settlement.name()) && village.get().color() == color
                && village.get().claimant().radius() > 0, "The village must have its name, its color and its claim: "
                        + village.get());
        helper.assertTrue(after.others().stream().noneMatch(other -> other.id().equals(settlement.id())),
                "The discovered village is not in the other settlements");
        helper.succeed();
    }

    /** A new id of a settlement keeps its place in the journals. */
    @GameTest(template = "empty", batch = "journal_rename")
    public static void renameKeepsTheVisit(GameTestHelper helper) {
        JournalData data = JournalData.get(helper.getLevel().getServer());
        UUID player = UUID.randomUUID();
        data.discover(player, "journal-old", 3);
        data.visit(player, "journal-old", 5);

        data.renameSettlement("journal-old", "journal-new");

        JournalData.Visit visit = data.visits(player).get("journal-new");
        helper.assertTrue(!data.visits(player).containsKey("journal-old"), "The old id must go away");
        helper.assertTrue(visit != null && visit.discovered() == 3 && visit.lastVisit() == 5,
                "The new id must keep the days: " + visit);
        helper.succeed();
    }
}
