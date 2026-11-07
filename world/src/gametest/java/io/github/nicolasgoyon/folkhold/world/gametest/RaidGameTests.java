package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MarchOnVillageGoal;
import io.github.nicolasgoyon.folkhold.world.village.RaidMarch;
import io.github.nicolasgoyon.folkhold.world.village.RaidSpawns;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the raids against the faction villages (see {@code VillageRaids}). The tests use the templates
 * {@code folkhold_world:arena_large} (32 × 4 × 32 blocks of air) and {@code folkhold_world:arena_huge} (56 × 4 × 56
 * blocks of air), and build their terrain.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaidGameTests {
    private static final int SIZE = 32;
    /** The ring of the spawn positions in the spawn test: smaller than in a village, so that it fits the arena. */
    private static final int RING_MIN = 6;
    private static final int RING_MAX = 13;
    private static final int SPAWN_TRIES = 40;
    /** An id for the raids of the tests, far from the ids of the raids of the world. */
    private static final int RAID_ID = 271_828;
    /** The ids of the raids of {@link #raidsOfLevel4And5GetExtraRavagers}: each run takes new ids. */
    private static final AtomicInteger NEXT_RAID_ID = new AtomicInteger(RAID_ID + 1_000);
    /** The size of the template {@code arena_huge}, in blocks. */
    private static final int HUGE = 56;
    /** The distance from the heart to the palisade in the test of the gates, in blocks. */
    private static final int PALISADE = 16;
    /** The day time of midnight: the heart keeps the gates closed. */
    private static final long MIDNIGHT = 18_000L;

    private RaidGameTests() {
    }

    /**
     * The checks of a spawn position, then the spawn positions of a ring around the heart. In the ring, the terrain
     * has a river, a pit 3 blocks deep, a roof and a floor of planks: the spawn positions are on the grass only. The
     * test has access to the sky: the GameTest server puts no roof of barriers on it, so the columns end at the
     * terrain.
     */
    @GameTest(template = "arena_large", timeoutTicks = 100, batch = "raid_spawn", skyAccess = true)
    public static void spawnPositionsAvoidRiversPitsRoofsAndFloors(GameTestHelper helper) {
        TestSupport.sweep(helper);
        buildGrass(helper);
        // A river: x 26 to 28, 2 blocks deep. The floor at z 0 and z 31 keeps the water in the arena.
        fill(helper, new BlockPos(26, -1, 1), new BlockPos(28, 0, SIZE - 2), Blocks.WATER.defaultBlockState());
        // A pit 3 blocks deep: x 15 to 17, z 3 to 5.
        fill(helper, new BlockPos(15, -2, 3), new BlockPos(17, 0, 5), Blocks.AIR.defaultBlockState());
        // A roof of planks over x 3 to 6, z 14 to 18.
        fill(helper, new BlockPos(3, 3, 14), new BlockPos(6, 3, 18), Blocks.OAK_PLANKS.defaultBlockState());
        // A floor of planks: x 14 to 18, z 26 to 29.
        fill(helper, new BlockPos(14, 0, 26), new BlockPos(18, 0, 29), Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos heart = helper.absolutePos(new BlockPos(16, 1, 16));
        ServerLevel level = helper.getLevel();

        // The light engine updates the light of the new blocks in its own thread, after a time that changes: the
        // checks run at each tick until they pass.
        helper.succeedWhen(() -> {
            assertCheck(helper, new BlockPos(24, 1, 16), RaidSpawns.Check.GOOD);
            assertCheck(helper, new BlockPos(27, 1, 16), RaidSpawns.Check.FLUID);
            assertCheck(helper, new BlockPos(16, -2, 4), RaidSpawns.Check.HOLLOW);
            assertCheck(helper, new BlockPos(4, 4, 16), RaidSpawns.Check.NOT_GROUND);
            assertCheck(helper, new BlockPos(4, 1, 16), RaidSpawns.Check.NO_SKY);
            assertCheck(helper, new BlockPos(16, 1, 27), RaidSpawns.Check.NOT_GROUND);
            helper.assertTrue(RaidSpawns.canWalk(level, helper.absolutePos(new BlockPos(30, 1, 16)), heart),
                    "A raider must have a path across the river");
            helper.assertFalse(RaidSpawns.canWalk(level, helper.absolutePos(new BlockPos(16, -2, 4)), heart),
                    "A raider must have no path out of the pit");

            List<BlockPos> found = new ArrayList<>();
            for (int seed = 0; seed < SPAWN_TRIES; seed++) {
                RaidSpawns.find(level, heart, List.of(), RandomSource.create(seed), RING_MIN, RING_MAX)
                        .ifPresent(found::add);
            }
            for (BlockPos pos : found) {
                BlockPos relative = helper.relativePos(pos);
                helper.assertTrue(relative.getY() == 1 && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK),
                        "A spawn position must be on the grass: " + relative);
                helper.assertTrue(level.canSeeSky(pos), "A spawn position must be under the sky: " + relative);
            }
            helper.assertTrue(found.size() >= SPAWN_TRIES * 3 / 4,
                    "Most searches must find a position: " + found.size() + " of " + SPAWN_TRIES);
        });
    }

    private static void assertCheck(GameTestHelper helper, BlockPos relative, RaidSpawns.Check expected) {
        RaidSpawns.Check check = RaidSpawns.check(helper.getLevel(), helper.absolutePos(relative));
        helper.assertTrue(check == expected, "The check of " + relative + " must be " + expected + ", not " + check);
    }

    /**
     * The approach of a gate of the palisade is outside the gate, after its stairs, on the terrain. It follows the
     * state of the fence gates.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "raid_approach")
    public static void approachIsOutsideTheGate(GameTestHelper helper) {
        TestSupport.sweep(helper);
        buildGrass(helper);
        BlockPos heart = helper.absolutePos(new BlockPos(16, 1, 2));
        List<BlockPos> gates = new ArrayList<>();
        for (int x = 15; x <= 17; x++) {
            BlockPos gate = new BlockPos(x, 1, 18);
            helper.setBlock(gate, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,
                    Direction.NORTH));
            gates.add(helper.absolutePos(gate));
        }
        ServerLevel level = helper.getLevel();

        List<VillageExits.Approach> closed = VillageExits.approaches(level, heart, gates);
        boolean openBefore = !closed.isEmpty() && closed.get(0).isOpen(level);
        for (BlockPos gate : gates) {
            BlockState state = level.getBlockState(gate);
            level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
        }
        List<VillageExits.Approach> open = VillageExits.approaches(level, heart, gates);

        helper.assertTrue(closed.size() == 1, "The three gates must make one approach: " + closed.size());
        VillageExits.Approach approach = closed.get(0);
        BlockPos expected = helper.absolutePos(new BlockPos(16, 1, 18 + VillageExits.APPROACH_DISTANCE));
        helper.assertTrue(approach.point().equals(expected),
                "The approach must be outside the middle gate: " + helper.relativePos(approach.point()));
        helper.assertTrue(approach.gates().size() == 3, "The approach must have the three gates");
        helper.assertFalse(openBefore, "The approach of a closed gate must be closed");
        helper.assertTrue(open.size() == 1 && open.get(0).isOpen(level), "The approach of an open gate must be open");
        helper.succeed();
    }

    /**
     * The approach of a gate in a pond is at the surface of the water, where a raider swims. The pond is 4 blocks
     * deep.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "raid_approach")
    public static void approachInWaterIsAtTheSurface(GameTestHelper helper) {
        TestSupport.sweep(helper);
        buildGrass(helper);
        // A pond: water from y = -3 to y = 0, in a basin of stone.
        fill(helper, new BlockPos(12, -4, 21), new BlockPos(20, 0, SIZE - 1), Blocks.STONE.defaultBlockState());
        fill(helper, new BlockPos(13, -3, 22), new BlockPos(19, 0, SIZE - 2), Blocks.WATER.defaultBlockState());
        BlockPos heart = helper.absolutePos(new BlockPos(16, 1, 2));
        List<BlockPos> gates = new ArrayList<>();
        for (int x = 15; x <= 17; x++) {
            BlockPos gate = new BlockPos(x, 1, 18);
            helper.setBlock(gate, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,
                    Direction.NORTH));
            gates.add(helper.absolutePos(gate));
        }

        List<VillageExits.Approach> approaches = VillageExits.approaches(helper.getLevel(), heart, gates);

        helper.assertTrue(approaches.size() == 1, "The three gates must make one approach: " + approaches.size());
        BlockPos expected = helper.absolutePos(new BlockPos(16, 1, 18 + VillageExits.APPROACH_DISTANCE));
        helper.assertTrue(approaches.get(0).point().equals(expected),
                "The approach must be at the surface of the pond: " + helper.relativePos(approaches.get(0).point()));
        helper.succeed();
    }

    /**
     * A raider marches on a village with a palisade. The arena {@code arena_huge} has a heart in its middle and a
     * square palisade of stone bricks 16 blocks from the heart, with a north gate of 3 fence gates. The raider is a
     * witch: it walks slowly, so one path does not take it through the gate before the next path.
     *
     * <ol>
     *   <li>The gate is closed: the raider walks to the front of the gate and stays outside while it forces the
     *       gate.</li>
     *   <li>The raider opens the gate, goes through it and arrives at the heart.</li>
     *   <li>The gate closes. Inside the palisade, 15 blocks from the heart, the march does not start again. The
     *       closed gate keeps the raider inside: through an open gate, the vanilla goals of the raid can move the
     *       raider out of the palisade, and then the march starts again.</li>
     *   <li>Outside the palisade, the march starts again.</li>
     * </ol>
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "raid_gate")
    public static void raiderForcesAClosedGateThenGoesThroughIt(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos approach = helper.absolutePos(heartRelative.north(PALISADE + VillageExits.APPROACH_DISTANCE));
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        Witch raider = helper.spawn(EntityType.WITCH, heartRelative.offset(10, 0, -PALISADE - 10));
        Raid raid = new Raid(RAID_ID + 1, level, heartPos);
        // At midnight, the heart keeps the gate closed: only the raider opens it.
        helper.onEachTick(() -> level.setDayTime(MIDNIGHT));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    helper.assertTrue(heart.getLayout().gates().equals(gates),
                            "The village must have the gates of the palisade: " + heart.getLayout().gates());
                    raid.joinRaid(1, raider, null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(horizontalDistance(raider.blockPosition(), front) <= 3.0,
                        "The raider must walk to the front of the gate: " + helper.relativePos(raider.blockPosition())))
                .thenExecuteFor(40, () -> helper.assertTrue(isMarching(raider) && !isOpen(level, gates)
                        && RaidMarch.squareDistance(heartPos, raider.blockPosition()) > PALISADE,
                        "The raider must stay outside while it forces the gate: "
                        + helper.relativePos(raider.blockPosition())))
                .thenWaitUntil(() -> helper.assertTrue(isOpen(level, gates)
                        && horizontalDistance(raider.blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL
                        && !isMarching(raider),
                        "The raider must force the gate, go through it and arrive at the heart: "
                        + helper.relativePos(raider.blockPosition())))
                .thenExecute(() -> {
                    setGates(level, gates, false);
                    moveTo(raider, heartPos.north(PALISADE - 1));
                })
                .thenExecuteFor(60, () -> helper.assertFalse(isMarching(raider),
                        "Inside the palisade, the march must not start again"))
                .thenExecute(() -> moveTo(raider, approach))
                .thenWaitUntil(() -> helper.assertTrue(isMarching(raider),
                        "Outside the palisade, the march must start again"))
                .thenExecute(() -> {
                    raider.discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * Raiders of a raid reach the heart of a village with a palisade and closed gates. The palisade of
     * {@link #buildPalisade} has one closed gate, on the north side. The time stays at midnight: the heart keeps the
     * gates closed, and only a raider can open the gate. A vindicator with no target starts outside the east side. A
     * pillager starts outside the south side, with the target of a villager near the heart. Each raider walks around
     * the palisade to the gate. The test records the tick that the gate opens, with a raider in front of it. Then the
     * vindicator arrives at the heart, and the pillager is inside the palisade.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_closed")
    public static void raidersForceClosedGatesToTheHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        Raid raid = new Raid(RAID_ID + 3, level, heartPos);
        List<Raider> raiders = new ArrayList<>();
        Villager[] villager = new Villager[1];
        boolean[] forced = new boolean[1];
        int outside = PALISADE + 5;
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            if (!forced[0] && !raiders.isEmpty() && isOpen(level, gates)) {
                forced[0] = raiders.stream()
                        .anyMatch(raider -> horizontalDistance(raider.blockPosition(), front) <= 3.0);
                helper.assertTrue(forced[0], "A raider in front of the gate must open it");
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    setGates(level, gates, false);
                    villager[0] = helper.spawn(EntityType.VILLAGER, heartRelative.east(3));
                    villager[0].setNoAi(true);
                    villager[0].setInvulnerable(true);
                    Raider vindicator = helper.spawn(EntityType.VINDICATOR, heartRelative.east(outside));
                    Raider pillager = helper.spawn(EntityType.PILLAGER, heartRelative.south(outside));
                    for (Raider raider : List.of(vindicator, pillager)) {
                        raid.joinRaid(1, raider, null, true);
                        raiders.add(raider);
                    }
                    pillager.setTarget(villager[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(forced[0]
                        && horizontalDistance(raiders.get(0).blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL
                        && RaidMarch.squareDistance(heartPos, raiders.get(1).blockPosition()) < PALISADE,
                        "A raider must force the closed gate, the vindicator must arrive at the heart, and the pillager "
                        + "with a target must be inside the palisade"))
                .thenExecute(() -> {
                    raiders.forEach(Raider::discard);
                    villager[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * Raiders of a raid reach the heart of a village through a gate of stacked fence gates. The palisade of
     * {@link #buildPalisade} has one closed gate of 2 rows of 3 fence gates, on the north side. The heart makes one
     * gate of the 2 rows: the stairs of the exits do not change the terrain in front of the gate, and the approach of
     * the gate has the 6 fence gates. The time stays at midnight: the heart keeps the gate closed. A vindicator starts
     * outside the east side, walks around the palisade to the gate and forces it. All the fence gates of the gate
     * open, and the vindicator arrives at the heart.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_stacked")
    public static void raidersForceStackedGatesToTheHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 2);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        Raid raid = new Raid(RAID_ID + 4, level, heartPos);
        Raider[] raider = new Raider[1];
        boolean[] forced = new boolean[1];
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            if (!forced[0] && raider[0] != null && gates.stream()
                    .anyMatch(gate -> level.getBlockState(gate).getValue(FenceGateBlock.OPEN))) {
                forced[0] = horizontalDistance(raider[0].blockPosition(), front) <= 3.0 && isOpen(level, gates);
                helper.assertTrue(forced[0], "A raider in front of the gate must open all the fence gates together");
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    helper.assertTrue(heart.getLayout().gates().size() == gates.size(),
                            "The village must have the 6 fence gates: " + heart.getLayout().gates());
                    int stairs = VillageExits.build(level, heartPos, heart.getLayout().gates());
                    helper.assertTrue(stairs == 0, "The stacked fence gates must get no stairs: " + stairs);
                    for (int dx = -1; dx <= 1; dx++) {
                        helper.assertBlockPresent(Blocks.AIR, heartRelative.offset(dx, 0, -PALISADE - 1));
                    }
                    List<VillageExits.Approach> approaches = VillageExits.approaches(level, heartPos,
                            heart.getLayout().gates());
                    helper.assertTrue(approaches.size() == 1 && approaches.get(0).gates().size() == 3
                            && approaches.get(0).blocks().size() == gates.size(),
                            "The 2 rows must make one gate: " + approaches);
                    setGates(level, gates, false);
                    raider[0] = helper.spawn(EntityType.VINDICATOR, heartRelative.east(PALISADE + 5));
                    raid.joinRaid(1, raider[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(forced[0]
                        && horizontalDistance(raider[0].blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL,
                        "The raider must force the stacked gate and arrive at the heart: "
                        + helper.relativePos(raider[0].blockPosition())))
                .thenExecute(() -> {
                    raider[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * A raider breaks a gate that a unit closes again after each force. The palisade of {@link #buildPalisade} has
     * one closed gate of 2 rows of 3 fence gates, on the north side. The time stays at midnight. At each tick, the test
     * closes the open fence gates while the raider is outside the palisade, as a unit that goes through the gate. The
     * config makes the times short: 1 second to force the gate, 4 seconds before the break, 2 seconds to break. The
     * raider forces the gate, then breaks the middle column (the 2 stacked fence gates), goes through the opening and
     * arrives at the heart. The 4 other fence gates stay, and the broken fence gates drop no item.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_break")
    public static void raiderBreaksAGateThatUnitsCloseAgain(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, true);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 2);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        List<BlockPos> column = gates.stream().filter(gate -> gate.getX() == heartPos.getX()).toList();
        List<BlockPos> sides = gates.stream().filter(gate -> gate.getX() != heartPos.getX()).toList();
        Raider[] raider = new Raider[1];
        int[] closes = new int[1];
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            if (raider[0] != null && closeGateOnRaider(level, gates, heartPos, front, raider[0])) {
                closes[0]++;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    setGates(level, gates, false);
                    raider[0] = helper.spawn(EntityType.VINDICATOR, heartRelative.east(PALISADE + 5));
                    raid(level, heartPos).joinRaid(1, raider[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(column.stream().allMatch(gate -> level.getBlockState(gate)
                        .isAir()), "The raider must break the middle column of the gate"))
                .thenExecute(() -> {
                    helper.assertTrue(closes[0] >= 1, "The raider must force the gate before the break");
                    helper.assertTrue(sides.stream().allMatch(gate -> level.getBlockState(gate).getBlock()
                            instanceof FenceGateBlock), "The other fence gates must stay");
                    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(column.get(0)).inflate(4.0))
                            .isEmpty(), "The broken fence gates must drop no item");
                    helper.assertTrue(VillageExits.approaches(level, heartPos, gates).stream()
                            .anyMatch(approach -> approach.isOpen(level)),
                            "The broken gate must be an open exit of the village");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        horizontalDistance(raider[0].blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL,
                        "The raider must go through the opening and arrive at the heart: "
                        + helper.relativePos(raider[0].blockPosition())))
                .thenExecute(() -> {
                    raider[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * With the game rule {@code mobGriefing} false, a raider does not break a gate: it only forces it. The setup is
     * the setup of {@link #raiderBreaksAGateThatUnitsCloseAgain} with one row of fence gates. The raider forces the
     * gate 3 times or more, and all the fence gates stay during 300 ticks after the first force: more than the
     * time before the break plus the time to break.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_no_griefing")
    public static void raiderDoesNotBreakAGateWithNoMobGriefing(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, false);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        Raider[] raider = new Raider[1];
        int[] closes = new int[1];
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            helper.assertTrue(gates.stream().allMatch(gate -> level.getBlockState(gate).getBlock()
                    instanceof FenceGateBlock), "With mobGriefing false, the raider must not break the gate");
            if (raider[0] != null && closeGateOnRaider(level, gates, heartPos, front, raider[0])) {
                closes[0]++;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    setGates(level, gates, false);
                    raider[0] = helper.spawn(EntityType.VINDICATOR, heartRelative.east(PALISADE + 5));
                    raid(level, heartPos).joinRaid(1, raider[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(closes[0] >= 1, "The raider must force the gate"))
                .thenIdle(300)
                .thenExecute(() -> {
                    raider[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                    helper.assertTrue(closes[0] >= 3, "The raider must force the gate again: " + closes[0]);
                })
                .thenSucceed();
    }

    /**
     * Ravagers break a gate of 3 columns column by column until the opening is as wide as a ravager. The palisade of
     * {@link #buildPalisade} has one closed gate of 3 fence gates, on the north side, with 3 blocks of free height for
     * the ravagers. The time stays at midnight. At each tick, the test closes the open fence gates while a raider is
     * outside the palisade, as a unit that goes through the gate. A ravager and a pillager on a ravager force the
     * gate, then break the middle column. The gate is then open for a pillager and closed for a ravager (1.95 blocks
     * wide). The raiders break the next column, go through the opening and arrive at the heart.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_break_ravager")
    public static void ravagersBreakTheGateUntilTheyFit(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, true);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        for (int dx = -1; dx <= 1; dx++) {
            helper.setBlock(heartRelative.offset(dx, 2, -PALISADE), Blocks.AIR);
        }
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        List<Raider> ravagers = new ArrayList<>();
        Raider[] rider = new Raider[1];
        boolean[] narrow = new boolean[1];
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            closeGateOnRaiders(level, gates, heartPos, front, ravagers);
            long broken = gates.stream().filter(gate -> level.getBlockState(gate).isAir()).count();
            if (broken == 1 && !narrow[0]) {
                VillageExits.Approach approach = VillageExits.approaches(level, heartPos, gates).get(0);
                narrow[0] = approach.isOpen(level, 0.6) && !approach.isOpen(level, 1.95);
                helper.assertTrue(narrow[0], "One broken column must be open for a pillager and closed for a ravager");
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    setGates(level, gates, false);
                    Raid raid = raid(level, heartPos);
                    Raider alone = helper.spawn(EntityType.RAVAGER, heartRelative.offset(8, 0, -PALISADE - 8));
                    Raider ridden = helper.spawn(EntityType.RAVAGER, heartRelative.offset(-8, 0, -PALISADE - 8));
                    rider[0] = helper.spawn(EntityType.PILLAGER, heartRelative.offset(-8, 0, -PALISADE - 8));
                    rider[0].startRiding(ridden, true);
                    for (Raider raider : List.of(alone, ridden, rider[0])) {
                        raid.joinRaid(1, raider, null, true);
                    }
                    ravagers.add(alone);
                    ravagers.add(ridden);
                })
                .thenWaitUntil(() -> helper.assertTrue(narrow[0] && ravagers.stream().allMatch(raider -> raider.isAlive()
                        && horizontalDistance(raider.blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL),
                        "The ravagers must break the gate until they fit and arrive at the heart: "
                        + ravagers.stream().map(raider -> helper.relativePos(raider.blockPosition())).toList()))
                .thenExecute(() -> {
                    helper.assertTrue(gates.stream().filter(gate -> level.getBlockState(gate).isAir()).count() >= 2,
                            "The raiders must break 2 columns or more");
                    ravagers.forEach(Raider::discard);
                    rider[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * A raider breaks a gate that was open in front of it, also when it did not force the gate. The time to force a
     * gate is 120 seconds, more than the time of the test. The test opens the gate for 2 ticks when the raider is in
     * front of it for 40 ticks, as a unit that goes through. Then it closes the open fence gates while the raider is
     * outside the palisade. The raider breaks the middle column.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1600, batch = "raid_gate_break_seen_open")
    public static void raiderBreaksAGateThatWasOpenInFrontOfIt(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, true);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        BlockPos middle = helper.absolutePos(heartRelative.north(PALISADE));
        Raider[] raider = new Raider[1];
        int[] inFront = new int[1];
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 120);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            if (raider[0] == null) {
                return;
            }
            if (inFront[0] > 0 || horizontalDistance(raider[0].blockPosition(), front) <= 2.0) {
                inFront[0]++;
            }
            if (inFront[0] == 40) {
                setGates(level, gates, true);
            } else if (inFront[0] > 42) {
                closeGateOnRaider(level, gates, heartPos, front, raider[0]);
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    setGates(level, gates, false);
                    raider[0] = helper.spawn(EntityType.VINDICATOR, heartRelative.offset(8, 0, -PALISADE - 8));
                    raid(level, heartPos).joinRaid(1, raider[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(middle).isAir(),
                        "The raider must break the middle column of a gate that was open in front of it"))
                .thenExecute(() -> {
                    raider[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * A ravager at a village with only gates of one column breaks the palisade next to a gate and arrives at the heart.
     * A gate of one column is too narrow for a ravager (1.95 blocks wide), also when it is open. The palisade of
     * {@link #buildNarrowPalisade} has a closed gate of one fence gate on each side, with 3 blocks of free height. The
     * time stays at midnight.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_narrow")
    public static void ravagerBreaksThePalisadeAtNarrowGates(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, true);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> walls = buildNarrowPalisade(helper, heartRelative);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        Raider[] ravager = new Raider[1];
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        helper.onEachTick(() -> level.setDayTime(MIDNIGHT));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    List<VillageExits.Approach> approaches = VillageRaids.approaches(level, heartPos);
                    helper.assertTrue(approaches.size() == 4 && approaches.stream()
                            .noneMatch(approach -> approach.fits(level, 1.95, 2.2)),
                            "No gate of one column must fit a ravager");
                    ravager[0] = helper.spawn(EntityType.RAVAGER, heartRelative.offset(6, 0, -PALISADE - 8));
                    raid(level, heartPos).joinRaid(1, ravager[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(ravager[0].isAlive()
                        && horizontalDistance(ravager[0].blockPosition(), heartPos) <= MarchOnVillageGoal.ARRIVAL,
                        "The ravager must break the palisade and arrive at the heart: "
                        + helper.relativePos(ravager[0].blockPosition())))
                .thenExecute(() -> {
                    helper.assertTrue(walls.stream().anyMatch(wall -> level.getBlockState(wall).isAir()),
                            "The ravager must break the palisade next to a gate");
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        BlockPos gate = heartRelative.relative(side, PALISADE);
                        for (int d = 2; d <= 3; d++) {
                            for (Direction along : List.of(side.getClockWise(), side.getCounterClockWise())) {
                                helper.assertBlockPresent(Blocks.STONE_BRICKS, gate.relative(along, d));
                            }
                        }
                    }
                    helper.assertBlockPresent(ModBlocks.VILLAGE_HEART.get(), heartRelative);
                    ravager[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * With the game rule {@code mobGriefing} false and no gate that fits, a ravager breaks nothing. The palisade of
     * {@link #buildNarrowPalisade} has only gates of one column. The ravager stays at a gate, and all the blocks of
     * the palisade next to the gates stay during 300 ticks: more than the time before the break plus the time to break.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 2400, batch = "raid_gate_narrow_no_griefing")
    public static void ravagerBreaksNothingWithNoMobGriefing(GameTestHelper helper) {
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_MOBGRIEFING, false);
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> walls = buildNarrowPalisade(helper, heartRelative);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        Raider[] ravager = new Raider[1];
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS, 4);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_BREAK_SECONDS, 2);
        helper.onEachTick(() -> {
            level.setDayTime(MIDNIGHT);
            helper.assertTrue(walls.stream().noneMatch(wall -> level.getBlockState(wall).isAir()),
                    "With mobGriefing false, the ravager must not break the palisade");
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    ravager[0] = helper.spawn(EntityType.RAVAGER, heartRelative.offset(6, 0, -PALISADE - 8));
                    raid(level, heartPos).joinRaid(1, ravager[0], null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(horizontalDistance(ravager[0].blockPosition(), front) <= 3.0,
                        "The ravager must walk to the front of the north gate: "
                        + helper.relativePos(ravager[0].blockPosition())))
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(RaidMarch.squareDistance(heartPos, ravager[0].blockPosition()) > PALISADE,
                            "The ravager must stay outside the palisade");
                    ravager[0].discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                })
                .thenSucceed();
    }

    /**
     * A gate with 2 blocks of free height is open for a pillager and closed for a ravager (2.2 blocks high). With 3
     * blocks of free height, the gate is open for a ravager.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "raid_gate_low")
    public static void lowGateIsClosedForARavager(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        setGates(level, gates, true);
        VillageExits.Approach low = VillageExits.approaches(level, heartPos, gates).get(0);
        helper.assertTrue(low.isOpen(level, 0.6, 1.95), "A low open gate must be open for a pillager");
        helper.assertFalse(low.isOpen(level, 1.95, 2.2), "A low open gate must be closed for a ravager");
        helper.assertFalse(low.fits(level, 1.95, 2.2), "A low gate must not fit a ravager");
        for (int dx = -1; dx <= 1; dx++) {
            helper.setBlock(heartRelative.offset(dx, 2, -PALISADE), Blocks.AIR);
        }
        helper.assertTrue(low.isOpen(level, 1.95, 2.2), "An open gate of 3 blocks of free height must be open for"
                + " a ravager");
        helper.succeed();
    }

    /** A new raid at the heart, with an identifier that no other test uses. */
    private static Raid raid(ServerLevel level, BlockPos heart) {
        return new Raid(NEXT_RAID_ID.getAndIncrement(), level, heart);
    }

    /**
     * Closes the open fence gates of a gate while the raider is outside the palisade or in the row of the gate, as a
     * unit that goes through the gate (see {@link #closeGateOnRaiders}).
     *
     * @param front the position in front of the gate, outside the palisade
     * @return true if the fence gates close
     */
    private static boolean closeGateOnRaider(ServerLevel level, List<BlockPos> gates, BlockPos heartPos,
            BlockPos front, Raider raider) {
        return closeGateOnRaiders(level, gates, heartPos, front, List.of(raider));
    }

    /**
     * Closes the open fence gates of a gate while a raider of the list is outside the palisade or in the row of the
     * gate, as a unit that goes through the gate. A raider that walks against the closed gate stands in the row of the
     * gate and can force the gate from there. When the gate opens, the raider moves into the fence gates before the
     * next tick of the test, and a fence gate that closes on a raider does not stop it. Thus the test moves each raider
     * that touches a closed fence gate back to the front of the gate before it closes the gate. This move is a part of
     * the test only: in the game, a raider in a forced gate goes through it.
     *
     * @param front the position in front of the gate, outside the palisade
     * @return true if the fence gates close
     */
    private static boolean closeGateOnRaiders(ServerLevel level, List<BlockPos> gates, BlockPos heartPos,
            BlockPos front, List<? extends Raider> raiders) {
        if (!anyOpen(level, gates) || raiders.stream()
                .noneMatch(raider -> RaidMarch.squareDistance(heartPos, raider.blockPosition()) >= PALISADE)) {
            return false;
        }
        for (Raider raider : raiders) {
            if (touchesClosedGate(level, gates, raider)) {
                raider.teleportTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
            }
        }
        setGates(level, gates, false);
        return true;
    }

    /** True if the box of the raider touches the collision of a fence gate of the list in its closed state. */
    private static boolean touchesClosedGate(ServerLevel level, List<BlockPos> gates, Raider raider) {
        VoxelShape box = Shapes.create(raider.getBoundingBox());
        for (BlockPos gate : gates) {
            BlockState state = level.getBlockState(gate);
            if (state.getBlock() instanceof FenceGateBlock && Shapes.joinIsNotEmpty(box, state
                    .setValue(FenceGateBlock.OPEN, false).getCollisionShape(level, gate)
                    .move(gate.getX(), gate.getY(), gate.getZ()), BooleanOp.AND)) {
                return true;
            }
        }
        return false;
    }

    /** True if a fence gate of the list is open. */
    private static boolean anyOpen(ServerLevel level, List<BlockPos> gates) {
        return gates.stream().map(level::getBlockState).anyMatch(state -> state.getBlock() instanceof FenceGateBlock
                && state.getValue(FenceGateBlock.OPEN));
    }

    /**
     * Builds a square palisade of stone bricks around the heart, {@link #PALISADE} blocks from it, 3 blocks high, with
     * a closed gate of 3 fence gates on the north side. The gate has 1 or 2 rows of fence gates. Above them, the gate
     * has free height up to 2 blocks.
     *
     * @param rows the number of rows of fence gates: 1 or 2
     * @return the positions of the fence gates
     */
    private static List<BlockPos> buildPalisade(GameTestHelper helper, BlockPos heart, int rows) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        for (int d = -PALISADE; d <= PALISADE; d++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(heart.offset(d, y - 1, -PALISADE), wall);
                helper.setBlock(heart.offset(d, y - 1, PALISADE), wall);
                helper.setBlock(heart.offset(-PALISADE, y - 1, d), wall);
                helper.setBlock(heart.offset(PALISADE, y - 1, d), wall);
            }
        }
        List<BlockPos> gates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 0; y < 2; y++) {
                BlockPos pos = heart.offset(dx, y, -PALISADE);
                if (y < rows) {
                    helper.setBlock(pos, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                            .setValue(FenceGateBlock.FACING, Direction.NORTH));
                    gates.add(helper.absolutePos(pos));
                } else {
                    helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        return gates;
    }

    /**
     * Builds a square palisade of stone bricks around the heart, {@link #PALISADE} blocks from it, 3 blocks high, with
     * a closed gate of one fence gate in the middle of each side. Each gate has 3 blocks of free height.
     *
     * @return the positions of the palisade next to the gates, on the line of the gates
     */
    private static List<BlockPos> buildNarrowPalisade(GameTestHelper helper, BlockPos heart) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        for (int d = -PALISADE; d <= PALISADE; d++) {
            for (int y = 0; y < 3; y++) {
                helper.setBlock(heart.offset(d, y, -PALISADE), wall);
                helper.setBlock(heart.offset(d, y, PALISADE), wall);
                helper.setBlock(heart.offset(-PALISADE, y, d), wall);
                helper.setBlock(heart.offset(PALISADE, y, d), wall);
            }
        }
        List<BlockPos> walls = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos gate = heart.relative(side, PALISADE);
            Direction along = side.getClockWise();
            helper.setBlock(gate, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, side));
            helper.setBlock(gate.above(), Blocks.AIR);
            helper.setBlock(gate.above(2), Blocks.AIR);
            for (int y = 0; y < 3; y++) {
                walls.add(helper.absolutePos(gate.relative(along).above(y)));
                walls.add(helper.absolutePos(gate.relative(along.getOpposite()).above(y)));
            }
        }
        return walls;
    }

    /** Opens or closes fence gates. */
    private static void setGates(ServerLevel level, List<BlockPos> gates, boolean open) {
        for (BlockPos gate : gates) {
            BlockState state = level.getBlockState(gate);
            if (state.getBlock() instanceof FenceGateBlock) {
                level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    /** True if all the fence gates are open. */
    private static boolean isOpen(ServerLevel level, List<BlockPos> gates) {
        return gates.stream().allMatch(gate -> level.getBlockState(gate).getValue(FenceGateBlock.OPEN));
    }

    private static void moveTo(Raider raider, BlockPos pos) {
        raider.getNavigation().stop();
        raider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, raider.getYRot(), raider.getXRot());
    }

    private static boolean isMarching(Raider raider) {
        return raider.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof MarchOnVillageGoal);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A raider of a raid at a faction village crosses a river on its march to the heart. The river (2 blocks deep)
     * goes across the arena between the raider and the heart, and walls close the arena: the raider swims.
     */
    @GameTest(template = "arena_large", timeoutTicks = 400, batch = "raid_march")
    public static void raiderCrossesARiverToTheHeart(GameTestHelper helper) {
        TestSupport.sweep(helper);
        buildGrass(helper);
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        fill(helper, new BlockPos(0, 1, 0), new BlockPos(SIZE - 1, 3, 0), wall);
        fill(helper, new BlockPos(0, 1, SIZE - 1), new BlockPos(SIZE - 1, 3, SIZE - 1), wall);
        fill(helper, new BlockPos(0, 1, 0), new BlockPos(0, 3, SIZE - 1), wall);
        fill(helper, new BlockPos(SIZE - 1, 1, 0), new BlockPos(SIZE - 1, 3, SIZE - 1), wall);
        fill(helper, new BlockPos(14, -1, 1), new BlockPos(16, 0, SIZE - 2), Blocks.WATER.defaultBlockState());
        ServerLevel level = helper.getLevel();
        BlockPos heart = helper.absolutePos(new BlockPos(30, 1, 16));
        VillageHearts.register(level, heart);
        Pillager raider = helper.spawn(EntityType.PILLAGER, new BlockPos(3, 1, 16));
        Raid raid = new Raid(RAID_ID, level, heart);
        raid.joinRaid(1, raider, null, true);

        helper.runAtTickTime(20, () -> helper.assertTrue(isMarching(raider), "The raider must march on the heart"));
        helper.runAtTickTime(395, () -> VillageHearts.unregister(level, heart));
        helper.succeedWhen(() -> {
            Vec3 pos = helper.relativeVec(raider.position());
            helper.assertTrue(pos.x() > 17.0 && raider.onGround() && !raider.isInWater(),
                    "The raider must cross the river: " + pos);
            raider.discard();
            VillageHearts.unregister(level, heart);
        });
    }

    /** A floor of grass at y = 0 under the whole arena. */
    private static void buildGrass(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 0, 0), new BlockPos(SIZE - 1, 0, SIZE - 1), Blocks.GRASS_BLOCK.defaultBlockState());
    }

    /** Fills a box of the arena with a block, both corners included. */
    private static void fill(GameTestHelper helper, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, state);
        }
    }

    /**
     * A raid of level 4 or 5 at a faction village gets extra ravagers in each wave ({@link VillageRaids#extraRavagers}):
     * the first raider of a new wave adds them, and the next raiders of the same wave add no more. A raid of level 3
     * gets no extra raiders. Each run uses new raid ids ({@code NEXT_RAID_ID}): {@code VillageRaids} keeps the waves
     * that have their extra ravagers by raid id, thus a second run in the same server starts with no reinforced wave.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "raid_waves")
    public static void raidsOfLevel4And5GetExtraRavagers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos heart = helper.absolutePos(new BlockPos(16, 1, 16));
        BlockPos spawn = helper.absolutePos(new BlockPos(16, 1, 4));
        VillageHearts.register(level, heart);
        Raid strong = new Raid(NEXT_RAID_ID.getAndIncrement(), level, heart);
        strong.setRaidOmenLevel(5);
        Raid weak = new Raid(NEXT_RAID_ID.getAndIncrement(), level, heart);
        weak.setRaidOmenLevel(3);
        try {
            strong.joinRaid(1, EntityType.PILLAGER.create(level), spawn, false);
            strong.joinRaid(1, EntityType.PILLAGER.create(level), spawn, false);
            long firstWave = ravagers(strong);
            strong.joinRaid(2, EntityType.PILLAGER.create(level), spawn, false);
            long twoWaves = ravagers(strong);
            weak.joinRaid(1, EntityType.PILLAGER.create(level), spawn, false);

            int extra = VillageRaids.extraRavagers(5);
            helper.assertTrue(VillageRaids.extraRavagers(3) == 0 && VillageRaids.extraRavagers(4) == 1 && extra == 2,
                    "A raid of level 3 must get 0 extra ravagers, level 4 must get 1, and level 5 must get 2");
            helper.assertTrue(firstWave == extra, "Wave 1 of a raid of level 5 must get " + extra + " ravagers: "
                    + firstWave);
            helper.assertTrue(twoWaves == 2L * extra, "Wave 2 must get " + extra + " more ravagers: " + twoWaves);
            helper.assertTrue(ravagers(weak) == 0, "A raid of level 3 must get no extra ravagers");
        } finally {
            strong.getAllRaiders().forEach(Raider::discard);
            weak.getAllRaiders().forEach(Raider::discard);
            VillageHearts.unregister(level, heart);
        }
        helper.succeed();
    }

    /**
     * The value {@code gateForceSeconds} of the config sets the time to force a gate. With 1 second, the closed gate
     * opens less than 100 ticks after the raider arrives in front of it. With the default value (10 seconds), the
     * raider stays 200 ticks in front of the gate. A listener sets the old value again when the test ends.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "raid_gate_config")
    public static void gateForceTimeFollowsTheConfig(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative, 1);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos front = helper.absolutePos(heartRelative.north(PALISADE + 1));
        Witch raider = helper.spawn(EntityType.WITCH, heartRelative.offset(10, 0, -PALISADE - 10));
        Raid raid = new Raid(RAID_ID + 5, level, heartPos);
        helper.onEachTick(() -> level.setDayTime(MIDNIGHT));
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_GATE_FORCE_SECONDS, 1);
        long[] arrival = {0L};

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageHearts.findNearest(level, heartPos, 1.0).isPresent(),
                        "The heart must be in the list of the loaded hearts"))
                .thenExecute(() -> {
                    VillageHeartBlockEntity heart = helper.getBlockEntity(heartRelative);
                    helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
                    raid.joinRaid(1, raider, null, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(horizontalDistance(raider.blockPosition(), front) <= 3.0,
                        "The raider must walk to the front of the gate: " + helper.relativePos(raider.blockPosition())))
                .thenExecute(() -> arrival[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertTrue(isOpen(level, gates), "The raider must force the gate"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - arrival[0];
                    raider.discard();
                    helper.setBlock(heartRelative, Blocks.AIR);
                    helper.assertTrue(elapsed < 100, "With gateForceSeconds = 1, the gate must open in less than 100"
                            + " ticks: " + elapsed);
                })
                .thenSucceed();
    }

    /**
     * The values {@code extraRavagersLevel4} and {@code extraRavagersLevel5} of the config set the extra ravagers of
     * each wave. The test sets 0 and 3. A listener sets the old values again when the test ends.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "raid_waves_config")
    public static void extraRavagersFollowTheConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos heart = helper.absolutePos(new BlockPos(16, 1, 16));
        BlockPos spawn = helper.absolutePos(new BlockPos(16, 1, 4));
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_EXTRA_RAVAGERS_LEVEL_4, 0);
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_EXTRA_RAVAGERS_LEVEL_5, 3);
        VillageHearts.register(level, heart);
        Raid raid = new Raid(NEXT_RAID_ID.getAndIncrement(), level, heart);
        raid.setRaidOmenLevel(5);
        try {
            raid.joinRaid(1, EntityType.PILLAGER.create(level), spawn, false);
            helper.assertTrue(VillageRaids.extraRavagers(4) == 0 && VillageRaids.extraRavagers(5) == 3,
                    "The extra ravagers must be the values of the config");
            helper.assertTrue(ravagers(raid) == 3, "Wave 1 of a raid of level 5 must get 3 ravagers: "
                    + ravagers(raid));
        } finally {
            raid.getAllRaiders().forEach(Raider::discard);
            VillageHearts.unregister(level, heart);
        }
        helper.succeed();
    }

    private static long ravagers(Raid raid) {
        return raid.getAllRaiders().stream().filter(raider -> raider.getType() == EntityType.RAVAGER).count();
    }
}
