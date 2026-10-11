package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageSpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village heart. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private VillageGameTests() {
    }

    /** Places the heart, then waits until the heart is in the list of loaded hearts. */
    private static void placeHeartAndWait(GameTestHelper helper, Runnable then) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        BlockPos heartPos = helper.absolutePos(HEART);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        VillageHearts.findNearest(helper.getLevel(), heartPos, 1.0).isPresent(),
                        "The heart is not registered"))
                .thenExecute(then)
                .thenSucceed();
    }

    /** A guard that spawns near a heart joins the village of this heart. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "village_link")
    public static void guardJoinsNearestVillage(GameTestHelper helper) {
        BlockPos heartPos = helper.absolutePos(HEART);
        placeHeartAndWait(helper, () -> {
            GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
            boolean linked = heartPos.equals(guard.getVillagePos());
            guard.discard();
            helper.assertTrue(linked, "The guard must join the village of the heart at " + heartPos);
        });
    }

    /** Hostile monsters cannot spawn naturally near a heart. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "village_spawn")
    public static void heartBlocksMonsterSpawnNearby(GameTestHelper helper) {
        BlockPos heartPos = helper.absolutePos(HEART);
        placeHeartAndWait(helper, () -> {
            helper.assertTrue(VillageSpawnProtection.isProtected(helper.getLevel(),
                    heartPos.getX() + 10, heartPos.getY(), heartPos.getZ()), "A position near the heart must be protected");
            helper.assertTrue(!VillageSpawnProtection.isProtected(helper.getLevel(),
                    heartPos.getX() + 1000, heartPos.getY(), heartPos.getZ()), "A far position must not be protected");
        });
    }

    /** An attack on a guard puts the village in alert, and the other guards of the village target the attacker. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "village_alert")
    public static void attackOnGuardAlertsVillage(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        BlockPos heartPos = helper.absolutePos(HEART);
        GuardEntity attacked = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        GuardEntity other = TestSupport.spawnGuard(helper, new BlockPos(13, 1, 13));
        attacked.setVillagePos(heartPos);
        other.setVillagePos(heartPos);
        Pig attacker = helper.spawn(EntityType.PIG, new BlockPos(5, 1, 3));
        AttributeInstance pigHealth = attacker.getAttribute(Attributes.MAX_HEALTH);
        if (pigHealth != null) {
            pigHealth.setBaseValue(500.0);
        }
        attacker.setHealth(500.0F);

        helper.runAfterDelay(5, () -> attacked.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0F));

        TestSupport.succeedWhenAndRemove(helper, () -> {
            VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
            helper.assertTrue(heart.getAlertState() == AlertState.ALERT,
                    "The village must be in alert, not " + heart.getAlertState());
            helper.assertTrue(other.getTarget() == attacker, "The other guard must target the attacker");
        }, attacked, other, attacker);
    }

    /** When the heart is removed, its units have no village. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "village_destroy")
    public static void removedHeartUnlinksUnits(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        guard.setVillagePos(helper.absolutePos(HEART));

        helper.runAfterDelay(5, () -> helper.setBlock(HEART, Blocks.AIR));

        TestSupport.succeedWhenAndRemove(helper,
                () -> helper.assertTrue(guard.getVillagePos() == null, "The guard must have no village"), guard);
    }
}
