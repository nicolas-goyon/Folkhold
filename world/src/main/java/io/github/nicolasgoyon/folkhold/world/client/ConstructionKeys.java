package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The keys of the construction tool of the operators. The key of the menu has no default key: the command
 * {@code /folkhold construction place} also opens the menu. The other keys act only when a ghost is in the level (see
 * {@link ConstructionGhost}).
 */
public final class ConstructionKeys {
    private static final String CATEGORY = "key.categories.folkhold_world";
    private static final String KEY = "key.folkhold_world.construction.";

    /** Opens the construction menu. */
    public static final KeyMapping MENU = key("menu", InputConstants.UNKNOWN.getValue());
    /** Turns the ghost by 90 degrees, clockwise. */
    public static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    /** Mirrors the ghost: no mirror, left to right, front to back. */
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_M);
    /** Locks the position of the ghost, or makes it follow the view again. */
    public static final KeyMapping LOCK = key("lock", GLFW.GLFW_KEY_G);
    /** Starts the construction at the position of the ghost. */
    public static final KeyMapping BUILD = key("build", GLFW.GLFW_KEY_ENTER);
    /** Removes the ghost. */
    public static final KeyMapping CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);
    /** Moves the ghost by one block, away from the player. */
    public static final KeyMapping FORWARD = key("forward", GLFW.GLFW_KEY_UP);
    /** Moves the ghost by one block, toward the player. */
    public static final KeyMapping BACK = key("back", GLFW.GLFW_KEY_DOWN);
    /** Moves the ghost by one block, to the left of the player. */
    public static final KeyMapping LEFT = key("left", GLFW.GLFW_KEY_LEFT);
    /** Moves the ghost by one block, to the right of the player. */
    public static final KeyMapping RIGHT = key("right", GLFW.GLFW_KEY_RIGHT);
    /** Moves the ghost one block up. */
    public static final KeyMapping UP = key("up", GLFW.GLFW_KEY_PAGE_UP);
    /** Moves the ghost one block down. */
    public static final KeyMapping DOWN = key("down", GLFW.GLFW_KEY_PAGE_DOWN);

    /** The keys that act on the ghost. */
    static final List<KeyMapping> GHOST_KEYS = List.of(ROTATE, MIRROR, LOCK, BUILD, CANCEL, FORWARD, BACK, LEFT,
            RIGHT, UP, DOWN);

    private ConstructionKeys() {
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(KEY + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MENU);
        GHOST_KEYS.forEach(event::register);
    }
}
