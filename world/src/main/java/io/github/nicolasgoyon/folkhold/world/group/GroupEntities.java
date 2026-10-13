package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ai.FollowEntityGoal;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Makes the entity of a member of a mobile group. The entity is not in the level yet.
 *
 * <ul>
 *   <li>Merchant: a {@link CaravanMerchant}. It walks on the route. The name of a caravan merchant is "Caravan of"
 *       and the name of the origin settlement. The name of a traveling merchant is "Traveling Merchant".</li>
 *   <li>Pack animal: a tamed vanilla mule with a chest. It follows the merchant (see {@link FollowEntityGoal}).
 *       Its chest holds a part of the cargo (see {@link CaravanCargo}). All mules have the same speed.</li>
 *   <li>Guard: a faction guard of the faction of the group, with no village. It follows the merchant when it has no
 *       target. Its home position follows the merchant (see {@link GroupMaterializer}), so its chase area has its
 *       center on the merchant.</li>
 *   <li>The merchant is a little slower than the mules and the guards, so the group stays together (see
 *       {@link RouteWalkGoal}).</li>
 * </ul>
 */
public final class GroupEntities {
    /** Movement speed of a mule of a group. A vanilla mule has a random speed. */
    private static final double MULE_MOVEMENT_SPEED = 0.25;
    /** Speed factor of a mule that follows the merchant. */
    private static final double MULE_SPEED = 1.0;
    /** Speed factor of a guard that follows the merchant. */
    private static final double GUARD_FOLLOW_SPEED = 1.0;
    /** Priority of the follow goal of a guard: after the attack goal, before the walk near the home position. */
    private static final int GUARD_FOLLOW_PRIORITY = 3;

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
            case MERCHANT -> ModEntities.CARAVAN_MERCHANT.get().create(level);
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
            group.merchant().ifPresent(leader -> guard.goalSelector.addGoal(GUARD_FOLLOW_PRIORITY,
                    new FollowEntityGoal(guard, leader.id(), GUARD_FOLLOW_SPEED)));
        } else if (mob instanceof CaravanMerchant merchant) {
            if (group.kind() == GroupKind.TRAVELING_MERCHANT) {
                merchant.setCustomName(Component.translatable("entity.folkhold_world.traveling_merchant"));
            } else {
                Optional<Settlement> origin = SettlementData.get(level.getServer()).registry()
                        .byId(group.originId());
                origin.ifPresent(settlement -> merchant.setCustomName(
                        Component.translatable("entity.folkhold_world.caravan_merchant.of", settlement.name())));
            }
        } else if (mob instanceof Mule mule) {
            CaravanCargo.equipChest(mule);
            mule.setTamed(true);
            AttributeInstance movement = mule.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movement != null) {
                movement.setBaseValue(MULE_MOVEMENT_SPEED);
            }
            group.merchant().ifPresent(leader ->
                    mule.goalSelector.addGoal(2, new FollowEntityGoal(mule, leader.id(), MULE_SPEED)));
            // Hidden leather boots: the mule walks on powder snow, as the other members of the group (entity type
            // tag powder_snow_walkable_mobs). The model of a mule does not show them, and they do not drop.
            mule.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            mule.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        mob.setPersistenceRequired();
        mob.setHealth(Math.min(member.health(), mob.getMaxHealth()));
        return mob;
    }
}
