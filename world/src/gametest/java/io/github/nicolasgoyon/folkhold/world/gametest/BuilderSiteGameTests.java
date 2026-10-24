package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BlockKind;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.core.sim.construction.WorkZone;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderMaterials;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the construction sites of the builders. The tests check the fences of the work zone, and the work
 * of the builders on the ground and on the scaffolding. They also check the pauses: a player in the zone, the night, a
 * stop.
 *
 * <p>Each test builds a small structure, captures it as a template, removes it, then the builders of a test village
 * build it again. The builders work 1 tick on each step.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuilderSiteGameTests {
    private static final BlockPos HEART = new BlockPos(2, 1, 2);
    private static final int ARENA = 32;
    private static final int HUGE = 56;
    private static final int BUILDERS = 2;
    private static final int COTTAGE_BUILDERS = 3;
    private static final ResourceLocation COTTAGE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_cottage");
    private static final String PLANKS = "minecraft:oak_planks";
    private static final String CALCITE = "minecraft:calcite";
    /** The number of blocks of the wall of the tests: 4 × 2. */
    private static final int WALL_BLOCKS = 8;
    private static final int CALCITE_BLOCKS = 4;
    /** The night time of the test of the night. */
    private static final long MIDNIGHT = 18000L;
    /** The test waits this number of ticks to see that a site does not progress. */
    private static final int PAUSE_TICKS = 60;
    /** The radius of the garrison of the heart during the test of a player in the zone: the minimum of the setting. */
    private static final int PLAYER_ACTIVATION_RADIUS = 16;
    /** The site of the test of the chunks that are not loaded is this far from the test, on the X axis, in blocks. */
    private static final int FAR = 4096;
    /** The limit of the steps for each tick after the load of the chunks, in the test of the chunks. */
    private static final int CATCH_UP_STEPS = 4;
    /** The work of the builders while the chunks are not loaded, in steps: more than the steps of the site. */
    private static final double PENDING_STEPS = 200.0;

    private BuilderSiteGameTests() {
    }

    /** A village heart, its builders near it, and a fast work of the builders. */
    private static List<Villager> village(GameTestHelper helper) {
        return village(helper, ARENA, BUILDERS);
    }

    /** As {@link #village(GameTestHelper)}, for an arena of a size and a number of builders. */
    private static List<Villager> village(GameTestHelper helper, int arena, int count) {
        TestSupport.buildFloor(helper, arena);
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(1);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        BlockPos heart = helper.absolutePos(HEART);
        // A new heart registers at its first tick: the test registers it now for the search of the village.
        VillageHearts.register(level, heart);
        List<Villager> builders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Villager villager = VillageFolk.spawn(level, heart, ModProfessions.BUILDER.get());
            helper.assertTrue(villager != null, "The builder must spawn");
            villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5 + i, 1, 5))));
            builders.add(villager);
        }
        return builders;
    }

    /**
     * Puts blocks in the level, keeps them as a template, then removes them. The template has the box of the blocks,
     * with its air.
     *
     * @return the id of the template
     */
    private static ResourceLocation template(GameTestHelper helper, String name, BlockPos from, BlockPos to,
            Block block) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, block);
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "test/" + name);
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        BlockPos min = helper.absolutePos(from);
        Vec3i size = to.subtract(from).offset(1, 1, 1);
        template.fillFromWorld(level, min, size, false, null);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, Blocks.AIR);
        }
        return id;
    }

    /** The end of a test: no site, no builders, no structure, the normal work time. */
    private static void cleanUp(GameTestHelper helper, List<Villager> builders, BlockPos from, BlockPos to) {
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        builders.forEach(Entity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.setBlock(HEART, Blocks.AIR);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(WorldConfig.CONSTRUCTION_BUILDER_TICKS.getDefault());
    }

    /** The site of the test, at an origin. A failed earlier test can leave a site in a chunk that is not loaded. */
    private static BuilderSite siteAt(GameTestHelper helper, BlockPos origin) {
        return BuilderSites.all(helper.getLevel()).stream().filter(site -> site.origin().equals(origin)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("No site at " + origin));
    }

    /** True if a site at an origin ended. */
    private static boolean ended(GameTestHelper helper, BlockPos origin) {
        return BuilderSites.all(helper.getLevel()).stream().noneMatch(site -> site.origin().equals(origin));
    }

    /**
     * The fences around a box and the scaffolding blocks in the work zone of the box. The fences of the structure, in
     * the box, do not count.
     */
    private static List<BlockPos> leftovers(ServerLevel level, BoundingBox box) {
        List<BlockPos> found = new ArrayList<>();
        int margin = WorldConfig.constructionReach().horizontal() + 1;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX() - margin, box.minY() - WorkZone.BELOW,
                box.minZ() - margin, box.maxX() + margin, box.maxY() + WorkZone.ABOVE, box.maxZ() + margin)) {
            boolean fence = level.getBlockState(pos).is(BuilderSite.FENCE) && !box.isInside(pos);
            if (fence || level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** The blocks of a box that are not a block. */
    private static List<BlockPos> missing(GameTestHelper helper, BlockPos from, BlockPos to, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!helper.getLevel().getBlockState(helper.absolutePos(pos)).is(block)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /**
     * The builders of the village build a wall that they reach from the ground. They put the fences of the work zone
     * first, and remove them at the end.
     */
    @GameTest(template = "arena_large", timeoutTicks = 2400, batch = "builder_site_wall", skyAccess = true)
    public static void buildersBuildAWallInsideTheirFences(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);
        BoundingBox box = BoundingBox.fromCorners(origin, helper.absolutePos(to));

        ConstructionPlacement.BuildersAnswer answer = ConstructionPlacement.startBuilders(level, id, origin,
                Rotation.NONE, Mirror.NONE, box, null);
        helper.assertTrue(answer.started(), "The builders of the village must start the site: " + answer.message());
        BuilderSite site = siteAt(helper, origin);
        helper.assertTrue(site.heart().equals(helper.absolutePos(HEART)), "The site must belong to the test village");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(site.phase() == BuilderSite.Phase.BUILD,
                        "The builders must put the fences first: " + site.phase() + " " + site.status() + " "
                                + site.appliedSteps() + "/" + site.totalSteps()))
                .thenExecute(() -> {
                    helper.assertTrue(site.fences().size() > 10, "The work zone must have its fences: "
                            + site.fences().size());
                    for (BlockPos fence : site.fences()) {
                        helper.assertTrue(level.getBlockState(fence).is(BuilderSite.FENCE), "No fence at " + fence);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin),
                        "The site must end: " + site.phase() + " " + site.status() + " " + site.appliedSteps() + "/"
                                + site.totalSteps()))
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.OAK_PLANKS);
                    List<BlockPos> leftovers = leftovers(level, box);
                    boolean alive = builders.stream().allMatch(Entity::isAlive);
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(missing.isEmpty(), "Blocks of the wall not in place: " + missing);
                    helper.assertTrue(leftovers.isEmpty(), "Fences or scaffolding after the end: " + leftovers);
                    helper.assertTrue(alive, "The builders must stay alive");
                })
                .thenSucceed();
    }

    /**
     * A player placed a block on a position of a wall: the builders build the wall around it, and the block of the
     * player stays.
     */
    @GameTest(template = "arena_large", timeoutTicks = 2400, batch = "builder_site_player_block", skyAccess = true)
    public static void buildersKeepTheBlockOfAPlayer(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_player_block", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);
        BoundingBox box = BoundingBox.fromCorners(origin, helper.absolutePos(to));
        BlockPos playerBlock = new BlockPos(15, 1, 16);
        helper.setBlock(playerBlock, Blocks.GLASS);
        VillageRepairData.get(level).addPlayerBlock(helper.absolutePos(HEART), helper.absolutePos(playerBlock));

        ConstructionPlacement.BuildersAnswer answer = ConstructionPlacement.startBuilders(level, id, origin,
                Rotation.NONE, Mirror.NONE, box, null);
        helper.assertTrue(answer.started(), "The builders of the village must start the site: " + answer.message());
        BuilderSite site = siteAt(helper, origin);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin),
                        "The site must end: " + site.phase() + " " + site.status() + " " + site.appliedSteps() + "/"
                                + site.totalSteps()))
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.OAK_PLANKS);
                    boolean kept = helper.getBlockState(playerBlock).is(Blocks.GLASS);
                    VillageRepairData.get(level).forget(helper.absolutePos(HEART));
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(kept, "The block of the player must stay");
                    helper.assertTrue(missing.equals(List.of(playerBlock)),
                            "Only the block of the player must not be a plank: " + missing);
                })
                .thenSucceed();
    }

    /**
     * A site goes on while its chunks are not loaded. The test starts a site far from the test, in a village with no
     * builders, then lets its chunks unload during the day. When the chunks load again, the site applies the steps of
     * the work of this time, at most {@link #CATCH_UP_STEPS} steps in each tick. The site ends with no builders.
     */
    @GameTest(template = "arena_large", timeoutTicks = 3000, batch = "builder_site_abstract", skyAccess = true)
    public static void sitesGoOnWhileTheirChunksAreNotLoaded(GameTestHelper helper) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(1);
        WorldConfig.CONSTRUCTION_CATCH_UP_STEPS.set(CATCH_UP_STEPS);
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "abstract_wall", from, to, Blocks.OAK_PLANKS);
        BlockPos center = helper.absolutePos(new BlockPos(ARENA / 2, 1, ARENA / 2)).offset(FAR, 0, 0);
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos middle = new ChunkPos(center);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                chunks.add(new ChunkPos(middle.x + dx, middle.z + dz));
            }
        }
        long[] time = {TestSupport.NOON};
        helper.onEachTick(() -> level.setDayTime(time[0]));
        BlockPos[] heart = new BlockPos[1];
        BuilderSite[] site = new BuilderSite[1];
        // The largest number of steps in one tick of the catch-up, and the phase and the steps of the last tick.
        int[] largest = {0};
        BuilderSite.Phase[] lastPhase = {null};
        int[] lastApplied = {0};
        helper.onEachTick(() -> {
            BuilderSite current = site[0];
            if (current == null) {
                return;
            }
            if (current.phase() == lastPhase[0] && current.status() == BuilderSite.Status.CATCHING_UP) {
                largest[0] = Math.max(largest[0], current.appliedSteps() - lastApplied[0]);
            }
            lastPhase[0] = current.phase();
            lastApplied[0] = current.appliedSteps();
        });

        helper.startSequence()
                .thenExecute(() -> chunks.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true)))
                .thenWaitUntil(() -> helper.assertTrue(ReadyChunks.isAreaReady(level, center.getX() - 24,
                        center.getZ() - 24, center.getX() + 24, center.getZ() + 24), "The far chunks must load"))
                .thenExecute(() -> {
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
                    heart[0] = new BlockPos(center.getX(), ground, center.getZ());
                    level.setBlock(heart[0], ModBlocks.VILLAGE_HEART.get().defaultBlockState(), Block.UPDATE_ALL);
                    VillageHearts.register(level, heart[0]);
                    site[0] = BuilderSites.start(level, id, heart[0].offset(5, 0, 0), Rotation.NONE, Mirror.NONE,
                            heart[0], null);
                    chunks.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                })
                .thenWaitUntil(() -> helper.assertTrue(site[0].status() == BuilderSite.Status.NOT_LOADED,
                        "The chunks of the site must unload: " + site[0].status()))
                .thenWaitUntil(() -> helper.assertTrue(site[0].pendingSteps() >= PENDING_STEPS,
                        "The site must count the work of the builders: " + site[0].pendingSteps()))
                .thenExecute(() -> chunks.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true)))
                .thenWaitUntil(() -> helper.assertTrue(BuilderSites.all(level).stream().noneMatch(found ->
                        found == site[0]), "The site must end with no builders: " + site[0].phase() + " "
                        + site[0].status() + " " + site[0].appliedSteps() + "/" + site[0].totalSteps()))
                .thenExecute(() -> {
                    BlockPos origin = heart[0].offset(5, 0, 0);
                    BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(to.subtract(from)));
                    List<BlockPos> missing = new ArrayList<>();
                    BlockPos.betweenClosedStream(box).filter(pos -> !level.getBlockState(pos).is(Blocks.OAK_PLANKS))
                            .forEach(pos -> missing.add(pos.immutable()));
                    List<BlockPos> leftovers = leftovers(level, box);
                    BlockPos.betweenClosedStream(box).forEach(pos -> level.setBlock(pos,
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                    level.setBlock(heart[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    chunks.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                    WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(WorldConfig.CONSTRUCTION_BUILDER_TICKS.getDefault());
                    WorldConfig.CONSTRUCTION_CATCH_UP_STEPS.set(
                            WorldConfig.CONSTRUCTION_CATCH_UP_STEPS.getDefault());
                    helper.assertTrue(missing.isEmpty(), "Blocks of the wall not in place: " + missing);
                    helper.assertTrue(leftovers.isEmpty(), "Fences or scaffolding after the end: " + leftovers);
                    helper.assertTrue(largest[0] > 0 && largest[0] <= CATCH_UP_STEPS,
                            "The site must apply " + CATCH_UP_STEPS + " steps at most in each tick: " + largest[0]);
                })
                .thenSucceed();
    }

    /**
     * The builders build a pillar that is higher than their reach: they put a scaffolding column, climb it, and
     * remove it at the end.
     */
    @GameTest(template = "arena_large", timeoutTicks = 3000, batch = "builder_site_pillar", skyAccess = true)
    public static void buildersClimbTheScaffolding(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(16, 1, 16);
        BlockPos to = new BlockPos(16, 6, 16);
        ResourceLocation id = template(helper, "builder_pillar", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);
        BoundingBox box = BoundingBox.fromCorners(origin, helper.absolutePos(to));
        List<Boolean> climbed = new ArrayList<>();

        BuilderSite site = BuilderSites.start(level, id, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (builders.stream().anyMatch(builder -> builder.getY() >= origin.getY() + 2.0)) {
                        climbed.add(true);
                    }
                    helper.assertTrue(ended(helper, origin), "The site must end: " + site.phase() + " "
                            + site.status() + " " + site.appliedSteps() + "/" + site.totalSteps());
                })
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.OAK_PLANKS);
                    List<BlockPos> leftovers = leftovers(level, box);
                    boolean alive = builders.stream().allMatch(Entity::isAlive);
                    boolean grounded = builders.stream().allMatch(builder -> builder.getY() < origin.getY() + 1.0);
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(missing.isEmpty(), "Blocks of the pillar not in place: " + missing);
                    helper.assertTrue(leftovers.isEmpty(), "Fences or scaffolding after the end: " + leftovers);
                    helper.assertTrue(!climbed.isEmpty(), "A builder must climb the scaffolding");
                    helper.assertTrue(alive && grounded, "The builders must be alive and on the ground");
                })
                .thenSucceed();
    }

    /**
     * The builders build the cottage of the villages: the walls, the roof, the door and the windows, with scaffolding
     * for the high blocks. The blocks of the level are the blocks of the template at the end.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 24000, batch = "builder_site_cottage", skyAccess = true)
    public static void buildersBuildTheCottage(GameTestHelper helper) {
        List<Villager> builders = village(helper, HUGE, COTTAGE_BUILDERS);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(COTTAGE).orElseThrow();
        BlockPos from = new BlockPos(24, 1, 24);
        BlockPos origin = helper.absolutePos(from);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BoundingBox box = template.getBoundingBox(settings, origin);
        Target expected = ConstructionTargets.fromTemplate(level, template, origin, settings);
        BlockPos to = from.offset(box.getXSpan() - 1, box.getYSpan() - 1, box.getZSpan() - 1);

        // The construction is longer than a day: the test stops the cycle of the day, else the builders stop at night.
        GameRules.BooleanValue daylight = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        boolean cycle = daylight.get();
        daylight.set(false, level.getServer());

        BuilderSite site = BuilderSites.start(level, COTTAGE, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin), "The site must end: " + site.phase()
                        + " " + site.status() + " " + site.appliedSteps() + "/" + site.totalSteps()))
                .thenExecute(() -> {
                    daylight.set(cycle, level.getServer());
                    List<String> differences = differences(expected, ConstructionTargets.fromLevel(level,
                            new BlockPos(box.minX(), box.minY(), box.minZ()),
                            new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
                    List<BlockPos> leftovers = leftovers(level, box);
                    boolean alive = builders.stream().allMatch(Entity::isAlive);
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(differences.isEmpty(), "Blocks different from the template: " + differences);
                    helper.assertTrue(leftovers.isEmpty(), "Fences or scaffolding after the end: " + leftovers);
                    helper.assertTrue(alive, "The builders must stay alive");
                })
                .thenSucceed();
    }

    /**
     * The positions of the target where the level has a different block. A connected block only needs the same
     * block: it takes its shape from its neighbors. The villagers open and close the doors on their way: the state
     * {@code open} does not count.
     */
    private static List<String> differences(Target expected, Target actual) {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<BuildPos, BuildBlock> entry : expected.blocks().entrySet()) {
            BuildBlock found = actual.at(entry.getKey());
            boolean same = entry.getValue().kind() == BlockKind.CONNECTED
                    ? blockName(found.state()).equals(blockName(entry.getValue().state()))
                    : found.sameBlock(entry.getValue())
                            || closed(found.state()).equals(closed(entry.getValue().state()));
            if (!same) {
                differences.add(entry.getKey() + ": " + found.state() + " instead of " + entry.getValue().state());
            }
        }
        return differences;
    }

    /** A state as text, with a closed door. */
    private static String closed(String state) {
        return state.replace("open=true", "open=false");
    }

    /** The block of a state as text, with no properties. */
    private static String blockName(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    /** The village with an economy of a test: its heart, its settlement with an empty stock, and its builders. */
    private record EconomyVillage(Settlement settlement, List<Villager> builders) {
        Stock stock() {
            return settlement.economy().stock();
        }
    }

    /** A village heart with its settlement and an empty stock, and the builders of the village. */
    private static EconomyVillage economyVillage(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        MinecraftServer server = helper.getLevel().getServer();
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        Settlements.startEconomy(server, settlement);
        TestSupport.emptyStock(settlement);
        return new EconomyVillage(settlement, builders);
    }

    /**
     * The material of a block: a common block costs items of the economy, through the recipes. A rare block (no recipe
     * from the items of the economy) and a valuable block come as they are from the stock.
     */
    @GameTest(template = "empty", batch = "builder_materials")
    public static void materialsOfTheBlocks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertFalse(BuilderMaterials.areFree(), "The builders of the tests use the materials of the stock");

        BuilderMaterials.Material planks = BuilderMaterials.of(server, Items.OAK_PLANKS);
        BuilderMaterials.Material stairs = BuilderMaterials.of(server, Items.OAK_STAIRS);
        BuilderMaterials.Material stripped = BuilderMaterials.of(server, Items.STRIPPED_SPRUCE_LOG);
        BuilderMaterials.Material calcite = BuilderMaterials.of(server, Items.CALCITE);
        BuilderMaterials.Material gold = BuilderMaterials.of(server, Items.GOLD_BLOCK);

        helper.assertTrue(planks.kind() == BuilderMaterials.Kind.COMMON
                && planks.cost().equals(Map.of("minecraft:oak_planks", 1.0)), "Planks cost planks: " + planks);
        helper.assertTrue(stairs.kind() == BuilderMaterials.Kind.COMMON
                && Math.abs(stairs.cost().getOrDefault("minecraft:oak_planks", 0.0) - 1.5) < 1.0E-9,
                "6 planks make 4 stairs: " + stairs);
        helper.assertTrue(stripped.kind() == BuilderMaterials.Kind.COMMON
                && stripped.cost().equals(Map.of("minecraft:spruce_log", 1.0)), "An axe strips a log: " + stripped);
        helper.assertTrue(calcite.kind() == BuilderMaterials.Kind.RARE, "Calcite is rare: " + calcite);
        helper.assertTrue(gold.kind() == BuilderMaterials.Kind.RARE && gold.price() >= 9.0,
                "A gold block is valuable: " + gold);
        helper.assertTrue(BuilderMaterials.of(server, Items.AIR).kind() == BuilderMaterials.Kind.FREE,
                "A block with no item is free");
        helper.succeed();
    }

    /** The builders take the materials of the blocks from the stock of the village. */
    @GameTest(template = "arena_large", timeoutTicks = 2400, batch = "builder_materials_stock", skyAccess = true)
    public static void buildersTakeTheMaterialsFromTheStock(GameTestHelper helper) {
        EconomyVillage village = economyVillage(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall_stock", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);
        village.stock().set(PLANKS, WALL_BLOCKS);

        BuilderSite site = BuilderSites.start(level, id, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin), "The site must end: " + site.phase()
                        + " " + site.status() + " " + site.appliedSteps() + "/" + site.totalSteps()))
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.OAK_PLANKS);
                    int left = village.stock().count(PLANKS);
                    cleanUp(helper, village.builders(), from, to);
                    helper.assertTrue(missing.isEmpty(), "Blocks of the wall not in place: " + missing);
                    helper.assertTrue(left == 0, "The builders must take the planks of the stock: " + left + " left");
                })
                .thenSucceed();
    }

    /**
     * With no materials in the stock, the builders wait: no block of the plan comes. With the materials, they build.
     */
    @GameTest(template = "arena_large", timeoutTicks = 2400, batch = "builder_materials_wait", skyAccess = true)
    public static void buildersWaitForTheMaterials(GameTestHelper helper) {
        EconomyVillage village = economyVillage(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall_wait", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);

        BuilderSite site = BuilderSites.start(level, id, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(site.phase() == BuilderSite.Phase.BUILD,
                        "The fences use no materials: " + site.phase()))
                .thenWaitUntil(() -> helper.assertTrue(site.status() == BuilderSite.Status.MATERIALS
                        && site.missingMaterials().contains(PLANKS),
                        "The site must wait for the planks: " + site.status() + " " + site.missingMaterials()))
                .thenIdle(PAUSE_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(site.appliedSteps() == 0, "No block with no materials: " + site.appliedSteps());
                    village.stock().set(PLANKS, WALL_BLOCKS);
                })
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin), "The site must end: " + site.phase()
                        + " " + site.status() + " " + site.appliedSteps() + "/" + site.totalSteps()))
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.OAK_PLANKS);
                    cleanUp(helper, village.builders(), from, to);
                    helper.assertTrue(missing.isEmpty(), "Blocks of the wall not in place: " + missing);
                })
                .thenSucceed();
    }

    /**
     * A rare block (calcite) gives a delivery quest of the village. The delivered blocks go into the stock, and the
     * builders take them there. At the end, the site withdraws its offers.
     */
    @GameTest(template = "arena_large", timeoutTicks = 2400, batch = "builder_materials_quest", skyAccess = true)
    public static void rareBlocksGiveAQuest(GameTestHelper helper) {
        EconomyVillage village = economyVillage(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 1, 16);
        ResourceLocation id = template(helper, "builder_calcite", from, to, Blocks.CALCITE);
        BlockPos origin = helper.absolutePos(from);
        Quests quests = QuestData.get(level.getServer()).quests();
        List<Quest> asked = new ArrayList<>();

        BuilderSite site = BuilderSites.start(level, id, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    asked.clear();
                    quests.offers(village.settlement().id()).stream()
                            .filter(quest -> quest.target().equals(CALCITE)).forEach(asked::add);
                    helper.assertTrue(asked.size() == 1, "The village must ask for the calcite: " + asked.size()
                            + " quests, site " + site.phase() + " " + site.status());
                })
                .thenExecute(() -> {
                    Quest quest = asked.get(0);
                    helper.assertTrue(quest.count() == CALCITE_BLOCKS && quest.kind() == QuestKind.DELIVERY,
                            "A delivery of " + CALCITE_BLOCKS + " calcite, not " + quest.count());
                    // A player delivers the calcite: it goes into the stock of the village.
                    village.stock().set(CALCITE, CALCITE_BLOCKS);
                })
                .thenWaitUntil(() -> helper.assertTrue(ended(helper, origin), "The site must end: " + site.phase()
                        + " " + site.status() + " " + site.appliedSteps() + "/" + site.totalSteps()))
                .thenExecute(() -> {
                    List<BlockPos> missing = missing(helper, from, to, Blocks.CALCITE);
                    boolean withdrawn = quests.byId(asked.get(0).id())
                            .filter(quest -> quest.state() == QuestState.OFFERED).isEmpty();
                    cleanUp(helper, village.builders(), from, to);
                    helper.assertTrue(missing.isEmpty(), "Blocks of calcite not in place: " + missing);
                    helper.assertTrue(withdrawn, "The site must withdraw its offer at its end");
                })
                .thenSucceed();
    }

    /**
     * A player in the work zone stops the builders. When the player goes out, the builders work again.
     */
    @GameTest(template = "arena_large", timeoutTicks = 600, batch = "builder_site_player", skyAccess = true)
    public static void playerInTheZoneStopsTheBuilders(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall_player", from, to, Blocks.OAK_PLANKS);
        // With a player near, the heart makes its garrison. The player is farther from the heart than this radius.
        int activation = WorldConfig.GARRISON_ACTIVATION_RADIUS.get();
        WorldConfig.GARRISON_ACTIVATION_RADIUS.set(PLAYER_ACTIVATION_RADIUS);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 1, 18))));

        BuilderSite site = BuilderSites.start(level, id, helper.absolutePos(from), Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenIdle(PAUSE_TICKS)
                .thenExecute(() -> {
                    BuilderSite.Status status = site.status();
                    int applied = site.appliedSteps();
                    level.getServer().getPlayerList().remove(player);
                    WorldConfig.GARRISON_ACTIVATION_RADIUS.set(activation);
                    helper.assertTrue(status == BuilderSite.Status.PLAYER,
                            "A player in the zone must stop the site: " + status);
                    helper.assertTrue(applied == 0 && site.fences().isEmpty(),
                            "No step while the player is in the zone: " + applied);
                })
                .thenWaitUntil(() -> helper.assertTrue(!site.fences().isEmpty(),
                        "The builders must work after the player goes out: " + site.status()))
                .thenExecute(() -> cleanUp(helper, builders, from, to))
                .thenSucceed();
    }

    /** At night, the builders do not work. */
    @GameTest(template = "arena_large", timeoutTicks = 400, batch = "builder_site_night", skyAccess = true)
    public static void buildersDoNotWorkAtNight(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall_night", from, to, Blocks.OAK_PLANKS);
        level.setDayTime(MIDNIGHT);

        BuilderSite site = BuilderSites.start(level, id, helper.absolutePos(from), Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenIdle(PAUSE_TICKS)
                .thenExecute(() -> {
                    BuilderSite.Status status = site.status();
                    int applied = site.appliedSteps();
                    int workers = site.builderCount();
                    level.setDayTime(TestSupport.NOON);
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(status == BuilderSite.Status.NIGHT, "The site must wait at night: " + status);
                    helper.assertTrue(applied == 0 && workers == 0, "No builder works at night: " + applied + " steps, "
                            + workers + " builders");
                })
                .thenSucceed();
    }

    /** The command {@code stop} ends a site: its fences go away. */
    @GameTest(template = "arena_large", timeoutTicks = 600, batch = "builder_site_stop", skyAccess = true)
    public static void stopRemovesTheFences(GameTestHelper helper) {
        List<Villager> builders = village(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = new BlockPos(14, 1, 16);
        BlockPos to = new BlockPos(17, 2, 16);
        ResourceLocation id = template(helper, "builder_wall_stop", from, to, Blocks.OAK_PLANKS);
        BlockPos origin = helper.absolutePos(from);
        BoundingBox box = BoundingBox.fromCorners(origin, helper.absolutePos(to));

        BuilderSite site = BuilderSites.start(level, id, origin, Rotation.NONE, Mirror.NONE,
                helper.absolutePos(HEART), null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(site.fences().size() >= 4,
                        "The builders must put some fences: " + site.fences().size()))
                .thenExecute(() -> {
                    int stopped = BuilderSites.stop(level);
                    List<BlockPos> leftovers = leftovers(level, box);
                    boolean ended = ended(helper, origin);
                    cleanUp(helper, builders, from, to);
                    helper.assertTrue(stopped >= 1 && ended, "The command must stop the site");
                    helper.assertTrue(leftovers.isEmpty(), "Fences after the stop: " + leftovers);
                })
                .thenSucceed();
    }
}
