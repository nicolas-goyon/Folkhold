package io.github.nicolasgoyon.folkhold.blocks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * A splash or a lingering water potion puts out the braziers, as the campfires. Vanilla puts out only the fire, the
 * candles and the blocks of the tag {@code minecraft:campfires}, and the brazier is not in this tag (see
 * {@link BrazierBlock}).
 */
public final class BrazierWater {
    private BrazierWater() {
    }

    /**
     * Puts out the braziers at the positions that a water potion puts out, as vanilla does for the fire: the
     * position in front of the face that the potion hits, the block of this face, and the four sides of the
     * position in front of the face.
     *
     * @param event the impact of a projectile
     */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        Level level = potion.level();
        PotionContents contents = potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (level.isClientSide() || !contents.is(Potions.WATER)) {
            return;
        }
        Direction face = hit.getDirection();
        BlockPos front = hit.getBlockPos().relative(face);
        putOut(level, front);
        putOut(level, front.relative(face.getOpposite()));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            putOut(level, front.relative(side));
        }
    }

    private static void putOut(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BrazierBlock && state.getValue(BrazierBlock.LIT)) {
            level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
            BrazierBlock.smoke(level, pos);
            level.setBlockAndUpdate(pos, state.setValue(BrazierBlock.LIT, false));
        }
    }
}
