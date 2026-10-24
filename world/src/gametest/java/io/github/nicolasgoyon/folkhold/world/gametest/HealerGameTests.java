package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.HealerEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the healer of the garrison. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HealerGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private HealerGameTests() {
    }

    /** Places a heart at the level 1, after the removal of the units and the monsters of earlier tests. */
    private static VillageHeartBlockEntity placeHeart(GameTestHelper helper) {
        TestSupport.sweep(helper);
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        TestSupport.setLevel(helper, heart, 1);
        return heart;
    }

    /** Removes the heart: a later test near it does not see a village. */
    private static void removeHeart(GameTestHelper helper) {
        helper.setBlock(HEART, Blocks.AIR);
    }

    /** Succeeds when the condition is true, then removes the units and the heart. */
    private static void succeedWhenAndClean(GameTestHelper helper, Runnable condition, Entity... units) {
        helper.startSequence()
                .thenWaitUntil(condition)
                .thenExecute(() -> {
                    for (Entity unit : units) {
                        unit.discard();
                    }
                    removeHeart(helper);
                })
                .thenSucceed();
    }

    private static <T extends FactionUnit> T villageUnit(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T unit = TestSupport.spawnUnit(helper, type, pos);
        unit.setVillagePos(helper.absolutePos(HEART));
        return unit;
    }

    /** The healer walks to a wounded guard of its village and gives it back the health of a bandage. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "healer_bandage")
    public static void healerBandagesAWoundedGuard(GameTestHelper helper) {
        placeHeart(helper);
        GuardEntity guard = villageUnit(helper, ModEntities.GUARD.get(), new BlockPos(3, 1, 3));
        HealerEntity healer = villageUnit(helper, ModEntities.HEALER.get(), new BlockPos(12, 1, 12));
        guard.setHealth(6.0F);
        int supply = healer.getSupply();
        float healed = (float) Math.min(guard.getMaxHealth(), 6.0 + WorldConfig.HEALER_HEAL_AMOUNT.get());

        succeedWhenAndClean(helper, () -> {
            helper.assertTrue(guard.getHealth() >= healed, "The guard must get the health of a bandage: "
                    + guard.getHealth());
            helper.assertTrue(healer.getSupply() == supply - 1, "The bandage must use the supply: "
                    + healer.getSupply());
        }, guard, healer);
    }

    /** A healer with no bandage does not heal. */
    @GameTest(template = "arena", timeoutTicks = 120, batch = "healer_no_supply")
    public static void healerWithNoBandageDoesNotHeal(GameTestHelper helper) {
        placeHeart(helper);
        GuardEntity guard = villageUnit(helper, ModEntities.GUARD.get(), new BlockPos(3, 1, 3));
        HealerEntity healer = villageUnit(helper, ModEntities.HEALER.get(), new BlockPos(5, 1, 3));
        guard.setHealth(6.0F);
        healer.setSupply(0);

        helper.runAfterDelay(100, () -> {
            float health = guard.getHealth();
            guard.discard();
            healer.discard();
            removeHeart(helper);
            helper.assertTrue(health == 6.0F, "A healer with no bandage must not heal: " + health);
            helper.succeed();
        });
    }

    /** The supply refills while the village is calm, and not during an alert. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "healer_refill")
    public static void supplyRefillsWhileTheVillageIsCalm(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        HealerEntity healer = villageUnit(helper, ModEntities.HEALER.get(), new BlockPos(5, 1, 3));
        long now = helper.getLevel().getGameTime();
        long delay = WorldConfig.HEALER_SUPPLY_REFILL_SECONDS.get() * 20L;
        healer.setSupply(0);
        healer.refillSupply(now);

        healer.refillSupply(now + delay);
        int calm = healer.getSupply();
        heart.setAlertState(AlertState.ALERT);
        healer.refillSupply(now + 3 * delay);
        int alert = healer.getSupply();
        healer.discard();
        removeHeart(helper);

        helper.assertTrue(calm == 1, "A calm village must give back one bandage: " + calm);
        helper.assertTrue(alert == 1, "A village in alert must not give back bandages: " + alert);
        helper.succeed();
    }

    /** During an alert, the healer gives regeneration and resistance to the units near it. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "healer_buff")
    public static void healerBuffsTheUnitsDuringAnAlert(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        GuardEntity guard = villageUnit(helper, ModEntities.GUARD.get(), new BlockPos(4, 1, 3));
        HealerEntity healer = villageUnit(helper, ModEntities.HEALER.get(), new BlockPos(5, 1, 3));
        heart.setAlertState(AlertState.ALERT);

        succeedWhenAndClean(helper, () -> helper.assertTrue(
                guard.hasEffect(MobEffects.REGENERATION) && guard.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                "The guard must get regeneration and resistance"), guard, healer);
    }

    /** A hamlet has no healer. A village (level 5) has one healer, with a potion of healing in its hand. */
    @GameTest(template = "arena", batch = "healer_garrison")
    public static void healersComeFromTheVillageTier(GameTestHelper helper) {
        VillageHeartBlockEntity heart = placeHeart(helper);
        TestSupport.setLevel(helper, heart, 1);
        long now = helper.getLevel().getGameTime();
        heart.ensureGarrison(now);
        long hamletHealers = heart.getUnits().stream().filter(unit -> unit.role() == GarrisonRole.HEALER).count();

        TestSupport.setLevel(helper, heart, 5);
        heart.ensureGarrison(now + 1);
        List<FactionUnit> healers = heart.getUnits().stream().filter(unit -> unit.role() == GarrisonRole.HEALER)
                .toList();
        boolean potion = healers.stream().allMatch(healer -> healer.getMainHandItem()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.HEALING));
        heart.getUnits().forEach(Entity::discard);
        removeHeart(helper);

        helper.assertTrue(hamletHealers == 0, "A hamlet must have no healer: " + hamletHealers);
        helper.assertTrue(healers.size() == 1, "A village must have one healer: " + healers.size());
        helper.assertTrue(potion, "A healer must hold a potion of healing");
        helper.succeed();
    }
}
