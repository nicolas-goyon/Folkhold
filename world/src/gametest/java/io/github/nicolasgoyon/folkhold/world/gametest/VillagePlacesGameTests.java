package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the places of the inhabitants of a village: the beds of the homes, and the places of the hall
 * (see {@link VillageBeds}). See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillagePlacesGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final ResourceLocation BARRACKS =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_barracks");
    /** The tests call the checks of the heart this number of times to create all the villagers. */
    private static final int FILL_CHECKS = 30;

    private VillagePlacesGameTests() {
    }

    /**
     * A heart in the middle of the arena. The area of its village is the arena: the scan does not count the beds of
     * the other tests.
     */
    private static VillageHeartBlockEntity heart(GameTestHelper helper, List<VillageReference.Piece> pieces) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        BoundingBox arena = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(TestSupport.ARENA_SIZE - 1, 3, TestSupport.ARENA_SIZE - 1)));
        heart.setRepairReference(new VillageReference(pieces, List.of(), Optional.of(arena)));
        return heart;
    }

    private static Settlement settlement(GameTestHelper helper, VillageHeartBlockEntity heart) {
        return Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
    }

    /** Removes the entities that the checks of the heart created. */
    private static void clear(GameTestHelper helper, VillageHeartBlockEntity heart) {
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
    }

    /**
     * The heart counts the beds of the homes of its village. The beds in a garrison building of the village structure
     * give no place. The places are the beds and the places of the hall: they limit the inhabitants.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_places_count")
    public static void heartCountsTheBedsOfTheHomes(GameTestHelper helper) {
        // The barracks template is 9 x 10 x 9: from its origin in the north-west corner, its box covers the blocks
        // from 0 to 8 on the X axis and on the Z axis.
        VillageHeartBlockEntity heart = heart(helper, List.of(new VillageReference.Piece(BARRACKS,
                helper.absolutePos(new BlockPos(0, 1, 0)), Rotation.NONE)));
        Settlement settlement = settlement(helper, heart);
        settlement.economy().setPopulation(20);
        TestSupport.placeBed(helper, new BlockPos(1, 1, 2), Direction.SOUTH);
        TestSupport.placeBed(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
        TestSupport.placeBed(helper, new BlockPos(11, 1, 12), Direction.SOUTH);
        TestSupport.placeBed(helper, new BlockPos(12, 1, 12), Direction.SOUTH);
        TestSupport.placeBed(helper, new BlockPos(13, 1, 12), Direction.SOUTH);

        heart.ensureGarrison(helper.getLevel().getGameTime());

        int beds = settlement.beds();
        int places = VillageBeds.places(settlement);
        int inhabitants = VillageFolk.inhabitantCount(settlement);
        clear(helper, heart);
        int expectedPlaces = 3 + VillageBeds.staff();
        helper.assertTrue(beds == 3, "Expected 3 beds of homes, found " + beds);
        helper.assertTrue(places == expectedPlaces, "Expected " + expectedPlaces + " places, found " + places);
        helper.assertTrue(inhabitants == expectedPlaces,
                "The places must limit the inhabitants to " + expectedPlaces + ", not " + inhabitants);
        helper.succeed();
    }

    /** The heart creates no inhabitant for a village with no free place. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "village_places_fill")
    public static void heartCreatesAnInhabitantForEachPlace(GameTestHelper helper) {
        VillageHeartBlockEntity heart = heart(helper, List.of());
        Settlement settlement = settlement(helper, heart);
        settlement.economy().setPopulation(20);
        TestSupport.placeBeds(helper, 2);

        long now = helper.getLevel().getGameTime();
        for (int i = 0; i < FILL_CHECKS; i++) {
            heart.ensureGarrison(now + i);
        }

        int inhabitants = VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).size();
        clear(helper, heart);
        int expected = 2 + VillageBeds.staff();
        helper.assertTrue(inhabitants == expected, "Expected " + expected + " inhabitants, found " + inhabitants);
        helper.succeed();
    }

    /** A new bed gives a place after the next scan of the village: for example after a finished construction site. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_places_scan")
    public static void newBedGivesAPlaceAfterAScan(GameTestHelper helper) {
        VillageHeartBlockEntity heart = heart(helper, List.of());
        Settlement settlement = settlement(helper, heart);
        TestSupport.placeBeds(helper, 1);
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);
        int first = settlement.beds();

        TestSupport.placeBeds(helper, 2);
        heart.ensureGarrison(now + 1);
        int beforeScan = settlement.beds();
        heart.requestScan();
        heart.ensureGarrison(now + 2);
        int afterScan = settlement.beds();

        clear(helper, heart);
        helper.assertTrue(first == 1, "Expected 1 bed after the first scan, found " + first);
        helper.assertTrue(beforeScan == 1, "The count must not change before a new scan, found " + beforeScan);
        helper.assertTrue(afterScan == 2, "Expected 2 beds after the new scan, found " + afterScan);
        helper.succeed();
    }
}
