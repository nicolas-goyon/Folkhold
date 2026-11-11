package io.github.nicolasgoyon.folkhold.events.gametest;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.block.BlockClass;
import io.github.nicolasgoyon.folkhold.events.block.EventBlockTags;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquake;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeSettings;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests of the earthquakes.
 *
 * <p>Templates (made with {@code tools/make_test_structure.py}):
 * <ul>
 *   <li>{@code folkhold_events:quake_arena}: 40 × 12 × 32 blocks of air.</li>
 *   <li>{@code folkhold_events:quake_small}: 16 × 12 × 16 blocks of air.</li>
 * </ul>
 *
 * <p>Each test builds a stone floor at y = 0 and starts its earthquake with a fixed seed and no announce. Each test
 * has its own batch: the earthquake of a test does not reach the blocks of another test. The tests with damage have
 * access to the sky: no barrier blocks cover the test area, and the top block of a column is a block of the test.
 */
@GameTestHolder(FolkholdEvents.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EarthquakeGameTests {
    private static final long SEED = 130L;
    private static final int LANDING_TICKS = EventTestSupport.LANDING_TICKS;
    /**
     * The limit of each flee step of {@link #animalsRunAway}, in orders to run away. On a flat floor, approximately 2
     * orders of 3 give no target, so the probability that all the orders give no target is below 10^-7.
     */
    private static final int FLEE_ORDERS = 40;
    /** The cow of {@link #animalsRunAway} goes this distance farther from the epicenter, in blocks. */
    private static final int FLEE_DISTANCE = 3;
    /**
     * The tick limit of {@link #animalsRunAway}: the wait for the floor, the two flee steps of {@link #FLEE_ORDERS}
     * orders each, and a margin.
     */
    private static final int FLEE_TIMEOUT_TICKS = (2 * FLEE_ORDERS + 2) * 20 + 100;

    private EarthquakeGameTests() {
    }

    /**
     * Stops the earthquakes of the earlier tests, sets the default chance of the resistant blocks, and fills the bottom
     * layer of the arena with stone. A test that fails or times out does not change the next tests.
     */
    private static void prepare(GameTestHelper helper, int sizeX, int sizeZ) {
        Earthquakes.stop(helper.getLevel());
        EventTestSupport.setConfigForTest(helper, EventsConfig.EARTHQUAKE_RESISTANT_CHANCE,
                EventsConfig.EARTHQUAKE_RESISTANT_CHANCE.getDefault());
        EventTestSupport.stoneFloor(helper, sizeX, sizeZ);
    }

    private static Earthquake start(GameTestHelper helper, BlockPos relativeCenter, EarthquakeSettings settings) {
        return Earthquakes.start(helper.getLevel(), helper.absolutePos(relativeCenter), settings, SEED)
                .orElseThrow(() -> new IllegalStateException("The earthquake of the test must start"));
    }

    /**
     * Records the largest number of blocks that an earthquake changes in one tick: the difference of its count of
     * changed blocks between two ticks of the test.
     *
     * @return an array with the largest number at index 0
     */
    private static int[] trackChangesInTick(GameTestHelper helper, Earthquake earthquake) {
        int[] max = {0};
        int[] last = {0};
        helper.onEachTick(() -> {
            int changed = earthquake.changedBlocks();
            max[0] = Math.max(max[0], changed - last[0]);
            last[0] = changed;
        });
        return max;
    }

    /** A wood house and a brick house at the same distance from the epicenter: the wood house loses more blocks. */
    @GameTest(template = "quake_arena", timeoutTicks = 600, batch = "earthquake_houses", skyAccess = true)
    public static void woodHouseLosesMoreBlocksThanBrickHouse(GameTestHelper helper) {
        prepare(helper, 40, 32);
        Map<BlockPos, Block> wood = EventTestSupport.buildHouse(helper, 7, 14, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS);
        Map<BlockPos, Block> brick = EventTestSupport.buildHouse(helper, 29, 14, Blocks.BRICKS, Blocks.BRICKS);
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(15, 3.0, 0, 60, 32));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int woodLost = EventTestSupport.lost(helper, wood);
                    int brickLost = EventTestSupport.lost(helper, brick);
                    FolkholdEvents.LOGGER.info("Earthquake test: the wood house lost {} of {} blocks, the brick house "
                            + "lost {} of {} blocks", woodLost, wood.size(), brickLost, brick.size());
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(woodLost > brickLost, "The wood house must lose more blocks than the brick "
                            + "house: " + woodLost + " and " + brickLost);
                    helper.assertTrue(woodLost > 0, "The wood house must take damage");
                    helper.assertTrue(brickLost < brick.size() / 2, "The brick house must keep most of its blocks");
                })
                .thenSucceed();
    }

    /** The protected blocks stay in place, also above a free position in a strong earthquake. */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "earthquake_protected", skyAccess = true)
    public static void protectedBlocksStayInPlace(GameTestHelper helper) {
        prepare(helper, 16, 16);
        List<BlockPos> protectedBlocks = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<Block> candidates = new ArrayList<>(List.of(Blocks.BEDROCK, Blocks.REINFORCED_DEEPSLATE,
                Blocks.END_PORTAL_FRAME, Blocks.SPAWNER));
        // The village heart of the blocks mod, when it is loaded. The events mod does not depend on it.
        BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("folkhold_blocks", "village_heart"))
                .ifPresent(candidates::add);
        int x = 4;
        for (Block block : candidates) {
            BlockPos pos = new BlockPos(x, 4, 6);
            helper.setBlock(pos, block);
            protectedBlocks.add(pos);
            states.add(helper.getBlockState(pos));
            x++;
        }
        BlockPos chestPos = new BlockPos(4, 4, 9);
        helper.setBlock(chestPos, Blocks.CHEST);
        if (helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND));
        }
        BlockPos control = new BlockPos(10, 4, 9);
        helper.setBlock(control, Blocks.OAK_PLANKS);
        Earthquake earthquake = start(helper, new BlockPos(7, 1, 7), new EarthquakeSettings(8, 10.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(!helper.getBlockState(control).is(Blocks.OAK_PLANKS),
                            "The floating planks must fall: the earthquake must reach the blocks");
                    for (int i = 0; i < protectedBlocks.size(); i++) {
                        BlockState state = helper.getBlockState(protectedBlocks.get(i));
                        helper.assertTrue(state.is(states.get(i).getBlock()),
                                "A protected block must stay in place: " + states.get(i));
                    }
                    helper.assertTrue(helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest
                            && chest.getItem(0).is(Items.DIAMOND), "The chest and its content must stay in place");
                })
                .thenSucceed();
    }

    /** An earthquake changes no more blocks in one tick than its batch size. */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "earthquake_batch", skyAccess = true)
    public static void batchLimitsTheChangedBlocksOfEachTick(GameTestHelper helper) {
        prepare(helper, 16, 16);
        for (int x = 3; x < 13; x++) {
            for (int z = 3; z < 13; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_PLANKS);
            }
        }
        int batch = 5;
        Earthquake earthquake = start(helper, new BlockPos(8, 1, 8), new EarthquakeSettings(12, 10.0, 0, 1, batch));
        int[] maxChanges = trackChangesInTick(helper, earthquake);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(maxChanges[0] <= batch, "At most " + batch
                            + " changed blocks in one tick, not " + maxChanges[0]);
                    helper.assertTrue(earthquake.changedBlocks() >= 50, "The earthquake must change most of the "
                            + "100 floating planks, not " + earthquake.changedBlocks());
                    helper.assertTrue(earthquake.elapsedTicks() >= earthquake.changedBlocks() / batch,
                            "The changes must spread over several ticks");
                })
                .thenSucceed();
    }

    /** Fills the bottom layer of the arena with grass blocks: natural ground where a crack can open. */
    private static void grassFloor(GameTestHelper helper) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 32; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
    }

    /** An earthquake opens cracks in the natural ground: some blocks of a flat grass floor go away. */
    @GameTest(template = "quake_arena", timeoutTicks = 400, batch = "earthquake_cracks", skyAccess = true)
    public static void cracksOpenInTheNaturalGround(GameTestHelper helper) {
        prepare(helper, 40, 32);
        grassFloor(helper);
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(15, 4.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int holes = countOtherBlocks(helper, Blocks.GRASS_BLOCK);
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(holes > 0, "The cracks must remove blocks of the grass floor");
                    helper.assertTrue(holes < 40 * 32 / 4, "The cracks must be narrow lines, not " + holes
                            + " blocks");
                })
                .thenSucceed();
    }

    /**
     * A crack does not remove a block outside the tag {@code folkhold_events:crackable}. The floor is gold blocks,
     * with the same earthquake as {@link #cracksOpenInTheNaturalGround}: all the gold blocks stay.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 400, batch = "earthquake_no_cracks", skyAccess = true)
    public static void cracksDoNotRemoveOtherBlocks(GameTestHelper helper) {
        prepare(helper, 40, 32);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 32; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GOLD_BLOCK);
            }
        }
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(15, 4.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int holes = countOtherBlocks(helper, Blocks.GOLD_BLOCK);
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(holes == 0, "A crack must not remove a block outside the tag crackable: "
                            + holes + " gold blocks went away");
                })
                .thenSucceed();
    }

    /**
     * A crack does not cut a wall or a platform of stone: a wall of 3 blocks across the area through the epicenter and
     * a platform of 7 × 7 × 2 blocks on a grass floor stay complete. The test sets the chance of the resistant blocks to
     * 0, so only the cracks can remove the stone blocks.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 400, batch = "earthquake_crack_walls", skyAccess = true)
    public static void cracksDoNotCutWallsAndPlatforms(GameTestHelper helper) {
        prepare(helper, 40, 32);
        grassFloor(helper);
        EventTestSupport.setConfigForTest(helper, EventsConfig.EARTHQUAKE_RESISTANT_CHANCE, 0.0);
        List<BlockPos> stone = new ArrayList<>();
        for (int x = 2; x < 38; x++) {
            for (int y = 1; y <= 3; y++) {
                stone.add(new BlockPos(x, y, 16));
            }
        }
        for (int x = 15; x < 22; x++) {
            for (int z = 6; z < 13; z++) {
                for (int y = 1; y <= 2; y++) {
                    stone.add(new BlockPos(x, y, z));
                }
            }
        }
        stone.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 14), new EarthquakeSettings(15, 4.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    // Air in the floor: a crack. A grass block below the stone can become dirt.
                    int holes = 0;
                    for (int x = 0; x < 40; x++) {
                        for (int z = 0; z < 32; z++) {
                            if (helper.getBlockState(new BlockPos(x, 0, z)).isAir()) {
                                holes++;
                            }
                        }
                    }
                    long lost = stone.stream().filter(pos -> !helper.getBlockState(pos).is(Blocks.STONE)).count();
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(holes > 0, "The cracks must open in the grass floor");
                    helper.assertTrue(lost == 0, "A crack must not cut the wall or the platform: " + lost
                            + " stone blocks went away");
                })
                .thenSucceed();
    }

    /** The number of positions of the floor of the arena (40 × 32, y = 0) without the given block. */
    private static int countOtherBlocks(GameTestHelper helper, Block block) {
        int count = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 32; z++) {
                if (!helper.getBlockState(new BlockPos(x, 0, z)).is(block)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** A sign with text on a wall of planks keeps its text: the earthquake does not move the support of the sign. */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "earthquake_sign", skyAccess = true)
    public static void signOnAWallKeepsItsText(GameTestHelper helper) {
        prepare(helper, 16, 16);
        for (int x = 5; x <= 11; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.OAK_PLANKS);
            }
        }
        BlockPos support = new BlockPos(8, 3, 8);
        BlockPos signPos = support.north();
        helper.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, Direction.NORTH));
        if (helper.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("Folkhold")), true);
        }
        Earthquake earthquake = start(helper, new BlockPos(8, 1, 6), new EarthquakeSettings(8, 10.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int wallLost = 0;
                    for (int x = 5; x <= 11; x++) {
                        for (int y = 1; y <= 4; y++) {
                            if (!helper.getBlockState(new BlockPos(x, y, 8)).is(Blocks.OAK_PLANKS)) {
                                wallLost++;
                            }
                        }
                    }
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(wallLost > 0, "The earthquake must reach the wall");
                    helper.assertTrue(helper.getBlockState(support).is(Blocks.OAK_PLANKS),
                            "The support of the sign must stay");
                    helper.assertTrue(helper.getBlockEntity(signPos) instanceof SignBlockEntity sign
                            && sign.getFrontText().getMessage(0, false).getString().equals("Folkhold"),
                            "The sign must keep its text");
                })
                .thenSucceed();
    }

    /** A falling block of an earthquake hurts the animal below it. */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "earthquake_falling", skyAccess = true)
    public static void fallingBlocksHurtAnimals(GameTestHelper helper) {
        prepare(helper, 16, 16);
        // Bedrock below the sheep: a crack does not open below it.
        helper.setBlock(new BlockPos(8, 0, 8), Blocks.BEDROCK);
        // No AI: the sheep does not run away, and the block falls on it.
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(8, 1, 8));
        helper.setBlock(new BlockPos(8, 8, 8), Blocks.OAK_PLANKS);
        Earthquake earthquake = start(helper, new BlockPos(8, 1, 8), new EarthquakeSettings(6, 10.0, 0, 1, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenWaitUntil(() -> helper.assertTrue(!sheep.isAlive() || sheep.getHealth() < sheep.getMaxHealth(),
                        "The falling block must hurt the sheep"))
                .thenExecute(() -> {
                    sheep.discard();
                    EventTestSupport.removeDebris(helper);
                })
                .thenSucceed();
    }

    /**
     * The animals near an earthquake run away from its epicenter during the announce: the navigation target of a cow
     * goes away from the epicenter, and then the cow is farther from the epicenter.
     *
     * <p>An animal runs only to a target in an entity-ticking chunk. The GameTest server keeps the chunks of the test
     * area at this status, and not all the chunks around them. Thus the cow is at the center of the large template, on
     * a stone floor: each target, at 16 blocks or less from the cow, is in the test area.
     *
     * <p>The cow has no free will: it has no goals, but it keeps its AI, so its navigation follows the orders of the
     * earthquake. Only the earthquake can move it, and its random stroll cannot meet the checks.
     *
     * <p>The test waits for states, and not for a number of ticks:
     * <ol>
     *   <li>The cow stands on the floor. A ground navigation makes no path for a mob in the air.</li>
     *   <li>The cow has a navigation target farther from the epicenter than the cow at the start, then the cow is
     *       {@value #FLEE_DISTANCE} blocks farther from the epicenter than at the start. The earthquake gives an order
     *       to run away each 20 ticks. The target of an order comes from the random source of the cow, with no seed,
     *       and on a flat floor approximately 2 orders of 3 find no target. Thus the limit of each step is
     *       {@value #FLEE_ORDERS} orders. A step also stops when the earthquake ends before this limit.</li>
     * </ol>
     */
    @GameTest(template = "quake_arena", timeoutTicks = FLEE_TIMEOUT_TICKS, batch = "earthquake_flee")
    public static void animalsRunAway(GameTestHelper helper) {
        prepare(helper, 40, 32);
        BlockPos center = helper.absolutePos(new BlockPos(18, 1, 16));
        Vec3 epicenter = Vec3.atBottomCenterOf(center);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(20, 1, 16));
        Earthquake[] earthquake = new Earthquake[1];
        double[] startDistance = new double[1];
        boolean[] hasTarget = {false};
        // The number of orders before the flee target.
        int[] targetOrders = new int[1];
        EventTestSupport.atTestEnd(helper, () -> {
            if (earthquake[0] != null) {
                earthquake[0].stop();
            }
            cow.discard();
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cow.onGround(), "The cow must stand on the floor"))
                .thenExecute(() -> {
                    startDistance[0] = cow.position().distanceTo(epicenter);
                    // The announce is longer than the two flee steps: the earthquake does not shake during the test.
                    earthquake[0] = start(helper, new BlockPos(18, 1, 16),
                            new EarthquakeSettings(4, 0.1, (2 * FLEE_ORDERS + 1) * 20, 20, 8));
                })
                .thenWaitUntil(() -> {
                    BlockPos target = cow.getNavigation().getTargetPos();
                    if (target != null && Vec3.atBottomCenterOf(target).distanceTo(epicenter) > startDistance[0]) {
                        hasTarget[0] = true;
                        targetOrders[0] = fleeOrders(earthquake[0]);
                    }
                    helper.assertTrue(hasTarget[0] || fleeOrders(earthquake[0]) >= FLEE_ORDERS
                            || earthquake[0].isDone(), "Waiting for a target away from the epicenter");
                })
                .thenExecute(() -> helper.assertTrue(hasTarget[0], "The cow must walk away from the epicenter after "
                        + fleeOrders(earthquake[0]) + " orders: " + fleeState(cow, earthquake[0], epicenter,
                                startDistance[0])))
                .thenWaitUntil(() -> helper.assertTrue(isAway(cow, epicenter, startDistance[0])
                        || fleeOrders(earthquake[0]) >= 2 * FLEE_ORDERS || earthquake[0].isDone(),
                        "Waiting for the cow to run away"))
                .thenExecute(() -> helper.assertTrue(isAway(cow, epicenter, startDistance[0]), "The cow must run "
                        + FLEE_DISTANCE + " blocks away from the epicenter after " + fleeOrders(earthquake[0])
                        + " orders: " + fleeState(cow, earthquake[0], epicenter, startDistance[0])))
                .thenExecute(() -> FolkholdEvents.LOGGER.info("Earthquake flee test: the cow has a flee target after {} "
                        + "orders and is away after {} orders", targetOrders[0], fleeOrders(earthquake[0])))
                .thenSucceed();
    }

    /** True if the cow is {@link #FLEE_DISTANCE} blocks farther from the epicenter than at the start, or more. */
    private static boolean isAway(Cow cow, Vec3 epicenter, double startDistance) {
        return cow.position().distanceTo(epicenter) >= startDistance + FLEE_DISTANCE;
    }

    /** The number of orders to run away that an earthquake gave: one at the first tick, then one each 20 ticks. */
    private static int fleeOrders(Earthquake earthquake) {
        return (earthquake.elapsedTicks() + 19) / 20;
    }

    /** The state of the cow and of the earthquake for a failure message of {@link #animalsRunAway}. */
    private static String fleeState(Cow cow, Earthquake earthquake, Vec3 epicenter, double startDistance) {
        BlockPos target = cow.getNavigation().getTargetPos();
        String targetDistance = target == null ? "none"
                : String.format(Locale.ROOT, "%.2f", Vec3.atBottomCenterOf(target).distanceTo(epicenter));
        return String.format(Locale.ROOT, "distance at the start %.2f, distance now %.2f, target %s (distance %s), "
                + "on the ground %b, earthquake phase %s", startDistance, cow.position().distanceTo(epicenter),
                target, targetDistance, cow.onGround(), earthquake.phase());
    }

    /** The command of the operators starts an earthquake at a position and stops it. */
    @GameTest(template = "quake_small", timeoutTicks = 20, batch = "earthquake_command")
    public static void commandStartsAndStopsAnEarthquake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Earthquakes.stop(level);
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                "folkhold earthquake start %d %d %d 8 0.5", pos.getX(), pos.getY(), pos.getZ()));
        List<Earthquake> started = Earthquakes.active(level);
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                "folkhold earthquake start %d %d %d 8 0.5", pos.getX() + 10, pos.getY(), pos.getZ()));
        List<Earthquake> overlap = Earthquakes.active(level);
        server.getCommands().performPrefixedCommand(source, "folkhold earthquake stop");
        List<Earthquake> afterStop = Earthquakes.active(level);
        CommandSourceStack player = server.createCommandSourceStack().withLevel(level).withPermission(0)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(player, String.format(Locale.ROOT,
                "folkhold earthquake start %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
        List<Earthquake> notOperator = Earthquakes.active(level);
        Earthquakes.stop(level);

        helper.assertTrue(started.size() == 1 && started.get(0).center().equals(pos)
                && started.get(0).settings().radius() == 8, "The command must start one earthquake at the position");
        helper.assertTrue(overlap.size() == 1, "An earthquake must not start in the area of an active earthquake");
        helper.assertTrue(afterStop.isEmpty(), "The command must stop the earthquake");
        helper.assertTrue(notOperator.isEmpty(), "A source that is not an operator must not start an earthquake");
        helper.succeed();
    }

    /** The class of a block comes from the tags, else from its blast resistance. */
    @GameTest(template = "quake_small", timeoutTicks = 20, batch = "earthquake_classes")
    public static void blockClassesComeFromTagsThenBlastResistance(GameTestHelper helper) {
        helper.assertTrue(BlockClass.of(Blocks.OAK_PLANKS.defaultBlockState()) == BlockClass.FRAGILE,
                "Planks must be fragile");
        helper.assertTrue(BlockClass.of(Blocks.DIRT.defaultBlockState()) == BlockClass.FRAGILE,
                "Dirt must be fragile");
        helper.assertTrue(BlockClass.of(Blocks.COBBLESTONE.defaultBlockState()) == BlockClass.STURDY,
                "Cobblestone must be sturdy");
        helper.assertTrue(BlockClass.of(Blocks.MUD_BRICKS.defaultBlockState()) == BlockClass.STURDY,
                "Mud bricks must be sturdy");
        helper.assertTrue(BlockClass.of(Blocks.STONE_BRICKS.defaultBlockState()) == BlockClass.RESISTANT,
                "Stone bricks must be resistant");
        helper.assertTrue(BlockClass.of(Blocks.WHITE_CONCRETE.defaultBlockState()) == BlockClass.RESISTANT,
                "Concrete must be resistant");
        helper.assertTrue(BlockClass.of(Blocks.NETHERRACK.defaultBlockState()) == BlockClass.FRAGILE,
                "Netherrack (no tag, blast resistance 0.4) must be fragile");
        helper.assertTrue(BlockClass.of(Blocks.IRON_BLOCK.defaultBlockState()) == BlockClass.RESISTANT,
                "An iron block (no tag, blast resistance 6) must be resistant");
        helper.assertTrue(Blocks.GLASS.defaultBlockState().is(EventBlockTags.SHATTERS), "Glass must shatter");
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(EventBlockTags.isProtected(helper.getLevel(), pos, Blocks.BEDROCK.defaultBlockState()),
                "Bedrock must be protected");
        helper.assertTrue(EventBlockTags.isProtected(helper.getLevel(), pos, Blocks.CHEST.defaultBlockState()),
                "A chest must be protected");
        helper.assertTrue(!EventBlockTags.isProtected(helper.getLevel(), pos, Blocks.STONE.defaultBlockState()),
                "Stone must not be protected");
        helper.succeed();
    }

    /**
     * The cost of a strong earthquake on the server, in an area with 9 wood houses and 3 brick houses. The shake is
     * short, so the batch size limits the changes of most ticks. The test measures the tick time of the server for 40
     * ticks before the earthquake, then during the earthquake and the landing of its blocks. It logs the two times,
     * the changed blocks and the largest number of falling blocks at one time.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 800, batch = "earthquake_cost", skyAccess = true)
    public static void costOfAnEarthquake(GameTestHelper helper) {
        prepare(helper, 40, 32);
        List<BlockPos> houses = new ArrayList<>();
        int[][] corners = {{4, 3}, {12, 3}, {22, 3}, {30, 3}, {4, 13}, {12, 13}, {24, 13}, {32, 13}, {4, 23},
            {12, 23}, {22, 23}, {30, 23}};
        for (int i = 0; i < corners.length; i++) {
            Block block = i % 4 == 3 ? Blocks.BRICKS : Blocks.OAK_PLANKS;
            houses.addAll(EventTestSupport.buildHouse(helper, corners[i][0], corners[i][1], block, block).keySet());
        }
        int batch = 16;
        MinecraftServer server = helper.getLevel().getServer();
        EventTestSupport.TickTimes before = new EventTestSupport.TickTimes();
        EventTestSupport.TickTimes during = new EventTestSupport.TickTimes();
        Earthquake[] earthquake = new Earthquake[1];
        int[] peakFalling = {0};
        helper.onEachTick(() -> {
            // The time of the last complete tick of the server.
            long nanos = EventTestSupport.lastTickNanos(server);
            if (earthquake[0] == null) {
                before.add(nanos);
            } else {
                during.add(nanos);
                peakFalling[0] = Math.max(peakFalling[0], helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class, helper.getBounds().inflate(8.0)).size());
            }
        });

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> earthquake[0] = start(helper, new BlockPos(20, 1, 16),
                        new EarthquakeSettings(20, 3.0, 0, 60, batch)))
                .thenWaitUntil(() -> helper.assertTrue(earthquake[0].isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    FolkholdEvents.LOGGER.info(String.format(Locale.ROOT, "Earthquake cost: %d house blocks, %d "
                                    + "changed blocks in %d ticks, at most %d falling blocks. Server tick before: "
                                    + "%.2f ms mean, %.2f ms max. During: %.2f ms mean, %.2f ms max",
                            houses.size(), earthquake[0].changedBlocks(), earthquake[0].elapsedTicks(),
                            peakFalling[0], before.meanMillis(), before.maxMillis(), during.meanMillis(),
                            during.maxMillis()));
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(earthquake[0].changedBlocks() > 0, "The earthquake must change blocks");
                })
                .thenSucceed();
    }
}
