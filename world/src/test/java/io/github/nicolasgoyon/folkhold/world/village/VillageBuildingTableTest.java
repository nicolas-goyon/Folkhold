package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.Requirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class VillageBuildingTableTest {
    /**
     * The data file of the mod gives a storehouse from the level 3, a barracks from the level 9 and one market from the
     * level 5. Each kind is a kind
     * of building sign, with a piece in the base variant of the village. The forge is the building of the blacksmith,
     * not a building of a level.
     */
    @Test
    void dataFileHasTheBuildingsOfTheLevels() throws IOException {
        try (InputStream stream = VillageBuildingTableTest.class.getResourceAsStream(
                "/data/folkhold_world/folkhold/village_buildings.json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            List<Requirement> requirements = VillageBuildingTable.parse(
                    JsonParser.parseReader(reader).getAsJsonObject());

            assertEquals(new Requirement("storehouse", 3), requirements.get(0));
            assertEquals(new Requirement("barracks", 9), requirements.get(1));
            assertEquals(1, requirements.stream()
                    .filter(requirement -> requirement.equals(new Requirement(VillageBuildings.MARKET, 5))).count(),
                    "One market from the level 5: " + requirements);
            assertTrue(requirements.stream().noneMatch(requirement -> requirement.kind().equals("forge")),
                    "The forge is a job building: " + requirements);
            List<String> kinds = Arrays.stream(BuildingSignKind.values()).map(BuildingSignKind::serializedName)
                    .toList();
            for (Requirement requirement : requirements) {
                assertTrue(kinds.contains(requirement.kind()), "Not a kind of building sign: " + requirement);
                String piece = requirement.kind().equals(VillageBuildings.MARKET) ? "market"
                        : "house_" + requirement.kind();
                assertTrue(VillageBuildingTableTest.class.getResource("/data/folkhold_world/structure/village/"
                        + piece + ".nbt") != null, "No piece for " + requirement);
            }
        }
    }
}
