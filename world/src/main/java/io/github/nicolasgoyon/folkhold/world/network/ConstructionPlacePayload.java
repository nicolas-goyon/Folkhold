package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Client to server: an operator starts the construction of a structure template at the position of its ghost. The
 * server checks the permission, the template, the size and the distance, then starts the construction.
 *
 * @param template     the id of the template
 * @param origin       the position of the origin of the template in the level
 * @param rotation     the turn of the template
 * @param mirror       the mirror of the template
 * @param stepsPerTick the number of steps of the construction for each tick, or
 *                     {@link io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement#BUILDERS}: the
 *                     builders of the nearest village build the structure
 */
public record ConstructionPlacePayload(ResourceLocation template, BlockPos origin, Rotation rotation, Mirror mirror,
        int stepsPerTick) implements CustomPacketPayload {

    public static final Type<ConstructionPlacePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_place"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPlacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ConstructionPlacePayload::template,
                    BlockPos.STREAM_CODEC, ConstructionPlacePayload::origin,
                    NeoForgeStreamCodecs.enumCodec(Rotation.class), ConstructionPlacePayload::rotation,
                    NeoForgeStreamCodecs.enumCodec(Mirror.class), ConstructionPlacePayload::mirror,
                    ByteBufCodecs.VAR_INT, ConstructionPlacePayload::stepsPerTick,
                    ConstructionPlacePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
