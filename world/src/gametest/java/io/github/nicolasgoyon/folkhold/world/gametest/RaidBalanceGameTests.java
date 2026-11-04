package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MonsterTargeting;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The harness of the raid balance: a vanilla raid of a given level against a faction village of a given level. The
 * targets are in the section "Raids against the faction villages" of {@code docs/design.md}.
 *
 * <p>The village of a trial is in the template {@code folkhold_world:arena_huge} (56 × 4 × 56 blocks of air): a heart
 * in the middle, the full garrison of the level, and {@link #VILLAGERS} vanilla villagers, each with a bed. The village
 * has no palisade: the trial measures the garrison against the raiders in the open. The garrison does not refill
 * during the trial. A {@link Outcome#HOLDS} result with no walls is also a result for a village with its palisade. A
 * {@link Outcome#FALLS} result is measured with no walls only: it does not prove that the village falls with its
 * palisade.
 *
 * <p>The arena is a cell of the GameTest grid. The barrier cages of the tests that ran before stay in the world, and
 * some of them are near the spawn positions of the raiders. A cage can block the path of a raider.
 *
 * <p>The raid level is the level of the Raid Omen (1 to 5). The GameTest world has the difficulty Normal: a raid has 5
 * waves, and a 6th wave when its level is 2 or more. The result of a trial:
 * <ul>
 *   <li>{@link Outcome#HOLDS}: the raid ends in a victory, all the raiders are dead.</li>
 *   <li>{@link Outcome#FALLS}: all the units of the garrison are dead, or the raid ends in a loss. Vanilla sets the
 *       loss when no village point of interest (a bed, a job site or a bell) is near the center of the raid.</li>
 *   <li>{@link Outcome#STALLED}: during {@link #STALL_TICKS} ticks, no raider and no unit dies, the raiders lose no
 *       health, and no wave spawns. For example, a raider is far from its target and does not move.</li>
 *   <li>{@link Outcome#UNDECIDED}: no result after {@link #TRIAL_SECONDS} seconds of real time.</li>
 *   <li>{@link Outcome#STOPPED}: the vanilla raid stops with no result, for example with no spawn position.</li>
 * </ul>
 *
 * <p>Each trial writes one line {@code Raid trial: ...} in the log. The environment variable
 * {@value #MATRIX_VARIABLE} adds the optional trials of the matrix {@link #MATRIX_RAIDS} × {@link #MATRIX_LEVELS}:
 * its value is the number of runs of each pair.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaidBalanceGameTests {
    /** The real-time limit of a trial, in seconds. */
    static final long TRIAL_SECONDS = 600;
    /** The time with no change after which a trial stalls, in ticks: 2 minutes of game time. */
    static final long STALL_TICKS = 2400;
    static final int VILLAGERS = 4;
    static final String MATRIX_VARIABLE = "FOLKHOLD_RAID_MATRIX";
    static final int[] MATRIX_RAIDS = {1, 3, 5};
    /** The first level of each tier: hamlet, village, town, city, capital. */
    static final int[] MATRIX_LEVELS = {1, 5, 9, 13, 17};
    private static final String TEMPLATE = FolkholdWorld.MOD_ID + ":arena_huge";
    private static final int HUGE = 56;
    private static final BlockPos HEART = new BlockPos(HUGE / 2, 1, HUGE / 2);
    /** The size of a chunk, in blocks. */
    private static final int CHUNK = 16;

    /** The result of a trial. */
    enum Outcome {
        ONGOING, HOLDS, FALLS, STALLED, UNDECIDED, STOPPED
    }

    /**
     * The measures of a trial.
     *
     * @param outcome   the result
     * @param units     the units of the garrison that are alive at the end
     * @param garrison  the units of the garrison at the start
     * @param villagers the villagers with a bed that are alive at the end
     * @param waves     the waves that the raid spawned
     * @param raiders   the raiders that are alive at the end
     * @param ticks     the game time of the raid, in ticks
     * @param seconds   the real time of the raid, in seconds
     */
    record Result(Outcome outcome, int units, int garrison, int villagers, int waves, int raiders, long ticks,
            double seconds) {
    }

    private RaidBalanceGameTests() {
    }

    /**
     * Exit criterion of version 1: a city (level 13) holds against a raid of level 5. The test is optional: with the
     * current balance, the city does not hold in each trial. It becomes required when the balance meets the target.
     */
    @GameTest(template = "arena_huge", timeoutTicks = TestSupport.WORKER_TICKS, batch = "raid_balance_city",
            required = false)
    public static void largeVillageResistsARaid(GameTestHelper helper) {
        trial(helper, 5, 13, result -> helper.assertTrue(result.outcome() == Outcome.HOLDS,
                "A city must hold against a raid of level 5: " + result));
    }

    /** Exit criterion of version 1: a hamlet (level 1) falls to a raid of level 1. */
    @GameTest(template = "arena_huge", timeoutTicks = TestSupport.WORKER_TICKS, batch = "raid_balance_hamlet")
    public static void smallVillageFalls(GameTestHelper helper) {
        trial(helper, 1, 1, result -> helper.assertTrue(result.outcome() == Outcome.FALLS,
                "A hamlet must fall to a raid of level 1: " + result));
    }

    /** The optional trials of the matrix. With no environment variable {@value #MATRIX_VARIABLE}, no trials. */
    @GameTestGenerator
    public static List<TestFunction> raidMatrix() {
        int runs = runs(System.getenv(MATRIX_VARIABLE));
        List<TestFunction> tests = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            for (int raidLevel : MATRIX_RAIDS) {
                for (int villageLevel : MATRIX_LEVELS) {
                    String name = String.format(Locale.ROOT, "raid%d_level%02d_run%d", raidLevel, villageLevel, run);
                    tests.add(new TestFunction("raid_matrix_" + name, "raidmatrix." + name, TEMPLATE, Rotation.NONE,
                            TestSupport.WORKER_TICKS, 0L, false, false, 1, 1, false,
                            helper -> trial(helper, raidLevel, villageLevel, result -> helper.assertTrue(
                                    result.outcome() == Outcome.HOLDS || result.outcome() == Outcome.FALLS,
                                    "The trial must give a result: " + result))));
                }
            }
        }
        return tests;
    }

    /** The number of runs of each pair of the matrix: the value of the variable, 0 when it is not a number. */
    static int runs(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Runs a trial: builds the village, starts the raid, waits for its result, writes the result in the log, removes
     * the village and the raiders, then gives the result to the check.
     *
     * @param helper       the helper of the test
     * @param raidLevel    the level of the raid, 1 to 5
     * @param villageLevel the level of the village, 1 to 20
     * @param check        the checks of the test
     */
    static void trial(GameTestHelper helper, int raidLevel, int villageLevel, Consumer<Result> check) {
        TestSupport.buildFloor(helper, HUGE);
        // The raiders come from outside the test area: no barriers around it.
        StructureUtils.removeBarriers(helper.getBounds(), helper.getLevel());
        forceChunks(helper, true);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        TestSupport.setLevel(helper, heart, villageLevel);
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(HEART);
        // The trial keeps the relative positions of the beds. For a test with no rotation, GameTestHelper.relativePos
        // gives the opposite x and z: it does not give back the relative position of an absolute position.
        List<BlockPos> heads = new ArrayList<>();
        List<BlockPos> beds = new ArrayList<>();
        for (int i = 0; i < VILLAGERS; i++) {
            BlockPos foot = HEART.offset(-3 + 2 * i, 0, 4);
            TestSupport.placeBed(helper, foot, Direction.SOUTH);
            heads.add(foot.south());
            beds.add(helper.absolutePos(foot.south()));
        }
        PoiManager pois = level.getPoiManager();
        Trial trial = new Trial(heart, raidLevel);

        GameTestSequence sequence = TestSupport.thenWaitForWorker(helper, helper.startSequence(), () -> {
            if (!VillageHearts.findNearest(level, heartPos, 1.0).isPresent()
                    || !beds.stream().allMatch(bed -> pois.existsAtPosition(PoiTypes.HOME, bed))) {
                return false;
            }
            heart.ensureGarrison(level.getGameTime());
            return heart.getGarrison().aliveCount() == fullSize(heart);
        }, "The heart must be loaded, the beds must be points of interest, and the heart must create its garrison");
        sequence
                .thenExecute(() -> {
                    for (int i = 0; i < VILLAGERS; i++) {
                        BlockPos bed = beds.get(i);
                        Villager villager = helper.spawn(EntityType.VILLAGER, heads.get(i).north(2));
                        pois.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1);
                        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
                        trial.villagers.add(villager);
                    }
                    trial.start(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(trial.isFinished(), "Waiting for the end of the raid"))
                .thenExecute(() -> {
                    Result result = trial.result();
                    FolkholdWorld.LOGGER.info("Raid trial: raid level {}, village level {}: {}", raidLevel,
                            villageLevel, result);
                    if (result.raiders() > 0 && result.outcome() != Outcome.FALLS) {
                        trial.raid.getAllRaiders().forEach(raider -> FolkholdWorld.LOGGER.info(
                                "Raid trial: raider left: {}, offset from the heart {}, distance {} blocks, ticking {}, "
                                        + "target {}, in water {}",
                                raider.getType(), raider.blockPosition().subtract(heartPos),
                                (int) Math.sqrt(raider.blockPosition().distSqr(heartPos)),
                                level.isPositionEntityTicking(raider.blockPosition()), raider.getTarget(),
                                raider.isInWater()));
                    }
                    trial.remove();
                    heads.forEach(head -> {
                        helper.setBlock(head, Blocks.AIR);
                        helper.setBlock(head.north(), Blocks.AIR);
                    });
                    helper.setBlock(HEART, Blocks.AIR);
                    forceChunks(helper, false);
                    check.accept(result);
                })
                .thenSucceed();
    }

    /**
     * The chunks in this distance from the heart stay loaded and ticking during a trial, in blocks: the distance from
     * the heart where the units can be ({@link MonsterTargeting#gateRadius()}), plus one chunk. The GameTest server
     * ticks only the chunks of the test area. Vanilla removes a raider that is 112 blocks or more from the center of
     * the raid: each raider of the raid is in a ticking chunk.
     */
    static int forcedRadius() {
        return (int) Math.ceil(MonsterTargeting.gateRadius()) + CHUNK;
    }

    /** Forces the chunks near the heart to stay loaded and ticking, or stops it. */
    private static void forceChunks(GameTestHelper helper, boolean forced) {
        BlockPos heart = helper.absolutePos(HEART);
        int radius = forcedRadius();
        for (int x = (heart.getX() - radius) >> 4; x <= (heart.getX() + radius) >> 4; x++) {
            for (int z = (heart.getZ() - radius) >> 4; z <= (heart.getZ() + radius) >> 4; z++) {
                helper.getLevel().setChunkForced(x, z, forced);
            }
        }
    }

    /** The number of units of the garrison of the level of the heart. */
    private static int fullSize(VillageHeartBlockEntity heart) {
        int size = 0;
        for (GarrisonRole role : GarrisonRole.values()) {
            size += role.count(heart.garrisonTraits());
        }
        return size;
    }

    /** The state of a running trial. */
    private static final class Trial {
        private final VillageHeartBlockEntity heart;
        private final int raidLevel;
        private final List<Villager> villagers = new ArrayList<>();
        private Raid raid;
        private int garrison;
        private long startTick;
        private long startNanos;
        private Outcome outcome = Outcome.ONGOING;
        private long endTick;
        private long endNanos;
        /** The state of the fight at the last change, and the game time of this change. */
        private List<Integer> lastState = List.of();
        private long lastChange;

        Trial(VillageHeartBlockEntity heart, int raidLevel) {
            this.heart = heart;
            this.raidLevel = raidLevel;
        }

        /** Starts the raid at the village. The vanilla raid needs a player: a mock player starts it, then leaves. */
        void start(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = TestSupport.mockPlayer(helper);
            raid = level.getRaids().createOrExtendRaid(player, heart.getBlockPos());
            level.getServer().getPlayerList().remove(player);
            helper.assertTrue(raid != null && !raid.isStarted(), "The trial must start a new raid");
            raid.setRaidOmenLevel(raidLevel);
            garrison = fullSize(heart);
            startTick = level.getGameTime();
            startNanos = System.nanoTime();
        }

        /** True when the trial has a result. The method sets the result at the first check that finds it. */
        boolean isFinished() {
            if (outcome != Outcome.ONGOING) {
                return true;
            }
            long now = System.nanoTime();
            long tick = heart.getLevel().getGameTime();
            List<Integer> state = List.of(raid.getTotalRaidersAlive(), (int) raid.getHealthOfLivingRaiders(),
                    heart.getGarrison().aliveCount(), raid.getGroupsSpawned());
            if (!state.equals(lastState) || !raid.isStarted()) {
                lastState = state;
                lastChange = tick;
            }
            if (raid.isVictory()) {
                outcome = Outcome.HOLDS;
            } else if (raid.isLoss() || (raid.isStarted() && heart.getGarrison().aliveCount() == 0)) {
                outcome = Outcome.FALLS;
            } else if (raid.isStopped()) {
                outcome = Outcome.STOPPED;
            } else if (tick - lastChange > STALL_TICKS) {
                outcome = Outcome.STALLED;
            } else if (now - startNanos > TRIAL_SECONDS * 1_000_000_000L) {
                outcome = Outcome.UNDECIDED;
            } else {
                return false;
            }
            endTick = tick;
            endNanos = now;
            return true;
        }

        Result result() {
            int alive = (int) villagers.stream().filter(Entity::isAlive).count();
            return new Result(outcome, heart.getGarrison().aliveCount(), garrison, alive, raid.getGroupsSpawned(),
                    raid.getTotalRaidersAlive(),
                    endTick - startTick, Math.round((endNanos - startNanos) / 1.0e8) / 10.0);
        }

        /** Stops the raid and removes the raiders, the units and the villagers. */
        void remove() {
            raid.getAllRaiders().forEach(Raider::discard);
            raid.stop();
            heart.getUnits().forEach(FactionUnit::discard);
            villagers.forEach(Villager::discard);
        }
    }
}
