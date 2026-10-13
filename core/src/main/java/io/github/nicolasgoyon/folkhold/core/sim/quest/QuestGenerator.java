package io.github.nicolasgoyon.folkhold.core.sim.quest;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Makes the quests of the villages from the state of the world.
 *
 * <ul>
 *   <li>The number of offers of a village comes from its level (see {@link #offerCount(int)}).</li>
 *   <li>A delivery quest asks for an item that the village needs. The needs with the most value come first. A
 *       village does not offer two deliveries of the same item.</li>
 *   <li>The emerald reward is the local price of the items, multiplied by a bonus. A quest with a large reward is
 *       only for the friend tier and the ally tier.</li>
 * </ul>
 */
public final class QuestGenerator {

    /**
     * Settings of the generation.
     *
     * @param offerLifetime         the time from the offer to the end of the offer, in ticks
     * @param deliveryDuration      the time from the acceptance of a delivery to its deadline, in ticks
     * @param deliveryBonus         the factor of the local price for the reward of a delivery
     * @param deliveryReputation    the reputation reward of a delivery
     * @param maxDeliveryCount      the maximum quantity of items of a delivery
     * @param friendRewardThreshold a quest with this reward in emeralds or more is only for the friends
     */
    public record Settings(long offerLifetime, long deliveryDuration, double deliveryBonus, int deliveryReputation,
            int maxDeliveryCount, long friendRewardThreshold) {
        public Settings {
            if (offerLifetime < 1 || deliveryDuration < 1 || deliveryBonus <= 0 || maxDeliveryCount < 1) {
                throw new IllegalArgumentException("Invalid quest settings");
            }
        }
    }

    private QuestGenerator() {
    }

    /**
     * The number of offers of a village: 1, plus 1 for each 5 levels (1 to 5 for the levels 1 to 20).
     *
     * @param level the level of the village
     * @return the number of offers
     */
    public static int offerCount(int level) {
        return 1 + Math.max(0, level) / 5;
    }

    /**
     * The lowest reputation tier for a reward.
     *
     * @param rewardEmeralds the emerald reward
     * @param settings       the settings
     * @return {@link ReputationTier#FRIEND} for a large reward, else {@link ReputationTier#NEUTRAL}
     */
    public static ReputationTier minTier(long rewardEmeralds, Settings settings) {
        return rewardEmeralds >= settings.friendRewardThreshold() ? ReputationTier.FRIEND : ReputationTier.NEUTRAL;
    }

    /**
     * Makes delivery quests for the needs of a village.
     *
     * @param settlementId  the id of the village
     * @param factionId     the id of its faction
     * @param needs         the needs: the missing quantity of each item
     * @param price         the local price of one item
     * @param offeredItems  the items of the deliveries that the village already offers
     * @param wanted        the maximum number of new quests
     * @param now           the game time
     * @param settings      the settings
     * @return the new quests
     */
    public static List<Quest> deliveries(String settlementId, String factionId, Map<String, Integer> needs,
            ToDoubleFunction<String> price, Collection<String> offeredItems, int wanted, long now,
            Settings settings) {
        List<Map.Entry<String, Integer>> candidates = needs.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && !offeredItems.contains(entry.getKey()))
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) ->
                        entry.getValue() * price.applyAsDouble(entry.getKey())).reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        List<Quest> quests = new ArrayList<>();
        for (Map.Entry<String, Integer> need : candidates) {
            if (quests.size() >= wanted) {
                break;
            }
            int count = Math.min(need.getValue(), settings.maxDeliveryCount());
            double value = count * price.applyAsDouble(need.getKey());
            long reward = Math.max(1, Math.round(value * settings.deliveryBonus()));
            quests.add(new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, settlementId, factionId,
                    need.getKey(), count, reward, settings.deliveryReputation(), minTier(reward, settings),
                    now + settings.offerLifetime(), settings.deliveryDuration()));
        }
        return quests;
    }
}
