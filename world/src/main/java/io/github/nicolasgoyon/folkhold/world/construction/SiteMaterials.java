package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderMaterials.Kind;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderMaterials.Material;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestSystem;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * The materials of one construction site of the builders (see {@link BuilderMaterials}).
 *
 * <ul>
 *   <li>The builders take whole items from the stock of the village. A block can cost a part of an item, for example
 *       3/8 of a log. The parts that the builders took and did not use stay in the site for the next blocks.</li>
 *   <li>A block waits when the stock does not have its materials: the steps after it wait too.</li>
 *   <li>The site asks the players for its rare and valuable blocks with delivery quests of the village. The site also
 *       asks for a common material when it waits for this material for some time
 *       ({@link WorldConfig#CONSTRUCTION_MATERIAL_QUEST_MINUTES}). The delivered items go into the stock of the
 *       village, and the builders take them there.</li>
 *   <li>At the end of the site, the whole items that the site did not use go back to the stock.</li>
 * </ul>
 */
final class SiteMaterials {
    private static final double EPSILON = 1.0E-6;

    /** The parts of items that the builders took from the stock and did not use, by item id. */
    private final Map<String, Double> buffer = new TreeMap<>();
    /** The delivery quests of the site: the item id and the id of the quest. */
    private final Map<String, String> quests = new TreeMap<>();
    /** The game time when the builders started to wait for an item, by item id. */
    private final Map<String, Long> waitingSince = new HashMap<>();
    /** The items that the builders wait for in this tick. */
    private final Set<String> missing = new TreeSet<>();

    /**
     * The materials that the rest of a site needs.
     *
     * @param count the number of items
     * @param price the price of one item, for the reward of a quest
     * @param rare  true for a rare or a valuable block: the site asks for it with a quest at once
     */
    record Need(int count, double price, boolean rare) {
    }

    /** The items that the builders wait for now, by item id. */
    Set<String> missing() {
        return Set.copyOf(missing);
    }

    /** Forgets the items that the builders waited for in the tick before. */
    void clearMissing() {
        missing.clear();
    }

    /** The parts of items that the site keeps, by item id. */
    Map<String, Double> buffer() {
        return Map.copyOf(buffer);
    }

    /**
     * The whole items that a block needs from the stock. For a common block, these are the parts of items that the
     * site does not have. For a rare block, this is the block itself.
     */
    private Map<String, Integer> units(Material material) {
        Map<String, Integer> units = new LinkedHashMap<>();
        if (material.kind() == Kind.RARE) {
            units.put(material.item(), 1);
        } else if (material.kind() == Kind.COMMON) {
            material.cost().forEach((item, quantity) -> {
                double lacking = quantity - buffer.getOrDefault(item, 0.0);
                if (lacking > EPSILON) {
                    units.put(item, (int) Math.ceil(lacking - EPSILON));
                }
            });
        }
        return units;
    }

    /**
     * True if the stock of the village has the materials of a block. A free block, or a village with no economy,
     * needs no materials.
     *
     * @param material   the material of the block
     * @param settlement the village, or null
     * @param now        the game time
     * @return true if the builders can make the block now
     */
    boolean has(Material material, @Nullable Settlement settlement, long now) {
        if (material.kind() == Kind.FREE || settlement == null) {
            return true;
        }
        Stock stock = settlement.economy().stock();
        boolean all = true;
        for (Map.Entry<String, Integer> entry : units(material).entrySet()) {
            if (stock.count(entry.getKey()) < entry.getValue()) {
                missing.add(entry.getKey());
                waitingSince.putIfAbsent(entry.getKey(), now);
                all = false;
            }
        }
        return all;
    }

    /**
     * Takes the materials of a block from the stock of the village. When the stock does not have all the materials,
     * nothing changes.
     *
     * @param material   the material of the block
     * @param settlement the village, or null
     * @param server     the server: the method marks the settlements as changed
     * @param now        the game time
     * @return true if the builders took the materials
     */
    boolean take(Material material, @Nullable Settlement settlement, MinecraftServer server, long now) {
        if (!has(material, settlement, now)) {
            return false;
        }
        if (material.kind() == Kind.FREE || settlement == null) {
            return true;
        }
        Stock stock = settlement.economy().stock();
        Map<String, Integer> units = units(material);
        units.forEach((item, count) -> {
            stock.remove(item, count);
            waitingSince.remove(item);
        });
        if (material.kind() == Kind.COMMON) {
            units.forEach((item, count) -> buffer.merge(item, (double) count, Double::sum));
            material.cost().forEach((item, quantity) -> buffer.merge(item, -quantity, Double::sum));
            buffer.values().removeIf(quantity -> quantity < EPSILON);
        }
        SettlementData.get(server).setDirty();
        return true;
    }

    /**
     * Gives the whole items that the site did not use back to the stock of the village, up to its stock capacity.
     *
     * @param settlement the village
     * @param server     the server: the method marks the settlements as changed
     */
    void giveBack(Settlement settlement, MinecraftServer server) {
        int capacity = LevelTables.get().traits(settlement.level()).stockCapacity();
        buffer.forEach((item, quantity) -> {
            int whole = (int) Math.floor(quantity + EPSILON);
            if (whole > 0) {
                settlement.economy().stock().add(item, whole, capacity);
            }
        });
        buffer.clear();
        SettlementData.get(server).setDirty();
    }

    /**
     * Offers delivery quests for the materials that the site needs and that the stock does not have. A quest for a
     * rare or a valuable block comes at once. A quest for a common material comes after a wait of
     * {@link WorldConfig#CONSTRUCTION_MATERIAL_QUEST_MINUTES}. The village offers one quest at a time for each item. The
     * quest asks for the quantity that the stock does not have, up to the maximum quantity of a delivery.
     *
     * @param server     the server
     * @param settlement the village
     * @param needs      the materials that the rest of the site needs, by item id
     * @param now        the game time of the level of the site
     */
    void offerQuests(MinecraftServer server, Settlement settlement, Map<String, Need> needs, long now) {
        QuestData data = QuestData.get(server);
        Quests all = data.quests();
        QuestGenerator.Settings settings = QuestSystem.settings();
        long questTime = server.overworld().getGameTime();
        long delay = WorldConfig.CONSTRUCTION_MATERIAL_QUEST_MINUTES.get() * (long) SharedConstants.TICKS_PER_MINUTE;
        quests.values().removeIf(questId -> !isOpen(all, questId));
        boolean changed = false;
        for (Map.Entry<String, Need> entry : needs.entrySet()) {
            String item = entry.getKey();
            Need need = entry.getValue();
            int lacking = need.count() - settlement.economy().stock().count(item);
            Long since = waitingSince.get(item);
            boolean ask = need.rare() || (delay > 0 && since != null && now - since >= delay);
            if (lacking <= 0 || !ask || quests.containsKey(item) || villageAsksFor(all, settlement, item)) {
                continue;
            }
            int count = Math.min(lacking, settings.maxDeliveryCount());
            long reward = Math.max(1, Math.round(count * need.price() * settings.deliveryBonus()));
            Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, settlement.id(),
                    settlement.factionId(), item, count, reward, settings.deliveryReputation(),
                    QuestGenerator.minTier(reward, settings), questTime + settings.offerLifetime(),
                    settings.deliveryDuration());
            all.add(quest);
            quests.put(item, quest.id());
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
    }

    /** True if a quest is offered or accepted. */
    private static boolean isOpen(Quests all, String questId) {
        return all.byId(questId)
                .filter(quest -> quest.state() == QuestState.OFFERED || quest.state() == QuestState.ACCEPTED)
                .isPresent();
    }

    /** True if the village offers a delivery of an item, or a player has such a quest. */
    private static boolean villageAsksFor(Quests all, Settlement settlement, String item) {
        return all.all().stream().anyMatch(quest -> quest.kind() == QuestKind.DELIVERY
                && quest.settlementId().equals(settlement.id()) && quest.target().equals(item)
                && (quest.state() == QuestState.OFFERED || quest.state() == QuestState.ACCEPTED));
    }

    /**
     * Withdraws the offers of the site. An accepted quest stays: the player can finish it, and the items go into the
     * stock of the village.
     *
     * @param server the server
     */
    void withdrawOffers(MinecraftServer server) {
        QuestData data = QuestData.get(server);
        boolean changed = false;
        for (String questId : quests.values()) {
            boolean offered = data.quests().byId(questId).filter(quest -> quest.state() == QuestState.OFFERED)
                    .isPresent();
            if (offered) {
                changed |= data.quests().withdraw(questId);
            }
        }
        quests.clear();
        if (changed) {
            data.setDirty();
        }
    }

    /** The data of the materials in the saved data of the site. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag parts = new CompoundTag();
        buffer.forEach(parts::putDouble);
        tag.put("Buffer", parts);
        CompoundTag questIds = new CompoundTag();
        quests.forEach(questIds::putString);
        tag.put("Quests", questIds);
        return tag;
    }

    /** Reads the data of the materials from the saved data of the site. */
    void load(CompoundTag tag) {
        CompoundTag parts = tag.getCompound("Buffer");
        for (String item : parts.getAllKeys()) {
            if (parts.contains(item, Tag.TAG_DOUBLE) && parts.getDouble(item) > EPSILON) {
                buffer.put(item, parts.getDouble(item));
            }
        }
        CompoundTag questIds = tag.getCompound("Quests");
        for (String item : questIds.getAllKeys()) {
            if (questIds.contains(item, Tag.TAG_STRING)) {
                quests.put(item, questIds.getString(item));
            }
        }
    }
}
