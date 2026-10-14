package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BuildingSignBlock;
import io.github.nicolasgoyon.folkhold.world.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ai.AnchorStrollGoal;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitNavigation;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the guard. See {@link TestSupport} for the templates and the helpers.
 *
 * <p>Tests with guards and monsters use separate batches. Tests in one batch run at the same time,
 * and a guard can see the monsters of a near test.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardGameTests {

    private GuardGameTests() {
    }

    /** A new guard has its equipment and the configured health. */
    @GameTest(template = "empty")
    public static void guardSpawnsWithEquipmentAndConfiguredHealth(GameTestHelper helper) {
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(0, 1, 0));

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
        TestSupport.buildFloor(helper);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        // A husk does not burn in daylight.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(10, 1, 10));

        TestSupport.succeedWhenAndRemove(helper,
                () -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"), guard);
    }

    /**
     * A guard without a village drops a target when the guard is farther than the chase radius from its home
     * position. The test removes the village of the guard: a heart of another test can be in the village radius.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "guard_chase")
    public static void guardDropsTargetOutsideChaseRadius(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        guard.setVillagePos(null);
        guard.setFactionId(null);
        guard.setHomePos(helper.absolutePos(new BlockPos(3, 1, 3)).offset(WorldConfig.CHASE_RADIUS.get() * 4, 0, 0));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 3));
        guard.setTarget(husk);

        TestSupport.succeedWhenAndRemove(helper, () -> {
            helper.assertTrue(guard.getTarget() == null, "The guard must drop the target");
            helper.assertTrue(husk.isAlive(), "The guard must not kill the target");
        }, guard, husk);
    }

    /** An attack on one guard alerts the other guards in the alert radius. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "guard_alert")
    public static void attackOnGuardAlertsOtherGuards(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        GuardEntity attacked = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        GuardEntity other = TestSupport.spawnGuard(helper, new BlockPos(12, 1, 12));
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

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(other.getTarget() == attacker,
                "The second guard must target the attacker of the first guard. First guard target: "
                        + attacked.getTarget() + ", last attacker: " + attacked.getLastHurtByMob()
                        + ", second guard target: " + other.getTarget()), attacked, other, attacker);
    }

    /** A hostile monster targets a guard before the guard attacks it. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "monster_targets_guard")
    public static void monsterTargetsGuardFirst(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        // The guard has no AI in this test. Then only the husk can start the fight,
        // and the guard cannot kill the husk before the check.
        guard.setNoAi(true);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(10, 1, 10));

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(husk.getTarget() == guard,
                "The husk must target the guard. Husk target: " + husk.getTarget()
                        + ", husk alive: " + husk.isAlive()), guard, husk);
    }

    /**
     * A guard goes through a closed fence gate in a wall: it opens the gate, passes, and closes the gate again.
     * The wall is 2 blocks high, so the gate is the only way. The test removes the village of the guard: a heart
     * of another test can be in the village radius and give the guard a night watch position.
     */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "guard_gate")
    public static void guardOpensAndClosesFenceGate(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos gate = new BlockPos(8, 1, 8);
        for (int x = 0; x < TestSupport.ARENA_SIZE; x++) {
            helper.setBlock(new BlockPos(x, 1, 8), Blocks.COBBLESTONE);
            helper.setBlock(new BlockPos(x, 2, 8), Blocks.COBBLESTONE);
        }
        helper.setBlock(gate, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.NORTH).setValue(FenceGateBlock.OPEN, false));
        helper.setBlock(gate.above(), Blocks.AIR);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(8, 1, 3));
        guard.setVillagePos(null);
        guard.setFactionId(null);
        guard.setNightPos(null);
        BlockPos goal = helper.absolutePos(new BlockPos(8, 1, 13));
        helper.startSequence()
                .thenExecute(() -> guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0))
                .thenWaitUntil(() -> {
                    if (guard.getTarget() == null && guard.getNavigation().isDone()
                            && guard.blockPosition().getZ() < goal.getZ() - 2) {
                        guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                    }
                    helper.assertTrue(guard.blockPosition().getZ() >= goal.getZ() - 2,
                            "The guard must go through the gate. Guard at " + guard.blockPosition()
                                    + ", target " + guard.getTarget());
                    helper.assertBlockProperty(gate, FenceGateBlock.OPEN, false);
                })
                .thenExecute(guard::discard)
                .thenSucceed();
    }

    /**
     * Two guards go one after the other through a closed wooden door in a wall. The first guard does not close the
     * door on the second guard. When both guards are on the other side, the door is closed again.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "guard_door")
    public static void guardsPassADoorAndCloseIt(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos door = new BlockPos(8, 1, 8);
        for (int x = 0; x < TestSupport.ARENA_SIZE; x++) {
            helper.setBlock(new BlockPos(x, 1, 8), Blocks.COBBLESTONE);
            helper.setBlock(new BlockPos(x, 2, 8), Blocks.COBBLESTONE);
        }
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, false);
        helper.setBlock(door, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        GuardEntity first = TestSupport.spawnGuard(helper, new BlockPos(8, 1, 4));
        GuardEntity second = TestSupport.spawnGuard(helper, new BlockPos(7, 1, 2));
        BlockPos goal = helper.absolutePos(new BlockPos(8, 1, 13));
        for (GuardEntity guard : new GuardEntity[] {first, second}) {
            guard.setVillagePos(null);
            guard.setFactionId(null);
            guard.setNightPos(null);
            // The home position is behind the door, so the anchor goals do not pull the guard back.
            guard.setHomePos(goal);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (GuardEntity guard : new GuardEntity[] {first, second}) {
                        if (guard.getNavigation().isDone() && guard.blockPosition().getZ() < goal.getZ() - 2) {
                            guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                        }
                        helper.assertTrue(guard.blockPosition().getZ() >= goal.getZ() - 3,
                                "Both guards must go through the door. Guard at " + guard.blockPosition());
                    }
                    helper.assertBlockProperty(door, DoorBlock.OPEN, false);
                })
                .thenExecute(first::discard)
                .thenExecute(second::discard)
                .thenSucceed();
    }

    /**
     * A guard with a home that it cannot reach (fenced in) waits between two path searches: at most one search for
     * each retry delay, not one search on each tick.
     */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "guard_unreachable_home")
    public static void unreachableHomeDoesNotSearchAPathOnEachTick(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos cell = new BlockPos(7, 1, 7);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(cell.offset(dx, 0, dz), Blocks.OAK_FENCE);
                }
            }
        }
        GuardEntity guard = TestSupport.spawnGuard(helper, cell);
        guard.setVillagePos(null);
        guard.setFactionId(null);
        guard.setNightPos(null);
        // The home is outside the patrol radius, so the guard tries to go back to it.
        guard.setHomePos(helper.absolutePos(new BlockPos(-WorldConfig.PATROL_RADIUS.get(), 1, -8)));
        guard.goalSelector.getAvailableGoals().removeIf(wrapped -> wrapped.getGoal() instanceof AnchorStrollGoal);
        UnitNavigation navigation = (UnitNavigation) guard.getNavigation();
        int ticks = 100;
        helper.runAfterDelay(ticks, () -> {
            long searches = navigation.searches();
            boolean inCell = guard.blockPosition().equals(helper.absolutePos(cell));
            guard.discard();
            helper.assertTrue(inCell, "The guard must stay in its cell, found at " + guard.blockPosition());
            helper.assertTrue(searches <= ticks / 20 + 2, "At most one path search for each retry delay, found "
                    + searches + " in " + ticks + " ticks");
            helper.assertTrue(searches >= 2, "The guard must try again, found " + searches + " searches");
            helper.succeed();
        });
    }

    /**
     * A building sign is not passable for the path finder. A guard goes through the gap of a line of signs, and does
     * not jump against a sign.
     */
    @GameTest(template = "arena", timeoutTicks = 500, batch = "guard_signs")
    public static void guardGoesAroundBuildingSigns(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockState sign = ModBlocks.buildingSign(BuildingSignKind.values()[0]).defaultBlockState()
                .setValue(BuildingSignBlock.FACING, Direction.NORTH);
        for (int x = 0; x < TestSupport.ARENA_SIZE; x++) {
            if (x != 13) {
                helper.setBlock(new BlockPos(x, 1, 8), sign);
            }
        }
        helper.assertTrue(!sign.isPathfindable(PathComputationType.LAND), "A building sign must not be passable");
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(4, 1, 4));
        guard.setVillagePos(null);
        guard.setFactionId(null);
        guard.setNightPos(null);
        BlockPos goal = helper.absolutePos(new BlockPos(4, 1, 13));
        // The home position is behind the signs too, so the anchor goals do not pull the guard back.
        guard.setHomePos(goal);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (guard.getNavigation().isDone() && guard.blockPosition().getZ() < goal.getZ() - 2) {
                        guard.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                    }
                    helper.assertTrue(guard.blockPosition().getZ() >= goal.getZ() - 2,
                            "The guard must go through the gap of the signs. Guard at " + guard.blockPosition());
                })
                .thenExecute(guard::discard)
                .thenSucceed();
    }
}
