package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.Garrison;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
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

    /** Places a heart. Its settlement is at the level 1, so its garrison has the units of the level 1. */
    private static VillageHeartBlockEntity placeHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        setLevel(helper, heart, 1);
        return heart;
    }

    private static void setLevel(GameTestHelper helper, VillageHeartBlockEntity heart, int level) {
        MinecraftServer server = helper.getLevel().getServer();
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        SettlementLevels.setProgress(server, settlement, level, 0);
    }

    private static int fullGarrisonSize(VillageHeartBlockEntity heart) {
        int size = 0;
        for (GarrisonRole role : GarrisonRole.values()) {
            size += role.count(heart.garrisonTraits());
        }
        return size;
    }

    /** The entries of a role in the garrison of a heart. */
    private static List<Garrison.Entry> entries(VillageHeartBlockEntity heart, GarrisonRole role) {
        return heart.getGarrison().entries().stream().filter(entry -> entry.role() == role).toList();
    }

    /** The loaded units of a role that are alive. */
    private static List<FactionUnit> units(VillageHeartBlockEntity heart, GarrisonRole role) {
        return heart.getUnits().stream().filter(unit -> unit.role() == role && unit.isAlive()).toList();
    }

    private static void removeUnits(VillageHeartBlockEntity heart) {
        heart.getUnits().forEach(Entity::discard);
    }

    /** A new heart creates its full garrison. A second call creates no duplicates. */
    @GameTest(template = "arena", batch = "garrison_full")
    public static void heartCreatesFullGarrisonOnce(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        int expected = fullGarrisonSize(heart);
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
        int expected = fullGarrisonSize(heart);
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
        int expected = fullGarrisonSize(heart);
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

    /**
     * The level of the village sets the number of units of each role: a town (level 9) has 4 guards, 3 archers and
     * 1 captain.
     */
    @GameTest(template = "arena", batch = "garrison_level")
    public static void garrisonFollowsTheLevel(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        setLevel(helper, heart, 9);

        heart.ensureGarrison(helper.getLevel().getGameTime());
        int guards = units(heart, GarrisonRole.GUARD).size();
        int archers = units(heart, GarrisonRole.ARCHER).size();
        int captains = units(heart, GarrisonRole.CAPTAIN).size();
        removeUnits(heart);

        helper.assertTrue(guards == 4 && archers == 3 && captains == 1,
                "A town must have 4 guards, 3 archers and 1 captain: " + guards + ", " + archers + ", " + captains);
        helper.succeed();
    }

    /** When the level changes, the units that are alive get the equipment set of the new level. */
    @GameTest(template = "arena", batch = "garrison_equipment")
    public static void equipmentFollowsTheLevel(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);
        FactionUnit guard = units(heart, GarrisonRole.GUARD).get(0);
        boolean ironSword = guard.getMainHandItem().is(Items.IRON_SWORD);

        setLevel(helper, heart, 17);
        heart.ensureGarrison(now + 1);
        boolean netheriteSword = guard.getMainHandItem().is(Items.NETHERITE_SWORD);
        boolean enchanted = guard.getItemBySlot(EquipmentSlot.HEAD).isEnchanted();
        String set = guard.getEquipmentSet();
        removeUnits(heart);

        helper.assertTrue(ironSword, "A guard of a hamlet must have an iron sword");
        helper.assertTrue(netheriteSword && enchanted, "A guard of a capital must have a netherite sword and an "
                + "enchanted helmet");
        helper.assertTrue("capital".equals(set), "The guard must have the set of the capital: " + set);
        helper.succeed();
    }

    /**
     * When the level goes down, the units that are alive stay. The garrison does not spawn new units for the dead
     * units above the number of the new level.
     */
    @GameTest(template = "arena", batch = "garrison_level_down")
    public static void levelDownKeepsTheUnitsThatAreAlive(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        setLevel(helper, heart, 17);
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);
        int capitalGuards = units(heart, GarrisonRole.GUARD).size();

        setLevel(helper, heart, 1);
        heart.ensureGarrison(now + 1);
        int guardsAfterLevelDown = units(heart, GarrisonRole.GUARD).size();
        List<FactionUnit> guards = units(heart, GarrisonRole.GUARD);
        for (int i = 0; i < guards.size() - 1; i++) {
            guards.get(i).kill();
        }
        heart.ensureGarrison(now + 2);
        int entriesAfterDeaths = entries(heart, GarrisonRole.GUARD).size();
        long delay = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        heart.ensureGarrison(now + delay + 100);
        int guardsAfterRefill = units(heart, GarrisonRole.GUARD).size();
        removeUnits(heart);

        helper.assertTrue(capitalGuards == 6, "A capital must have 6 guards: " + capitalGuards);
        helper.assertTrue(guardsAfterLevelDown == 6, "The guards that are alive must stay: " + guardsAfterLevelDown);
        helper.assertTrue(entriesAfterDeaths == 2, "A hamlet keeps 2 guard entries: " + entriesAfterDeaths);
        helper.assertTrue(guardsAfterRefill == 2, "A hamlet must refill to 2 guards: " + guardsAfterRefill);
        helper.succeed();
    }
}
