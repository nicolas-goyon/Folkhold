package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the end of a construction that an operator started with the ghost. The client removes the ghost
 * of this construction.
 *
 * @param template the id of the template
 * @param origin   the origin of the template in the level
 * @param finished true when the construction applied all its steps, false when a command stopped it
 */
public record ConstructionEndPayload(ResourceLocation template, BlockPos origin, boolean finished)
        implements CustomPacketPayload {

    public static final Type<ConstructionEndPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionEndPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, ConstructionEndPayload::template,
                    BlockPos.STREAM_CODEC, ConstructionEndPayload::origin,
                    ByteBufCodecs.BOOL, ConstructionEndPayload::finished, ConstructionEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
