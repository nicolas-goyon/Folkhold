package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.Objects;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Cancels damage between units of the same village (for example an arrow of an archer that hits a guard).
 * Units without a village do not hurt other units without a village.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof FactionUnit target
                && event.getSource().getEntity() instanceof FactionUnit attacker
                && Objects.equals(target.getVillagePos(), attacker.getVillagePos())) {
            event.setCanceled(true);
        }
    }
}
