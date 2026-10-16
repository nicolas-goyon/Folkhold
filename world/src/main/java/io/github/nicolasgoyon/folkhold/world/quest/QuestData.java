package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The quests of a world: the offers of the villages, the quests of the players, and the rewards that wait for a
 * player who was not in the game when a quest completed. Saved in the Overworld data ({@code folkhold_quests.dat}).
 */
public final class QuestData extends SavedData {
    private static final String FILE_ID = "folkhold_quests";

    private final Quests quests = new Quests();
    private final List<Reward> pendingRewards = new ArrayList<>();

    /**
     * The reward of a completed quest.
     *
     * @param playerId     the player
     * @param factionId    the faction that pays the emeralds
     * @param settlementId the village of the quest
     * @param emeralds     the emeralds, before the limit of the treasury
     * @param reputation   the reputation
     * @param name         the text of the quest
     */
    public record Reward(UUID playerId, String factionId, String settlementId, long emeralds, int reputation,
            Component name) {
    }

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
     * Keeps a reward for a player who is not in the game. The player gets it at the next login.
     *
     * @param reward the reward
     */
    public void addPendingReward(Reward reward) {
        pendingRewards.add(reward);
        setDirty();
    }

    /**
     * Takes the rewards that wait for a player.
     *
     * @param playerId the player
     * @return the rewards, in the order of the completions. The data does not keep them any more.
     */
    public List<Reward> takePendingRewards(UUID playerId) {
        List<Reward> taken = new ArrayList<>();
        Iterator<Reward> iterator = pendingRewards.iterator();
        while (iterator.hasNext()) {
            Reward reward = iterator.next();
            if (reward.playerId().equals(playerId)) {
                taken.add(reward);
                iterator.remove();
            }
        }
        if (!taken.isEmpty()) {
            setDirty();
        }
        return taken;
    }

    /** The number of rewards that wait for a player. */
    public int pendingRewardCount() {
        return pendingRewards.size();
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
        ListTag rewards = new ListTag();
        for (Reward reward : pendingRewards) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", reward.playerId());
            entry.putString("Faction", reward.factionId());
            entry.putString("Settlement", reward.settlementId());
            entry.putLong("Emeralds", reward.emeralds());
            entry.putInt("Reputation", reward.reputation());
            entry.putString("Name", Component.Serializer.toJson(reward.name(), registries));
            rewards.add(entry);
        }
        tag.put("PendingRewards", rewards);
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
        ListTag rewards = tag.getList("PendingRewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < rewards.size(); i++) {
            CompoundTag entry = rewards.getCompound(i);
            if (!entry.hasUUID("Player")) {
                continue;
            }
            Component name = Component.Serializer.fromJson(entry.getString("Name"), registries);
            data.pendingRewards.add(new Reward(entry.getUUID("Player"), entry.getString("Faction"),
                    entry.getString("Settlement"), entry.getLong("Emeralds"), entry.getInt("Reputation"),
                    name == null ? Component.empty() : name));
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
