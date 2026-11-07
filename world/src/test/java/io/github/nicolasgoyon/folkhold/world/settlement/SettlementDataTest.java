package io.github.nicolasgoyon.folkhold.world.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The save and the load of the settlement data ({@link SettlementData}). */
class SettlementDataTest {
    @Test
    void lastFallPenaltySurvivesSaveAndLoad() {
        SettlementData data = new SettlementData();
        Settlement fallen = settlement("fallen");
        fallen.setLastFallPenalty(123456L);
        data.registry().add(fallen);
        data.registry().add(settlement("calm"));

        CompoundTag tag = data.save(new CompoundTag(), null);
        SettlementData loaded = SettlementData.load(tag, null);

        assertEquals(123456L, loaded.registry().byId("fallen").orElseThrow().lastFallPenalty());
        assertEquals(Settlement.NO_FALL_PENALTY, loaded.registry().byId("calm").orElseThrow().lastFallPenalty());
    }

    @Test
    void missingTagLoadsAsNoPenalty() {
        SettlementData data = new SettlementData();
        Settlement fallen = settlement("fallen");
        fallen.setLastFallPenalty(500L);
        data.registry().add(fallen);
        CompoundTag tag = data.save(new CompoundTag(), null);
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        list.getCompound(0).remove("LastFallPenalty");
        assertFalse(list.getCompound(0).contains("LastFallPenalty"));

        SettlementData loaded = SettlementData.load(tag, null);

        assertEquals(Settlement.NO_FALL_PENALTY, loaded.registry().byId("fallen").orElseThrow().lastFallPenalty());
    }

    @Test
    void jobBuildingsSurviveSaveAndLoad() {
        SettlementData data = new SettlementData();
        Settlement scanned = settlement("scanned");
        scanned.setJobBuildings(Optional.of(Map.of("folkhold_world:blacksmith", 1)));
        Settlement empty = settlement("empty");
        empty.setJobBuildings(Optional.of(Map.of()));
        data.registry().add(scanned);
        data.registry().add(empty);
        data.registry().add(settlement("abstract"));

        SettlementData loaded = SettlementData.load(data.save(new CompoundTag(), null), null);

        assertEquals(Optional.of(Map.of("folkhold_world:blacksmith", 1)),
                loaded.registry().byId("scanned").orElseThrow().jobBuildings());
        assertEquals(Optional.of(Map.of()), loaded.registry().byId("empty").orElseThrow().jobBuildings());
        assertEquals(Optional.empty(), loaded.registry().byId("abstract").orElseThrow().jobBuildings());
    }

    @Test
    void settlementOfAnEarlierVersionHasUnknownJobBuildings() {
        SettlementData data = new SettlementData();
        data.registry().add(settlement("old"));
        CompoundTag tag = data.save(new CompoundTag(), null);
        tag.putInt("Version", 1);

        SettlementData loaded = SettlementData.load(tag, null);

        assertEquals(Optional.empty(), loaded.registry().byId("old").orElseThrow().jobBuildings());
    }

    private static Settlement settlement(String id) {
        return new Settlement(id, 0, 0, "faction", "Village " + id, 8, 8);
    }
}
