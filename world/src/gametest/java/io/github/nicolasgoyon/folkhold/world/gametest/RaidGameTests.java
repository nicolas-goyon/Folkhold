package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MarchOnVillageGoal;
import io.github.nicolasgoyon.folkhold.world.village.RaidMarch;
import io.github.nicolasgoyon.folkhold.world.village.RaidSpawns;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    /** The size of the template {@code arena_huge}, in blocks. */
    private static final int HUGE = 56;
    /** The distance from the heart to the palisade in the test of the gates, in blocks. */
    private static final int PALISADE = 16;

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

        // Some ticks for the light of the new blocks.
        helper.runAfterDelay(10, () -> {
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
            helper.succeed();
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
     *   <li>The gate stays closed: the raider walks to the approach of the gate and waits there.</li>
     *   <li>The gate opens: the raider goes through the gate and arrives at the heart.</li>
     *   <li>Inside the palisade, 15 blocks from the heart, the march does not start again.</li>
     *   <li>Outside the palisade, the march starts again.</li>
     * </ol>
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "raid_gate")
    public static void raiderWaitsAtAClosedGateThenGoesThroughIt(GameTestHelper helper) {
        TestSupport.buildFloor(helper, HUGE);
        BlockPos heartRelative = new BlockPos(HUGE / 2, 1, HUGE / 2);
        List<BlockPos> gates = buildPalisade(helper, heartRelative);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(heartRelative);
        BlockPos approach = helper.absolutePos(heartRelative.north(PALISADE + VillageExits.APPROACH_DISTANCE));
        Witch raider = helper.spawn(EntityType.WITCH, heartRelative.offset(10, 0, -PALISADE - 10));
        Raid raid = new Raid(RAID_ID + 1, level, heartPos);

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
                .thenWaitUntil(() -> helper.assertTrue(horizontalDistance(raider.blockPosition(), approach)
                        <= RaidMarch.AT_APPROACH, "The raider must walk to the approach of the gate: "
                        + helper.relativePos(raider.blockPosition())))
                .thenExecuteFor(40, () -> helper.assertTrue(isMarching(raider)
                        && RaidMarch.squareDistance(heartPos, raider.blockPosition()) > PALISADE,
                        "The raider must wait outside the closed gate: " + helper.relativePos(raider.blockPosition())))
                .thenExecute(() -> setGates(level, gates, true))
                .thenWaitUntil(() -> helper.assertTrue(horizontalDistance(raider.blockPosition(), heartPos)
                        <= MarchOnVillageGoal.ARRIVAL && !isMarching(raider),
                        "The raider must go through the gate and arrive at the heart: "
                        + helper.relativePos(raider.blockPosition())))
                .thenExecute(() -> moveTo(raider, heartPos.north(PALISADE - 1)))
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
     * Builds a square palisade of stone bricks around the heart, {@link #PALISADE} blocks from it, 3 blocks high, with
     * a closed gate of 3 fence gates on the north side. The gate has 2 blocks of free height.
     *
     * @return the positions of the fence gates
     */
    private static List<BlockPos> buildPalisade(GameTestHelper helper, BlockPos heart) {
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
            BlockPos gate = heart.offset(dx, 0, -PALISADE);
            helper.setBlock(gate, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                    .setValue(FenceGateBlock.FACING, Direction.NORTH));
            helper.setBlock(gate.above(), Blocks.AIR);
            gates.add(helper.absolutePos(gate));
        }
        return gates;
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

}
