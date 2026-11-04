package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A minecart with an ender chest. It opens the ender chest of the player who uses it, as the ender chest block. Two
 * players see their own ender chests in the same minecart.
 *
 * <p>The minecart holds no items: it is not a {@link Container}. Thus hoppers, hopper minecarts and the item handlers
 * of NeoForge have no access to it, and a train counts it as an empty wagon. It drops only its item.
 */
public class EnderChestMinecart extends AbstractMinecart {
    private static final Component TITLE = Component.translatable("container.enderchest");
    /** The maximum distance between the player and the minecart for the menu, as for the vanilla chest minecart. */
    private static final double REACH = 4.0;

    public EnderChestMinecart(EntityType<? extends EnderChestMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.ENDER_CHEST_MINECART.get();
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
        return Blocks.ENDER_CHEST.defaultBlockState().setValue(EnderChestBlock.FACING, Direction.NORTH);
    }

    @Override
    public int getDefaultDisplayOffset() {
        return 8;
    }

    /** Opens the ender chest of the player. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PlayerEnderChestContainer chest = player.getEnderChestInventory();
        // No ender chest block is open: the menu of the minecart sets the rule to close it.
        chest.setActiveChest(null);
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new Menu(id, inventory, chest, this), TITLE));
        player.awardStat(Stats.OPEN_ENDERCHEST);
        gameEvent(GameEvent.CONTAINER_OPEN, player);
        PiglinAi.angerNearbyPiglins(player, true);
        playChestSound(SoundEvents.ENDER_CHEST_OPEN);
        return InteractionResult.CONSUME;
    }

    private void playChestSound(SoundEvent sound) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.BLOCKS, 0.5F,
                level().random.nextFloat() * 0.1F + 0.9F);
    }

    /**
     * The menu of the ender chest of a player. It closes when the minecart is removed or when the player is too far
     * from the minecart.
     */
    private static final class Menu extends ChestMenu {
        private final EnderChestMinecart cart;

        Menu(int id, Inventory inventory, Container chest, EnderChestMinecart cart) {
            super(MenuType.GENERIC_9x3, id, inventory, chest, 3);
            this.cart = cart;
        }

        @Override
        public boolean stillValid(Player player) {
            return super.stillValid(player) && player.canInteractWithEntity(cart, REACH);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (!player.level().isClientSide) {
                cart.level().gameEvent(GameEvent.CONTAINER_CLOSE, cart.position(), GameEvent.Context.of(player));
                cart.playChestSound(SoundEvents.ENDER_CHEST_CLOSE);
            }
        }
    }
}
