package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationTicker;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Commands for the abstract simulation (permission level 2):
 * <ul>
 *   <li>{@code /folkhold settlement list [faction]}: the settlements, nearest first.</li>
 *   <li>{@code /folkhold settlement info [id]}: level, population, jobs, main stocks with their price, claims.
 *       With no id: the nearest settlement.</li>
 *   <li>{@code /folkhold settlement level <id> <level>}: sets the level.</li>
 *   <li>{@code /folkhold settlement stock <id> <item> <count>}: sets a stock.</li>
 *   <li>{@code /folkhold settlement route <from> <to>}: calculates the route between two settlements again and
 *       shows its length and the time of the calculation (see {@link CaravanRoutes}).</li>
 *   <li>{@code /folkhold admin}: opens the admin screen (see {@link AdminActions}).</li>
 *   <li>{@code /folkhold sim tick [count]} and {@code /folkhold sim cycle [count]}: run the simulation now for all
 *       active settlements and for the world systems (for example the mobile groups).</li>
 *   <li>{@code /folkhold sim profile [reset]}: the time measurements of the systems of the simulation (see
 *       {@link SimulationProfiler}), and the routes on the way.</li>
 * </ul>
 */
public final class SettlementCommands {
    private static final String KEY = "commands.folkhold_world.";
    private static final int MAX_RUNS = 1000;
    private static final int INFO_STOCKS = 12;

    private static final SuggestionProvider<CommandSourceStack> SETTLEMENT_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(SettlementData.get(context.getSource().getServer()).registry().all()
                    .stream().map(Settlement::id), builder);

    private SettlementCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("settlement")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(Commands.literal("list")
                                .executes(context -> list(context, null))
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                FactionData.get(context.getSource().getServer()).factions().stream()
                                                        .map(FactionSpec::id), builder))
                                        .executes(context -> list(context,
                                                StringArgumentType.getString(context, "faction")))))
                        .then(Commands.literal("info")
                                .executes(context -> info(context, nearest(context)))
                                .then(idArgument().executes(context -> info(context, byId(context)))))
                        .then(Commands.literal("level")
                                .then(idArgument()
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(SettlementCommands::setLevel))))
                        .then(Commands.literal("stock")
                                .then(idArgument()
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> SharedSuggestionProvider
                                                        .suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                        .executes(SettlementCommands::setStock)))))
                        .then(Commands.literal("route")
                                .then(Commands.argument("from", StringArgumentType.word()).suggests(SETTLEMENT_IDS)
                                        .then(Commands.argument("to", StringArgumentType.word())
                                                .suggests(SETTLEMENT_IDS)
                                                .executes(SettlementCommands::route)))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .executes(context -> {
                            AdminActions.sendList(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("sim")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(runLiteral("tick", false))
                        .then(runLiteral("cycle", true))
                        .then(Commands.literal("profile")
                                .executes(SettlementCommands::profile)
                                .then(Commands.literal("reset").executes(context -> {
                                    SimulationProfiler.reset();
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable(KEY + "sim.profile.reset"), false);
                                    return 1;
                                })))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests(SETTLEMENT_IDS);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> runLiteral(String name, boolean cycle) {
        return Commands.literal(name)
                .executes(context -> run(context, cycle, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_RUNS))
                        .executes(context -> run(context, cycle, IntegerArgumentType.getInteger(context, "count"))));
    }

    private static Optional<Settlement> byId(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        return SettlementData.get(context.getSource().getServer()).registry().byId(id);
    }

    private static Optional<Settlement> nearest(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        return SettlementData.get(context.getSource().getServer()).registry().all().stream()
                .min(Comparator.comparingLong(settlement -> settlement.distanceSquared(pos.getX(), pos.getZ())));
    }

    private static Component factionName(MinecraftServer server, String factionId) {
        return FactionData.get(server).byId(factionId).<Component>map(FactionVisuals::name)
                .orElse(Component.literal(factionId));
    }

    private static Component state(Settlement settlement) {
        return Component.translatable(KEY + "settlement.state." + settlement.state().serializedName());
    }

    private static int list(CommandContext<CommandSourceStack> context, String factionId) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        BlockPos pos = BlockPos.containing(source.getPosition());
        List<Settlement> settlements = SettlementData.get(server).registry().all().stream()
                .filter(settlement -> factionId == null || settlement.factionId().equals(factionId))
                .sorted(Comparator.comparingLong(settlement -> settlement.distanceSquared(pos.getX(), pos.getZ())))
                .toList();
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.list.header", settlements.size()), false);
        for (Settlement settlement : settlements) {
            source.sendSuccess(() -> Component.translatable(KEY + "settlement.list.entry", settlement.name(),
                    settlement.id(), factionName(server, settlement.factionId()), settlement.level(),
                    SettlementLevels.tierName(settlement.level()), state(settlement), settlement.x(),
                    settlement.z()), false);
        }
        return settlements.size();
    }

    private static int info(CommandContext<CommandSourceStack> context, Optional<Settlement> found) {
        CommandSourceStack source = context.getSource();
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        Settlement settlement = found.get();
        EconomyState economy = settlement.economy();
        SettlementData data = SettlementData.get(server);
        int needed = LevelTables.get().traits(settlement.level()).experience();
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.header", settlement.name(),
                settlement.id(), factionName(server, settlement.factionId()), state(settlement)), false);
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.level", settlement.level(),
                SettlementLevels.tierName(settlement.level()), settlement.experience(), needed), false);
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.position", settlement.x(),
                settlement.z(), settlement.biome(), data.claims().cellCount(settlement.id())), false);
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.economy", economy.population(),
                Math.round(economy.foodCovered() * 100), Math.round(economy.jobsWorking() * 100),
                data.treasury(settlement.factionId())), false);
        Map<String, Integer> jobs = new TreeMap<>();
        economy.jobs().forEach(job -> jobs.merge(job, 1, Integer::sum));
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.jobs",
                ComponentUtils.formatList(jobs.entrySet(), entry -> Component.translatable(KEY + "settlement.info.job",
                        entry.getKey(), entry.getValue()))), false);
        List<Map.Entry<String, Integer>> stocks = economy.stock().asMap().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(INFO_STOCKS)
                .toList();
        for (Map.Entry<String, Integer> stock : stocks) {
            ItemInfo item = EconomyData.catalog().info(stock.getKey());
            double price = Prices.price(item, stock.getValue(), economy.population(), Economies.settings());
            source.sendSuccess(() -> Component.translatable(KEY + "settlement.info.stock", stock.getKey(),
                    stock.getValue(), Merchants.priceText(price)), false);
        }
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Settlement> settlement = byId(context);
        if (settlement.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        int level = LevelTables.get().clamp(IntegerArgumentType.getInteger(context, "level"));
        SettlementLevels.setProgress(source.getServer(), settlement.get(), level, 0);
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.level.set", settlement.get().name(), level,
                SettlementLevels.tierName(level)), true);
        return level;
    }

    private static int setStock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Settlement> settlement = byId(context);
        if (settlement.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        ResourceLocation item = ResourceLocationArgument.getId(context, "item");
        if (!BuiltInRegistries.ITEM.containsKey(item)) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown_item", item.toString()));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        settlement.get().economy().stock().set(item.toString(), count);
        SettlementData.get(source.getServer()).setDirty();
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.stock.set", settlement.get().name(),
                item.toString(), count), true);
        return count;
    }

    private static int route(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        SettlementData data = SettlementData.get(server);
        Optional<Settlement> from = data.registry().byId(StringArgumentType.getString(context, "from"));
        Optional<Settlement> to = data.registry().byId(StringArgumentType.getString(context, "to"));
        if (from.isEmpty() || to.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "settlement.unknown"));
            return 0;
        }
        CaravanRoutes.Calculation calculation = CaravanRoutes.calculate(server, from.get(), to.get());
        String millis = String.format(Locale.ROOT, "%.1f", calculation.nanos() / 1_000_000.0);
        if (calculation.route().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "settlement.route.none", from.get().name(),
                    to.get().name(), calculation.examined(), millis), false);
            return 0;
        }
        Route route = calculation.route().get();
        source.sendSuccess(() -> Component.translatable(KEY + "settlement.route.found", from.get().name(),
                to.get().name(), Math.round(route.length()), route.points().size(), calculation.examined(), millis),
                false);
        return 1;
    }

    private static int profile(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<SimulationProfiler.Entry> entries = SimulationProfiler.entries();
        source.sendSuccess(() -> Component.translatable(KEY + "sim.profile.header", entries.size(),
                CaravanRoutes.pending(), CaravanRoutes.pendingDetails()), false);
        for (SimulationProfiler.Entry entry : entries) {
            source.sendSuccess(() -> Component.translatable(KEY + "sim.profile.entry", entry.name(), entry.calls(),
                    millis(entry.averageNanos()), millis(entry.maxNanos()), millis(entry.lastNanos()),
                    millis(entry.totalNanos())), false);
        }
        return entries.size();
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean cycle, int count) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        SettlementData data = SettlementData.get(server);
        List<Settlement> active = data.registry().all().stream().filter(Settlement::isActive).toList();
        long number = data.tickNumber();
        for (int run = 1; run <= count; run++) {
            TickInfo tick = new TickInfo(number + run, cycle);
            for (Settlement settlement : active) {
                if (settlement.isActive()) {
                    SimulationTicker.runSystems(server, settlement, tick);
                }
            }
            SimulationTicker.runWorldSystems(server, tick);
        }
        source.sendSuccess(() -> Component.translatable(KEY + (cycle ? "sim.cycle" : "sim.tick"), count,
                active.size()), true);
        return count;
    }
}
