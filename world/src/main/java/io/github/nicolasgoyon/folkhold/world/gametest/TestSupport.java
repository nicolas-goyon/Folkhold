package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the GameTests of the world mod.
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
 * <p>Each test removes its entities when it passes. The next batch runs near the same place, and a unit
 * that stays alive can attack the monsters of a later test.
 */
final class TestSupport {
    static final int ARENA_SIZE = 16;

    private TestSupport() {
    }

    /** Fills the bottom layer of the arena (y = 0) with smooth stone. */
    static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    /** Spawns a unit as a spawn egg or a command does: equipment, configured values, home, village. */
    static <T extends FactionUnit> T spawnUnit(GameTestHelper helper, EntityType<T> type, BlockPos relativePos) {
        T unit = helper.spawn(type, relativePos);
        unit.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(unit.blockPosition()),
                MobSpawnType.COMMAND, null);
        return unit;
    }

    /** Spawns a guard. See {@link #spawnUnit(GameTestHelper, EntityType, BlockPos)}. */
    static GuardEntity spawnGuard(GameTestHelper helper, BlockPos relativePos) {
        return spawnUnit(helper, ModEntities.GUARD.get(), relativePos);
    }

    /** Waits for the condition, removes the entities of the test, then marks the test as passed. */
    static void succeedWhenAndRemove(GameTestHelper helper, Runnable condition, Entity... entities) {
        helper.startSequence()
                .thenWaitUntil(condition)
                .thenExecute(() -> {
                    for (Entity entity : entities) {
                        entity.discard();
                    }
                })
                .thenSucceed();
    }
}
