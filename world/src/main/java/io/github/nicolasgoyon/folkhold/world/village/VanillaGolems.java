package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * The faction golems take the place of the vanilla iron golems in their villages.
 *
 * <p>The inhabitants of a faction village are vanilla villagers, and vanilla villagers call vanilla iron golems. In a
 * village with faction golems (see {@code golem} in the level table), the villagers do not call vanilla iron golems:
 * the call fails, and the villagers near it act as if they saw a golem. In a smaller village, the villagers call
 * vanilla iron golems as usual. The rule does not change the other vanilla iron golems: a golem that a player builds,
 * or a golem from a spawn egg or a command.
 */
public final class VanillaGolems {
    /** The villagers in this distance from a failed call act as if they saw a golem, in blocks. */
    private static final double VILLAGER_RADIUS = 16.0;

    private VanillaGolems() {
    }

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.MOB_SUMMONED || event.getEntity().getType() != EntityType.IRON_GOLEM) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (!hasFactionGolems(level, pos)) {
            return;
        }
        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(VILLAGER_RADIUS))) {
            GolemSensor.golemDetected(villager);
        }
    }

    /**
     * Tells if a position is in a village with faction golems: the level of the nearest loaded village heart has
     * golems in its garrison.
     *
     * @param level the level
     * @param pos   the position
     * @return true if the villagers at this position must not call a vanilla iron golem
     */
    public static boolean hasFactionGolems(Level level, BlockPos pos) {
        return VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get())
                .filter(heartPos -> ReadyChunks.isReady(level, heartPos))
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .map(heart -> GarrisonRole.GOLEM.count(heart.garrisonTraits()) > 0)
                .orElse(false);
    }
}
