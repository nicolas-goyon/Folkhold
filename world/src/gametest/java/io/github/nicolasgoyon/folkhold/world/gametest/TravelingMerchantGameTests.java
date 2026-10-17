package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupSystem;
import io.github.nicolasgoyon.folkhold.world.group.TravelingMerchants;
import io.github.nicolasgoyon.folkhold.world.group.TravelingMerchants.RareItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the traveling merchants: rare items, offers, stay at a village, restock. See {@link TestSupport}
 * for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelingMerchantGameTests {
    private static final int FAR_X = 23_000_000;

    private TravelingMerchantGameTests() {
    }

    /** A traveling merchant far from the tests, with its rare items. */
    private static MobileGroup merchant() {
        Route route = new Route(List.of(new Route.Point(FAR_X, 0), new Route.Point(FAR_X + 500, 0)));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.TRAVELING_MERCHANT,
                "test_neutral", "test_origin", "test_destination", route);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 22.0F));
        for (RareItem rare : TravelingMerchants.itemsOf(group)) {
            group.cargo().set(rare.item(), rare.max());
        }
        return group;
    }

    /** At the end of its route, a traveling merchant stays near the village. It does not arrive. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "traveling_stay")
    public static void merchantStaysAtTheEndOfItsRoute(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MobileGroup group = merchant();
        group.setState(GroupState.TRAVELING);
        group.setProgress(group.route().length() - 1.0);
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(group);

        MobileGroupSystem.tick(server, new TickInfo(1000, false));

        boolean kept = data.groups().byId(group.id()).isPresent();
        data.groups().remove(group.id());
        helper.assertTrue(kept, "The traveling merchant must stay in the world");
        helper.assertTrue(group.state() == GroupState.STOPPED, "The traveling merchant must stop");
        helper.assertTrue(group.departureTick() == 1000 + WorldConfig.TRAVELING_STAY_TICKS.get(),
                "The traveling merchant must stay for the configured time: " + group.departureTick());
        helper.succeed();
    }
}
