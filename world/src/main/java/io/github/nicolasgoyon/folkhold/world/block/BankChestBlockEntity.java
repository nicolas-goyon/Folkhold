package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Content of a bank chest while a player looks at it. The content comes from the stocks of the village when the
 * player opens the chest (see {@link BankChests#open}). When the player closes the chest, the changes go back to
 * the stocks and to the treasury. The content is not saved.
 */
public class BankChestBlockEntity extends BlockEntity {
    public static final int SIZE = 27;

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

    /** The items of the chest while a player looks at it. */
    public SimpleContainer container() {
        return container;
    }

    /** Quantity of each item id when the player opened the chest. */
    public Map<String, Integer> snapshot() {
        return snapshot;
    }
}
