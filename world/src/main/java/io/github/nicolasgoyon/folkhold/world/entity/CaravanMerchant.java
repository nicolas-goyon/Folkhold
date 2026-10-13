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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * The merchant of a caravan. It looks like a wandering trader and walks on the route of its caravan (see
 * {@link RouteWalkGoal}).
 *
 * <ul>
 *   <li>It has no offers of the wandering trader.</li>
 *   <li>It does not drink potions, does not despawn and does not walk away from its route.</li>
 *   <li>After the arrival of its group, it walks near the destination heart (its restriction area, see
 *       {@link io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer#release}).</li>
 * </ul>
 */
public class CaravanMerchant extends WanderingTrader {
    /**
     * Speed factor of the merchant on the route. The merchant is a little slower than the guards and the mules of
     * its group, so they can follow it.
     */
    public static final double ROUTE_SPEED = 0.45;
    /** Speed factor of the merchant when it walks in a village after the arrival. */
    private static final double STROLL_SPEED = 0.4;

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
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED) {
            @Override
            public boolean canUse() {
                return hasRestriction() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** No offers of the wandering trader. */
    @Override
    protected void updateTrades() {
    }

    /**
     * Sets the offers. The offers of a caravan merchant come from the cargo of its caravan.
     *
     * @param offers the offers
     */
    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
