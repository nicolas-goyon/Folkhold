package io.github.nicolasgoyon.folkhold.world.quest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationTicker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The escort quests: the player protects a real caravan to its destination.
 *
 * <ul>
 *   <li>When the trade makes a caravan, its village offers an escort quest. The caravan waits at the village until a
 *       player accepts, or until the end of the offer. Then it leaves.</li>
 *   <li>The escorted caravan keeps its entities while the player is in the escort distance.</li>
 *   <li>When the player is farther than the escort distance, the caravan stops. When the player stays away longer
 *       than a delay, the quest fails and the caravan continues as an abstract group.</li>
 *   <li>Each escort has 1 to 3 ambushes of vanilla monsters at random points of the route. The length of the route
 *       and the level of the origin village set the size of an ambush.</li>
 *   <li>Success: the caravan arrives and its merchant is alive. The reward comes from the value of the cargo.</li>
 *   <li>Failure: the merchant dies, the player stays away too long, or the deadline ends.</li>
 *   <li>Each second, the player of an escort gets the state of the caravan in the quest HUD.</li>
 * </ul>
 */
public final class Escorts {
    /** Key in the persistent data of an ambush monster. */
    public static final String AMBUSH_TAG = "folkhold_world:ambush";
    private static final int CHECK_INTERVAL = 20;
    private static final long TICKS_PER_MINUTE = 1200L;
    /** The ambushes are between these shares of the route. */
    private static final double AMBUSH_FIRST = 0.2;
    private static final double AMBUSH_LAST = 0.9;
    /** Distance in blocks in front of the merchant where an ambush appears. */
    private static final double AMBUSH_AHEAD = 16.0;
    private static final int MIN_AMBUSH = 2;
    private static final int MAX_AMBUSH = 8;

    /** Game time when the player of each escort went away, by quest id. Not saved. */
    private static final Map<String, Long> AWAY_SINCE = new HashMap<>();
    /** The progress of the remaining ambushes of each escort, by quest id. Not saved. */
    private static final Map<String, Deque<Double>> AMBUSHES = new HashMap<>();

    private Escorts() {
    }

    /**
     * Offers an escort quest for a new caravan. The caravan waits at its village until the end of the offer.
     *
     * @param server     the server
     * @param caravan    the caravan, in the state waiting
     * @param origin     the village of the caravan
     * @param tickNumber the number of the abstract tick
     * @return the quest, or an empty value if the escort offers are off
     */
    public static Optional<Quest> offer(MinecraftServer server, MobileGroup caravan, Settlement origin,
            long tickNumber) {
        if (!WorldConfig.QUEST_ESCORT_OFFERS.get()) {
            return Optional.empty();
        }
        int wait = WorldConfig.QUEST_ESCORT_WAIT_TICKS.get();
        caravan.setDepartureTick(tickNumber + wait);
        long now = server.overworld().getGameTime();
        long offerEnd = now + (long) wait * SimulationTicker.settings().intervalTicks();
        long reward = Math.max(1, Math.round(caravan.payment() * WorldConfig.QUEST_ESCORT_REWARD_SHARE.get()));
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.ESCORT, origin.id(), origin.factionId(),
                caravan.id(), 0, reward, WorldConfig.QUEST_ESCORT_REPUTATION.get(),
                QuestGenerator.minTier(reward, QuestSystem.settings()), offerEnd,
                WorldConfig.QUEST_ESCORT_MINUTES.get() * TICKS_PER_MINUTE);
        QuestData data = QuestData.get(server);
        data.quests().add(quest);
        data.setDirty();
        return Optional.of(quest);
    }

    /** The accepted escort quests. */
    public static List<Quest> active(MinecraftServer server) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : QuestData.get(server).quests().all()) {
            if (quest.kind() == QuestKind.ESCORT && quest.state() == QuestState.ACCEPTED) {
                result.add(quest);
            }
        }
        return result;
    }

    /** An accepted escort: the caravan leaves now and keeps its entities. */
    public static void onAccepted(ServerPlayer player, Quest quest) {
        if (quest.kind() != QuestKind.ESCORT) {
            return;
        }
        MinecraftServer server = player.server;
        caravan(server, quest).ifPresent(group -> {
            if (group.state() == GroupState.WAITING) {
                group.setState(GroupState.TRAVELING);
            }
            GroupMaterializer.keep(group.id(), true);
            AMBUSHES.put(quest.id(), plan(quest, group));
            MobileGroupData.get(server).setDirty();
        });
    }

    /** An escort that ends (completed, failed or abandoned) releases its caravan. */
    public static void onEnded(MinecraftServer server, Quest quest) {
        if (quest.kind() != QuestKind.ESCORT) {
            return;
        }
        AWAY_SINCE.remove(quest.id());
        AMBUSHES.remove(quest.id());
        caravan(server, quest).ifPresent(group -> {
            GroupMaterializer.keep(group.id(), false);
            if (group.state() == GroupState.STOPPED) {
                group.setState(GroupState.TRAVELING);
            }
            MobileGroupData.get(server).setDirty();
        });
    }

    /**
     * A caravan arrives or is lost in the simulation, before the simulation removes it. Its accepted escort
     * succeeds if the caravan arrives with its merchant alive. Else it fails.
     *
     * @param server the server
     * @param group  the caravan
     */
    public static void onGroupFinished(MinecraftServer server, MobileGroup group) {
        for (Quest quest : active(server)) {
            if (!quest.target().equals(group.id())) {
                continue;
            }
            boolean merchantAlive = group.merchant().filter(merchant -> merchant.isAlive()).isPresent();
            if (group.state() == GroupState.ARRIVED && merchantAlive) {
                QuestBoards.complete(server, quest);
            } else {
                QuestBoards.fail(server, quest);
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % CHECK_INTERVAL == 0) {
            SimulationProfiler.time("escorts (each second)", () -> update(server, now));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        AWAY_SINCE.clear();
        AMBUSHES.clear();
    }

    /**
     * Checks all escorts now: end of the offers of the caravans that left, success, failure, stop and departure of
     * the caravan, ambushes.
     *
     * @param server the server
     * @param now    the game time
     */
    public static void update(MinecraftServer server, long now) {
        ServerLevel level = server.overworld();
        QuestData data = QuestData.get(server);
        for (Quest quest : List.copyOf(data.quests().all())) {
            if (quest.kind() == QuestKind.ESCORT && quest.state() == QuestState.OFFERED
                    && caravan(server, quest).filter(group -> group.state() == GroupState.WAITING).isEmpty()) {
                data.quests().withdraw(quest.id());
                data.setDirty();
            }
        }
        for (Quest quest : active(server)) {
            Optional<MobileGroup> found = caravan(server, quest);
            if (found.isEmpty() || found.get().state() == GroupState.LOST) {
                QuestBoards.fail(server, quest);
                continue;
            }
            MobileGroup group = found.get();
            if (group.state() == GroupState.ARRIVED) {
                if (group.merchant().filter(merchant -> merchant.isAlive()).isPresent()) {
                    QuestBoards.complete(server, quest);
                } else {
                    QuestBoards.fail(server, quest);
                }
                continue;
            }
            ServerPlayer player = quest.playerId() == null ? null
                    : server.getPlayerList().getPlayer(quest.playerId());
            if (player != null && player.level() == level
                    && distance(level, player, group) <= WorldConfig.QUEST_ESCORT_DISTANCE.get()) {
                AWAY_SINCE.remove(quest.id());
                if (group.state() == GroupState.STOPPED) {
                    group.setState(GroupState.TRAVELING);
                }
                GroupMaterializer.keep(group.id(), true);
                ambushes(level, quest, group);
            } else {
                if (group.state() == GroupState.TRAVELING) {
                    group.setState(GroupState.STOPPED);
                }
                long away = AWAY_SINCE.computeIfAbsent(quest.id(), id -> now);
                if (now - away > WorldConfig.QUEST_ESCORT_AWAY_SECONDS.get() * 20L) {
                    QuestBoards.fail(server, quest);
                    continue;
                }
            }
            MobileGroupData.get(server).setDirty();
            if (player != null) {
                QuestBoards.sendLog(player);
            }
        }
    }

    /**
     * The state of the caravan of an accepted escort, for the quest HUD of its player.
     *
     * @param server the server
     * @param quest  the escort quest
     * @param player the player of the quest
     * @return the state, or empty if the caravan does not exist
     */
    public static Optional<QuestLogPayload.Escort> hudState(MinecraftServer server, Quest quest, ServerPlayer player) {
        Optional<MobileGroup> found = caravan(server, quest);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MobileGroup group = found.get();
        ServerLevel level = server.overworld();
        int health = -1;
        int maxHealth = -1;
        Optional<Entity> merchant = group.merchant().map(member -> level.getEntity(member.id()));
        if (merchant.isPresent() && merchant.get() instanceof LivingEntity living) {
            health = (int) Math.ceil(living.getHealth());
            maxHealth = (int) Math.ceil(living.getMaxHealth());
        }
        int distance = player.level() == level ? (int) Math.round(distance(level, player, group)) : Integer.MAX_VALUE;
        Long since = AWAY_SINCE.get(quest.id());
        int awaySeconds = since == null ? -1 : (int) Math.max(0,
                WorldConfig.QUEST_ESCORT_AWAY_SECONDS.get() - (level.getGameTime() - since) / 20L);
        return Optional.of(new QuestLogPayload.Escort((int) Math.round(group.progress()),
                (int) Math.round(group.route().length()), health, maxHealth, Math.min(distance, 99_999),
                WorldConfig.QUEST_ESCORT_DISTANCE.get(), awaySeconds));
    }

    /**
     * Makes an ambush of vanilla monsters in front of the merchant of a caravan with entities. The monsters attack
     * the merchant.
     *
     * @param level the level
     * @param group the caravan
     * @param size  the number of monsters
     * @return the monsters
     */
    public static List<Mob> spawnAmbush(ServerLevel level, MobileGroup group, int size) {
        List<Mob> monsters = new ArrayList<>();
        Optional<Entity> merchant = group.merchant().map(member -> level.getEntity(member.id()));
        if (merchant.isEmpty() || !(merchant.get() instanceof LivingEntity target)) {
            return monsters;
        }
        Route route = group.route();
        Route.Position ahead = route.positionAt(route.progressNear(target.getX(), target.getZ()) + AMBUSH_AHEAD);
        SplittableRandom random = new SplittableRandom(level.getGameTime() ^ group.id().hashCode());
        for (int i = 0; i < size; i++) {
            EntityType<? extends Mob> type = switch (i % 3) {
                case 0 -> EntityType.ZOMBIE;
                case 1 -> EntityType.SKELETON;
                default -> EntityType.SPIDER;
            };
            int x = (int) Math.floor(ahead.x()) + random.nextInt(-3, 4);
            int z = (int) Math.floor(ahead.z()) + random.nextInt(-3, 4);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            if (type != EntityType.SPIDER) {
                // A helmet protects the monster from the sun, so the ambush also works in the day.
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
            mob.getPersistentData().putBoolean(AMBUSH_TAG, true);
            mob.setTarget(target);
            level.addFreshEntity(mob);
            monsters.add(mob);
        }
        return monsters;
    }

    /** The number of monsters of an ambush: 2, plus 1 for each 500 blocks of route and for each 5 levels, up to 8. */
    public static int ambushSize(double routeLength, int originLevel) {
        return Math.max(MIN_AMBUSH, Math.min(MAX_AMBUSH, MIN_AMBUSH + (int) (routeLength / 500) + originLevel / 5));
    }

    /** The caravan of an escort quest. */
    public static Optional<MobileGroup> caravan(MinecraftServer server, Quest quest) {
        return MobileGroupData.get(server).groups().byId(quest.target());
    }

    /** The ambushes of a caravan with entities, when it passes their points of the route. */
    private static void ambushes(ServerLevel level, Quest quest, MobileGroup group) {
        if (!GroupMaterializer.isMaterialized(group)) {
            return;
        }
        Deque<Double> plan = AMBUSHES.computeIfAbsent(quest.id(), id -> plan(quest, group));
        int originLevel = SettlementData.get(level.getServer()).registry().byId(group.originId())
                .map(Settlement::level).orElse(1);
        while (!plan.isEmpty() && group.progress() >= plan.peekFirst()) {
            plan.pollFirst();
            spawnAmbush(level, group, ambushSize(group.route().length(), originLevel));
        }
    }

    /**
     * The progress of the ambushes of an escort: 1 to 3 random points between 20 % and 90 % of the route. The id of
     * the quest selects them, so the plan is the same after a restart. The points behind the caravan are not in the
     * plan.
     */
    public static Deque<Double> plan(Quest quest, MobileGroup group) {
        SplittableRandom random = new SplittableRandom(quest.id().hashCode());
        int count = 1 + random.nextInt(3);
        double length = group.route().length();
        List<Double> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(length * (AMBUSH_FIRST + random.nextDouble() * (AMBUSH_LAST - AMBUSH_FIRST)));
        }
        points.sort(Double::compare);
        Deque<Double> plan = new ArrayDeque<>();
        for (double point : points) {
            if (point > group.progress()) {
                plan.add(point);
            }
        }
        return plan;
    }

    /** Horizontal distance from a player to a caravan: to its merchant if it has entities. */
    private static double distance(ServerLevel level, ServerPlayer player, MobileGroup group) {
        double x;
        double z;
        Optional<Entity> merchant = group.merchant().map(member -> level.getEntity(member.id()));
        if (merchant.isPresent() && merchant.get() != null) {
            x = merchant.get().getX();
            z = merchant.get().getZ();
        } else {
            Route.Position position = group.position();
            x = position.x();
            z = position.z();
        }
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
