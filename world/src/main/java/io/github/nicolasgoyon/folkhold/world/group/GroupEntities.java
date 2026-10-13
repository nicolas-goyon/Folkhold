package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Makes the entity of a member of a mobile group. The entity is not in the level yet.
 *
 * <ul>
 *   <li>Merchant: a vanilla villager with the Folkhold profession {@code merchant}, with no offers.</li>
 *   <li>Pack animal: a vanilla mule with a chest.</li>
 *   <li>Guard: a faction guard of the faction of the group, with no village. Its home position is its spawn
 *       position.</li>
 * </ul>
 */
public final class GroupEntities {

    private GroupEntities() {
    }

    /**
     * Makes the entity of a member.
     *
     * @param level  the level
     * @param group  the group
     * @param member the member
     * @param pos    the spawn position
     * @return the entity with the position, or null
     */
    @Nullable
    public static Mob create(ServerLevel level, MobileGroup group, GroupMember member, BlockPos pos) {
        Mob mob = switch (member.role()) {
            case MERCHANT -> EntityType.VILLAGER.create(level);
            case PACK_ANIMAL -> EntityType.MULE.create(level);
            case GUARD -> ModEntities.GUARD.get().create(level);
        };
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (mob instanceof GuardEntity guard) {
            guard.setFactionId(group.factionId());
            guard.setHomePos(pos);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        if (mob instanceof GuardEntity guard) {
            // The spawn can find a village heart near the origin. A guard of a group has no village.
            guard.setVillagePos(null);
        } else if (mob instanceof Villager villager) {
            villager.setVillagerData(villager.getVillagerData().setProfession(ModProfessions.MERCHANT.get())
                    .setLevel(1));
            villager.setVillagerXp(1);
            villager.setOffers(new MerchantOffers());
        } else if (mob instanceof Mule mule) {
            mule.setChest(true);
        }
        mob.setPersistenceRequired();
        mob.setHealth(Math.min(member.health(), mob.getMaxHealth()));
        return mob;
    }
}
