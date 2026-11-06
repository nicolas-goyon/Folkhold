package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;

/**
 * The effects of the end of a raid at a faction village on the abstract simulation. {@link RaidHelpers} calls
 * {@link #apply} one time for each raid, at the first server tick after its victory or its loss.
 *
 * <ul>
 *   <li>Village that holds ({@link VillageRaids#holds}): the settlement keeps its experience and its stocks. Each
 *       helper gets {@code raidEffects.holdReputation} with the faction and the village.</li>
 *   <li>Village that falls: each helper gets {@code raidEffects.fallReputation}. The settlement loses
 *       {@code raidEffects.fallExperiencePercent} % of the experience of its current level and
 *       {@code raidEffects.fallStockPercent} % of each item of its stocks, only when {@link #paysFallPenalty} is
 *       true.</li>
 * </ul>
 * The players who do not help get no change. A settlement that is not active (ruins) gets no change.
 */
public final class RaidEffects {

    private RaidEffects() {
    }

    /**
     * True when the settlement of a raid that ends loses experience and stocks. The village falls when it does not
     * hold. A village that falls and wins the raid (the garrison died, also a garrison with no living unit at the
     * start, and the players killed the raiders) with a heart that is not broken keeps its experience and its stocks:
     * the players saved it. This village still falls for the rewards and the balance tests.
     *
     * @param holds       true when the village holds ({@link VillageRaids#holds})
     * @param victory     true when the raid ends in a victory
     * @param heartBroken true when the heart of the village is broken during the raid
     * @return true when the settlement loses experience and stocks
     */
    public static boolean paysFallPenalty(boolean holds, boolean victory, boolean heartBroken) {
        return !holds && (!victory || heartBroken);
    }

    /**
     * The experience that a settlement loses after a fall.
     *
     * @param levelExperience the experience of the current level of the settlement (the experience to the next level)
     * @param percent         the loss, in percent of {@code levelExperience} (0 to 100)
     * @return the loss, rounded to the nearest value, 0 or more
     */
    public static int experienceLoss(int levelExperience, int percent) {
        return (int) Math.round(Math.max(0, levelExperience) * Math.max(0, Math.min(100, percent)) / 100.0);
    }

    /**
     * Removes a part of each item of a stock. The loss of each item is rounded down: an item with a small count can
     * stay complete.
     *
     * @param stock   the stock
     * @param percent the loss, in percent of each item (0 to 100)
     * @return the total count of items that the stock loses
     */
    public static int removeStocks(Stock stock, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int removed = 0;
        for (Map.Entry<String, Integer> entry : List.copyOf(stock.asMap().entrySet())) {
            removed += stock.remove(entry.getKey(), (int) ((long) entry.getValue() * clamped / 100));
        }
        return removed;
    }

    /**
     * The reputation change of a helper at the end of a raid.
     *
     * @param holds      true when the village holds
     * @param holdChange the change when the village holds
     * @param fallChange the change when the village falls
     * @return the change
     */
    public static int reputationChange(boolean holds, int holdChange, int fallChange) {
        return holds ? holdChange : fallChange;
    }

    /**
     * Applies the effects of the end of a raid.
     *
     * @param level       the level of the raid
     * @param raid        the raid, after its victory or its loss
     * @param villageId   the settlement of the village, or null when the fall check did not find the heart
     * @param heartBroken true when the heart of the village is broken during the raid
     * @param helpers     the helpers of the raid
     */
    static void apply(ServerLevel level, Raid raid, @Nullable String villageId, boolean heartBroken,
            Set<UUID> helpers) {
        MinecraftServer server = level.getServer();
        String id = villageId;
        if (id == null) {
            id = VillageRaids.heartOf(raid, level)
                    .map(pos -> level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart
                            ? heart.getVillageId().toString() : null)
                    .orElse(null);
        }
        SettlementData data = SettlementData.get(server);
        Optional<Settlement> found = id == null ? Optional.empty() : data.registry().byId(id);
        if (found.isEmpty()) {
            return;
        }
        Settlement settlement = found.get();
        boolean holds = VillageRaids.holds(level, raid);
        int experience = 0;
        int stocks = 0;
        if (settlement.isActive() && paysFallPenalty(holds, raid.isVictory(), heartBroken)) {
            experience = experienceLoss(LevelTables.get().traits(settlement.level()).experience(),
                    WorldConfig.RAID_FALL_EXPERIENCE_PERCENT.get());
            SettlementLevels.addExperience(server, settlement, -experience);
            stocks = removeStocks(settlement.economy().stock(), WorldConfig.RAID_FALL_STOCK_PERCENT.get());
            data.setDirty();
        }
        int change = reputationChange(holds, WorldConfig.RAID_HOLD_REPUTATION.get(),
                WorldConfig.RAID_FALL_REPUTATION.get());
        for (UUID helper : helpers) {
            Reputations.apply(server, helper, settlement.factionId(), settlement.id(), change);
        }
        FolkholdWorld.LOGGER.info("Raid {} at {}: {}, experience -{}, stocks -{}, {} helpers with reputation {}",
                raid.getId(), settlement.name(), holds ? "holds" : "falls", experience, stocks, helpers.size(),
                change);
    }
}
