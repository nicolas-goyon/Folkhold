package io.github.nicolasgoyon.folkhold.world.journal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * A spyglass explores the vanilla maps farther (see {@link io.github.nicolasgoyon.folkhold.world.mixin.MapItemMixin}).
 *
 * <ul>
 *   <li>A player with a spyglass in the inventory updates the map in the hand in a radius {@link #FACTOR} times larger
 *       than the radius of the game.</li>
 *   <li>The update of a map reads only the chunks that are loaded: it never loads or generates a chunk. Thus the radius
 *       stops at the view distance of the server.</li>
 * </ul>
 */
public final class SpyglassMaps {
    /** The radius of the update of a map with a spyglass, as a multiple of the radius of the game. */
    public static final int FACTOR = 3;

    private SpyglassMaps() {
    }

    /**
     * The radius of the update of a map for a viewer.
     *
     * @param radius the radius of the game, in pixels of a map of scale 0
     * @param viewer the entity that holds the map
     * @return the radius
     */
    public static int radius(int radius, Entity viewer) {
        return viewer instanceof Player player && hasSpyglass(player) ? radius * FACTOR : radius;
    }

    /** True if a player has a spyglass in the inventory. */
    public static boolean hasSpyglass(Player player) {
        return player.getInventory().contains(stack -> stack.is(Items.SPYGLASS));
    }
}
