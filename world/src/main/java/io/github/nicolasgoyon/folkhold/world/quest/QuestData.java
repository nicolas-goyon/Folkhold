package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The quests of a world: the offers of the villages and the quests of the players. Saved in the Overworld data
 * ({@code folkhold_quests.dat}).
 */
public final class QuestData extends SavedData {
    private static final String FILE_ID = "folkhold_quests";

    private final Quests quests = new Quests();

    private QuestData() {
    }

    public static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(QuestData::new, QuestData::load, null), FILE_ID);
    }

    /**
     * The quests. Call {@link #setDirty()} after a change.
     *
     * @return the registry
     */
    public Quests quests() {
        return quests;
    }

    /**
     * Replaces the id of a village in all quests.
     *
     * @param oldId the current id of the village
     * @param newId the new id
     */
    public void renameSettlement(String oldId, String newId) {
        quests.renameSettlement(oldId, newId);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Quest quest : quests.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", quest.id());
            entry.putString("Kind", quest.kind().serializedName());
            entry.putString("Settlement", quest.settlementId());
            entry.putString("Faction", quest.factionId());
            entry.putString("Target", quest.target());
            entry.putInt("Count", quest.count());
            entry.putLong("RewardEmeralds", quest.rewardEmeralds());
            entry.putInt("RewardReputation", quest.rewardReputation());
            entry.putString("MinTier", quest.minTier().serializedName());
            entry.putLong("OfferEnd", quest.offerEnd());
            entry.putLong("Duration", quest.duration());
            entry.putString("State", quest.state().serializedName());
            if (quest.playerId() != null) {
                entry.putUUID("Player", quest.playerId());
            }
            entry.putLong("Deadline", quest.deadline());
            list.add(entry);
        }
        tag.put("Quests", list);
        return tag;
    }

    /**
     * Reads the data from a tag. {@link #save(CompoundTag, HolderLookup.Provider)} writes this tag.
     *
     * @param tag        the tag
     * @param registries the registries
     * @return the data
     */
    public static QuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestData data = new QuestData();
        ListTag list = tag.getList("Quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Quest quest = new Quest(entry.getString("Id"), QuestKind.fromSerializedName(entry.getString("Kind")),
                    entry.getString("Settlement"), entry.getString("Faction"), entry.getString("Target"),
                    entry.getInt("Count"), entry.getLong("RewardEmeralds"), entry.getInt("RewardReputation"),
                    tier(entry.getString("MinTier")), entry.getLong("OfferEnd"), entry.getLong("Duration"));
            quest.restore(QuestState.fromSerializedName(entry.getString("State")),
                    entry.hasUUID("Player") ? entry.getUUID("Player") : null, entry.getLong("Deadline"));
            if (data.quests.byId(quest.id()).isEmpty()) {
                data.quests.add(quest);
            }
        }
        return data;
    }

    private static ReputationTier tier(String name) {
        try {
            return ReputationTier.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ReputationTier.NEUTRAL;
        }
    }
}
