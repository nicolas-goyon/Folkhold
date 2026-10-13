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
 * Server to client: an important event for the player. The client shows it in a toast (see
 * {@link io.github.nicolasgoyon.folkhold.world.notice.Notices}).
 *
 * @param title   the main text
 * @param details the second text, or empty
 * @param tone    the tone (serialized name of {@link io.github.nicolasgoyon.folkhold.world.notice.Notices.Tone})
 */
public record NoticePayload(Component title, Optional<Component> details, String tone)
        implements CustomPacketPayload {

    public static final Type<NoticePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, NoticePayload::title,
            ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, NoticePayload::details,
            ByteBufCodecs.STRING_UTF8, NoticePayload::tone,
            NoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
