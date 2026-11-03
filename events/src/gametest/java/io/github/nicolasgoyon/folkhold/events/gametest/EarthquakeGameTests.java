package io.github.nicolasgoyon.folkhold.events.gametest;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.block.BlockClass;
import io.github.nicolasgoyon.folkhold.events.block.EventBlockTags;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquake;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeSettings;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
    /** Time for the last falling blocks to land, in ticks. */
    private static final int LANDING_TICKS = 60;

    private EarthquakeGameTests() {
    }

    /** Stops the earthquakes of the earlier tests and fills the bottom layer of the arena with stone. */
    private static void prepare(GameTestHelper helper, int sizeX, int sizeZ) {
        Earthquakes.stop(helper.getLevel());
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * Builds a hollow house of one block: walls of 5 × 5 blocks from y = 1 to y = 3, and a flat roof at y = 4.
     *
     * @return the relative positions of the blocks of the house
     */
    private static List<BlockPos> buildHouse(GameTestHelper helper, int minX, int minZ, Block block) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = minX; x < minX + 5; x++) {
            for (int z = minZ; z < minZ + 5; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = x == minX || x == minX + 4 || z == minZ || z == minZ + 4;
                    if (wall || y == 4) {
                        BlockPos pos = new BlockPos(x, y, z);
                        helper.setBlock(pos, block);
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    /** The number of positions that do not have their block any more. */
    private static int lost(GameTestHelper helper, List<BlockPos> blocks, Block block) {
        int lost = 0;
        for (BlockPos pos : blocks) {
            if (!helper.getBlockState(pos).is(block)) {
                lost++;
            }
        }
        return lost;
    }

    /** Removes the falling blocks and the items that the earthquake leaves in the test area. */
    private static void removeDebris(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, helper.getBounds().inflate(8.0),
                entity -> entity instanceof FallingBlockEntity || entity instanceof ItemEntity)) {
            entity.discard();
        }
    }

    private static Earthquake start(GameTestHelper helper, BlockPos relativeCenter, EarthquakeSettings settings) {
        return Earthquakes.start(helper.getLevel(), helper.absolutePos(relativeCenter), settings, SEED);
    }

    /** A wood house and a brick house at the same distance from the epicenter: the wood house loses more blocks. */
    @GameTest(template = "quake_arena", timeoutTicks = 600, batch = "earthquake_houses", skyAccess = true)
    public static void woodHouseLosesMoreBlocksThanBrickHouse(GameTestHelper helper) {
        prepare(helper, 40, 32);
        List<BlockPos> wood = buildHouse(helper, 7, 14, Blocks.OAK_PLANKS);
        List<BlockPos> brick = buildHouse(helper, 29, 14, Blocks.BRICKS);
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(15, 3.0, 0, 60, 32));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int woodLost = lost(helper, wood, Blocks.OAK_PLANKS);
                    int brickLost = lost(helper, brick, Blocks.BRICKS);
                    FolkholdEvents.LOGGER.info("Earthquake test: the wood house lost {} of {} blocks, the brick house "
                            + "lost {} of {} blocks", woodLost, wood.size(), brickLost, brick.size());
                    removeDebris(helper);
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
                    removeDebris(helper);
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

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    removeDebris(helper);
                    helper.assertTrue(earthquake.maxChangesInTick() <= batch, "At most " + batch
                            + " changed blocks in one tick, not " + earthquake.maxChangesInTick());
                    helper.assertTrue(earthquake.changedBlocks() >= 50, "The earthquake must change most of the "
                            + "100 floating planks, not " + earthquake.changedBlocks());
                    helper.assertTrue(earthquake.elapsedTicks() >= earthquake.changedBlocks() / batch,
                            "The changes must spread over several ticks");
                })
                .thenSucceed();
    }

    /** An earthquake opens cracks in the surface: some blocks of a flat stone floor go away. */
    @GameTest(template = "quake_arena", timeoutTicks = 400, batch = "earthquake_cracks", skyAccess = true)
    public static void cracksOpenInTheSurface(GameTestHelper helper) {
        prepare(helper, 40, 32);
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(12, 2.0, 0, 20, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int holes = 0;
                    for (int x = 0; x < 40; x++) {
                        for (int z = 0; z < 32; z++) {
                            if (!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.STONE)) {
                                holes++;
                            }
                        }
                    }
                    removeDebris(helper);
                    helper.assertTrue(holes > 0, "The cracks must remove blocks of the floor");
                    helper.assertTrue(holes < 40 * 32 / 4, "The cracks must be narrow lines, not " + holes
                            + " blocks");
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
                    removeDebris(helper);
                })
                .thenSucceed();
    }

    /** The animals near an earthquake run away from its epicenter during the announce. */
    @GameTest(template = "quake_small", timeoutTicks = 200, batch = "earthquake_flee")
    public static void animalsRunAway(GameTestHelper helper) {
        prepare(helper, 16, 16);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 1, 8));
        BlockPos center = helper.absolutePos(new BlockPos(6, 1, 8));
        Vec3 epicenter = Vec3.atBottomCenterOf(center);
        double start = cow.position().distanceTo(epicenter);
        Earthquakes.start(helper.getLevel(), center, new EarthquakeSettings(4, 0.1, 400, 20, 8), SEED);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    BlockPos target = cow.getNavigation().getTargetPos();
                    helper.assertTrue(target != null && Vec3.atBottomCenterOf(target).distanceTo(epicenter) > start,
                            "The cow must walk away from the epicenter");
                })
                .thenExecute(() -> {
                    Earthquakes.stop(helper.getLevel());
                    cow.discard();
                })
                .thenSucceed();
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
     * The cost of a strong earthquake on the server thread, in an area with 9 wood houses and 3 brick houses. The
     * shake is short, so the batch size limits the changes of most ticks. The test logs
     * the changed blocks, the time of the earthquake for each tick, the largest number of falling blocks at one time
     * and the mean tick time of the server.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 800, batch = "earthquake_cost", skyAccess = true)
    public static void costOfAnEarthquake(GameTestHelper helper) {
        prepare(helper, 40, 32);
        List<BlockPos> houses = new ArrayList<>();
        int[][] corners = {{4, 3}, {12, 3}, {22, 3}, {30, 3}, {4, 13}, {12, 13}, {24, 13}, {32, 13}, {4, 23},
            {12, 23}, {22, 23}, {30, 23}};
        for (int i = 0; i < corners.length; i++) {
            Block block = i % 4 == 3 ? Blocks.BRICKS : Blocks.OAK_PLANKS;
            houses.addAll(buildHouse(helper, corners[i][0], corners[i][1], block));
        }
        int batch = 16;
        Earthquake earthquake = start(helper, new BlockPos(20, 1, 16), new EarthquakeSettings(20, 3.0, 0, 60, batch));
        int[] peakFalling = {0};
        long[] serverNanos = {0L};
        int[] serverTicks = {0};
        helper.onEachTick(() -> {
            if (!earthquake.isDone()) {
                peakFalling[0] = Math.max(peakFalling[0], helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class, helper.getBounds().inflate(8.0)).size());
                serverNanos[0] += helper.getLevel().getServer().getAverageTickTimeNanos();
                serverTicks[0]++;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(earthquake.isDone(), "The earthquake must end"))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    int ticks = Math.max(1, earthquake.elapsedTicks());
                    FolkholdEvents.LOGGER.info(String.format(Locale.ROOT, "Earthquake cost: %d house blocks, %d "
                                    + "changed blocks in %d ticks, at most %d in one tick, earthquake %.3f ms mean and "
                                    + "%.3f ms max for each tick, at most %d falling blocks, server tick %.2f ms mean",
                            houses.size(), earthquake.changedBlocks(), ticks, earthquake.maxChangesInTick(),
                            earthquake.busyNanos() / 1e6 / ticks, earthquake.maxTickNanos() / 1e6, peakFalling[0],
                            serverNanos[0] / 1e6 / Math.max(1, serverTicks[0])));
                    removeDebris(helper);
                    helper.assertTrue(earthquake.maxChangesInTick() <= batch, "At most " + batch
                            + " changed blocks in one tick");
                    helper.assertTrue(earthquake.changedBlocks() > 0, "The earthquake must change blocks");
                })
                .thenSucceed();
    }
}
