package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * A minecart with a double chest: 54 slots. It works as the vanilla chest minecart: hoppers, comparators on a
 * detector rail, and more than one player in the menu at the same time.
 *
 * <p>It has no loot table. It ignores a loot table from a command or from saved data. Thus it never generates loot,
 * also when no player breaks it.
 */
public class DoubleChestMinecart extends AbstractMinecartContainer {
    /** The number of slots: 6 rows of 9 slots. */
    public static final int SIZE = 54;

    public DoubleChestMinecart(EntityType<? extends DoubleChestMinecart> type, Level level) {
        super(type, level);
        // Replaces the list of the parent class with a list of SIZE slots.
        clearItemStacks();
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DOUBLE_CHEST_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public Type getMinecartType() {
        return Type.CHEST;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
    }

    @Override
    public int getDefaultDisplayOffset() {
        return 8;
    }

    /** The client draws a second chest above the bounding box: the culling box is 1 block taller. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.sixRows(id, inventory, this);
    }

    @Override
    public void stopOpen(Player player) {
        level().gameEvent(GameEvent.CONTAINER_CLOSE, position(), GameEvent.Context.of(player));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult result = interactWithContainerVehicle(player);
        if (result.consumesAction()) {
            gameEvent(GameEvent.CONTAINER_OPEN, player);
            PiglinAi.angerNearbyPiglins(player, true);
        }
        return result;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        // No loot table: see the class comment.
    }

    @Override
    public void setLootTable(ResourceKey<LootTable> lootTable, long seed) {
        // No loot table: see the class comment.
    }
}
