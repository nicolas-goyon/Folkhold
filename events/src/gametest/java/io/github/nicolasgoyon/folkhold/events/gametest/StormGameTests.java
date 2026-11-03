package io.github.nicolasgoyon.folkhold.events.gametest;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import io.github.nicolasgoyon.folkhold.events.common.BlockChanges;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeSettings;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import io.github.nicolasgoyon.folkhold.events.storm.Storm;
import io.github.nicolasgoyon.folkhold.events.storm.StormMath;
import io.github.nicolasgoyon.folkhold.events.storm.StormSettings;
import io.github.nicolasgoyon.folkhold.events.storm.Storms;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests of the storms.
 *
 * <p>Templates: {@code folkhold_events:quake_arena} (40 × 12 × 32 blocks of air) and
 * {@code folkhold_events:quake_small} (16 × 12 × 16 blocks of air). Each test builds a stone floor at y = 0 and starts its storm with a fixed seed, no
 * announce, no lightning and no change to the weather, except the tests of the lightning and of the weather. The wind
 * blows to the +X side of the test: the GameTest framework can rotate a test, so the tests turn the wind and the
 * attached blocks with the rotation of the test. Each test has its own batch, and the tests with damage have access to
 * the sky.
 */
@GameTestHolder(FolkholdEvents.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StormGameTests {
    private static final long SEED = 129L;
    private StormGameTests() {
    }

    /**
     * Stops the storms of the earlier tests, sets the default values of the config that the tests change, and fills the
     * bottom layer of the area with stone. A test that fails or times out does not change the next tests.
     */
    private static void prepare(GameTestHelper helper, int sizeX, int sizeZ) {
        Storms.stop(helper.getLevel());
        EventsConfig.STORM_WIND_PUSH.set(EventsConfig.STORM_WIND_PUSH.getDefault());
        EventsConfig.MAX_ACTIVE_EVENTS.set(EventsConfig.MAX_ACTIVE_EVENTS.getDefault());
        EventTestSupport.stoneFloor(helper, sizeX, sizeZ);
    }

    /** The direction in the world of a horizontal direction of the test, with the rotation of the test. */
    private static Direction direction(GameTestHelper helper, Direction relative) {
        return helper.getTestRotation().rotate(relative);
    }

    /** The angle of a wind that blows to the +X side of the test (see {@link StormMath}). */
    private static double windAngle(GameTestHelper helper) {
        return switch (direction(helper, Direction.EAST)) {
            case SOUTH -> 90.0;
            case WEST -> 180.0;
            case NORTH -> 270.0;
            default -> 0.0;
        };
    }

    /** Settings with a wind to the +X side of the test, no announce, no lightning and no change to the weather. */
    private static StormSettings settings(GameTestHelper helper, int radius, double strength, int durationTicks,
            int blocksPerTick, int maxChanges) {
        return new StormSettings(radius, strength, windAngle(helper), 0, durationTicks, blocksPerTick, maxChanges, 0,
                false);
    }

    private static Storm start(GameTestHelper helper, BlockPos relativeCenter, StormSettings settings) {
        return Storms.start(helper.getLevel(), helper.absolutePos(relativeCenter), settings, SEED)
                .orElseThrow(() -> new IllegalStateException("The storm of the test must start"));
    }

    /**
     * A house of dirt and wood and a house of bricks side by side, in the same storm, at the same distance from the
     * center and with a wall to the wind: the house of dirt and wood loses more blocks, and the house of bricks loses
     * no block.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 600, batch = "storm_houses", skyAccess = true)
    public static void dirtAndWoodHouseLosesMoreBlocksThanBrickHouse(GameTestHelper helper) {
        prepare(helper, 40, 32);
        Map<BlockPos, Block> wood = EventTestSupport.buildHouse(helper, 14, 5, Blocks.DIRT, Blocks.OAK_PLANKS);
        Map<BlockPos, Block> brick = EventTestSupport.buildHouse(helper, 14, 22, Blocks.BRICKS, Blocks.BRICKS);
        Storm storm = start(helper, new BlockPos(20, 1, 16), settings(helper, 20, 10.0, 100, 8, 200));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenIdle(EventTestSupport.LANDING_TICKS)
                .thenExecute(() -> {
                    int woodLost = EventTestSupport.lost(helper, wood);
                    int brickLost = EventTestSupport.lost(helper, brick);
                    FolkholdEvents.LOGGER.info("Storm test: the dirt and wood house lost {} of {} blocks, the brick "
                            + "house lost {} of {} blocks, {} torn of {} selected blocks", woodLost, wood.size(),
                            brickLost, brick.size(), storm.changedBlocks(), storm.plannedBlocks());
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(woodLost > brickLost, "The dirt and wood house must lose more blocks than the "
                            + "brick house: " + woodLost + " and " + brickLost);
                    helper.assertTrue(woodLost > 0, "The dirt and wood house must take damage");
                    helper.assertTrue(brickLost == 0, "The brick house must keep all its blocks, not lose "
                            + brickLost);
                    helper.assertTrue(woodLost <= wood.size() / 2, "The wind tears up only the exposed blocks, not "
                            + woodLost + " blocks");
                })
                .thenSucceed();
    }

    /**
     * The wind does not move a protected block, a block with a block entity, an attached block, or the support of
     * these. A wall of planks faces the wind. A sign, a torch and a chest are on its downwind side, and an item frame
     * is on its upwind side. The supports are exposed to the wind. The other planks of the wall fly away.
     */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "storm_protected", skyAccess = true)
    public static void protectedBlocksAndTheirSupportsStay(GameTestHelper helper) {
        prepare(helper, 16, 16);
        List<BlockPos> wall = new ArrayList<>();
        for (int z = 3; z <= 12; z++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos pos = new BlockPos(6, y, z);
                helper.setBlock(pos, Blocks.OAK_PLANKS);
                wall.add(pos);
            }
        }
        BlockPos signSupport = new BlockPos(6, 2, 4);
        BlockPos signPos = signSupport.east();
        helper.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,
                direction(helper, Direction.EAST)));
        if (helper.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("Folkhold")), true);
        }
        BlockPos torchSupport = new BlockPos(6, 2, 7);
        BlockPos torchPos = torchSupport.east();
        helper.setBlock(torchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                direction(helper, Direction.EAST)));
        BlockPos frameSupport = new BlockPos(6, 2, 10);
        ServerLevel level = helper.getLevel();
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(frameSupport.west()),
                direction(helper, Direction.WEST));
        frame.setItem(new ItemStack(Items.DIAMOND), false);
        level.addFreshEntity(frame);
        BlockPos chestPos = new BlockPos(7, 1, 12);
        helper.setBlock(chestPos, Blocks.CHEST);
        if (helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.EMERALD));
        }
        BlockPos chestSupport = chestPos.west();
        Storm storm = start(helper, new BlockPos(8, 1, 8), settings(helper, 8, 20.0, 60, 16, 200));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenIdle(EventTestSupport.LANDING_TICKS)
                .thenExecute(() -> {
                    long wallLost = wall.stream().filter(pos -> !helper.getBlockState(pos).is(Blocks.OAK_PLANKS))
                            .count();
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(wallLost > 0, "The wind must reach the wall");
                    for (BlockPos support : List.of(signSupport, torchSupport, frameSupport, chestSupport)) {
                        helper.assertTrue(helper.getBlockState(support).is(Blocks.OAK_PLANKS),
                                "A support must stay: " + support.toShortString());
                    }
                    helper.assertTrue(helper.getBlockEntity(signPos) instanceof SignBlockEntity sign
                            && sign.getFrontText().getMessage(0, false).getString().equals("Folkhold"),
                            "The sign must keep its text");
                    helper.assertTrue(helper.getBlockState(torchPos).is(Blocks.WALL_TORCH), "The torch must stay");
                    helper.assertTrue(frame.isAlive() && frame.getItem().is(Items.DIAMOND),
                            "The item frame must stay with its item");
                    helper.assertTrue(helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest
                            && chest.getItem(0).is(Items.EMERALD), "The chest and its content must stay");
                    frame.discard();
                })
                .thenSucceed();
    }

    /**
     * The wind never deletes a block with no drop. A wall of 24 planks with a row of 8 glass blocks on it faces a
     * strong wind. After the storm, each plank is a block, a flying block or an item, and each glass block is a block
     * or an item.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 600, batch = "storm_drops", skyAccess = true)
    public static void tornBlocksLandOrDropTheirItem(GameTestHelper helper) {
        prepare(helper, 40, 32);
        for (int z = 12; z < 20; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.OAK_PLANKS);
            }
            helper.setBlock(new BlockPos(5, 4, z), Blocks.GLASS);
        }
        Storm storm = start(helper, new BlockPos(8, 1, 16), settings(helper, 12, 20.0, 60, 16, 200));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenIdle(EventTestSupport.LANDING_TICKS * 2)
                .thenExecute(() -> {
                    int planks = countBlocks(helper, Blocks.OAK_PLANKS) + countFlying(helper, Blocks.OAK_PLANKS)
                            + countItems(helper, Items.OAK_PLANKS);
                    int glass = countBlocks(helper, Blocks.GLASS) + countItems(helper, Items.GLASS);
                    int changed = storm.changedBlocks();
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(changed > 0, "The storm must tear up blocks");
                    helper.assertTrue(planks == 24, "Each plank must be a block or an item, not " + planks + " of 24");
                    helper.assertTrue(glass == 8, "Each glass block must be a block or an item, not " + glass
                            + " of 8");
                })
                .thenSucceed();
    }

    /** The number of blocks of a type in the test area. */
    private static int countBlocks(GameTestHelper helper, Block block) {
        int count = 0;
        AABB bounds = helper.getBounds();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            if (helper.getLevel().getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    private static int countFlying(GameTestHelper helper, Block block) {
        return helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, helper.getBounds().inflate(16.0),
                entity -> entity.getBlockState().is(block)).size();
    }

    private static int countItems(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16.0),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /**
     * A storm tears up {@link StormSettings#maxChanges()} blocks at most, and changes no more blocks in one tick than
     * its batch size. The area has 50 exposed planks.
     */
    @GameTest(template = "quake_small", timeoutTicks = 400, batch = "storm_batch", skyAccess = true)
    public static void capAndBatchLimitTheChanges(GameTestHelper helper) {
        prepare(helper, 16, 16);
        for (int x = 3; x <= 11; x += 2) {
            for (int z = 3; z < 13; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_PLANKS);
            }
        }
        int batch = 3;
        int cap = 20;
        Storm storm = start(helper, new BlockPos(8, 1, 8), settings(helper, 12, 20.0, 40, batch, cap));
        int[] maxChanges = {0};
        int[] last = {0};
        helper.onEachTick(() -> {
            int changed = storm.changedBlocks();
            maxChanges[0] = Math.max(maxChanges[0], changed - last[0]);
            last[0] = changed;
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenIdle(EventTestSupport.LANDING_TICKS)
                .thenExecute(() -> {
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(storm.changedBlocks() == cap, "The storm must tear up " + cap
                            + " of the 50 exposed planks, not " + storm.changedBlocks());
                    helper.assertTrue(maxChanges[0] <= batch, "At most " + batch + " changed blocks in one tick, not "
                            + maxChanges[0]);
                })
                .thenSucceed();
    }

    /**
     * The wind pushes an animal under the open sky to the east. An animal below a stone roof stays. The storm tears up
     * no block.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 300, batch = "storm_push", skyAccess = true)
    public static void windPushesTheAnimalsUnderTheOpenSky(GameTestHelper helper) {
        prepare(helper, 40, 32);
        for (int x = 22; x <= 30; x++) {
            for (int z = 19; z <= 25; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        // No free will: the sheep do not walk, and only the wind moves them.
        Sheep outside = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(4, 1, 8));
        Sheep sheltered = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(26, 1, 22));
        Vec3 outsideStart = outside.position();
        Vec3 shelteredStart = sheltered.position();
        Storm storm = start(helper, new BlockPos(20, 1, 16), settings(helper, 20, 2.0, 60, 8, 0));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenExecute(() -> {
                    Direction wind = direction(helper, Direction.EAST);
                    double outsideMove = outside.position().subtract(outsideStart)
                            .dot(Vec3.atLowerCornerOf(wind.getNormal()));
                    double shelteredMove = sheltered.position().subtract(shelteredStart).horizontalDistance();
                    outside.discard();
                    sheltered.discard();
                    helper.assertTrue(outsideMove > 2.0, "The wind must push the sheep along the wind, not "
                            + outsideMove + " blocks");
                    helper.assertTrue(shelteredMove < 0.5, "The sheep below the roof must stay, not move "
                            + shelteredMove + " blocks");
                })
                .thenSucceed();
    }

    /** A flying block hurts the animal that it hits. */
    @GameTest(template = "quake_small", timeoutTicks = 300, batch = "storm_flying", skyAccess = true)
    public static void flyingBlocksHurtAnimals(GameTestHelper helper) {
        prepare(helper, 16, 16);
        // No push: the wind does not move the sheep out of the path of the blocks.
        EventsConfig.STORM_WIND_PUSH.set(0.0);
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(6, y, 8), Blocks.OAK_PLANKS);
        }
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(7, 1, 8));
        Storm storm = start(helper, new BlockPos(8, 1, 8), settings(helper, 6, 20.0, 40, 8, 10));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenWaitUntil(() -> helper.assertTrue(!sheep.isAlive() || sheep.getHealth() < sheep.getMaxHealth(),
                        "A flying block must hurt the sheep"))
                .thenExecute(() -> {
                    EventsConfig.STORM_WIND_PUSH.set(EventsConfig.STORM_WIND_PUSH.getDefault());
                    sheep.discard();
                    EventTestSupport.removeDebris(helper);
                })
                .thenSucceed();
    }

    /** Lightning strikes in the area of a storm during the wind. */
    @GameTest(template = "quake_small", timeoutTicks = 300, batch = "storm_lightning", skyAccess = true)
    public static void lightningStrikesInTheArea(GameTestHelper helper) {
        prepare(helper, 16, 16);
        Storm storm = start(helper, new BlockPos(8, 1, 8),
                new StormSettings(6, 1.0, windAngle(helper), 0, 100, 4, 0, 10, false));
        List<LightningBolt> bolts = new ArrayList<>();
        helper.onEachTick(() -> bolts.addAll(helper.getLevel().getEntitiesOfClass(LightningBolt.class,
                helper.getBounds().inflate(2.0))));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenExecute(() -> {
                    clearFire(helper);
                    helper.assertTrue(storm.lightningStrikes() > 0, "Lightning must strike during the storm");
                    helper.assertTrue(!bolts.isEmpty(), "The lightning must strike in the area");
                })
                .thenSucceed();
    }

    /** Removes the fire that the lightning can light in the test area. */
    private static void clearFire(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < 12; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.FIRE)) {
                        helper.setBlock(pos, Blocks.AIR);
                    }
                }
            }
        }
    }

    /**
     * A storm with the weather of the config: rain during the announce, thunder during the wind, clear weather after
     * the storm.
     */
    @GameTest(template = "quake_small", timeoutTicks = 200, batch = "storm_weather")
    public static void stormSetsTheWeather(GameTestHelper helper) {
        prepare(helper, 16, 16);
        ServerLevel level = helper.getLevel();
        Storm storm = start(helper, new BlockPos(8, 1, 8), new StormSettings(6, 1.0, windAngle(helper), 20, 20, 4, 0, 0,
                true));
        boolean[] rainBeforeWind = {false};
        boolean[] thunderDuringWind = {false};
        helper.onEachTick(() -> {
            if (storm.phase() == Storm.Phase.ANNOUNCE && level.getLevelData().isRaining()
                    && !level.getLevelData().isThundering()) {
                rainBeforeWind[0] = true;
            }
            if (storm.phase() == Storm.Phase.WIND && level.getLevelData().isThundering()) {
                thunderDuringWind[0] = true;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.isDone(), "The storm must end"))
                .thenExecute(() -> {
                    boolean clear = !level.getLevelData().isRaining() && !level.getLevelData().isThundering();
                    helper.assertTrue(rainBeforeWind[0], "The rain must start during the announce");
                    helper.assertTrue(thunderDuringWind[0], "The thunder must start with the wind");
                    helper.assertTrue(clear, "The weather must be clear after the storm");
                })
                .thenSucceed();
    }

    /**
     * The command of the operators starts a storm at a position and stops it. A storm does not start in the area of an
     * active storm, or when the server has the largest number of active events.
     */
    @GameTest(template = "quake_small", timeoutTicks = 20, batch = "storm_command")
    public static void commandStartsAndStopsAStorm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Storms.stop(level);
        Earthquakes.stop(level);
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                "folkhold storm start %d %d %d 8 0.5", pos.getX(), pos.getY(), pos.getZ()));
        List<Storm> started = Storms.active(level);
        boolean weather = started.size() == 1 && started.get(0).settings().weather();
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                "folkhold storm start %d %d %d 8 0.5", pos.getX() + 10, pos.getY(), pos.getZ()));
        List<Storm> overlap = Storms.active(level);
        server.getCommands().performPrefixedCommand(source, "folkhold storm status");
        server.getCommands().performPrefixedCommand(source, "folkhold storm stop");
        List<Storm> afterStop = Storms.active(level);
        CommandSourceStack player = server.createCommandSourceStack().withLevel(level).withPermission(0)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(player, String.format(Locale.ROOT,
                "folkhold storm start %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
        List<Storm> notOperator = Storms.active(level);
        // The global cap: with a cap of 1 and an active earthquake, no storm starts.
        EventsConfig.MAX_ACTIVE_EVENTS.set(1);
        Earthquakes.start(level, pos, new EarthquakeSettings(4, 0.1, 400, 20, 8));
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                "folkhold storm start %d %d %d 8 0.5", pos.getX(), pos.getY(), pos.getZ()));
        List<Storm> overCap = Storms.active(level);
        Storms.stop(level);
        Earthquakes.stop(level);
        EventsConfig.MAX_ACTIVE_EVENTS.set(EventsConfig.MAX_ACTIVE_EVENTS.getDefault());

        helper.assertTrue(started.size() == 1 && started.get(0).center().equals(pos)
                && started.get(0).radius() == 8, "The command must start one storm at the position");
        helper.assertTrue(overlap.size() == 1, "A storm must not start in the area of an active storm");
        helper.assertTrue(afterStop.isEmpty(), "The command must stop the storm");
        helper.assertTrue(notOperator.isEmpty(), "A source that is not an operator must not start a storm");
        helper.assertTrue(overCap.isEmpty(), "A storm must not start above the cap of active events");
        helper.assertTrue(weather == EventsConfig.STORM_WEATHER.get(), "The command must use the weather of the "
                + "config");
        helper.succeed();
    }

    /**
     * The cost of a strong storm on the server, in an area with 9 wood houses and 3 brick houses and two animals. The
     * test measures the tick time of the server for 40 ticks before the storm, then during the storm and the landing
     * of its blocks. It logs the two times, the torn blocks and the largest number of flying blocks at one time.
     */
    @GameTest(template = "quake_arena", timeoutTicks = 800, batch = "storm_cost", skyAccess = true)
    public static void costOfAStorm(GameTestHelper helper) {
        prepare(helper, 40, 32);
        int houseBlocks = 0;
        int[][] corners = {{4, 3}, {12, 3}, {22, 3}, {30, 3}, {4, 13}, {12, 13}, {24, 13}, {32, 13}, {4, 23},
            {12, 23}, {22, 23}, {30, 23}};
        for (int i = 0; i < corners.length; i++) {
            Block block = i % 4 == 3 ? Blocks.BRICKS : Blocks.OAK_PLANKS;
            houseBlocks += EventTestSupport.buildHouse(helper, corners[i][0], corners[i][1], block, block).size();
        }
        Sheep first = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(2, 1, 10));
        Sheep second = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(2, 1, 20));
        MinecraftServer server = helper.getLevel().getServer();
        EventTestSupport.TickTimes before = new EventTestSupport.TickTimes();
        EventTestSupport.TickTimes during = new EventTestSupport.TickTimes();
        Storm[] storm = new Storm[1];
        int[] peakFlying = {0};
        helper.onEachTick(() -> {
            long nanos = EventTestSupport.lastTickNanos(server);
            if (storm[0] == null) {
                before.add(nanos);
            } else {
                during.add(nanos);
                peakFlying[0] = Math.max(peakFlying[0], helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class, helper.getBounds().inflate(16.0)).size());
            }
        });
        int total = houseBlocks;

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> storm[0] = start(helper, new BlockPos(20, 1, 16),
                        settings(helper, 24, 3.0, 200, 8, BlockChanges.MAX_CHANGES_PER_TICK)))
                .thenWaitUntil(() -> helper.assertTrue(storm[0].isDone(), "The storm must end"))
                .thenIdle(EventTestSupport.LANDING_TICKS)
                .thenExecute(() -> {
                    FolkholdEvents.LOGGER.info(String.format(Locale.ROOT, "Storm cost: %d house blocks, %d torn "
                                    + "blocks in %d ticks, at most %d flying blocks. Server tick before: %.2f ms mean, "
                                    + "%.2f ms max. During: %.2f ms mean, %.2f ms max",
                            total, storm[0].changedBlocks(), storm[0].elapsedTicks(), peakFlying[0],
                            before.meanMillis(), before.maxMillis(), during.meanMillis(), during.maxMillis()));
                    first.discard();
                    second.discard();
                    EventTestSupport.removeDebris(helper);
                    helper.assertTrue(storm[0].changedBlocks() > 0, "The storm must tear up blocks");
                })
                .thenSucceed();
    }
}
