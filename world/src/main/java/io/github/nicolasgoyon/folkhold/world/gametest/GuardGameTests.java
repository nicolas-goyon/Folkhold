package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the guard.
 *
 * <p>Templates (made with {@code tools/make_test_structure.py}):
 * <ul>
 *   <li>{@code folkhold_world:empty}: 1 air block.</li>
 *   <li>{@code folkhold_world:arena}: 16 × 4 × 16 blocks of air. Each test builds the floor at y = 0 with
 *       {@link #buildFloor(GameTestHelper)}.</li>
 * </ul>
 *
 * <p>The floor is not part of the template. The position of a template block and the position from
 * {@link GameTestHelper#absolutePos(BlockPos)} do not always agree, and an entity can spawn inside a floor
 * block. Blocks that a test places with {@link GameTestHelper#setBlock(BlockPos, net.minecraft.world.level.block.Block)}
 * use the same positions as the entities.
 *
 * <p>Tests with guards and monsters use separate batches. Tests in one batch run at the same time,
 * and a guard can see the monsters of a near test. Each test removes its entities when it passes:
 * the next batch runs near the same place, and a guard that stays alive can attack its monsters.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardGameTests {

    private GuardGameTests() {
    }

    /** Spawns a guard as a spawn egg or a command does: equipment, configured values, home position. */
    private static GuardEntity spawnGuard(GameTestHelper helper, BlockPos relativePos) {
        GuardEntity guard = helper.spawn(ModEntities.GUARD.get(), relativePos);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                MobSpawnType.COMMAND, null);
        return guard;
    }

    private static final int ARENA_SIZE = 16;

    /** Fills the bottom layer of the arena (y = 0) with smooth stone. */
    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    /** Waits for the condition, removes the entities of the test, then marks the test as passed. */
    private static void succeedWhenAndRemove(GameTestHelper helper, Runnable condition, Entity... entities) {
        helper.startSequence()
                .thenWaitUntil(condition)
                .thenExecute(() -> {
                    for (Entity entity : entities) {
                        entity.discard();
                    }
                })
                .thenSucceed();
    }

    /** A new guard has its equipment and the configured health. */
    @GameTest(template = "empty")
    public static void guardSpawnsWithEquipmentAndConfiguredHealth(GameTestHelper helper) {
        GuardEntity guard = spawnGuard(helper, new BlockPos(0, 1, 0));

        helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
                "The guard must hold an iron sword");
        helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                "The guard must hold a shield");

        double expectedHealth = WorldConfig.GUARD.health();
        helper.assertTrue(Math.abs(guard.getMaxHealth() - expectedHealth) < 0.001,
                "Max health must be " + expectedHealth + ", not " + guard.getMaxHealth());
        helper.assertTrue(Math.abs(guard.getHealth() - expectedHealth) < 0.001,
                "The guard must spawn with full health");
        guard.discard();
        helper.succeed();
    }

    /** A guard attacks and kills a hostile monster near its home position. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "guard_kill")
    public static void guardKillsHostileMonster(GameTestHelper helper) {
        buildFloor(helper);
        GuardEntity guard = spawnGuard(helper, new BlockPos(3, 1, 3));
        // A husk does not burn in daylight.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(10, 1, 10));

        succeedWhenAndRemove(helper, () -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"), guard);
    }

    /** A guard drops a target that is farther than the chase radius from its home position. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "guard_chase")
    public static void guardDropsTargetOutsideChaseRadius(GameTestHelper helper) {
        buildFloor(helper);
        GuardEntity guard = spawnGuard(helper, new BlockPos(3, 1, 3));
        guard.setHomePos(helper.absolutePos(new BlockPos(3, 1, 3)).offset(WorldConfig.CHASE_RADIUS.get() * 4, 0, 0));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 3));
        guard.setTarget(husk);

        succeedWhenAndRemove(helper, () -> {
            helper.assertTrue(guard.getTarget() == null, "The guard must drop the target");
            helper.assertTrue(husk.isAlive(), "The guard must not kill the target");
        }, guard, husk);
    }

    /** An attack on one guard alerts the other guards in the alert radius. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "guard_alert")
    public static void attackOnGuardAlertsOtherGuards(GameTestHelper helper) {
        buildFloor(helper);
        GuardEntity attacked = spawnGuard(helper, new BlockPos(3, 1, 3));
        GuardEntity other = spawnGuard(helper, new BlockPos(12, 1, 12));
        // A pig is not a monster. Only the alert can make the second guard target it.
        Pig attacker = helper.spawn(EntityType.PIG, new BlockPos(5, 1, 3));
        // The pig must stay alive during the test: a guard drops a dead target.
        // An invulnerable pig does not work: mobs cannot target an invulnerable entity.
        AttributeInstance pigHealth = attacker.getAttribute(Attributes.MAX_HEALTH);
        if (pigHealth != null) {
            pigHealth.setBaseValue(500.0);
        }
        attacker.setHealth(500.0F);

        // Wait some ticks before the attack. HurtByTargetGoal ignores an attack at tick 0 of the guard.
        helper.runAfterDelay(5, () -> attacked.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0F));

        succeedWhenAndRemove(helper, () -> helper.assertTrue(other.getTarget() == attacker,
                "The second guard must target the attacker of the first guard. First guard target: "
                        + attacked.getTarget() + ", last attacker: " + attacked.getLastHurtByMob()
                        + ", second guard target: " + other.getTarget()), attacked, other, attacker);
    }

    /** A hostile monster targets a guard before the guard attacks it. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "monster_targets_guard")
    public static void monsterTargetsGuardFirst(GameTestHelper helper) {
        buildFloor(helper);
        GuardEntity guard = spawnGuard(helper, new BlockPos(3, 1, 3));
        // The guard has no AI in this test. Then only the husk can start the fight,
        // and the guard cannot kill the husk before the check.
        guard.setNoAi(true);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(10, 1, 10));

        succeedWhenAndRemove(helper, () -> helper.assertTrue(husk.getTarget() == guard,
                "The husk must target the guard. Husk target: " + husk.getTarget()
                        + ", husk alive: " + husk.isAlive()), guard, husk);
    }
}
