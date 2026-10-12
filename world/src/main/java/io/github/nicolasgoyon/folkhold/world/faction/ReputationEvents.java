package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.VillageCat;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Player actions that change the reputation:
 * <ul>
 *   <li>kill a faction unit: {@code reputation.killUnit} with its faction and village;</li>
 *   <li>hit a faction unit (not a kill): {@code reputation.hitUnit};</li>
 *   <li>kill a hostile monster near a village: {@code reputation.killMonsterNearVillage} with this village;</li>
 *   <li>kill a village cat: {@code reputation.killCat}, hit a village cat: {@code reputation.hitCat}.</li>
 * </ul>
 */
public final class ReputationEvents {

    private ReputationEvents() {
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof FactionUnit unit) {
            applyToUnit(player, unit, WorldConfig.REPUTATION_KILL_UNIT.get());
        } else if (victim instanceof VillageCat cat) {
            applyToCat(player, cat, WorldConfig.REPUTATION_KILL_CAT.get());
        } else if (victim instanceof Enemy && victim.level() instanceof ServerLevel level) {
            VillageHearts.findNearest(level, victim.blockPosition(), WorldConfig.VILLAGE_RADIUS.get())
                    .map(pos -> level.getBlockEntity(pos))
                    .filter(VillageHeartBlockEntity.class::isInstance)
                    .map(VillageHeartBlockEntity.class::cast)
                    .filter(heart -> heart.getFactionId() != null)
                    .ifPresent(heart -> Reputations.apply(player.server, player, heart.getFactionId(),
                            heart.getVillageId().toString(), WorldConfig.REPUTATION_KILL_MONSTER.get()));
        }
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity().isDeadOrDying()) {
            return;
        }
        if (event.getEntity() instanceof FactionUnit unit) {
            applyToUnit(player, unit, WorldConfig.REPUTATION_HIT_UNIT.get());
        } else if (event.getEntity() instanceof VillageCat cat) {
            applyToCat(player, cat, WorldConfig.REPUTATION_HIT_CAT.get());
        }
    }

    private static void applyToCat(ServerPlayer player, VillageCat cat, int delta) {
        if (cat.getVillagePos() != null && cat.level() instanceof ServerLevel level
                && level.getBlockEntity(cat.getVillagePos()) instanceof VillageHeartBlockEntity heart
                && heart.getFactionId() != null) {
            Reputations.apply(player.server, player, heart.getFactionId(), heart.getVillageId().toString(), delta);
        }
    }

    private static void applyToUnit(ServerPlayer player, FactionUnit unit, int delta) {
        String factionId = unit.getFactionId();
        if (factionId == null) {
            return;
        }
        Reputations.apply(player.server, player, factionId, villageId(unit), delta);
    }

    @Nullable
    private static String villageId(FactionUnit unit) {
        return unit.getVillageHeart().map(heart -> heart.getVillageId().toString()).orElse(null);
    }
}
