package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload.Summary;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationTicker;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of the admin screen. An operator (permission level 2) sees all settlements of the world, also
 * the settlements that no player found, and can change them.
 *
 * <p>Actions ({@link AdminActionPayload#action()}):
 * <ul>
 *   <li>{@code refresh}: sends the list again.</li>
 *   <li>{@code details}: sends the details of the settlement.</li>
 *   <li>{@code teleport}: moves the player to the village heart.</li>
 *   <li>{@code level_set}: sets the level to the value, with 0 experience.</li>
 *   <li>{@code experience_add}: adds the value to the experience. The level changes if necessary.</li>
 *   <li>{@code cycle}: runs one economic cycle of the settlement now.</li>
 *   <li>{@code economy_reset}: removes the stock and starts the economy again (population, jobs, start stock).</li>
 *   <li>{@code ruin}: destroys the settlement as in the game ({@link Settlements#ruin}), with no player as
 *       cause.</li>
 *   <li>{@code restore}: makes the settlement active again, with its heart and its bank
 *       ({@link Settlements#restore}).</li>
 *   <li>{@code alert}: sets the alert state of the village heart to the {@link AlertState} with the ordinal of the
 *       value. The heart must be loaded.</li>
 *   <li>{@code bells}: rings the bells of the village. The heart must be loaded.</li>
 *   <li>{@code tick_all} and {@code cycle_all}: run one abstract tick or one cycle of all active settlements and
 *       of the world systems (for example the caravans).</li>
 *   <li>{@code groups}: sends the caravans, the traveling merchants and the quests (see {@link GroupOverview}).
 *       The client shows them in a tabbed screen.</li>
 * </ul>
 * After an action, the server sends the list and the details of the settlement again.
 */
public final class AdminActions {
    /** The permission level of the admin screen and of its actions. */
    public static final int PERMISSION_LEVEL = 2;

    private static final String KEY = "screen.folkhold_world.admin.";
    private static final int DETAIL_STOCKS = 8;

    private AdminActions() {
    }

    /**
     * True if the player can use the admin screen.
     *
     * @param player the player
     * @return true for an operator
     */
    public static boolean isAllowed(ServerPlayer player) {
        return player.hasPermissions(PERMISSION_LEVEL);
    }

    /** Opens the admin screen of the player, or updates it: sends the list of the settlements. */
    public static void sendList(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, list(player.server, player.blockPosition()));
    }

    /**
     * Handles an action from the client. A player who is not an operator gets a message and the action does not
     * occur.
     *
     * @param player  the player who sent the action
     * @param payload the action
     */
    public static void handle(ServerPlayer player, AdminActionPayload payload) {
        if (!isAllowed(player)) {
            player.sendSystemMessage(Component.translatable(KEY + "denied"));
            return;
        }
        if (payload.action().equals("groups")) {
            if (player.connection.hasChannel(VillageInfoPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, GroupOverview.build(player.server));
            }
            return;
        }
        Optional<Component> message = perform(player.server, player, payload);
        message.ifPresent(player::sendSystemMessage);
        sendList(player);
        if (!payload.settlementId().isEmpty()) {
            details(player.server, payload.settlementId())
                    .ifPresent(details -> PacketDistributor.sendToPlayer(player, details));
        }
    }

    /**
     * Does an action. This method does not check the permission.
     *
     * @param server  the server
     * @param player  the player for the teleport action, or null
     * @param payload the action
     * @return a message for the player, if the action gives one
     */
    public static Optional<Component> perform(MinecraftServer server, @Nullable ServerPlayer player,
            AdminActionPayload payload) {
        SettlementData data = SettlementData.get(server);
        String action = payload.action();
        switch (action) {
            case "refresh", "details" -> {
                return Optional.empty();
            }
            case "tick_all", "cycle_all" -> {
                boolean cycle = action.equals("cycle_all");
                TickInfo tick = new TickInfo(data.tickNumber() + 1, cycle);
                List<Settlement> active = data.registry().all().stream().filter(Settlement::isActive).toList();
                active.forEach(settlement -> SimulationTicker.runSystems(server, settlement, tick));
                SimulationTicker.runWorldSystems(server, tick);
                return Optional.of(Component.translatable(KEY + "done." + action, active.size()));
            }
            default -> {
                // The other actions need a settlement.
            }
        }
        Optional<Settlement> found = data.registry().byId(payload.settlementId());
        if (found.isEmpty()) {
            return Optional.of(Component.translatable(KEY + "unknown"));
        }
        Settlement settlement = found.get();
        Component name = Component.literal(settlement.name());
        switch (action) {
            case "teleport" -> {
                if (player == null) {
                    return Optional.empty();
                }
                ServerLevel level = server.overworld();
                BlockPos target = teleportTarget(level, settlement);
                player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(),
                        player.getXRot());
                return Optional.of(Component.translatable(KEY + "done.teleport", name));
            }
            case "level_set" -> {
                int level = LevelTables.get().clamp(payload.value());
                SettlementLevels.setProgress(server, settlement, level, 0);
                return Optional.of(Component.translatable(KEY + "done.level_set", name, level,
                        SettlementLevels.tierName(level)));
            }
            case "experience_add" -> {
                SettlementLevels.addExperience(server, settlement, payload.value());
                return Optional.of(Component.translatable(KEY + "done.experience_add", name, payload.value()));
            }
            case "cycle" -> {
                SimulationTicker.runSystems(server, settlement, new TickInfo(data.tickNumber() + 1, true));
                return Optional.of(Component.translatable(KEY + "done.cycle", name));
            }
            case "economy_reset" -> {
                EconomyState economy = settlement.economy();
                List.copyOf(economy.stock().asMap().keySet()).forEach(item -> economy.stock().set(item, 0));
                economy.setInitialized(false);
                Settlements.startEconomy(server, settlement);
                data.setDirty();
                return Optional.of(Component.translatable(KEY + "done.economy_reset", name));
            }
            case "ruin" -> {
                Settlements.ruin(server.overworld(), settlement.id(),
                        new BlockPos(settlement.x(), settlement.heartY(), settlement.z()), null);
                return Optional.of(Component.translatable(KEY + "done.ruin", name));
            }
            case "restore" -> {
                Settlements.restore(server.overworld(), settlement);
                return Optional.of(Component.translatable(KEY + "done.restore", name));
            }
            case "alert", "bells" -> {
                Optional<VillageHeartBlockEntity> heart = loadedHeart(server, settlement);
                if (heart.isEmpty()) {
                    return Optional.of(Component.translatable(KEY + "heart_not_loaded", name));
                }
                if (action.equals("bells")) {
                    heart.get().ringBells();
                    return Optional.of(Component.translatable(KEY + "done.bells", name));
                }
                AlertState[] states = AlertState.values();
                AlertState state = states[Math.floorMod(payload.value(), states.length)];
                heart.get().setAlertState(state);
                return Optional.of(Component.translatable(KEY + "done.alert", name, alertName(state)));
            }
            default -> {
                return Optional.of(Component.translatable(KEY + "unknown_action", action));
            }
        }
    }

    /**
     * The list of all settlements, nearest to the position first.
     *
     * @param server the server
     * @param pos    the position of the player
     * @return the payload
     */
    public static AdminListPayload list(MinecraftServer server, BlockPos pos) {
        List<Summary> summaries = SettlementData.get(server).registry().all().stream()
                .sorted(Comparator.comparingLong(settlement -> settlement.distanceSquared(pos.getX(), pos.getZ())))
                .map(settlement -> new Summary(settlement.id(), settlement.name(),
                        factionName(server, settlement.factionId()), settlement.level(),
                        SettlementLevels.tierName(settlement.level()), settlement.isActive(), settlement.x(),
                        settlement.z(), settlement.economy().population(),
                        (int) Math.round(Math.sqrt(settlement.distanceSquared(pos.getX(), pos.getZ())))))
                .toList();
        return new AdminListPayload(summaries);
    }

    /**
     * The details of a settlement.
     *
     * @param server       the server
     * @param settlementId the settlement id
     * @return the payload, or empty if the settlement does not exist
     */
    public static Optional<AdminDetailsPayload> details(MinecraftServer server, String settlementId) {
        SettlementData data = SettlementData.get(server);
        return data.registry().byId(settlementId).map(settlement -> {
            EconomyState economy = settlement.economy();
            List<Row> rows = new ArrayList<>();
            rows.add(row("id", Component.literal(settlement.id())));
            rows.add(row("faction", factionName(server, settlement.factionId())));
            rows.add(row("state", Component.translatable(KEY + "state." + settlement.state().serializedName())));
            rows.add(row("level", Component.translatable(KEY + "level_value", settlement.level(),
                    SettlementLevels.tierName(settlement.level()))));
            rows.add(row("experience", Component.translatable(KEY + "fraction", settlement.experience(),
                    LevelTables.get().traits(settlement.level()).experience())));
            rows.add(row("position", settlement.hasHeart()
                    ? Component.translatable(KEY + "position", settlement.x(), settlement.heartY(), settlement.z())
                    : Component.translatable(KEY + "position_unknown_y", settlement.x(), settlement.z())));
            rows.add(row("heart", Component.translatable(KEY + (loadedHeart(server, settlement).isPresent()
                    ? "heart.loaded" : "heart.unloaded"))));
            loadedHeart(server, settlement).ifPresent(heart ->
                    rows.add(row("alert", alertName(heart.getAlertState()))));
            rows.add(row("biome", Component.literal(settlement.biome().isEmpty() ? "-" : settlement.biome())));
            rows.add(row("claimed_cells", Component.literal(String.valueOf(data.claims().cellCount(
                    settlement.id())))));
            rows.add(row("population", Component.translatable(KEY + "fraction", economy.population(),
                    LevelTables.get().traits(settlement.level()).maxPopulation())));
            rows.add(row("inhabitants", Component.literal(String.valueOf(
                    VillageFolk.inhabitantCount(settlement)))));
            rows.add(row("food", Component.translatable(KEY + "percent", Math.round(economy.foodCovered() * 100))));
            rows.add(row("jobs_working", Component.translatable(KEY + "percent",
                    Math.round(economy.jobsWorking() * 100))));
            Map<String, Integer> jobs = new TreeMap<>();
            economy.jobs().forEach(job -> jobs.merge(job, 1, Integer::sum));
            jobs.forEach((job, count) -> rows.add(new Row(Component.translatable(KEY + "job", job),
                    Component.literal(String.valueOf(count)))));
            rows.add(row("treasury", Component.literal(String.valueOf(data.treasury(settlement.factionId())))));
            rows.add(row("stock_total", Component.translatable(KEY + "fraction", economy.stock().total(),
                    LevelTables.get().traits(settlement.level()).stockCapacity())));
            economy.stock().asMap().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(DETAIL_STOCKS)
                    .forEach(entry -> rows.add(new Row(Component.literal(entry.getKey()),
                            Component.literal(String.valueOf(entry.getValue())))));
            if (!EconomyData.isActive()) {
                rows.add(row("economy", Component.translatable(KEY + "economy.inactive")));
            }
            return new AdminDetailsPayload(settlement.id(), Component.literal(settlement.name()),
                    settlement.isActive(), rows);
        });
    }

    /** The village heart of the settlement, if its chunk is loaded. */
    private static Optional<VillageHeartBlockEntity> loadedHeart(MinecraftServer server, Settlement settlement) {
        if (!settlement.hasHeart()) {
            return Optional.empty();
        }
        ServerLevel level = server.overworld();
        BlockPos pos = new BlockPos(settlement.x(), settlement.heartY(), settlement.z());
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        return level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart
                ? Optional.of(heart) : Optional.empty();
    }

    /** A free position near the village heart: the top of the terrain, 2 blocks from the heart. */
    private static BlockPos teleportTarget(ServerLevel level, Settlement settlement) {
        int x = settlement.x() + 2;
        int z = settlement.z() + 2;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static Component factionName(MinecraftServer server, String factionId) {
        return FactionVisuals.faction(server, factionId).<Component>map(FactionVisuals::name)
                .orElse(Component.literal(factionId));
    }

    private static Component alertName(AlertState state) {
        return Component.translatable(KEY + "alert." + state.name().toLowerCase(Locale.ROOT));
    }

    private static Row row(String labelKey, Component value) {
        return new Row(Component.translatable(KEY + "row." + labelKey), value);
    }
}
