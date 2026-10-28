package io.github.nicolasgoyon.folkhold.world.client.map;

import io.github.nicolasgoyon.folkhold.world.journal.MapMoves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The buttons of the move of a map in the screen of the cartography table (see {@link MapMoves}): an arrow for each
 * direction, between the slots and the preview of the map. A button sends its direction to the menu of the server.
 */
public final class CartographyButtons {
    /** The middle of the arrows, from the top left corner of the screen. */
    private static final int MIDDLE_X = 50;
    private static final int MIDDLE_Y = 42;
    private static final int SIZE = 11;
    private static final int GAP = 11;

    private CartographyButtons() {
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CartographyTableScreen screen)) {
            return;
        }
        int left = screen.getGuiLeft() + MIDDLE_X - SIZE / 2;
        int top = screen.getGuiTop() + MIDDLE_Y - SIZE / 2;
        add(event, screen, MapMoves.NORTH, "^", "north", left, top - GAP);
        add(event, screen, MapMoves.SOUTH, "v", "south", left, top + GAP);
        add(event, screen, MapMoves.WEST, "<", "west", left - GAP, top);
        add(event, screen, MapMoves.EAST, ">", "east", left + GAP, top);
    }

    private static void add(ScreenEvent.Init.Post event, CartographyTableScreen screen, int direction, String arrow,
            String name, int x, int y) {
        Button button = Button.builder(Component.literal(arrow), pressed -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, direction);
            }
        }).bounds(x, y, SIZE, SIZE)
                .tooltip(Tooltip.create(Component.translatable("folkhold_world.cartography.move." + name)))
                .build();
        event.addListener(button);
    }
}
