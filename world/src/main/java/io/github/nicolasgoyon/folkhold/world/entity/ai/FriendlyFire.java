package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionHorseEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Cancels damage between units of the same village (for example an arrow of an archer that hits a guard). The horses of
 * the riders of the village count as units of the village. Units without a village do not hurt other units without a
 * village.
 *
 * <p>A unit also does not hurt a villager. A unit never targets a villager, thus all the damage from a unit to a
 * villager is an accident: for example an arrow that misses a raider and hits a villager behind it. During a raid, the
 * death of the last villagers near the center of the raid ends the raid in a loss: without this rule, the archers of
 * the village can make the village fall.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof FactionUnit attacker
                && (event.getEntity() instanceof Villager || isOfVillage(event.getEntity(), attacker.getVillagePos()))) {
            event.setCanceled(true);
        }
    }

    /** True if the entity is a unit or the horse of a rider of the village (or with no village, for null). */
    private static boolean isOfVillage(LivingEntity entity, @Nullable BlockPos villagePos) {
        if (entity instanceof FactionUnit unit) {
            return Objects.equals(unit.getVillagePos(), villagePos);
        }
        if (entity instanceof FactionHorseEntity horse) {
            return Objects.equals(horse.getVillagePos(), villagePos);
        }
        return false;
    }
}
