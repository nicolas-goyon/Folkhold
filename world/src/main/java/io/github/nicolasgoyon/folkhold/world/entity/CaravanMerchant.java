package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.group.RouteWalkGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The merchant of a caravan. It looks like a wandering trader and walks on the route of its caravan (see
 * {@link RouteWalkGoal}).
 *
 * <ul>
 *   <li>It has no offers of the wandering trader.</li>
 *   <li>It does not drink potions, does not despawn and does not walk away from its route.</li>
 * </ul>
 */
public class CaravanMerchant extends WanderingTrader {
    /** Speed factor of the merchant on the route. */
    private static final double ROUTE_SPEED = 0.6;

    public CaravanMerchant(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /** The attributes: the health of a villager and the speed of a villager. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new RouteWalkGoal(this, ROUTE_SPEED));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** No offers of the wandering trader. */
    @Override
    protected void updateTrades() {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
