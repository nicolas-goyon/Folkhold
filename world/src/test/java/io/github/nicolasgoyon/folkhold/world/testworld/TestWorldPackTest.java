package io.github.nicolasgoyon.folkhold.world.testworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.junit.jupiter.api.Test;

class TestWorldPackTest {
    /** The mod offers the data pack of the test world. The game does not turn it on by itself. */
    @Test
    void dataPackIsOfferedAndOff() {
        List<Pack> packs = offeredPacks(PackType.SERVER_DATA);

        assertEquals(1, packs.size());
        Pack pack = packs.get(0);
        assertNotNull(pack, "The data pack must have a valid pack.mcmeta");
        assertEquals(TestWorldPack.PACK_ID, pack.getId());
        assertFalse(pack.isRequired(), "The player must be able to turn off the data pack");
        assertFalse(pack.getPackSource().shouldAddAutomatically(), "The game must not turn on the data pack");
    }

    /** The mod offers no resource pack. */
    @Test
    void noResourcePack() {
        assertTrue(offeredPacks(PackType.CLIENT_RESOURCES).isEmpty());
    }

    private static List<Pack> offeredPacks(PackType type) {
        List<Pack> packs = new ArrayList<>();
        TestWorldPack.onAddPackFinders(new AddPackFindersEvent(type, source -> source.loadPacks(packs::add), false));
        return packs;
    }
}
