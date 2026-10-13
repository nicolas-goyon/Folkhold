package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player enters a territory. The client shows a banner at the top of the screen for some
 * seconds, in place of the vanilla title.
 *
 * @param title        the name of the territory, for example "Lands of Ashmark" or "Unclaimed territory"
 * @param subtitle     the tier of the player in the territory, or empty
 * @param factionColor the color of the faction for its banner, or -1 for no faction
 */
public record TerritoryTitlePayload(Component title, Optional<Component> subtitle, int factionColor)
        implements CustomPacketPayload {

    public static final Type<TerritoryTitlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory_title"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryTitlePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC, TerritoryTitlePayload::title,
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, TerritoryTitlePayload::subtitle,
                    ByteBufCodecs.INT, TerritoryTitlePayload::factionColor,
                    TerritoryTitlePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
