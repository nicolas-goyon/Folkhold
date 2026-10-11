package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Stops the natural spawn of hostile monsters near a loaded village heart.
 */
public final class VillageSpawnProtection {

    private VillageSpawnProtection() {
    }

    /**
     * Tells if a position is in the spawn protection of a loaded village heart.
     *
     * @param level the level
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     * @return true if hostile monsters must not spawn at this position
     */
    public static boolean isProtected(Level level, double x, double y, double z) {
        int radius = WorldConfig.SPAWN_PROTECTION_RADIUS.get();
        return radius > 0
                && VillageHearts.findNearest(level, BlockPos.containing(x, y, z), radius).isPresent();
    }

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        if (isProtected(event.getLevel().getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
