package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the captain. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaptainGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private CaptainGameTests() {
    }

    private static VillageHeartBlockEntity placeHeart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        return helper.getBlockEntity(HEART);
    }

    private static <T extends FactionUnit> T villageUnit(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T unit = TestSupport.spawnUnit(helper, type, pos);
        unit.setVillagePos(helper.absolutePos(HEART));
        return unit;
    }

    /** The units of the village near the captain get Strength. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "captain_aura")
    public static void captainGivesStrengthToVillageUnits(GameTestHelper helper) {
        placeHeart(helper);
        CaptainEntity captain = villageUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        GuardEntity guard = villageUnit(helper, ModEntities.GUARD.get(), new BlockPos(6, 1, 4));

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(guard.hasEffect(MobEffects.DAMAGE_BOOST),
                "The guard must have Strength"), captain, guard);
    }

    /** When the captain has a target, the village goes to the assault state. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "captain_assault")
    public static void captainTargetPutsVillageInAssault(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        CaptainEntity captain = villageUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 1, 4));
        AttributeInstance pigHealth = pig.getAttribute(Attributes.MAX_HEALTH);
        if (pigHealth != null) {
            pigHealth.setBaseValue(500.0);
        }
        pig.setHealth(500.0F);
        captain.setTarget(pig);

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(heart.getAlertState() == AlertState.ASSAULT,
                "The village must be in assault, not " + heart.getAlertState()), captain, pig);
    }

    /** When the captain dies, some units of the village run away and all units get Weakness. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "captain_death")
    public static void captainDeathRoutsUnits(GameTestHelper helper) {
        placeHeart(helper);
        CaptainEntity captain = villageUnit(helper, ModEntities.CAPTAIN.get(), new BlockPos(4, 1, 4));
        List<GuardEntity> guards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            guards.add(villageUnit(helper, ModEntities.GUARD.get(), new BlockPos(2 + i * 3, 1, 12)));
        }

        helper.runAfterDelay(5, captain::kill);

        List<Entity> all = new ArrayList<>(guards);
        all.add(captain);
        TestSupport.succeedWhenAndRemove(helper, () -> {
            long routed = guards.stream().filter(FactionUnit::isRouted).count();
            helper.assertTrue(routed >= 1, "At least one guard must be routed");
            helper.assertTrue(routed < guards.size(), "Not all guards must be routed");
            helper.assertTrue(guards.stream().allMatch(guard -> guard.hasEffect(MobEffects.WEAKNESS)),
                    "All guards must have Weakness");
        }, all.toArray(new Entity[0]));
    }
}
