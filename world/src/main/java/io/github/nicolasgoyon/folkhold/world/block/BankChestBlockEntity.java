package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Content of a bank chest.
 *
 * <ul>
 *   <li>Active village: the content is a view of the stocks. It comes from the stocks of the village when the player
 *       opens the chest (see {@link BankChests}). When the player closes the chest, the changes go back to the stocks
 *       and to the treasury, and the chest becomes empty.</li>
 *   <li>No active village (ruins): the chest holds real items, as a normal chest. The content is saved.</li>
 * </ul>
 */
public class BankChestBlockEntity extends BlockEntity {
    public static final int SIZE = 27;
    private static final String ITEMS_TAG = "Items";

    private final Map<String, Integer> snapshot = new HashMap<>();
    private final SimpleContainer container = new SimpleContainer(SIZE) {
        @Override
        public void stopOpen(Player player) {
            super.stopOpen(player);
            BankChests.onClose(BankChestBlockEntity.this, player);
        }
    };

    public BankChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_CHEST.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!container.isEmpty()) {
            tag.put(ITEMS_TAG, container.createTag(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.clearContent();
        if (tag.contains(ITEMS_TAG, Tag.TAG_LIST)) {
            container.fromTag(tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND), registries);
        }
    }

    /** The items of the chest. */
    public SimpleContainer container() {
        return container;
    }

    /** Quantity of each item id when the player opened the chest. */
    public Map<String, Integer> snapshot() {
        return snapshot;
    }
}
