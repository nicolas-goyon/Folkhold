package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MarkerShapes;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.journal.CartofoldMaps;
import io.github.nicolasgoyon.folkhold.world.journal.Journals;
import io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for Cartofold, a required dependency of the world mod: its survey maps move and update at the
 * cartography table. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CartofoldGameTests {
    private CartofoldGameTests() {
    }

    /** Cartofold is loaded, with its survey map. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "cartofold")
    public static void cartofoldIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("cartofold"), "Cartofold is loaded");
        ResourceLocation surveyMap = ResourceLocation.fromNamespaceAndPath("cartofold", "survey_map");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(surveyMap), "The survey map is registered");
        helper.succeed();
    }

    /**
     * After the discovery of a village, the player has its territory and a ring marker on the layers of Cartofold.
     * A check with no change does not make a new layer.
     */
    @GameTest(template = "arena", batch = "cartofold_layers")
    public static void discoverySendsTheLayers(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos heartPos = new BlockPos(8, 1, 8);
        helper.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(heartPos);
        heart.ensureSettlement();
        MinecraftServer server = helper.getLevel().getServer();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        ServerPlayer player = TestSupport.mockPlayer(helper);

        TerritoryMaps.update(player);
        boolean before = CartofoldMaps.sent(player, CartofoldMaps.VILLAGES).map(LayerContent::markers)
                .orElse(List.of()).stream().anyMatch(marker -> marker.id().equals(settlement.id()));
        Journals.update(player, Optional.of(settlement), Optional.empty());
        LayerContent villages = CartofoldMaps.sent(player, CartofoldMaps.VILLAGES).orElseThrow();
        LayerContent territories = CartofoldMaps.sent(player, CartofoldMaps.TERRITORIES).orElseThrow();
        TerritoryMaps.update(player);
        boolean same = CartofoldMaps.sent(player, CartofoldMaps.VILLAGES).orElseThrow() == villages
                && CartofoldMaps.sent(player, CartofoldMaps.TERRITORIES).orElseThrow() == territories;

        helper.assertFalse(before, "The layer must not have the village before the discovery");
        Optional<MapMarker> marker = villages.marker(settlement.id());
        helper.assertTrue(marker.isPresent() && marker.get().icon().equals(Optional.of(MarkerShapes.RING)),
                "The layer must have a ring for the discovered village: " + marker);
        helper.assertTrue(territories.areas().stream().anyMatch(area -> area.id().equals(settlement.id())
                && !area.chunks().isEmpty()), "The layer must have the territory of the village");
        helper.assertTrue(same, "A check with no change must keep the layers");
        helper.succeed();
    }

    /** The cost of the chunks of the territories: 64 villages of radius 12, first with no cache, then with it. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "cartofold_cost")
    public static void costOfTheTerritories(GameTestHelper helper) {
        String prefix = UUID.randomUUID() + "-";
        List<TerritoryMapPayload.Village> villages = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            Claimant claimant = new Claimant(prefix + i, (i % 8) * 300, (i / 8) * 300, 1, 12);
            villages.add(new TerritoryMapPayload.Village(claimant, 64, "Village " + i, "Faction", 0x808080));
        }
        TerritoryMapPayload map = new TerritoryMapPayload(villages, List.of());

        long start = System.nanoTime();
        LayerContent first = CartofoldMaps.territories(map);
        long cold = System.nanoTime() - start;
        start = System.nanoTime();
        LayerContent second = CartofoldMaps.territories(map);
        long warm = System.nanoTime() - start;
        int chunks = first.areas().stream().mapToInt(area -> area.chunks().size()).sum();
        FolkholdWorld.LOGGER.info("Cartofold territories: 64 villages, {} chunks, {} ms with no cache, {} ms with the "
                + "cache", chunks, cold / 1_000_000.0, warm / 1_000_000.0);

        helper.assertTrue(first.equals(second), "The cache must give the same layer");
        helper.assertTrue(cold < 2_000_000_000L, "The territories must take less than 2 s: " + cold + " ns");
        helper.succeed();
    }
}
