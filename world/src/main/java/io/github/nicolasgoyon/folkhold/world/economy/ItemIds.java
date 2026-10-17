package io.github.nicolasgoyon.folkhold.world.economy;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The item ids of the simulation: the keys of the stocks, the cargos and the quests are the registry names of the
 * items, for example {@code minecraft:bread}.
 */
public final class ItemIds {
    private ItemIds() {
    }

    /**
     * Gives the item with an id.
     *
     * @param id the id, for example {@code minecraft:bread}
     * @return the item, or empty for an invalid id, an unknown id or air
     */
    public static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(location).filter(item -> item != Items.AIR);
    }

    /**
     * Gives the id of an item.
     *
     * @param item the item
     * @return the id, for example {@code minecraft:bread}
     */
    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
