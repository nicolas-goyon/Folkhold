package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The cargo of a caravan in the chests of its mules.
 *
 * <ul>
 *   <li>When the caravan gets its entities, the cargo goes into the mule chests. The items that do not fit stay in
 *       the abstract cargo (the overflow).</li>
 *   <li>While the caravan has entities, its cargo is the content of the chests of the living mules, plus the
 *       overflow. A theft or the death of a mule (it drops its chest) removes items from the cargo.</li>
 * </ul>
 */
public final class CaravanCargo {

    private CaravanCargo() {
    }

    /**
     * Puts a cargo into the chests of mules.
     *
     * @param cargo the cargo, by item id
     * @param mules the mules, with a chest
     * @return the overflow: the items that do not fit, by item id
     */
    public static Map<String, Integer> load(Map<String, Integer> cargo, List<AbstractChestedHorse> mules) {
        Map<String, Integer> overflow = new TreeMap<>();
        int muleIndex = 0;
        int slot = 0;
        for (Map.Entry<String, Integer> entry : cargo.entrySet()) {
            Optional<Item> item = ItemIds.item(entry.getKey());
            if (item.isEmpty()) {
                overflow.merge(entry.getKey(), entry.getValue(), Integer::sum);
                continue;
            }
            int remaining = entry.getValue();
            int maxStack = new ItemStack(item.get()).getMaxStackSize();
            while (remaining > 0 && muleIndex < mules.size()) {
                AbstractChestedHorse mule = mules.get(muleIndex);
                int first = chestStart(mule);
                if (first + slot >= mule.inventory.getContainerSize()) {
                    muleIndex++;
                    slot = 0;
                    continue;
                }
                int count = Math.min(remaining, maxStack);
                mule.inventory.setItem(first + slot, new ItemStack(item.get(), count));
                remaining -= count;
                slot++;
            }
            if (remaining > 0) {
                overflow.merge(entry.getKey(), remaining, Integer::sum);
            }
        }
        return overflow;
    }

    /**
     * The items in the chest of a mule.
     *
     * @param mule the mule
     * @return the quantities, by item id
     */
    public static Map<String, Integer> contents(AbstractChestedHorse mule) {
        Map<String, Integer> result = new TreeMap<>();
        SimpleContainer inventory = mule.inventory;
        for (int i = chestStart(mule); i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                result.merge(ItemIds.id(stack.getItem()), stack.getCount(),
                        Integer::sum);
            }
        }
        return result;
    }

    /**
     * Removes items from the chests of mules.
     *
     * @param mules the mules
     * @param item  the item
     * @param count the quantity to remove
     * @return the quantity that the chests had
     */
    public static int remove(List<AbstractChestedHorse> mules, Item item, int count) {
        int removed = 0;
        for (AbstractChestedHorse mule : mules) {
            SimpleContainer inventory = mule.inventory;
            for (int i = chestStart(mule); i < inventory.getContainerSize() && removed < count; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(item)) {
                    int taken = Math.min(stack.getCount(), count - removed);
                    stack.shrink(taken);
                    inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    removed += taken;
                }
            }
        }
        return removed;
    }

    /**
     * Removes all items from the chest of a mule.
     *
     * @param mule the mule
     */
    public static void clear(AbstractChestedHorse mule) {
        SimpleContainer inventory = mule.inventory;
        for (int i = chestStart(mule); i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Gives a chest to a mule and makes its inventory with the chest slots. */
    public static void equipChest(AbstractChestedHorse mule) {
        mule.setChest(true);
        mule.createInventory();
    }

    /** The index of the first chest slot in the inventory of a mule. */
    private static int chestStart(AbstractChestedHorse mule) {
        return Math.max(0, mule.inventory.getContainerSize() - mule.getInventoryColumns() * 3);
    }

}
