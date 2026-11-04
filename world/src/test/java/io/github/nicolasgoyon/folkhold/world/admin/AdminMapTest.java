package io.github.nicolasgoyon.folkhold.world.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MarkerShapes;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload.Kind;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class AdminMapTest {
    private static MapMarker marker(Kind kind, String id, int x) {
        return MapMarker.tinted(kind.markerId(id), Level.OVERWORLD, new BlockPos(x, 64, 0), 0x808080,
                MarkerShapes.DIAMOND, Component.literal(id));
    }

    private static LayerContent layer(List<MapMarker> markers) {
        return new LayerContent(LayerInfo.of(AdminMap.LAYER, Component.literal("Admin"), 40, false), List.of(),
                List.of(), markers);
    }

    /** A moved or a new group is in the change. A group that ended leaves the layer. A settlement stays. */
    @Test
    void groupChangeHasTheMovesAndTheEnds() {
        MapMarker village = marker(Kind.VILLAGE, "northam", 0);
        MapMarker caravan = marker(Kind.CARAVAN, "c1", 10);
        MapMarker merchant = marker(Kind.MERCHANT, "m1", 20);
        LayerContent content = layer(List.of(village, caravan, merchant));
        MapMarker moved = marker(Kind.CARAVAN, "c1", 11);
        MapMarker started = marker(Kind.CARAVAN, "c2", 30);

        LayerUpdate change = AdminMap.groupChange(content, List.of(moved, started)).orElseThrow();

        assertEquals(List.of(moved, started), change.markers());
        assertEquals(List.of(merchant.id()), change.removedMarkers());
        LayerContent next = content.apply(change);
        assertEquals(Optional.of(village), next.marker(village.id()));
        assertEquals(Optional.of(moved), next.marker(caravan.id()));
    }

    /** With no change of the groups, the server sends nothing. */
    @Test
    void noChangeGivesNoUpdate() {
        MapMarker caravan = marker(Kind.CARAVAN, "c1", 10);
        assertTrue(AdminMap.groupChange(layer(List.of(caravan)), List.of(caravan)).isEmpty());
    }
}
