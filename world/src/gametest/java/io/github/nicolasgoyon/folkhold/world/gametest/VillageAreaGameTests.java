package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageSizes;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the area of a village (see {@link VillageHearts}): the area of a large village follows the area
 * of its structure. The tests remove the heart before its first tick: the heart does not clear the trees of the large
 * area.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageAreaGameTests {
    /** The size of the arena {@code arena_huge}, in blocks. */
    private static final int ARENA_SIZE = 56;
    /** The position of the heart in the arena. */
    private static final BlockPos HEART = new BlockPos(2, 1, 2);
    /** A position at the edge of the arena: farther from the heart than the village radius. */
    private static final BlockPos EDGE = new BlockPos(ARENA_SIZE - 4, 1, ARENA_SIZE - 4);
    /** The number of searches of the units for the measure of the cost. */
    private static final int SEARCHES = 200;

    private VillageAreaGameTests() {
    }

    /**
     * A unit at the edge of the core of a city belongs to the village: the unit is in the structure area of the
     * heart, farther than the village radius. With no structure area, the unit at the same position belongs to no
     * village. A position out of the structure area is in no village.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "village_area")
    public static void unitAtTheEdgeOfACityBelongsToTheVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = placeHeart(helper);
        BlockPos heartPos = heart.getBlockPos();
        BlockPos edge = helper.absolutePos(EDGE);
        int radius = cityRadius();
        BlockPos outsideCity = heartPos.offset(radius + 1, 0, 0);

        GuardEntity withoutStructure = TestSupport.spawnGuard(helper, EDGE);
        heart.setRepairReference(new VillageReference(List.of(), List.of(), Optional.of(cityArea(heartPos))));
        GuardEntity withStructure = TestSupport.spawnGuard(helper, EDGE);
        boolean listed = heart.getUnits().contains(withStructure);
        boolean outsideInVillage = VillageHearts.findAt(level, outsideCity).isPresent();
        BlockPos memberOf = withStructure.getVillagePos();
        BlockPos memberOfWithout = withoutStructure.getVillagePos();
        withoutStructure.discard();
        withStructure.discard();
        removeHeart(helper, heartPos);

        helper.assertTrue(Math.sqrt(heartPos.distSqr(edge)) > WorldConfig.VILLAGE_RADIUS.get(),
                "The edge must be farther than the village radius: " + edge);
        helper.assertTrue(memberOfWithout == null,
                "With no structure area, the unit at the edge must belong to no village: " + memberOfWithout);
        helper.assertTrue(heartPos.equals(memberOf),
                "The unit at the edge of the city must belong to the village " + heartPos + ": " + memberOf);
        helper.assertTrue(listed, "The heart must find the unit at the edge of the city");
        helper.assertFalse(outsideInVillage, "A position out of the city area must be in no village: " + outsideCity);
        helper.succeed();
    }

    /**
     * The cost of the search of the units in the area of a city and in the village radius: the test writes the mean
     * time of the two searches in the log (simulation profiler, {@link VillageHeartBlockEntity#UNITS_PROFILER_NAME}).
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "village_area_cost")
    public static void unitSearchCostInACityArea(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        BlockPos heartPos = heart.getBlockPos();
        long radiusNanos = measure(heart);
        heart.setRepairReference(new VillageReference(List.of(), List.of(), Optional.of(cityArea(heartPos))));
        GuardEntity guard = TestSupport.spawnGuard(helper, EDGE);
        long cityNanos = measure(heart);
        int units = heart.getUnits().size();
        guard.discard();
        removeHeart(helper, heartPos);

        FolkholdWorld.LOGGER.info("Village area cost: {} searches of the units, mean {} ns in the village radius, "
                + "{} ns in the area of a city", SEARCHES, radiusNanos, cityNanos);
        helper.assertTrue(units == 1, "The heart must find the unit in the area of the city: " + units);
        helper.succeed();
    }

    /** Places a heart in the arena and registers it. */
    private static VillageHeartBlockEntity placeHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper, ARENA_SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        BlockPos heartPos = helper.absolutePos(HEART);
        VillageHearts.register(helper.getLevel(), heartPos);
        return (VillageHeartBlockEntity) helper.getLevel().getBlockEntity(heartPos);
    }

    private static void removeHeart(GameTestHelper helper, BlockPos heartPos) {
        helper.setBlock(HEART, Blocks.AIR);
        VillageHearts.unregister(helper.getLevel(), heartPos);
    }

    /** The radius of the structure of a city, in blocks. */
    private static int cityRadius() {
        return VillageSizes.get().size("city").orElseThrow().maxRadius();
    }

    /** The structure area of a city around a heart. */
    private static BoundingBox cityArea(BlockPos heartPos) {
        int radius = cityRadius();
        return new BoundingBox(heartPos.getX() - radius, heartPos.getY() - 8, heartPos.getZ() - radius,
                heartPos.getX() + radius, heartPos.getY() + 16, heartPos.getZ() + radius);
    }

    /** The mean time of a search of the units, in nanoseconds, from the simulation profiler. */
    private static long measure(VillageHeartBlockEntity heart) {
        SimulationProfiler.Entry before = unitSearches();
        for (int i = 0; i < SEARCHES; i++) {
            heart.getUnits();
        }
        SimulationProfiler.Entry after = unitSearches();
        return (after.totalNanos() - before.totalNanos()) / Math.max(1, after.calls() - before.calls());
    }

    /** The measurements of the search of the units. */
    private static SimulationProfiler.Entry unitSearches() {
        return SimulationProfiler.entries().stream()
                .filter(entry -> entry.name().equals(VillageHeartBlockEntity.UNITS_PROFILER_NAME)).findFirst()
                .orElse(new SimulationProfiler.Entry(VillageHeartBlockEntity.UNITS_PROFILER_NAME, 0, 0, 0, 0));
    }
}
