package io.github.nicolasgoyon.folkhold.world.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ProfessionTexturesTest {
    /** Each profession of the mod has a villager texture and a zombie villager texture. */
    @Test
    void eachProfessionHasItsTextures() {
        List<ResourceLocation> professions = BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream()
                .filter(id -> id.getNamespace().equals(FolkholdWorld.MOD_ID))
                .toList();

        assertFalse(professions.isEmpty(), "The mod must register professions");
        for (ResourceLocation id : professions) {
            for (String model : List.of("villager", "zombie_villager")) {
                String path = "/assets/" + id.getNamespace() + "/textures/entity/" + model + "/profession/"
                        + id.getPath() + ".png";
                assertNotNull(ProfessionTexturesTest.class.getResource(path), "No texture " + path);
            }
        }
    }
}
