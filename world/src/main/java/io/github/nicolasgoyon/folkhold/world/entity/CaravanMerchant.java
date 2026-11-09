package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitNavigation;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UsePassageGoal;
import io.github.nicolasgoyon.folkhold.world.group.CaravanStays;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.group.RouteWalkGoal;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 *   <li>It has the navigation of the faction units ({@link UnitNavigation}): its path can go through a gate of a
 *       village, and it opens and closes the fence gates and the wooden doors ({@link UsePassageGoal}). A gate stays
 *       open until the mules and the guards of its group are through it.</li>
 *   <li>After the arrival of its caravan, it walks to the stop of the caravan and stays there: the caravan yard of
 *       the market, or the plaza (see {@link CaravanStays}). During this stay, it sells the goods of the stock of the
 *       destination that no worker sells, at all the hours.</li>
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
    /** Game ticks between two paths to the stop. */
    private static final int STAY_REPATH_TICKS = 40;
    /** After this time far from the stop, the merchant moves directly to the stop, in game ticks. */
    public static final int STAY_STUCK_TICKS = 400;

    /** The id of the destination settlement during the stay, or null. Not saved: the stay ends at a load. */
    @Nullable
    private String staySettlement;

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
    protected PathNavigation createNavigation(Level level) {
        return new UnitNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new UsePassageGoal(this, this::followers));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new RouteWalkGoal(this, ROUTE_SPEED));
        goalSelector.addGoal(3, new GoToStopGoal());
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED) {
            @Override
            public boolean canUse() {
                return hasRestriction() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** The other entities of the group of the merchant: they follow it through the gates. */
    private List<LivingEntity> followers() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        String groupId = GroupMaterializer.groupId(this);
        if (groupId == null) {
            return List.of();
        }
        return MobileGroupData.get(serverLevel.getServer()).groups().byId(groupId)
                .map(group -> GroupMaterializer.memberEntities(serverLevel, group).stream()
                        .filter(member -> member != this)
                        .toList())
                .orElse(List.of());
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

    /**
     * Starts the stay of the merchant at the stop of its caravan: the merchant walks to the stop and stays near it.
     *
     * @param settlementId the id of the destination settlement
     * @param stop         the stop
     */
    public void stay(String settlementId, CaravanStays.Stop stop) {
        staySettlement = settlementId;
        restrictTo(stop.pos(), stop.radius());
    }

    /** The id of the destination settlement during the stay, or null when the merchant does not stay. */
    @Nullable
    public String staySettlement() {
        return staySettlement;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** During the stay, the merchant walks to its stop when it is out of its restriction area. */
    private final class GoToStopGoal extends Goal {
        private int repathTicks;
        private int farTicks;

        GoToStopGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return staySettlement != null && hasRestriction() && !isWithinRestriction();
        }

        @Override
        public void start() {
            repathTicks = 0;
            farTicks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos stop = getRestrictCenter();
            if (++farTicks > STAY_STUCK_TICKS) {
                // The stop at the plaza is the heart, a solid block: the merchant stands on a free position.
                BlockPos stand = CaravanStays.standPos(level(), stop);
                getNavigation().stop();
                moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, getYRot(), getXRot());
                farTicks = 0;
                return;
            }
            if (--repathTicks <= 0 || getNavigation().isDone()) {
                repathTicks = STAY_REPATH_TICKS;
                getNavigation().moveTo(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5, STROLL_SPEED);
            }
        }
    }
}
