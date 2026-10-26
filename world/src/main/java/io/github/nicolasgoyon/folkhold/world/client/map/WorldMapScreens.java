package io.github.nicolasgoyon.folkhold.world.client.map;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/**
 * Opens the world map of Xaero's World Map at a block. The game loads this class only when Xaero's World Map is
 * present.
 *
 * <p>Xaero's World Map has no public method that opens its map at a position: a new map screen shows the position of
 * the player. Thus this class makes the screen with reflection, and puts the camera of the screen at the block. When
 * the player attached the camera to the player in the options of the map, the map stays on the player.
 */
final class WorldMapScreens {
    /** The class of the screen of the world map. */
    private static final String SCREEN_CLASS = "xaero.map.gui.GuiMap";

    private WorldMapScreens() {
    }

    /**
     * Opens the world map.
     *
     * @param parent the screen that Escape opens again
     * @param blockX block X at the center of the map
     * @param blockZ block Z at the center of the map
     * @return true if the world map opens, false if the world map is not ready
     * @throws ReflectiveOperationException if the screen of the world map is not accessible
     */
    static boolean open(Screen parent, int blockX, int blockZ) throws ReflectiveOperationException {
        Minecraft minecraft = Minecraft.getInstance();
        WorldMapSession session = WorldMapSession.getCurrentSession();
        Entity camera = minecraft.getCameraEntity();
        if (session == null || !session.isUsable() || camera == null) {
            return false;
        }
        Class<?> type = Class.forName(SCREEN_CLASS);
        Screen screen = (Screen) type.getConstructor(Screen.class, Screen.class, MapProcessor.class, Entity.class)
                .newInstance(parent, parent, session.getMapProcessor(), camera);
        set(type, screen, "shouldResetCameraPos", false);
        set(type, screen, "cameraX", (double) blockX);
        set(type, screen, "cameraZ", (double) blockZ);
        minecraft.setScreen(screen);
        return true;
    }

    private static void set(Class<?> type, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
