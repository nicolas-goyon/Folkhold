package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the territory where the player is. The client shows it in the territory HUD.
 *
 * @param faction the name of the faction that owns the territory (in its color), or empty outside a territory
 * @param tier    the reputation tier of the player with this faction, or empty
 * @param village the name of the village near the player, or empty
 */
public record TerritoryStatusPayload(Optional<Component> faction, Optional<Component> tier,
        Optional<Component> village) implements CustomPacketPayload {

    public static final Type<TerritoryStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryStatusPayload::faction,
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryStatusPayload::tier,
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryStatusPayload::village,
                    TerritoryStatusPayload::new);

    /** The status outside all territories. */
    public static final TerritoryStatusPayload NONE =
            new TerritoryStatusPayload(Optional.empty(), Optional.empty(), Optional.empty());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
