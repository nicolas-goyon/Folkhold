package io.github.nicolasgoyon.folkhold.world.mixin;

import io.github.nicolasgoyon.folkhold.world.journal.MapMoves;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The move and the update of a map in the cartography table (see {@link MapMoves}): a button of the screen selects a
 * direction or the update, and the map with a paper gives the moved or updated map in place of the zoomed map.
 */
@Mixin(CartographyTableMenu.class)
// Mixin calls the injected methods, and gives them the parameter of the callback.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class CartographyTableMenuMixin extends AbstractContainerMenu {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    @Final
    private ResultContainer resultContainer;

    /** The selected button ({@link MapMoves#NONE} for the zoom of the game). */
    @Unique
    private int folkhold$direction = MapMoves.NONE;

    private CartographyTableMenuMixin() {
        super(null, 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < MapMoves.NORTH || id > MapMoves.UPDATE) {
            return false;
        }
        folkhold$direction = id == folkhold$direction ? MapMoves.NONE : id;
        slotsChanged(slots.get(0).container);
        return true;
    }

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void folkhold$moveResult(ItemStack map, ItemStack firstSlotStack, ItemStack resultOutput,
            CallbackInfo info) {
        if (folkhold$direction == MapMoves.NONE || !firstSlotStack.is(Items.PAPER)) {
            return;
        }
        info.cancel();
        access.execute((level, pos) -> {
            MapItemSavedData data = MapItem.getSavedData(map, level);
            if (data == null || data.locked) {
                resultContainer.removeItemNoUpdate(2);
            } else {
                ItemStack result = MapMoves.marked(map.copyWithCount(1), folkhold$direction);
                if (!ItemStack.matches(result, resultOutput)) {
                    resultContainer.setItem(2, result);
                }
            }
            broadcastChanges();
        });
    }
}
