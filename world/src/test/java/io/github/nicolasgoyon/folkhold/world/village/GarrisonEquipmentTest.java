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
import java.util.Map;
import java.util.Objects;
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

    /** The archers of all the sets have a bow: their attack needs it. */
    @Test
    void archersHaveABow() throws IOException {
        Sets sets = dataFile();

        for (String set : sets.ids()) {
            Piece weapon = sets.pieces(set, GarrisonRole.ARCHER).get(EquipmentSlot.MAINHAND);
            assertEquals("minecraft:bow", weapon.item(), set);
        }
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
