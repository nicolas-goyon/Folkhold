package io.github.nicolasgoyon.folkhold.world.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.config.TestConfig;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscortsTest {
    @BeforeAll
    static void loadConfig() {
        TestConfig.loadWorldConfig();
    }

    /** The time of an escort: the minimum for a short route, more for a long route. */
    @Test
    void escortTimeFollowsTheRouteLength() {
        long minimum = WorldConfig.QUEST_ESCORT_MINUTES.get();
        long expected = (long) Math.ceil(20_000.0 / WorldConfig.QUEST_ESCORT_BLOCKS_PER_MINUTE.get()) + 10;

        assertEquals(minimum, Escorts.escortMinutes(100));
        assertEquals(Math.max(minimum, expected), Escorts.escortMinutes(20_000));
    }

    /** The size of an ambush: 2, plus 1 for each 500 blocks and for each 5 levels, 8 at most. */
    @Test
    void ambushSizeFollowsTheRouteAndTheLevel() {
        assertEquals(2, Escorts.ambushSize(100, 1));
        assertEquals(3, Escorts.ambushSize(500, 1));
        assertEquals(4, Escorts.ambushSize(500, 5));
        assertEquals(8, Escorts.ambushSize(5000, 30));
    }

    /** The plan of the ambushes: 1 to 3 points between 20 % and 90 % of the route, the same for one quest id. */
    @Test
    void ambushPlanIsStableForAQuest() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(1000, 0)));
        MobileGroup caravan = new MobileGroup("caravan", GroupKind.CARAVAN, "faction", "origin", "destination",
                route);
        Quest quest = new Quest("quest-plan", QuestKind.ESCORT, "origin", "faction", "caravan", 0, 10, 10,
                ReputationTier.NEUTRAL, 1000, 24_000);

        Deque<Double> plan = Escorts.plan(quest, caravan);

        assertTrue(plan.size() >= 1 && plan.size() <= 3, "1 to 3 ambushes, found " + plan.size());
        assertTrue(plan.stream().allMatch(point -> point >= 200.0 && point <= 900.0),
                "The ambushes are between 20 % and 90 % of the route: " + plan);
        assertEquals(List.copyOf(plan), List.copyOf(Escorts.plan(quest, caravan)));
    }
}
