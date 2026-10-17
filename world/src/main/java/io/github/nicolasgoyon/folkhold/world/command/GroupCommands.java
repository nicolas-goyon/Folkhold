package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.group.Caravans;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.quest.Escorts;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestSystem;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Commands of the caravans and the quests (operators only):
 * <ul>
 *   <li>{@code /folkhold caravan list}: the mobile groups, with their state and position.</li>
 *   <li>{@code /folkhold caravan info <id>}: the members, the cargo and the payment of a group.</li>
 *   <li>{@code /folkhold caravan spawn <from> <to>}: a caravan that leaves now, with up to 32 items of the largest
 *       stock of the origin.</li>
 *   <li>{@code /folkhold caravan tp <id>}: moves the player to a group.</li>
 *   <li>{@code /folkhold caravan remove <id>}: removes a group and its entities. Its escort fails.</li>
 *   <li>{@code /folkhold quest list [player]}: the active quests of a player.</li>
 *   <li>{@code /folkhold quest give <player> delivery|escort}: gives a quest of the nearest village to the
 *       player. An escort quest also makes its caravan, to the nearest village with a route.</li>
 *   <li>{@code /folkhold quest complete <id>} and {@code /folkhold quest fail <id>}: end an accepted quest.</li>
 * </ul>
 */
public final class GroupCommands {
    private static final String KEY = "commands.folkhold_world.";
    private static final int SPAWN_CARGO = 32;
    private static final int SHORT_ID = 8;

    private static final SuggestionProvider<CommandSourceStack> GROUP_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(MobileGroupData.get(context.getSource().getServer()).groups().all()
                    .stream().map(MobileGroup::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SETTLEMENT_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(SettlementData.get(context.getSource().getServer()).registry().all()
                    .stream().filter(Settlement::isActive).map(Settlement::id), builder);
    private static final SuggestionProvider<CommandSourceStack> QUEST_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(QuestData.get(context.getSource().getServer()).quests().all()
                    .stream().filter(quest -> quest.state() == QuestState.ACCEPTED).map(Quest::id), builder);

    private GroupCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("caravan")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(Commands.literal("list").executes(GroupCommands::list))
                        .then(Commands.literal("info").then(groupArgument().executes(GroupCommands::info)))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("from", StringArgumentType.word()).suggests(SETTLEMENT_IDS)
                                        .then(Commands.argument("to", StringArgumentType.word())
                                                .suggests(SETTLEMENT_IDS)
                                                .executes(GroupCommands::spawn))))
                        .then(Commands.literal("tp").then(groupArgument().executes(GroupCommands::teleport)))
                        .then(Commands.literal("remove").then(groupArgument().executes(GroupCommands::remove))))
                .then(Commands.literal("quest")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(Commands.literal("list")
                                .executes(context -> listQuests(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> listQuests(context,
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("delivery")
                                                .executes(context -> giveDelivery(context,
                                                        EntityArgument.getPlayer(context, "player"))))
                                        .then(Commands.literal("escort")
                                                .executes(context -> giveEscort(context,
                                                        EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("complete").then(questArgument()
                                .executes(context -> endQuest(context, true))))
                        .then(Commands.literal("fail").then(questArgument()
                                .executes(context -> endQuest(context, false))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> groupArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests(GROUP_IDS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> questArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS);
    }

    private static Optional<MobileGroup> group(CommandContext<CommandSourceStack> context) {
        return MobileGroupData.get(context.getSource().getServer()).groups()
                .byId(StringArgumentType.getString(context, "id"));
    }

    /** The name of a settlement, or its id if it is not known. */
    static String settlementName(MinecraftServer server, String id) {
        return SettlementData.get(server).registry().byId(id).map(Settlement::name).orElse(id);
    }

    /** The length of a route on each surface, in blocks. */
    static Map<Route.Surface, Double> surfaceLengths(Route route) {
        Map<Route.Surface, Double> lengths = new EnumMap<>(Route.Surface.class);
        for (Route.Surface surface : Route.Surface.values()) {
            lengths.put(surface, 0.0);
        }
        for (int i = 0; i < route.surfaces().size(); i++) {
            lengths.merge(route.surfaces().get(i), route.progressOf(i + 1) - route.progressOf(i), Double::sum);
        }
        return lengths;
    }

    /** The first characters of an id. */
    static String shortId(String id) {
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        List<MobileGroup> groups = List.copyOf(MobileGroupData.get(server).groups().all());
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.list.header", groups.size()), false);
        for (MobileGroup group : groups) {
            Route.Position position = group.position();
            source.sendSuccess(() -> Component.translatable(KEY + "caravan.list.entry", shortId(group.id()),
                    Component.translatable(KEY + "caravan.kind." + group.kind().serializedName()),
                    settlementName(server, group.originId()), settlementName(server, group.destinationId()),
                    Component.translatable(KEY + "caravan.state." + group.state().serializedName()),
                    Math.round(group.progress()), Math.round(group.route().length()), Math.round(position.x()),
                    Math.round(position.z())), false);
        }
        return groups.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<MobileGroup> found = group(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "caravan.unknown"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        MobileGroup group = found.get();
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.info.header", group.id(),
                Component.translatable(KEY + "caravan.kind." + group.kind().serializedName()), group.factionId(),
                Component.translatable(KEY + "caravan.state." + group.state().serializedName())), false);
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.info.journey",
                settlementName(server, group.originId()), settlementName(server, group.destinationId()),
                Math.round(group.progress()), Math.round(group.route().length()), group.departureTick(),
                GroupMaterializer.isMaterialized(group), GroupMaterializer.isKept(group.id())), false);
        Map<Route.Surface, Double> lengths = surfaceLengths(group.route());
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.info.route", group.route().points().size(),
                Component.translatable(KEY + (group.isRouteReady() ? "caravan.route.ready"
                        : "caravan.route.pending")),
                Math.round(lengths.get(Route.Surface.LAND)), Math.round(lengths.get(Route.Surface.FORD)),
                Math.round(lengths.get(Route.Surface.WATER))), false);
        for (GroupMember member : group.members()) {
            source.sendSuccess(() -> Component.translatable(KEY + "caravan.info.member",
                    Component.translatable(KEY + "caravan.role." + member.role().serializedName()),
                    String.format(Locale.ROOT, "%.1f", member.health()), member.id().toString()), false);
        }
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.info.cargo",
                ComponentUtils.formatList(group.cargo().asMap().entrySet(),
                        entry -> Component.translatable(KEY + "caravan.info.cargo_item", entry.getKey(),
                                entry.getValue())),
                group.payment()), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        SettlementData data = SettlementData.get(server);
        Optional<Settlement> from = data.registry().byId(StringArgumentType.getString(context, "from"))
                .filter(Settlement::isActive);
        Optional<Settlement> to = data.registry().byId(StringArgumentType.getString(context, "to"))
                .filter(Settlement::isActive);
        if (from.isEmpty() || to.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        Optional<MobileGroup> caravan = spawnCaravan(server, from.get(), to.get());
        if (caravan.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "caravan.no_route", from.get().name(), to.get().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.spawned", caravan.get().id(),
                from.get().name(), to.get().name()), true);
        return 1;
    }

    /**
     * Makes a caravan with up to 32 items of the largest stock of the origin. The caravan leaves now if its detailed
     * route is in the data. Else it leaves when the worker thread has calculated its detailed route (see
     * {@link CaravanRoutes#prepare}).
     *
     * @param server the server
     * @param from   the origin
     * @param to     the destination
     * @return the caravan, or an empty value if no route connects the two settlements
     */
    public static Optional<MobileGroup> spawnCaravan(MinecraftServer server, Settlement from, Settlement to) {
        Optional<Route> route = CaravanRoutes.route(server, from, to);
        if (route.isEmpty()) {
            return Optional.empty();
        }
        Optional<Map.Entry<String, Integer>> largest = from.economy().stock().asMap().entrySet().stream()
                .max(Map.Entry.comparingByValue());
        Map<String, Integer> cargo = Map.of();
        long payment = 0;
        if (largest.isPresent()) {
            String item = largest.get().getKey();
            int count = from.economy().stock().remove(item, SPAWN_CARGO);
            cargo = Map.of(item, count);
            double price = Prices.price(EconomyData.catalog().info(item), from.economy().stock().count(item),
                    from.economy().population(), Economies.settings());
            payment = Math.round(price * count);
            SettlementData.get(server).setDirty();
        }
        long tick = SettlementData.get(server).tickNumber();
        MobileGroup caravan = Caravans.create(from, to, route.get(), cargo, payment, tick);
        CaravanRoutes.prepare(server, caravan);
        if (caravan.isRouteReady()) {
            caravan.setState(GroupState.TRAVELING);
        }
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(caravan);
        data.setDirty();
        return Optional.of(caravan);
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<MobileGroup> found = group(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "caravan.unknown"));
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        Route.Position position = found.get().position();
        int x = (int) Math.floor(position.x());
        int z = (int) Math.floor(position.z());
        level.getChunk(x >> 4, z >> 4);
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(),
                player.getXRot());
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.teleported", shortId(found.get().id())),
                false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Optional<MobileGroup> found = group(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "caravan.unknown"));
            return 0;
        }
        MobileGroup group = found.get();
        group.setState(GroupState.LOST);
        Escorts.onGroupFinished(server, group);
        GroupMaterializer.keep(group.id(), false);
        GroupMaterializer.dematerialize(server.overworld(), group, false);
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().remove(group.id());
        data.setDirty();
        source.sendSuccess(() -> Component.translatable(KEY + "caravan.removed", shortId(group.id())), true);
        return 1;
    }

    private static int listQuests(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        List<Quest> quests = QuestData.get(server).quests().active(player.getUUID());
        source.sendSuccess(() -> Component.translatable(KEY + "quest.list.header", player.getDisplayName(),
                quests.size()), false);
        for (Quest quest : quests) {
            source.sendSuccess(() -> Component.translatable(KEY + "quest.list.entry", quest.id(),
                    QuestBoards.name(server, quest), settlementName(server, quest.settlementId()),
                    quest.rewardEmeralds()), false);
        }
        return quests.size();
    }

    /** The active village nearest to a player. */
    private static Optional<Settlement> nearestVillage(MinecraftServer server, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return SettlementData.get(server).registry().all().stream().filter(Settlement::isActive)
                .min(Comparator.comparingLong(settlement -> settlement.distanceSquared(pos.getX(), pos.getZ())));
    }

    private static int giveDelivery(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Optional<Settlement> village = nearestVillage(server, player);
        if (village.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        QuestSystem.offerDeliveries(server, village.get(), server.overworld().getGameTime());
        Optional<Quest> offer = QuestData.get(server).quests().offers(village.get().id()).stream()
                .filter(quest -> quest.kind() == QuestKind.DELIVERY).findFirst();
        if (offer.isEmpty() || !QuestBoards.give(player, offer.get().id())) {
            source.sendFailure(Component.translatable(KEY + "quest.no_offer", village.get().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "quest.given", QuestBoards.name(server, offer.get()),
                player.getDisplayName()), true);
        return 1;
    }

    private static int giveEscort(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Optional<Settlement> origin = nearestVillage(server, player);
        if (origin.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        List<Settlement> destinations = SettlementData.get(server).registry().all().stream()
                .filter(settlement -> settlement.isActive() && !settlement.id().equals(origin.get().id()))
                .sorted(Comparator.comparingLong(settlement ->
                        settlement.distanceSquared(origin.get().x(), origin.get().z())))
                .toList();
        for (Settlement destination : destinations) {
            Optional<MobileGroup> caravan = spawnCaravan(server, origin.get(), destination);
            if (caravan.isEmpty()) {
                continue;
            }
            caravan.get().setState(GroupState.WAITING);
            Optional<Quest> quest = Escorts.offer(server, caravan.get(), origin.get(),
                    SettlementData.get(server).tickNumber());
            if (quest.isPresent() && QuestBoards.give(player, quest.get().id())) {
                source.sendSuccess(() -> Component.translatable(KEY + "quest.given",
                        QuestBoards.name(server, quest.get()), player.getDisplayName()), true);
                return 1;
            }
            break;
        }
        source.sendFailure(Component.translatable(KEY + "quest.no_offer", origin.get().name()));
        return 0;
    }

    private static int endQuest(CommandContext<CommandSourceStack> context, boolean complete) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Optional<Quest> quest = QuestData.get(server).quests().byId(StringArgumentType.getString(context, "id"))
                .filter(found -> found.state() == QuestState.ACCEPTED);
        if (quest.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "quest.unknown"));
            return 0;
        }
        boolean done;
        if (complete) {
            QuestBoards.completeAndNotify(server, quest.get());
            done = quest.get().state() == QuestState.COMPLETED;
        } else {
            done = QuestBoards.fail(server, quest.get());
        }
        source.sendSuccess(() -> Component.translatable(KEY + (complete ? "quest.completed" : "quest.failed"),
                QuestBoards.name(server, quest.get())), true);
        return done ? 1 : 0;
    }
}
