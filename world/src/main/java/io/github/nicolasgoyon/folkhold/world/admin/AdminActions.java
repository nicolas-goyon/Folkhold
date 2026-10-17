package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.network.AdminActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminDetailsPayload.Stock;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload;
import io.github.nicolasgoyon.folkhold.world.network.AdminListPayload.Summary;
import io.github.nicolasgoyon.folkhold.world.network.AdminResultPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
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
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

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
 *   <li>{@code discover}: checks now all the placement regions in the discovery radius around the player (see
 *       {@link SettlementDiscovery#discoverAround}).</li>
 *   <li>{@code groups}: sends the caravans, the traveling merchants and the quests (see {@link GroupOverview}).
 *       The client shows them in a tabbed screen.</li>
 * </ul>
 * After an action, the server sends the result ({@link AdminResultPayload}), the list and the details of the
 * settlement again.
 */
public final class AdminActions {
    /** The permission level of the admin screen and of its actions. */
    public static final int PERMISSION_LEVEL = 2;

    private static final String KEY = "screen.folkhold_world.admin.";
    /** The number of stock items that the details show. */
    private static final int DETAIL_STOCKS = 5;
    /** The maximum distance from the heart to the target of the teleport action, in blocks. */
    private static final int TELEPORT_RADIUS = 8;

    private AdminActions() {
    }

    /**
     * The result of an action.
     *
     * @param message the text for the player
     * @param success true if the action occurred
     */
    public record Result(Component message, boolean success) {

        static Result done(String key, Object... args) {
            return new Result(Component.translatable(KEY + "done." + key, args), true);
        }

        static Result refused(String key, Object... args) {
            return new Result(Component.translatable(KEY + key, args), false);
        }
    }

    /** The actions that change the details of a settlement and not the list: the list is not sent again. */
    private static final Set<String> DETAILS_ONLY_ACTIONS = Set.of("details", "alert", "bells");

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
        ModNetwork.send(player, list(player.server, player.blockPosition()));
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
            ModNetwork.send(player, GroupOverview.build(player.server));
            return;
        }
        perform(player.server, player, payload).ifPresent(result -> {
            if (!ModNetwork.send(player, new AdminResultPayload(result.message(), result.success()))) {
                player.sendSystemMessage(result.message());
            }
        });
        if (!DETAILS_ONLY_ACTIONS.contains(payload.action())) {
            sendList(player);
        }
        if (!payload.settlementId().isEmpty()) {
            details(player.server, payload.settlementId()).ifPresent(details -> ModNetwork.send(player, details));
        }
    }

    /**
     * Does an action. This method does not check the permission.
     *
     * @param server  the server
     * @param player  the player for the teleport action, or null
     * @param payload the action
     * @return the result for the player, if the action gives one
     */
    public static Optional<Result> perform(MinecraftServer server, @Nullable ServerPlayer player,
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
                return Optional.of(Result.done(action, active.size()));
            }
            case "discover" -> {
                if (player == null) {
                    return Optional.empty();
                }
                SettlementDiscovery.Discovery discovery = SettlementDiscovery.discoverAround(server.overworld(),
                        player.blockPosition());
                return Optional.of(Result.done("discover", discovery.inRadius(), discovery.radius(),
                        discovery.found()));
            }
            default -> {
                // The other actions need a settlement.
            }
        }
        Optional<Settlement> found = data.registry().byId(payload.settlementId());
        if (found.isEmpty()) {
            return Optional.of(Result.refused("unknown"));
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
                return Optional.of(Result.done("teleport", name));
            }
            case "level_set" -> {
                int level = LevelTables.get().clamp(payload.value());
                SettlementLevels.setProgress(server, settlement, level, 0);
                return Optional.of(Result.done("level_set", name, level,
                        SettlementLevels.tierName(level)));
            }
            case "experience_add" -> {
                SettlementLevels.addExperience(server, settlement, payload.value());
                return Optional.of(Result.done("experience_add", name, payload.value()));
            }
            case "cycle" -> {
                SimulationTicker.runSystems(server, settlement, new TickInfo(data.tickNumber() + 1, true));
                return Optional.of(Result.done("cycle", name));
            }
            case "economy_reset" -> {
                EconomyState economy = settlement.economy();
                List.copyOf(economy.stock().asMap().keySet()).forEach(item -> economy.stock().set(item, 0));
                economy.setInitialized(false);
                Settlements.startEconomy(server, settlement);
                data.setDirty();
                return Optional.of(Result.done("economy_reset", name));
            }
            case "ruin" -> {
                if (!settlement.isActive()) {
                    return Optional.of(Result.refused("already_ruins", name));
                }
                Settlements.ruin(server.overworld(), settlement.id(),
                        new BlockPos(settlement.x(), settlement.heartY(), settlement.z()), null);
                return Optional.of(Result.done("ruin", name));
            }
            case "restore" -> {
                if (settlement.isActive()) {
                    return Optional.of(Result.refused("already_active", name));
                }
                Settlements.restore(server.overworld(), settlement);
                return Optional.of(Result.done("restore", name));
            }
            case "alert", "bells" -> {
                Optional<VillageHeartBlockEntity> heart = loadedHeart(server, settlement);
                if (heart.isEmpty()) {
                    return Optional.of(Result.refused("heart_not_loaded", name));
                }
                if (action.equals("bells")) {
                    heart.get().ringBells();
                    return Optional.of(Result.done("bells", name));
                }
                AlertState[] states = AlertState.values();
                AlertState state = states[Math.floorMod(payload.value(), states.length)];
                heart.get().setAlertState(state);
                return Optional.of(Result.done("alert", name, alertName(state)));
            }
            default -> {
                return Optional.of(Result.refused("unknown_action", action));
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
                        (int) Math.round(Math.sqrt(settlement.distanceSquared(pos.getX(), pos.getZ()))),
                        settlement.hasHeart()))
                .toList();
        return new AdminListPayload(summaries);
    }

    /**
     * The details of a settlement: the village, economy and jobs sections, and the largest stocks.
     *
     * @param server       the server
     * @param settlementId the settlement id
     * @return the payload, or empty if the settlement does not exist
     */
    public static Optional<AdminDetailsPayload> details(MinecraftServer server, String settlementId) {
        SettlementData data = SettlementData.get(server);
        return data.registry().byId(settlementId).map(settlement -> {
            EconomyState economy = settlement.economy();
            LevelTraits traits = LevelTables.get().traits(settlement.level());
            Optional<VillageHeartBlockEntity> heart = loadedHeart(server, settlement);

            List<Row> village = new ArrayList<>();
            village.add(Row.bar(label("experience"), fraction(settlement.experience(), traits.experience()),
                    settlement.experience(), traits.experience()));
            village.add(new Row(label("position"), settlement.hasHeart()
                    ? Component.translatable(KEY + "position", settlement.x(), settlement.heartY(), settlement.z())
                    : Component.translatable(KEY + "position_unknown_y", settlement.x(), settlement.z())));
            village.add(new Row(label("heart"),
                    Component.translatable(KEY + (heart.isPresent() ? "heart.loaded" : "heart.unloaded"))));
            village.add(new Row(label("claimed_cells"),
                    Component.literal(String.valueOf(data.claims().cellCount(settlement.id())))));
            village.add(Row.bar(label("population"), Component.translatable(KEY + "population_value",
                    economy.population(), traits.maxPopulation(), VillageFolk.inhabitantCount(settlement)),
                    economy.population(), traits.maxPopulation()));

            Map<String, Integer> jobs = new TreeMap<>();
            economy.jobs().forEach(job -> jobs.merge(job, 1, Integer::sum));
            List<Row> economyRows = new ArrayList<>();
            int food = (int) Math.round(economy.foodCovered() * 100);
            economyRows.add(Row.bar(label("food"), Component.translatable(KEY + "percent", food), food, 100));
            economyRows.add(new Row(label("jobs_working"), Component.translatable(KEY + "jobs_value",
                    Math.round(economy.jobsWorking() * 100), jobs.size())));
            economyRows.add(new Row(label("treasury"),
                    Component.literal(String.valueOf(data.treasury(settlement.factionId())))));
            economyRows.add(Row.bar(label("stock_total"), fraction(economy.stock().total(), traits.stockCapacity()),
                    economy.stock().total(), traits.stockCapacity()));
            if (!EconomyData.isActive()) {
                economyRows.add(new Row(label("economy"), Component.translatable(KEY + "economy.inactive")));
            }

            List<Section> sections = new ArrayList<>();
            sections.add(new Section(Component.translatable(KEY + "section.village"), village));
            sections.add(new Section(Component.translatable(KEY + "section.economy"), economyRows));
            if (!jobs.isEmpty()) {
                List<Row> jobRows = new ArrayList<>();
                jobs.forEach((job, count) -> jobRows.add(new Row(Component.literal(job),
                        Component.literal(String.valueOf(count)))));
                sections.add(new Section(Component.translatable(KEY + "section.jobs"), jobRows));
            }

            List<Stock> stocks = economy.stock().asMap().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(entry -> new Stock(entry.getKey(), entry.getValue()))
                    .toList();
            int alert = heart.map(found -> found.getAlertState().ordinal()).orElse(AdminDetailsPayload.NO_ALERT);
            return new AdminDetailsPayload(settlement.id(), Component.literal(settlement.name()),
                    settlement.isActive(), factionName(server, settlement.factionId()), settlement.biome(),
                    settlement.level(), SettlementLevels.tierName(settlement.level()), alert, settlement.x(),
                    settlement.hasHeart() ? settlement.heartY() : AdminDetailsPayload.NO_Y, settlement.z(),
                    sections, stocks.subList(0, Math.min(DETAIL_STOCKS, stocks.size())),
                    Math.max(0, stocks.size() - DETAIL_STOCKS));
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

    /**
     * A safe position near the village heart, for the teleport action.
     *
     * <ul>
     *   <li>The method loads the chunks first. The height of a chunk that is not loaded is the bottom of the
     *       world.</li>
     *   <li>If the heart position is known, the method finds the nearest free position at the level of the heart:
     *       a solid floor and two free blocks above it, at most {@link #TELEPORT_RADIUS} blocks from the heart.</li>
     *   <li>If there is no such position, the target is the top of the terrain, 2 blocks from the heart.</li>
     * </ul>
     *
     * @param level      the level
     * @param settlement the settlement
     * @return the position of the feet of the player
     */
    public static BlockPos teleportTarget(ServerLevel level, Settlement settlement) {
        int x = settlement.x();
        int z = settlement.z();
        for (int chunkX = (x - TELEPORT_RADIUS) >> 4; chunkX <= (x + TELEPORT_RADIUS) >> 4; chunkX++) {
            for (int chunkZ = (z - TELEPORT_RADIUS) >> 4; chunkZ <= (z + TELEPORT_RADIUS) >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        if (settlement.hasHeart()) {
            Optional<BlockPos> free = freePositionNear(level, new BlockPos(x, settlement.heartY(), z));
            if (free.isPresent()) {
                return free.get();
            }
        }
        int targetX = x + 2;
        int targetZ = z + 2;
        return new BlockPos(targetX, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ),
                targetZ);
    }

    /** The nearest free position around the heart, by rings of growing distance. */
    private static Optional<BlockPos> freePositionNear(ServerLevel level, BlockPos heart) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int radius = 1; radius <= TELEPORT_RADIUS; radius++) {
            for (int dy : new int[] {0, 1, -1, 2, -2, 3}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        pos.set(heart.getX() + dx, heart.getY() + dy, heart.getZ() + dz);
                        if (canStand(level, pos)) {
                            return Optional.of(pos.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** True if a player can stand at the position: a solid floor below, two free blocks, no liquid. */
    private static boolean canStand(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    private static Component factionName(MinecraftServer server, String factionId) {
        return FactionVisuals.faction(server, factionId).<Component>map(FactionVisuals::name)
                .orElse(Component.literal(factionId));
    }

    private static Component alertName(AlertState state) {
        return Component.translatable(KEY + "alert." + state.name().toLowerCase(Locale.ROOT));
    }

    private static Component label(String key) {
        return Component.translatable(KEY + "row." + key);
    }

    private static Component fraction(int value, int max) {
        return Component.translatable(KEY + "fraction", value, max);
    }
}
