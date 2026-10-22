package io.github.nicolasgoyon.folkhold.world.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The model and the animations of the animated villagers (see {@link AnimatedVillagers}) agree. */
class AnimatedVillagerAssetsTest {
    private static final String MODEL = "/assets/folkhold_world/geo/entity/animated_villager.geo.json";
    private static final String ANIMATIONS =
            "/assets/folkhold_world/animations/entity/animated_villager.animation.json";

    private static JsonObject read(String path) throws IOException {
        InputStream stream = AnimatedVillagerAssetsTest.class.getResourceAsStream(path);
        assertNotNull(stream, "No file " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> bones() throws IOException {
        Set<String> bones = new HashSet<>();
        for (JsonElement bone : read(MODEL).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones")) {
            bones.add(bone.getAsJsonObject().get("name").getAsString());
        }
        return bones;
    }

    /** The renderer and the animations need these bones: the head turns, and the hands keep the skin. */
    @Test
    void modelHasTheBonesOfTheRenderer() throws IOException {
        Set<String> bones = bones();

        for (String bone : List.of("head", "hat", "body", "rightArm", "leftArm", "rightHand", "leftHand", "rightLeg",
                "leftLeg")) {
            assertTrue(bones.contains(bone), "No bone " + bone);
        }
    }

    /** The animations of the controllers exist, and each animated bone is a bone of the model. */
    @Test
    void animationsMoveBonesOfTheModel() throws IOException {
        Set<String> bones = bones();
        JsonObject animations = read(ANIMATIONS).getAsJsonObject("animations");

        for (String name : List.of("misc.idle", "move.walk", "action.place", "action.use")) {
            assertTrue(animations.has(name), "No animation " + name);
        }
        for (String name : animations.keySet()) {
            for (String bone : animations.getAsJsonObject(name).getAsJsonObject("bones").keySet()) {
                assertTrue(bones.contains(bone), name + " moves the unknown bone " + bone);
            }
        }
    }
}
