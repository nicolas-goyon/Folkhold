package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The player links and unlinks minecarts:
 * <ul>
 *   <li>the item "chain" on a first minecart, then on a second minecart, links them. The link uses 1 chain. The
 *       action bar shows the first minecart;</li>
 *   <li>shears, or an empty main hand while the player sneaks, remove the front link of a minecart, or its back link
 *       when it has no front link. The chain drops. An empty hand without sneak still lets the player ride.</li>
 * </ul>
 */
public final class ChainUse {
    /** The first minecart that each player selects with a chain. */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private record Selection(ResourceKey<Level> level, UUID cart) {
    }

    private ChainUse() {
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractMinecart cart)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        boolean server = !event.getLevel().isClientSide();
        if (stack.is(Items.CHAIN)) {
            // The chain never starts the vanilla interaction: no ride, no menu of a chest minecart.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(!server));
            if (server && player instanceof ServerPlayer serverPlayer) {
                useChain(serverPlayer, cart, stack);
            }
            return;
        }
        boolean shears = stack.is(Tags.Items.TOOLS_SHEAR);
        boolean emptyHand = stack.isEmpty() && event.getHand() == InteractionHand.MAIN_HAND
                && player.isSecondaryUseActive();
        if (!server || !(shears || emptyHand) || !(cart.level() instanceof ServerLevel level)) {
            return;
        }
        CartLink link = CartLinks.get(cart);
        Optional<UUID> partner = link.front().or(link::back);
        if (partner.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        CartLinks.unlink(level, cart, partner.get(), !player.hasInfiniteMaterials());
        level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL,
                1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("folkhold_balance.chain.unlinked"), true);
    }

    private static void useChain(ServerPlayer player, AbstractMinecart cart, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        Selection selection = SELECTIONS.get(player.getUUID());
        Optional<AbstractMinecart> first = Optional.empty();
        if (selection != null && selection.level() == level.dimension()) {
            first = CartLinks.find(level, selection.cart()).filter(found -> found != cart);
        }
        if (first.isEmpty()) {
            SELECTIONS.put(player.getUUID(), new Selection(level.dimension(), cart.getUUID()));
            player.displayClientMessage(Component.translatable("folkhold_balance.chain.first", cart.getDisplayName()),
                    true);
            return;
        }
        SELECTIONS.remove(player.getUUID());
        int maxCarts = BalanceConfig.TRAIN_MAX_CARTS.get();
        CartLinks.LinkResult result = CartLinks.link(level, first.get(), cart, BalanceConfig.coupling().maxDistance(),
                maxCarts);
        if (result == CartLinks.LinkResult.LINKED) {
            stack.consume(1, player);
            level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), SoundEvents.CHAIN_PLACE,
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        player.displayClientMessage(result.message(maxCarts), true);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SELECTIONS.clear();
    }
}
