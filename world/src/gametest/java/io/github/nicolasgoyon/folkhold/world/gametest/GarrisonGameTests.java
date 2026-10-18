package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the garrison of a village. See {@link TestSupport} for the templates and the helpers.
 *
 * <p>The tests call {@link VillageHeartBlockEntity#ensureGarrison(long)} directly. In the game, the heart
 * calls it when a player is near.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarrisonGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private GarrisonGameTests() {
    }

    private static VillageHeartBlockEntity placeHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        return helper.getBlockEntity(HEART);
    }

    private static int fullGarrisonSize() {
        int size = 0;
        for (GarrisonRole role : GarrisonRole.values()) {
            size += role.configuredCount();
        }
        return size;
    }

    private static void removeUnits(VillageHeartBlockEntity heart) {
        heart.getUnits().forEach(Entity::discard);
    }

    /** A new heart creates its full garrison. A second call creates no duplicates. */
    @GameTest(template = "arena", batch = "garrison_full")
    public static void heartCreatesFullGarrisonOnce(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        int expected = fullGarrisonSize();
        long now = helper.getLevel().getGameTime();

        heart.ensureGarrison(now);
        int afterFirst = heart.getUnits().size();
        heart.ensureGarrison(now + 1);
        int afterSecond = heart.getUnits().size();
        removeUnits(heart);

        helper.assertTrue(afterFirst == expected, "Expected " + expected + " units, found " + afterFirst);
        helper.assertTrue(afterSecond == expected, "A second call must not add units, found " + afterSecond);
        helper.succeed();
    }

    /** A dead unit is replaced after the refill delay, and not before. */
    @GameTest(template = "arena", batch = "garrison_refill")
    public static void deadUnitIsReplacedAfterDelay(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        int expected = fullGarrisonSize();
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);

        List<FactionUnit> units = heart.getUnits();
        helper.assertTrue(!units.isEmpty(), "The garrison must have units");
        units.get(0).kill();
        int aliveAfterDeath = heart.getGarrison().aliveCount();

        heart.ensureGarrison(now + 1);
        int aliveBeforeDelay = heart.getGarrison().aliveCount();

        long delay = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        heart.ensureGarrison(now + delay + 100);
        int aliveAfterDelay = heart.getGarrison().aliveCount();
        removeUnits(heart);

        helper.assertTrue(aliveAfterDeath == expected - 1, "The dead unit must be marked dead, alive: " + aliveAfterDeath);
        helper.assertTrue(aliveBeforeDelay == expected - 1, "No replacement before the delay, alive: " + aliveBeforeDelay);
        helper.assertTrue(aliveAfterDelay == expected, "Replacement after the delay, alive: " + aliveAfterDelay);
        helper.succeed();
    }

    /** A unit that unloads with its chunk is still alive: the heart does not create a duplicate. */
    @GameTest(template = "arena", batch = "garrison_unload")
    public static void unloadedUnitIsNotDuplicated(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        int expected = fullGarrisonSize();
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);

        heart.getUnits().get(0).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        heart.ensureGarrison(now + 1);
        int loaded = heart.getUnits().size();
        int alive = heart.getGarrison().aliveCount();
        removeUnits(heart);

        helper.assertTrue(loaded == expected - 1, "No new unit must spawn, loaded units: " + loaded);
        helper.assertTrue(alive == expected, "The unloaded unit is still alive in the garrison, alive: " + alive);
        helper.succeed();
    }
}
