package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Makes hostile monsters target guards, as they target iron golems.
 *
 * <p>Neutral monsters (for example the enderman) and the entity types in the tag
 * {@code folkhold_world:ignores_guards} do not target guards.
 */
public final class MonsterTargeting {

    /** Entity types that do not attack guards first. */
    public static final TagKey<EntityType<?>> IGNORES_GUARDS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "ignores_guards"));

    /** Priority of the new target goal. Zombies use 2 for players and 3 for villagers and iron golems. */
    private static final int TARGET_PRIORITY = 3;

    private MonsterTargeting() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof PathfinderMob mob) || !(mob instanceof Enemy)
                || mob instanceof NeutralMob || mob.getType().is(IGNORES_GUARDS)) {
            return;
        }
        if (!WorldConfig.SPEC.isLoaded() || !WorldConfig.MONSTERS_ATTACK_GUARDS.get()) {
            return;
        }
        mob.targetSelector.addGoal(TARGET_PRIORITY, new NearestAttackableTargetGoal<>(mob, GuardEntity.class, true));
    }
}
