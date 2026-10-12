package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The villagers of a faction village: the merchants and the inhabitants. They are vanilla villagers with a
 * Folkhold profession, so the vanilla systems work with them (raids, bell, beds, iron golems, zombies).
 *
 * <ul>
 *   <li>Each villager records the position of the heart of its village ({@link Merchants#VILLAGE_TAG}).</li>
 *   <li>A villager has 1 experience point, so it keeps its profession with no job site block and does not take
 *       the workstations of the village.</li>
 *   <li>The simulation sets the population: the villagers of a faction village do not breed, and a dead villager
 *       removes 1 from the population of the settlement.</li>
 * </ul>
 */
public final class VillageFolk {

    private VillageFolk() {
    }

    /** True if the villager belongs to a faction village. */
    public static boolean isVillageFolk(Villager villager) {
        return villager.getPersistentData().contains(Merchants.VILLAGE_TAG);
    }

    /** The loaded villagers of a village with a profession. */
    public static List<Villager> of(ServerLevel level, BlockPos heartPos, VillagerProfession profession) {
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        return level.getEntitiesOfClass(Villager.class, new AABB(heartPos).inflate(radius, 32.0, radius),
                villager -> villager.getVillagerData().getProfession() == profession
                        && villager.getPersistentData().getLong(Merchants.VILLAGE_TAG) == heartPos.asLong()
                        && isVillageFolk(villager));
    }

    /**
     * The target number of inhabitants: the population minus the merchants, with a maximum
     * ({@link WorldConfig#MAX_INHABITANTS}).
     *
     * @param settlement the settlement
     * @return the number of inhabitants
     */
    public static int inhabitantCount(Settlement settlement) {
        int population = settlement.economy().population() - Merchants.merchantCount(settlement);
        return Math.max(0, Math.min(WorldConfig.MAX_INHABITANTS.get(), population));
    }

    /**
     * Creates a villager of the village on the ground near the heart.
     *
     * @param level      the level
     * @param heartPos   the position of the heart
     * @param profession the profession of the villager
     * @return the villager, or null
     */
    @Nullable
    public static Villager spawn(ServerLevel level, BlockPos heartPos, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        BlockPos pos = Garrison.groundSpawnPos(level, heartPos);
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));
        villager.setVillagerXp(1);
        villager.getPersistentData().putLong(Merchants.VILLAGE_TAG, heartPos.asLong());
        level.addFreshEntity(villager);
        return villager;
    }

    /** A dead villager of a faction village removes 1 from the population of its settlement. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !isVillageFolk(villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos heartPos = BlockPos.of(villager.getPersistentData().getLong(Merchants.VILLAGE_TAG));
        if (level.isLoaded(heartPos)
                && level.getBlockEntity(heartPos) instanceof VillageHeartBlockEntity heart) {
            Settlements.of(level.getServer(), heart).ifPresent(settlement -> {
                settlement.economy().setPopulation(Math.max(0, settlement.economy().population() - 1));
                SettlementData.get(level.getServer()).setDirty();
            });
        }
    }

    /** The villagers of a faction village do not breed: the simulation sets the population. */
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.getParentA() instanceof Villager a && isVillageFolk(a)
                || event.getParentB() instanceof Villager b && isVillageFolk(b)) {
            event.setCanceled(true);
        }
    }
}
