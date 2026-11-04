package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A minecart that burns fuel and pulls the minecarts behind it (see {@link CartLinks}). Put it at the front of a
 * train.
 *
 * <p>The locomotive sets its own speed at the end of its tick with {@link Traction}: the pull of the locomotive, the
 * mass of the train and the rails under each minecart. The vanilla slope, powered rail and slowdown do not change
 * its speed. The chains of {@link Trains} then pull the minecarts behind it. A train that waits does not move, so the
 * fuel does not burn: the fuel burns for each block that the locomotive moves.
 *
 * <p>The player adds fuel with a right click. A right click with an empty hand starts or stops the locomotive. A
 * locomotive that is stopped or has no fuel brakes with its pulling force. It continues when the player adds fuel.
 */
public abstract class Locomotive extends AbstractMinecart {
    /** The speed cap of the vanilla rails, in blocks per tick. */
    public static final float RAIL_SPEED_CAP = 0.4F;
    private static final EntityDataAccessor<Boolean> DATA_BURNING =
            SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
    /** A movement below this distance gives no direction, in blocks. */
    private static final double MIN_MOVE = 1.0E-3;

    private double fuel;
    private boolean running;
    /** The horizontal unit vector of the direction of travel, or zero before the first start. */
    private double dirX;
    private double dirZ;
    /** The position at the start of the last tick, or NaN. */
    private double startX = Double.NaN;
    private double startZ = Double.NaN;

    protected Locomotive(EntityType<? extends Locomotive> type, Level level) {
        super(type, level);
    }

    /** The stats of this type of locomotive, from the configuration. */
    public abstract LocomotiveStats stats();

    /** The fuel value of an item in ticks of furnace fuel, or 0 when the item is not a fuel of this locomotive. */
    protected abstract int fuelValue(ItemStack stack);

    public double fuel() {
        return fuel;
    }

    public void setFuel(double fuel) {
        this.fuel = Math.clamp(fuel, 0.0, stats().fuelCapacity());
    }

    public boolean isRunning() {
        return running;
    }

    /** Tells if the locomotive runs and has fuel. The client gets this value. */
    public boolean isBurning() {
        return entityData.get(DATA_BURNING);
    }

    /**
     * Starts or stops the locomotive. On start, the locomotive goes away from the minecart behind it, or away from a
     * position when it has no minecart behind it.
     *
     * @param from the position of the player
     */
    public void setRunning(boolean running, Vec3 from) {
        this.running = running;
        if (running && level() instanceof ServerLevel level) {
            Vec3 back = CartLinks.get(this).back().flatMap(id -> CartLinks.find(level, id)).map(AbstractMinecart::position)
                    .orElse(from);
            double dx = getX() - back.x;
            double dz = getZ() - back.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > MIN_MOVE) {
                dirX = dx / length;
                dirZ = dz / length;
            }
        }
        entityData.set(DATA_BURNING, running && fuel > 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURNING, false);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel) {
            // The movement from the start of the last tick, with the corrections of the train: a train that waits
            // goes back to its position, so its fuel does not burn.
            double moved = Math.min(1.0, Math.sqrt(Math.pow(xo - startX, 2) + Math.pow(zo - startZ, 2)));
            if (running && fuel > 0.0 && Double.isFinite(moved)) {
                fuel = Math.max(0.0, fuel - moved * stats().fuelPerBlock());
            }
            startX = xo;
            startZ = zo;
            entityData.set(DATA_BURNING, running && fuel > 0.0);
        }
        super.tick();
        if (level() instanceof ServerLevel level && !isRemoved() && isOnRails()) {
            drive(level);
        }
        if (level().isClientSide() && isBurning() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 0.0, 0.0, 0.0);
        }
    }

    /** Sets the motion of the next tick from the movement of this tick and the forces on the train. */
    private void drive(ServerLevel level) {
        Optional<List<AbstractMinecart>> train = CartLinks.walk(level, this, false, BalanceConfig.TRAIN_MAX_CARTS.get());
        if (train.isEmpty()) {
            // A minecart of the train is not loaded: the train waits (see Trains).
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > MIN_MOVE && dx * dirX + dz * dirZ > 0.0) {
            // The direction follows the rails.
            dirX = dx / moved;
            dirZ = dz / moved;
        }
        double speed = Math.max(0.0, dx * dirX + dz * dirZ);
        LocomotiveStats stats = stats();
        Traction traction = BalanceConfig.traction();
        double mass = 0.0;
        double resistance = 0.0;
        List<AbstractMinecart> carts = train.get();
        for (int i = 0; i < carts.size(); i++) {
            AbstractMinecart cart = carts.get(i);
            double cartMass = i == 0 ? stats.mass() : traction.wagonMass(fillRatio(cart));
            double tx = dirX;
            double tz = dirZ;
            if (i > 0) {
                AbstractMinecart front = carts.get(i - 1);
                double fx = front.getX() - cart.getX();
                double fz = front.getZ() - cart.getZ();
                double length = Math.sqrt(fx * fx + fz * fz);
                if (length > MIN_MOVE) {
                    tx = fx / length;
                    tz = fz / length;
                }
            }
            mass += cartMass;
            resistance += cartMass * railResistance(level, cart, tx, tz, traction);
        }
        // A locomotive that does not pull brakes with its pulling force.
        double pull = running && fuel > 0.0 ? stats.pull(speed) : -stats.force();
        double next = Traction.nextSpeed(speed, pull, resistance, mass, Math.min(stats.maxSpeed(), RAIL_SPEED_CAP));
        setDeltaMovement(dirX * next, 0.0, dirZ * next);
    }

    /** The resistance of the rail under a minecart for each unit of mass, for a travel along (tx, tz). */
    private static double railResistance(ServerLevel level, AbstractMinecart cart, double tx, double tz,
                                         Traction traction) {
        BlockPos pos = cart.getCurrentRailPosition();
        BlockState state = level.getBlockState(pos);
        if (!cart.isOnRails() || !(state.getBlock() instanceof BaseRailBlock rail)) {
            return traction.friction();
        }
        double uphill = switch (rail.getRailDirection(state, level, pos, cart)) {
            case ASCENDING_EAST -> tx;
            case ASCENDING_WEST -> -tx;
            case ASCENDING_SOUTH -> tz;
            case ASCENDING_NORTH -> -tz;
            default -> 0.0;
        };
        Traction.RailPower power = Traction.RailPower.NONE;
        if (rail instanceof PoweredRailBlock powered && !powered.isActivatorRail()) {
            power = state.getValue(PoweredRailBlock.POWERED) ? Traction.RailPower.POWERED
                    : Traction.RailPower.UNPOWERED;
        }
        return traction.resistance(uphill, power);
    }

    /** The fill ratio of the slots of a minecart with a container, from 0 to 1. Other minecarts give 0. */
    static double fillRatio(AbstractMinecart cart) {
        if (!(cart instanceof Container container) || container.getContainerSize() == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                sum += (double) stack.getCount() / stack.getMaxStackSize();
            }
        }
        return sum / container.getContainerSize();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult result = super.interact(player, hand);
        if (result.consumesAction()) {
            return result;
        }
        ItemStack stack = player.getItemInHand(hand);
        int value = fuelValue(stack);
        if (value <= 0 && (!stack.isEmpty() || hand != InteractionHand.MAIN_HAND)) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        LocomotiveStats stats = stats();
        if (value > 0) {
            if (fuel + value > stats.fuelCapacity()) {
                player.displayClientMessage(Component.translatable("folkhold_balance.locomotive.full"), true);
                return InteractionResult.CONSUME;
            }
            fuel += value;
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (remainder.isEmpty()) {
                stack.consume(1, player);
            } else {
                // A lava bucket gives back an empty bucket.
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, remainder));
            }
            entityData.set(DATA_BURNING, running && fuel > 0.0);
        } else {
            setRunning(!running, player.position());
        }
        String state = !running ? "stopped" : fuel > 0.0 ? "running" : "no_fuel";
        player.displayClientMessage(Component.translatable("folkhold_balance.locomotive." + state,
                (int) (fuel / Math.max(stats.fuelPerBlock(), 1.0E-3))), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public float getMaxCartSpeedOnRail() {
        return RAIL_SPEED_CAP;
    }

    /** The locomotive sets its own speed: no vanilla slope. */
    @Override
    public double getSlopeAdjustment() {
        return 0.0;
    }

    /** The locomotive sets its own speed: no vanilla push or brake of the powered rails. */
    @Override
    public boolean shouldDoRailFunctions() {
        return false;
    }

    @Override
    public boolean canBeRidden() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Fuel", fuel);
        tag.putBoolean("Running", running);
        tag.putDouble("DirX", dirX);
        tag.putDouble("DirZ", dirZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        double saved = tag.getDouble("Fuel");
        fuel = Double.isFinite(saved) ? Math.max(0.0, saved) : 0.0;
        running = tag.getBoolean("Running");
        double x = tag.getDouble("DirX");
        double z = tag.getDouble("DirZ");
        double length = Math.sqrt(x * x + z * z);
        if (Double.isFinite(length) && length > MIN_MOVE) {
            dirX = x / length;
            dirZ = z / length;
        }
    }
}
