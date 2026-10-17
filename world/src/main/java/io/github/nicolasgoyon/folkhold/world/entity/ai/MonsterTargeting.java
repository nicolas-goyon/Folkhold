package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
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
 * Makes hostile monsters target faction units (guards, archers, captains), as they target iron golems.
 *
 * <p>Neutral monsters (for example the enderman) and the entity types in the tag
 * {@code folkhold_world:ignores_guards} do not target faction units. A monster looks for a unit only near a
 * loaded village heart: the units live in the villages, and a scan for each monster of the world costs too much.
 */
public final class MonsterTargeting {

    /** Entity types that do not attack faction units first. */
    public static final TagKey<EntityType<?>> IGNORES_GUARDS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "ignores_guards"));

    /** Priority of the new target goal. Zombies use 2 for players and 3 for villagers and iron golems. */
    private static final int TARGET_PRIORITY = 3;
    /** A monster checks its distance to the nearest heart at this interval, in ticks. */
    private static final int GATE_INTERVAL = 20;

    /**
     * The target goal of a monster, with a gate on the distance to the nearest loaded village heart. The goal
     * selector asks {@link #canUse()} on one tick of two: the gate counts its own time.
     */
    static final class UnitTargetGoal extends NearestAttackableTargetGoal<FactionUnit> {
        private boolean nearHeart;
        private int nextGateCheck;

        UnitTargetGoal(PathfinderMob mob) {
            super(mob, FactionUnit.class, true);
        }

        @Override
        public boolean canUse() {
            if (mob.tickCount >= nextGateCheck) {
                nearHeart = VillageHearts.findNearest(mob.level(), mob.blockPosition(), gateRadius()).isPresent();
                nextGateCheck = mob.tickCount + GATE_INTERVAL;
            }
            return nearHeart && super.canUse();
        }
    }

    /** The distance from a heart where the units can be: the village radius and the chase radius. */
    public static double gateRadius() {
        return WorldConfig.VILLAGE_RADIUS.get() + WorldConfig.CHASE_RADIUS.get();
    }

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
        mob.targetSelector.addGoal(TARGET_PRIORITY, new UnitTargetGoal(mob));
    }
}
