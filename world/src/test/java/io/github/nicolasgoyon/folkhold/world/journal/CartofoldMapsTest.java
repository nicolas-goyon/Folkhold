package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MarkerShapes;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class CartofoldMapsTest {
    private static final Claimant NORTHAM = new Claimant("northam", 0, 0, 2, 3);
    private static final Claimant SOUTHAM = new Claimant("southam", 16 * 5, 0, 1, 3);

    /** With no neighbor, the territory is the disc of the claim. */
    @Test
    void chunksOfAClaimAlone() {
        List<ChunkPos> chunks = CartofoldMaps.chunks(NORTHAM, List.of());

        assertEquals(TerritoryClaims.compute(List.of(NORTHAM)).chunkCount("northam"), chunks.size());
        assertTrue(chunks.contains(new ChunkPos(0, 0)));
        assertTrue(chunks.contains(new ChunkPos(2, 0)));
        assertFalse(chunks.contains(new ChunkPos(4, 0)));
    }

    /** A neighbor takes the chunks that are nearer to it: the two territories have no chunk in common. */
    @Test
    void neighborTakesItsChunks() {
        List<ChunkPos> north = CartofoldMaps.chunks(NORTHAM, List.of(SOUTHAM));
        List<ChunkPos> south = CartofoldMaps.chunks(SOUTHAM, List.of(NORTHAM));
        TerritoryClaims claims = TerritoryClaims.compute(List.of(NORTHAM, SOUTHAM));

        assertEquals(claims.chunkCount("northam"), north.size());
        assertEquals(claims.chunkCount("southam"), south.size());
        assertTrue(north.contains(new ChunkPos(2, 0)) && !north.contains(new ChunkPos(3, 0)));
        assertTrue(south.contains(new ChunkPos(3, 0)));
        assertTrue(north.stream().noneMatch(south::contains));
    }

    /** The layer of the territories has an area for each discovered village, in the color of its faction. */
    @Test
    void territoriesLayer() {
        TerritoryMapPayload map = new TerritoryMapPayload(
                List.of(new TerritoryMapPayload.Village(NORTHAM, 70, "Northam", "Ash", 0xAA5500)),
                List.of(SOUTHAM));

        LayerContent layer = CartofoldMaps.territories(map);

        assertEquals(CartofoldMaps.TERRITORIES, layer.info().id());
        assertEquals(1, layer.areas().size());
        ChunkArea area = layer.areas().get(0);
        assertEquals("northam", area.id());
        assertEquals(CartofoldMaps.chunks(NORTHAM, List.of(SOUTHAM)).size(), area.chunks().size());
        assertEquals(0x40AA5500, area.fillColor());
        assertEquals(0xCCAA5500, area.borderColor());
        assertEquals(1, area.tooltip().size());
        assertEquals(layer, CartofoldMaps.territories(map), "The same map gives the same layer");
    }

    /** A village marker has the color of its faction and the shape of its state, and opens the journal. */
    @Test
    void villagesLayer() {
        List<CartofoldMaps.Mark> marks = List.of(
                new CartofoldMaps.Mark("a", "Northam", "Ash", 0xAA5500, new BlockPos(1, 70, 2),
                        CartofoldMaps.State.VISITED),
                new CartofoldMaps.Mark("b", "Southam", "Oak", 0x00AA55, BlockPos.ZERO,
                        CartofoldMaps.State.DISCOVERED),
                new CartofoldMaps.Mark("c", "Old", "Oak", 0x00AA55, BlockPos.ZERO, CartofoldMaps.State.RUINS));

        List<MapMarker> markers = CartofoldMaps.villages(marks).markers();

        assertEquals(3, markers.size());
        assertEquals(Optional.of(MarkerShapes.SQUARE), markers.get(0).icon());
        assertEquals(Optional.of(MarkerShapes.RING), markers.get(1).icon());
        assertEquals(Optional.of(MarkerShapes.CROSS), markers.get(2).icon());
        assertEquals(0xAA5500, markers.get(0).color());
        assertEquals(new BlockPos(1, 70, 2), markers.get(0).pos());
        assertTrue(markers.stream().allMatch(marker -> marker.clickable() && marker.tinted()
                && marker.tooltip().isPresent()));
    }

    /** A route has one line for each part on one surface, with the color of the surface. */
    @Test
    void routeLayer() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(10, 0), new Route.Point(20, 0),
                new Route.Point(30, 5)), List.of(Route.Surface.ROAD, Route.Surface.ROAD, Route.Surface.FORD));

        List<MapLine> lines = CartofoldMaps.route(route, true).lines();
        List<MapLine> coarse = CartofoldMaps.route(route, false).lines();

        assertEquals(2, lines.size());
        assertEquals(3, lines.get(0).points().size());
        assertEquals(CartofoldMaps.surfaceColor(Route.Surface.ROAD), lines.get(0).color());
        assertEquals(List.of(new BlockPos(20, 64, 0), new BlockPos(30, 64, 5)), lines.get(1).points());
        assertEquals(CartofoldMaps.surfaceColor(Route.Surface.FORD), lines.get(1).color());
        assertTrue(coarse.stream().allMatch(line -> line.color() == CartofoldMaps.COARSE_COLOR));
    }
}
