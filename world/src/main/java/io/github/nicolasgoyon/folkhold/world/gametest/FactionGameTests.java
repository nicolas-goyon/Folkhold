package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the factions. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private FactionGameTests() {
    }

    /** The world has 5 to 7 factions with known cultures and start relations. */
    @GameTest(template = "empty", batch = "faction_data")
    public static void worldHasFactions(GameTestHelper helper) {
        FactionData data = FactionData.get(helper.getLevel().getServer());
        List<FactionSpec> factions = data.factions();
        helper.assertTrue(factions.size() >= 5 && factions.size() <= 7, "Expected 5 to 7 factions, found " + factions.size());
        helper.assertTrue(factions.stream().allMatch(f -> Cultures.byId(f.cultureId()).isPresent()),
                "Each faction must have a known culture");
        helper.assertTrue(!data.relations().asMap().isEmpty(), "The factions must have start relations");
        helper.succeed();
    }

    /** A village belongs to the faction of its territory, and its garrison has the same faction. */
    @GameTest(template = "arena", batch = "faction_village")
    public static void villageAndGarrisonGetTheTerritoryFaction(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        String expected = FactionData.get(helper.getLevel().getServer()).ownerAt(helper.absolutePos(HEART)).id();
        String actual = heart.getFactionId();
        List<FactionUnit> units = heart.getUnits();
        boolean unitsMatch = !units.isEmpty() && units.stream().allMatch(unit -> expected.equals(unit.getFactionId()));
        units.forEach(Entity::discard);

        helper.assertTrue(expected.equals(actual), "The village faction must be " + expected + ", not " + actual);
        helper.assertTrue(unitsMatch, "All units must have the faction of the village");
        helper.succeed();
    }
}
