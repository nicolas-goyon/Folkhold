package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player wants to open a bank chest. The client shows the confirmation screen of the pillage.
 *
 * @param pos the position of the bank chest
 */
public record BankPillageRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<BankPillageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "bank_pillage_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankPillageRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, BankPillageRequestPayload::pos, BankPillageRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
