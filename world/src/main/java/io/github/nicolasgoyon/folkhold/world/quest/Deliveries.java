package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The delivery quests: a village with a shortage asks the player for an item.
 *
 * <ul>
 *   <li>The player gives the items with the button of the quest screen of the village that gives the quest.</li>
 *   <li>The player must have all the items. The items leave the inventory of the player and go into the stock of
 *       the village, up to its stock capacity.</li>
 *   <li>Then the quest is complete: the player gets the emeralds and the reputation (see
 *       {@link QuestBoards#complete}).</li>
 *   <li>An expired or failed quest gives no reward.</li>
 * </ul>
 */
public final class Deliveries {

    private Deliveries() {
    }

    /** True if the player can give the items of the quest at the screen of this village. */
    public static boolean canDeliverAt(Quest quest, Settlement settlement) {
        return quest.kind() == QuestKind.DELIVERY && quest.state() == QuestState.ACCEPTED
                && quest.settlementId().equals(settlement.id());
    }

    /**
     * The player gives the items of an accepted delivery quest to the village of the quest.
     *
     * @param player  the player
     * @param questId the id of the quest
     * @return true if the quest is complete
     */
    public static boolean deliver(ServerPlayer player, String questId) {
        Optional<Quest> found = QuestData.get(player.server).quests().byId(questId)
                .filter(quest -> quest.kind() == QuestKind.DELIVERY && quest.state() == QuestState.ACCEPTED
                        && player.getUUID().equals(quest.playerId()));
        if (found.isEmpty()) {
            return false;
        }
        Quest quest = found.get();
        Optional<Settlement> village = SettlementData.get(player.server).registry().byId(quest.settlementId())
                .filter(Settlement::isActive);
        Optional<Item> item = item(quest.target());
        if (village.isEmpty() || item.isEmpty()) {
            return false;
        }
        if (count(player.getInventory(), item.get()) < quest.count()) {
            QuestBoards.message(player, "quest_missing_items", quest.count(),
                    new ItemStack(item.get()).getHoverName());
            return false;
        }
        remove(player.getInventory(), item.get(), quest.count());
        int capacity = LevelTables.get().traits(village.get().level()).stockCapacity();
        village.get().economy().stock().add(quest.target(), quest.count(), capacity);
        SettlementData.get(player.server).setDirty();
        return QuestBoards.complete(player.server, quest);
    }

    /** The number of items of a kind in an inventory. */
    public static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void remove(Inventory inventory, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        inventory.setChanged();
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }
}
