package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The caravans, the traveling merchants and the quests of the world, for the admin screen. The client shows them in
 * the tabbed screen of the village heart (see {@link VillageInfoPayload}).
 *
 * <ul>
 *   <li>Caravans and traveling merchants: the first characters of the id (for the commands), the journey, the
 *       state, the progress, the cargo and the position. A group with entities has a mark.</li>
 *   <li>Quests: the village, the quest, the state, the player (or "offer") and the reward.</li>
 * </ul>
 */
public final class GroupOverview {
    private static final String KEY = "screen.folkhold_world.admin.groups.";
    private static final int SHORT_ID = 8;

    private GroupOverview() {
    }

    /**
     * Builds the overview.
     *
     * @param server the server
     * @return the payload of the screen
     */
    public static VillageInfoPayload build(MinecraftServer server) {
        List<Row> caravans = new ArrayList<>();
        List<Row> merchants = new ArrayList<>();
        for (MobileGroup group : MobileGroupData.get(server).groups().all()) {
            Route.Position position = group.position();
            Component label = Component.translatable(KEY + "group", shortId(group.id()),
                    name(server, group.originId()), name(server, group.destinationId()),
                    GroupMaterializer.isMaterialized(group) ? Component.translatable(KEY + "materialized")
                            : Component.empty());
            Component value = Component.translatable(KEY + "group_value",
                    Component.translatable("commands.folkhold_world.caravan.state." + group.state().serializedName()),
                    Math.round(group.progress()), Math.round(group.route().length()), group.cargo().total(),
                    Math.round(position.x()), Math.round(position.z()));
            (group.kind() == GroupKind.TRAVELING_MERCHANT ? merchants : caravans).add(new Row(label, value));
        }
        List<Row> quests = new ArrayList<>();
        for (Quest quest : QuestData.get(server).quests().all()) {
            Component label = Component.translatable(KEY + "quest", name(server, quest.settlementId()),
                    QuestBoards.name(server, quest));
            Component player = quest.state() == QuestState.OFFERED ? Component.translatable(KEY + "offer")
                    : playerName(server, quest);
            Component value = Component.translatable(KEY + "quest_value",
                    Component.translatable(KEY + "state." + quest.state().serializedName()), player,
                    quest.rewardEmeralds());
            quests.add(new Row(label, value));
        }
        return new VillageInfoPayload(Component.translatable(KEY + "title"), List.of(
                new Section(Component.translatable(KEY + "caravans"), caravans),
                new Section(Component.translatable(KEY + "traveling_merchants"), merchants),
                new Section(Component.translatable(KEY + "quests"), quests)));
    }

    private static Component playerName(MinecraftServer server, Quest quest) {
        if (quest.playerId() == null) {
            return Component.literal("?");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(quest.playerId());
        return player != null ? player.getDisplayName() : Component.literal(quest.playerId().toString());
    }

    private static String name(MinecraftServer server, String settlementId) {
        return SettlementData.get(server).registry().byId(settlementId).map(Settlement::name).orElse("?");
    }

    private static String shortId(String id) {
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }
}
