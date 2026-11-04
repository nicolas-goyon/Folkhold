package io.github.nicolasgoyon.folkhold.balance.rail;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A minecart of the mod with a container: the double chest minecart and the shulker box minecart. It works as the
 * vanilla chest minecart: hoppers, comparators on a detector rail, and more than one player in the menu at the same
 * time.
 *
 * <p>It has no loot table. It ignores a loot table from a command or from saved data. Thus it never generates loot,
 * also when no player breaks it. A subclass gives its number of slots in {@link #getContainerSize()} as a constant:
 * the constructor of this class reads it before the subclass sets its fields.
 */
public abstract class StorageMinecart extends AbstractMinecartContainer {
    protected StorageMinecart(EntityType<? extends StorageMinecart> type, Level level) {
        super(type, level);
        // Replaces the list of the parent class with a list of getContainerSize() slots.
        clearItemStacks();
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
    public int getDefaultDisplayOffset() {
        return 8;
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
