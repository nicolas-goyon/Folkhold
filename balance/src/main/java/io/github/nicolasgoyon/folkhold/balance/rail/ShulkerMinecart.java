package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A minecart with a shulker box: 27 slots, with no color or with one of the 16 dye colors.
 *
 * <p>The minecart keeps its items as a shulker box does. When it is removed (broken, destroyed, killed), it drops one
 * item with its items ({@link DataComponents#CONTAINER}), its color ({@link DataComponents#BASE_COLOR}) and its name.
 * Its stacks never drop one by one. An empty minecart drops its item only when it is broken and the game rule
 * {@code doEntityDrops} is true, as the other minecarts. A shulker box or a shulker box minecart cannot go into it.
 *
 * <p>It has no loot table (see {@link StorageMinecart}).
 */
public class ShulkerMinecart extends StorageMinecart {
    /** The number of slots: 3 rows of 9 slots, as a shulker box. */
    public static final int SIZE = 27;

    /** The id of the dye color, or -1 for no color. */
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(ShulkerMinecart.class, EntityDataSerializers.INT);
    private static final String COLOR_TAG = "Color";

    /** True when damage destroys the minecart, false for a removal with no damage (for example {@code /kill}). */
    private boolean broken;

    public ShulkerMinecart(EntityType<? extends ShulkerMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, -1);
    }

    /** The color of the shulker box, or null for no color. */
    @Nullable
    public DyeColor getColor() {
        int id = entityData.get(DATA_COLOR);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public void setColor(@Nullable DyeColor color) {
        entityData.set(DATA_COLOR, color == null ? -1 : color.getId());
    }

    /** Gives the minecart the color and the items of its item. The item and the dispenser call this on placement. */
    public void loadFromItem(ItemStack stack) {
        setColor(stack.get(DataComponents.BASE_COLOR));
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItemStacks());
    }

    /** The item of this minecart, with its color, its items and its name. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(getDropItem());
        stack.set(DataComponents.BASE_COLOR, getColor());
        if (!isEmpty()) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItemStacks()));
        }
        stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    /** The same rule as the shulker box: no shulker box and no shulker box minecart. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.canFitInsideContainerItems();
    }

    @Override
    protected Item getDropItem() {
        return ModItems.SHULKER_BOX_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack stack = new ItemStack(getDropItem());
        stack.set(DataComponents.BASE_COLOR, getColor());
        return stack;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return ShulkerBoxBlock.getBlockByColor(getColor()).defaultBlockState();
    }

    /** Damage destroys the minecart: it drops only its item, with its items (see {@link #remove}). */
    @Override
    public void destroy(DamageSource source) {
        broken = true;
        kill();
        if (source.getDirectEntity() instanceof Player player) {
            PiglinAi.angerNearbyPiglins(player, true);
        }
    }

    /**
     * Drops the item with the items before the removal, then clears the slots. Thus the parent class drops no stack.
     * A creative player, an explosion, lava, {@code /kill} and the other removals all come here.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !isRemoved()) {
            boolean drops = level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
            if (!isEmpty() || (broken && drops)) {
                spawnAtLocation(toItem());
            }
            clearContent();
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        DyeColor color = getColor();
        if (color != null) {
            tag.putByte(COLOR_TAG, (byte) color.getId());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setColor(tag.contains(COLOR_TAG) ? DyeColor.byId(tag.getByte(COLOR_TAG)) : null);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ShulkerBoxMenu(id, inventory, this);
    }

}
