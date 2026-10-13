package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the territory where the player is. The client shows it in the territory HUD.
 *
 * @param faction      the name of the faction that owns the territory (in its color), "Unclaimed territory", or
 *                     empty outside the overworld
 * @param factionColor the color of the faction, for its banner, or -1 for no faction
 * @param tier         the reputation tier of the player with this faction (serialized name), or empty
 * @param village      the name of the village near the player, or empty
 * @param villagePos   the position of the heart of this village, or empty
 */
public record TerritoryStatusPayload(Optional<Component> faction, int factionColor, Optional<String> tier,
        Optional<Component> village, Optional<BlockPos> villagePos) implements CustomPacketPayload {

    public static final Type<TerritoryStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryStatusPayload::faction,
                    ByteBufCodecs.INT, TerritoryStatusPayload::factionColor,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), TerritoryStatusPayload::tier,
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryStatusPayload::village,
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC), TerritoryStatusPayload::villagePos,
                    TerritoryStatusPayload::new);

    /** The status outside all territories. */
    public static final TerritoryStatusPayload NONE = new TerritoryStatusPayload(Optional.empty(), -1,
            Optional.empty(), Optional.empty(), Optional.empty());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
