package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionHorseEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Cancels damage between units of the same village (for example an arrow of an archer that hits a guard). The horses of
 * the riders of the village count as units of the village. Units without a village do not hurt other units without a
 * village.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof FactionUnit attacker
                && isOfVillage(event.getEntity(), attacker.getVillagePos())) {
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
