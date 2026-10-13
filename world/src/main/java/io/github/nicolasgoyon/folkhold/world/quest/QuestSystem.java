package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.group.VillageTrade;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;

/**
 * The quests of the villages. A {@link io.github.nicolasgoyon.folkhold.world.settlement.WorldSystem} that runs on
 * each abstract tick.
 *
 * <ul>
 *   <li>On each abstract tick, the offers after their end expire, and the accepted quests after their deadline fail
 *       (see {@link Quests#expire(long)}). The offers of a village that is not active end.</li>
 *   <li>On each cycle, each active village with an economy makes new delivery offers from its needs, up to its
 *       number of offers (see {@link QuestGenerator}).</li>
 *   <li>The listeners get each quest that changes state here (for the messages to the players).</li>
 * </ul>
 */
public final class QuestSystem {
    private static final long TICKS_PER_MINUTE = 1200L;
    private static final List<BiConsumer<MinecraftServer, Quest>> LISTENERS = new ArrayList<>();

    private QuestSystem() {
    }

    /**
     * Adds a listener for the quests that expire or fail in the system.
     *
     * @param listener the listener
     */
    public static void addListener(BiConsumer<MinecraftServer, Quest> listener) {
        LISTENERS.add(listener);
    }

    /** The settings of the generation from the server configuration. */
    public static QuestGenerator.Settings settings() {
        return new QuestGenerator.Settings(WorldConfig.QUEST_OFFER_MINUTES.get() * TICKS_PER_MINUTE,
                WorldConfig.QUEST_DELIVERY_MINUTES.get() * TICKS_PER_MINUTE, WorldConfig.QUEST_DELIVERY_BONUS.get(),
                WorldConfig.QUEST_DELIVERY_REPUTATION.get(), WorldConfig.QUEST_DELIVERY_MAX_COUNT.get(),
                WorldConfig.QUEST_FRIEND_REWARD.get());
    }

    /** Runs the quests for one abstract tick. */
    public static void tick(MinecraftServer server, TickInfo tick) {
        QuestData data = QuestData.get(server);
        Quests quests = data.quests();
        long now = server.overworld().getGameTime();
        List<Quest> changed = new ArrayList<>(quests.expire(now));
        SettlementData settlements = SettlementData.get(server);
        for (Quest quest : List.copyOf(quests.all())) {
            if (quest.state() == QuestState.OFFERED && settlements.registry().byId(quest.settlementId())
                    .filter(Settlement::isActive).isEmpty()) {
                quests.withdraw(quest.id());
            }
        }
        changed.forEach(quest -> LISTENERS.forEach(listener -> listener.accept(server, quest)));
        quests.removeFinished();
        if (tick.cycle()) {
            for (Settlement settlement : settlements.registry().all()) {
                if (settlement.isActive()) {
                    offerDeliveries(server, settlement, now);
                }
            }
        }
        data.setDirty();
    }

    /**
     * Makes the delivery offers of a village, up to its number of offers.
     *
     * @param server     the server
     * @param settlement the village
     * @param now        the game time
     * @return the new quests
     */
    public static List<Quest> offerDeliveries(MinecraftServer server, Settlement settlement, long now) {
        if (!EconomyData.isActive() || !settlement.economy().isInitialized()) {
            return List.of();
        }
        Quests quests = QuestData.get(server).quests();
        List<Quest> offers = quests.offers(settlement.id());
        int wanted = QuestGenerator.offerCount(settlement.level()) - offers.size();
        if (wanted <= 0) {
            return List.of();
        }
        ItemCatalog catalog = EconomyData.catalog();
        int population = settlement.economy().population();
        Map<String, Integer> needs = TradeMatcher.needs(settlement.economy().stock(), population, catalog,
                VillageTrade.usedItems(settlement, catalog));
        Set<String> offered = offers.stream().filter(quest -> quest.kind() == QuestKind.DELIVERY)
                .map(Quest::target).collect(Collectors.toSet());
        List<Quest> created = QuestGenerator.deliveries(settlement.id(), settlement.factionId(), needs,
                item -> Prices.price(catalog.info(item), settlement.economy().stock().count(item), population,
                        Economies.settings()), offered, wanted, now, settings());
        created.forEach(quests::add);
        QuestData.get(server).setDirty();
        return created;
    }
}
