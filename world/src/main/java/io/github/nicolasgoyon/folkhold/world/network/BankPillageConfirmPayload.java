package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player confirmed the pillage of a bank chest.
 *
 * @param pos the position of the bank chest
 */
public record BankPillageConfirmPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<BankPillageConfirmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "bank_pillage_confirm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankPillageConfirmPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, BankPillageConfirmPayload::pos, BankPillageConfirmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
