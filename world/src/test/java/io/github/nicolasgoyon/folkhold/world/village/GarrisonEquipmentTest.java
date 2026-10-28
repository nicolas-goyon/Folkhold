package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonEquipment.Piece;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonEquipment.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class GarrisonEquipmentTest {
    private static Sets dataFile() throws IOException {
        try (InputStream stream = GarrisonEquipmentTest.class.getResourceAsStream(
                "/data/folkhold_world/folkhold/garrison_equipment.json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            return GarrisonEquipment.parse(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /** Each equipment set of the level table is in the data file, with the pieces of each role. */
    @Test
    void dataFileHasTheSetsOfTheLevelTable() throws IOException {
        Sets sets = dataFile();

        for (int level = 1; level <= LevelTable.DEFAULT.maxLevel(); level++) {
            String set = LevelTable.DEFAULT.traits(level).garrison().equipment();
            assertTrue(sets.ids().contains(set), "No set " + set);
            for (GarrisonRole role : GarrisonRole.values()) {
                assertTrue(sets.sets().get(set).containsKey(role.serializedName()), set + " has no " + role);
            }
        }
    }

    /** The set of a hamlet in the data file is the default set of the code. */
    @Test
    void hamletOfTheDataFileIsTheDefaultSet() throws IOException {
        assertEquals(GarrisonEquipment.DEFAULT.sets().get(GarrisonEquipment.DEFAULT_SET),
                dataFile().sets().get(GarrisonEquipment.DEFAULT_SET));
    }

    /** Each variant of the archers of all the sets has a bow or a crossbow: their attack needs it. */
    @Test
    void archersHaveABowOrACrossbow() throws IOException {
        Sets sets = dataFile();

        for (String set : sets.ids()) {
            for (Map<EquipmentSlot, Piece> variant : sets.variants(set, GarrisonRole.ARCHER)) {
                String weapon = variant.get(EquipmentSlot.MAINHAND).item();
                assertTrue(weapon.equals("minecraft:bow") || weapon.equals("minecraft:crossbow"), set + ": " + weapon);
            }
        }
    }

    /** The archers of the town, the city and the capital have a bow and a crossbow. The smaller villages use a bow. */
    @Test
    void largerVillagesHaveCrossbowArchers() throws IOException {
        Sets sets = dataFile();

        for (String set : List.of("town", "city", "capital")) {
            List<String> weapons = sets.variants(set, GarrisonRole.ARCHER).stream()
                    .map(variant -> variant.get(EquipmentSlot.MAINHAND).item()).toList();
            assertEquals(List.of("minecraft:bow", "minecraft:crossbow"), weapons, set);
        }
        for (String set : List.of("hamlet", "village")) {
            assertEquals(1, sets.variants(set, GarrisonRole.ARCHER).size(), set);
        }
    }

    /**
     * A role can have a list of variants. The id of a unit selects one variant: the same id gives the same variant,
     * and the ids of many units give all the variants.
     */
    @Test
    void unitIdSelectsAVariant() {
        Sets sets = parse("""
                {"elite": {"archer": [{"mainhand": "minecraft:bow"}, {"mainhand": "minecraft:crossbow"}]}}
                """);

        assertEquals(2, sets.variants("elite", GarrisonRole.ARCHER).size());
        assertEquals("minecraft:bow", sets.pieces("elite", GarrisonRole.ARCHER).get(EquipmentSlot.MAINHAND).item());
        Set<String> weapons = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            UUID id = new UUID(0L, i);
            Piece weapon = sets.pieces("elite", GarrisonRole.ARCHER, id).get(EquipmentSlot.MAINHAND);
            assertEquals(weapon, sets.pieces("elite", GarrisonRole.ARCHER, id).get(EquipmentSlot.MAINHAND));
            weapons.add(weapon.item());
        }
        assertEquals(Set.of("minecraft:bow", "minecraft:crossbow"), weapons);
        assertThrows(IllegalArgumentException.class, () -> parse("{\"a\": {\"archer\": []}}"));
    }

    /**
     * The set of a culture changes the roles that it gives. The other roles, and the units of the other cultures and
     * of no culture, use the set with no culture.
     */
    @Test
    void cultureSetChangesItsRoles() {
        Sets sets = parse("""
                {"hamlet": {"guard": {"mainhand": "minecraft:iron_sword"}, "archer": {"mainhand": "minecraft:bow"}},
                 "valeholm/hamlet": {"guard": {"mainhand": "minecraft:iron_axe"}}}
                """);
        UUID id = new UUID(0L, 1L);

        assertEquals("minecraft:iron_axe", sets.pieces("valeholm", "hamlet", GarrisonRole.GUARD, id)
                .get(EquipmentSlot.MAINHAND).item());
        assertEquals("minecraft:bow", sets.pieces("valeholm", "hamlet", GarrisonRole.ARCHER, id)
                .get(EquipmentSlot.MAINHAND).item(), "A role that the culture does not change");
        assertEquals("minecraft:iron_sword", sets.pieces("ashmark", "hamlet", GarrisonRole.GUARD, id)
                .get(EquipmentSlot.MAINHAND).item(), "Another culture");
        assertEquals("minecraft:iron_sword", sets.pieces(null, "hamlet", GarrisonRole.GUARD, id)
                .get(EquipmentSlot.MAINHAND).item(), "No culture");
    }

    /**
     * The cultures of the data file: Valeholm has axes and bows, Ashmark crossbows, Sunreach spears (tridents) and
     * bows. Each culture has a set for each set of the level table.
     */
    @Test
    void dataFileHasTheWeaponsOfTheCultures() throws IOException {
        Sets sets = dataFile();
        UUID id = new UUID(0L, 1L);
        for (String set : List.of("hamlet", "village", "town", "city", "capital")) {
            for (String culture : List.of("valeholm", "ashmark", "sunreach")) {
                assertTrue(sets.ids().contains(culture + "/" + set), "No set " + culture + "/" + set);
            }
            for (GarrisonRole role : List.of(GarrisonRole.GUARD, GarrisonRole.CAPTAIN, GarrisonRole.RIDER)) {
                assertTrue(weapon(sets, "valeholm", set, role).endsWith("_axe"), set + " " + role);
                assertEquals("minecraft:trident", weapon(sets, "sunreach", set, role), set + " " + role);
                assertTrue(weapon(sets, "ashmark", set, role).endsWith("_sword"), set + " " + role);
            }
            for (Map<EquipmentSlot, Piece> variant : sets.variants("ashmark", set, GarrisonRole.ARCHER)) {
                assertEquals("minecraft:crossbow", variant.get(EquipmentSlot.MAINHAND).item(), set);
            }
            for (String culture : List.of("valeholm", "sunreach")) {
                for (Map<EquipmentSlot, Piece> variant : sets.variants(culture, set, GarrisonRole.ARCHER)) {
                    assertEquals("minecraft:bow", variant.get(EquipmentSlot.MAINHAND).item(), culture + " " + set);
                }
            }
            assertTrue(sets.pieces("sunreach", set, GarrisonRole.GUARD, id).get(EquipmentSlot.OFFHAND) == null,
                    "A spear needs both hands: no shield " + set);
        }
    }

    private static String weapon(Sets sets, String culture, String set, GarrisonRole role) {
        return sets.pieces(culture, set, role, new UUID(0L, 1L)).get(EquipmentSlot.MAINHAND).item();
    }

    /** A piece can have enchantments. An unknown set or role gives the pieces of the role in the default set. */
    @Test
    void piecesWithEnchantmentsAndFallback() {
        JsonObject json = JsonParser.parseString("""
                {"elite": {"guard": {"head": {"item": "minecraft:diamond_helmet",
                                              "enchantments": {"minecraft:protection": 2}},
                                     "chest": "tabard"}}}
                """).getAsJsonObject();

        Sets sets = GarrisonEquipment.parse(json);

        Map<EquipmentSlot, Piece> guard = sets.pieces("elite", GarrisonRole.GUARD);
        assertEquals(new Piece("minecraft:diamond_helmet",
                Map.of(ResourceLocation.withDefaultNamespace("protection"), 2), null), guard.get(EquipmentSlot.HEAD));
        assertEquals(Piece.of(GarrisonEquipment.TABARD), guard.get(EquipmentSlot.CHEST));
        assertEquals(2, guard.size());
        Map<EquipmentSlot, Piece> hamletArcher = GarrisonEquipment.DEFAULT.pieces(GarrisonEquipment.DEFAULT_SET,
                GarrisonRole.ARCHER);
        assertEquals(hamletArcher, sets.pieces("elite", GarrisonRole.ARCHER));
        assertEquals(hamletArcher, sets.pieces("unknown", GarrisonRole.ARCHER));
    }

    /** A piece can have a potion: the healer holds a potion of healing. */
    @Test
    void pieceWithAPotion() {
        Sets sets = parse("""
                {"elite": {"healer": {"mainhand": {"item": "minecraft:potion", "potion": "minecraft:healing"}}}}
                """);

        Piece healing = Piece.withPotion(Items.POTION, ResourceLocation.withDefaultNamespace("healing"));
        assertEquals(healing, sets.pieces("elite", GarrisonRole.HEALER).get(EquipmentSlot.MAINHAND));
        assertEquals(healing, GarrisonEquipment.DEFAULT.pieces(GarrisonEquipment.DEFAULT_SET, GarrisonRole.HEALER)
                .get(EquipmentSlot.MAINHAND));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"a\": {\"healer\": {\"mainhand\": {\"item\": "
                + "\"minecraft:potion\", \"potion\": \"minecraft:no_such_potion\"}}}}"));
    }

    /** An unknown role, slot or item, or an enchantment level below 1, makes the file not valid. */
    @Test
    void unknownValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"a\": {\"wizard\": {}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"a\": {\"guard\": {\"tail\": \"minecraft:stone\"}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"a\": {\"guard\": {\"head\": \"minecraft:no_such_item\"}}}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"a\": {\"guard\": {\"head\": {\"item\": "
                + "\"minecraft:iron_helmet\", \"enchantments\": {\"minecraft:protection\": 0}}}}}"));
    }

    private static Sets parse(String json) {
        return GarrisonEquipment.parse(JsonParser.parseString(json).getAsJsonObject());
    }
}
