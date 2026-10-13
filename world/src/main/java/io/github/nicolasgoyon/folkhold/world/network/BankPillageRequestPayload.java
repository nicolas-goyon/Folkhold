package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player wants to open a bank chest. The client shows the confirmation dialog of the pillage,
 * with what the pillage changes.
 *
 * @param pos         the position of the bank chest
 * @param village     the name of the village
 * @param faction     the name of the faction, in its color
 * @param tierBefore  the reputation tier of the player in the village now (serialized name)
 * @param valueBefore the effective reputation of the player in the village now
 * @param tierAfter   the reputation tier after the pillage (serialized name)
 * @param valueAfter  the effective reputation after the pillage
 * @param guards      the guards of the garrison that are alive
 * @param archers     the archers of the garrison that are alive
 */
public record BankPillageRequestPayload(BlockPos pos, Component village, Component faction, String tierBefore,
        int valueBefore, String tierAfter, int valueAfter, int guards, int archers) implements CustomPacketPayload {

    public static final Type<BankPillageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "bank_pillage_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankPillageRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.village);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.faction);
                buf.writeUtf(payload.tierBefore);
                buf.writeInt(payload.valueBefore);
                buf.writeUtf(payload.tierAfter);
                buf.writeInt(payload.valueAfter);
                buf.writeVarInt(payload.guards);
                buf.writeVarInt(payload.archers);
            },
            buf -> new BankPillageRequestPayload(BlockPos.STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readInt(),
                    buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
