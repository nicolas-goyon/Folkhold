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
     * The player gives the items of an accepted delivery quest at the quest screen of a village. The village must
     * be the village of the quest.
     *
     * @param player  the player
     * @param questId the id of the quest
     * @param here    the village of the screen
     * @return the result: done when the quest is complete
     */
    public static QuestBoards.Outcome deliver(ServerPlayer player, String questId, Settlement here) {
        Optional<Quest> found = QuestData.get(player.server).quests().byId(questId)
                .filter(quest -> canDeliverAt(quest, here) && player.getUUID().equals(quest.playerId()));
        Optional<Item> item = found.flatMap(quest -> item(quest.target()));
        if (found.isEmpty() || item.isEmpty() || !here.isActive()) {
            return QuestBoards.Outcome.of(false, "quest_gone");
        }
        Quest quest = found.get();
        if (count(player.getInventory(), item.get()) < quest.count()) {
            return QuestBoards.Outcome.of(false, "quest_missing_items", quest.count(),
                    new ItemStack(item.get()).getHoverName());
        }
        remove(player.getInventory(), item.get(), quest.count());
        int capacity = LevelTables.get().traits(here.level()).stockCapacity();
        here.economy().stock().add(quest.target(), quest.count(), capacity);
        SettlementData.get(player.server).setDirty();
        return QuestBoards.complete(player.server, quest)
                .map(message -> new QuestBoards.Outcome(true, message))
                .orElse(QuestBoards.Outcome.of(false, "quest_gone"));
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
