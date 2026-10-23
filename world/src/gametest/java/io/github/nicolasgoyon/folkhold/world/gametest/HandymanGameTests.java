package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.village.VillageBraziers;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the handymen of the faction villages and the braziers (see {@code Handymen} and
 * {@link VillageBraziers}). The tests use the template {@code arena_huge}: the scan area of the heart in the middle
 * stays in the arena. Each test keeps a fixed day time during its run.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandymanGameTests {
    /** The size of the template {@code arena_huge}, in blocks. */
    private static final int HUGE = 56;
    private static final BlockPos HEART = new BlockPos(HUGE / 2, 1, HUGE / 2);
    private static final BlockPos WEST = HEART.offset(-6, 0, 0);
    private static final BlockPos EAST = HEART.offset(6, 0, 3);
    /** The handyman starts 6 blocks north of the heart, far from the braziers. */
    private static final BlockPos START = HEART.offset(0, 0, -6);
    private static final long EVENING = VillageBraziers.EVENING + 200L;
    private static final long NIGHT = 18_000L;
    private static final long MORNING = 500L;
    private static final long DAY = VillageBraziers.MORNING_END + 1_000L;

    private HandymanGameTests() {
    }

    /**
     * In the evening, the handyman lights the braziers before the night. It is next to each brazier when the brazier
     * changes.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "handyman_evening")
    public static void handymanLightsTheBraziersInTheEvening(GameTestHelper helper) {
        List<BlockPos> braziers = List.of(WEST, EAST);
        village(helper, braziers, false);
        long[] time = {EVENING};
        helper.onEachTick(() -> helper.getLevel().setDayTime(time[0]));
        Villager handyman = handyman(helper);
        Map<BlockPos, BlockPos> changes = new HashMap<>();
        recordChanges(helper, braziers, handyman, changes);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, true),
                        "The handyman must light the braziers: " + states(helper, braziers)))
                .thenExecute(() -> {
                    cleanUp(helper, time, braziers, handyman);
                    assertNextTo(helper, braziers, changes);
                })
                .thenSucceed();
    }

    /**
     * The heart lights the braziers at night. At the start of the day, the braziers burn until the handyman puts them
     * out in the morning: it is next to each brazier when the brazier changes.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "handyman_morning")
    public static void handymanPutsOutTheBraziersInTheMorning(GameTestHelper helper) {
        List<BlockPos> braziers = List.of(WEST, EAST);
        village(helper, braziers, false);
        long[] time = {NIGHT};
        helper.onEachTick(() -> helper.getLevel().setDayTime(time[0]));
        Villager handyman = handyman(helper);
        Map<BlockPos, BlockPos> changes = new HashMap<>();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, true),
                        "The heart must light the braziers at night: " + states(helper, braziers)))
                .thenExecute(() -> {
                    handyman.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(START)));
                    recordChanges(helper, braziers, handyman, changes);
                    time[0] = MORNING;
                })
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, false),
                        "The handyman must put out the braziers: " + states(helper, braziers)))
                .thenExecute(() -> {
                    cleanUp(helper, time, braziers, handyman);
                    assertNextTo(helper, braziers, changes);
                })
                .thenSucceed();
    }

    /**
     * A brazier in a ring of stone has no position next to it: the handyman cannot go there. The brazier burns during
     * the morning, and the heart puts it out at the start of the work of the villagers.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1200, batch = "handyman_unreachable")
    public static void heartPutsOutTheBraziersThatTheHandymanCannotReach(GameTestHelper helper) {
        List<BlockPos> braziers = List.of(WEST, EAST);
        village(helper, braziers, false);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(EAST.offset(dx, 0, dz), stone);
                    helper.setBlock(EAST.offset(dx, 1, dz), stone);
                }
            }
        }
        long[] time = {NIGHT};
        helper.onEachTick(() -> helper.getLevel().setDayTime(time[0]));
        Villager handyman = handyman(helper);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, true),
                        "The heart must light the braziers at night: " + states(helper, braziers)))
                .thenExecute(() -> time[0] = MORNING)
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, List.of(WEST), false),
                        "The handyman must put out the brazier that it can reach: " + states(helper, braziers)))
                .thenExecuteFor(60, () -> helper.assertTrue(burn(helper, List.of(EAST), true),
                        "The brazier in the ring must burn during the morning: " + states(helper, braziers)))
                .thenExecute(() -> time[0] = DAY)
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, List.of(EAST), false),
                        "The heart must put out the brazier at the start of the work: " + states(helper, braziers)))
                .thenExecute(() -> cleanUp(helper, time, braziers, handyman))
                .thenSucceed();
    }

    /**
     * A village with no handyman: in the evening, the braziers do not change. The heart lights them at the start of
     * the night and puts them out at the start of the day.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 600, batch = "handyman_none")
    public static void villageWithNoHandymanChangesTheBraziersAtNightAndDay(GameTestHelper helper) {
        List<BlockPos> braziers = List.of(WEST, EAST);
        village(helper, braziers, false);
        long[] time = {EVENING};
        helper.onEachTick(() -> helper.getLevel().setDayTime(time[0]));

        helper.startSequence()
                .thenExecuteFor(60, () -> helper.assertTrue(burn(helper, braziers, false),
                        "With no handyman, the braziers must not burn in the evening: " + states(helper, braziers)))
                .thenExecute(() -> time[0] = NIGHT)
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, true),
                        "The heart must light the braziers at night: " + states(helper, braziers)))
                .thenExecute(() -> time[0] = MORNING)
                .thenWaitUntil(() -> helper.assertTrue(burn(helper, braziers, false),
                        "With no handyman, the heart must put out the braziers at the start of the day: "
                        + states(helper, braziers)))
                .thenExecute(() -> cleanUp(helper, time, braziers, null))
                .thenSucceed();
    }

    /** Builds the arena, the braziers and the heart. The heart scans the braziers. */
    private static void village(GameTestHelper helper, List<BlockPos> braziers, boolean lit) {
        TestSupport.sweep(helper);
        TestSupport.buildFloor(helper, HUGE);
        for (BlockPos brazier : braziers) {
            helper.setBlock(brazier, ModBlocks.BRAZIER.get().defaultBlockState().setValue(BrazierBlock.LIT, lit));
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = helper.absolutePos(HEART);
        VillageHearts.register(level, heartPos);
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
        helper.assertTrue(heart.getLayout().braziers().size() == braziers.size(),
                "The heart must find the braziers: " + heart.getLayout().braziers());
    }

    /** A handyman of the village, at {@link #START}. */
    private static Villager handyman(GameTestHelper helper) {
        Villager villager = VillageFolk.spawn(helper.getLevel(), helper.absolutePos(HEART),
                ModProfessions.HANDYMAN.get());
        helper.assertTrue(villager != null, "The handyman must spawn");
        villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(START)));
        return villager;
    }

    /**
     * Records the position of the handyman when a brazier changes: the absolute position of the feet of the handyman
     * at the last change of each brazier.
     */
    private static void recordChanges(GameTestHelper helper, List<BlockPos> braziers, Villager handyman,
            Map<BlockPos, BlockPos> changes) {
        Map<BlockPos, Boolean> last = new HashMap<>();
        braziers.forEach(brazier -> last.put(brazier, isLit(helper, brazier)));
        helper.onEachTick(() -> {
            for (BlockPos brazier : braziers) {
                boolean lit = isLit(helper, brazier);
                if (lit != last.get(brazier)) {
                    last.put(brazier, lit);
                    changes.put(brazier, handyman.blockPosition());
                }
            }
        });
    }

    /**
     * Each brazier changed with the handyman next to it: on one of the 8 columns around the brazier. The test compares
     * absolute positions: in this version of the game, {@code GameTestHelper.relativePos} turns the position by 180
     * degrees.
     */
    private static void assertNextTo(GameTestHelper helper, List<BlockPos> braziers, Map<BlockPos, BlockPos> changes) {
        for (BlockPos brazier : braziers) {
            BlockPos pos = helper.absolutePos(brazier);
            BlockPos feet = changes.get(brazier);
            helper.assertTrue(feet != null && Math.abs(feet.getX() - pos.getX()) <= 1
                    && Math.abs(feet.getZ() - pos.getZ()) <= 1 && Math.abs(feet.getY() - pos.getY()) <= 2,
                    "The handyman must be next to the brazier " + brazier + " when it changes, not at "
                    + (feet == null ? null : feet.subtract(pos)) + " from it");
        }
    }

    private static boolean isLit(GameTestHelper helper, BlockPos brazier) {
        BlockState state = helper.getBlockState(brazier);
        return state.getBlock() instanceof BrazierBlock && state.getValue(BrazierBlock.LIT);
    }

    /** True if all the braziers burn, or if none burns. */
    private static boolean burn(GameTestHelper helper, List<BlockPos> braziers, boolean lit) {
        return braziers.stream().allMatch(brazier -> isLit(helper, brazier) == lit);
    }

    private static String states(GameTestHelper helper, List<BlockPos> braziers) {
        return braziers.stream().map(brazier -> brazier + (isLit(helper, brazier) ? " burns" : " out")).toList()
                .toString();
    }

    /** Removes the handyman, the braziers and the heart, and sets the day time to noon. */
    private static void cleanUp(GameTestHelper helper, long[] time, List<BlockPos> braziers,
            @Nullable Villager handyman) {
        if (handyman != null) {
            handyman.discard();
        }
        braziers.forEach(brazier -> helper.setBlock(brazier, Blocks.AIR));
        helper.setBlock(HEART, Blocks.AIR);
        time[0] = TestSupport.NOON;
        helper.getLevel().setDayTime(TestSupport.NOON);
    }
}
