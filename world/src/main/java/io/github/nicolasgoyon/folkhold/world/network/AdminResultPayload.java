package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the result of an admin action. The admin screen shows it in its status line. If the screen is
 * closed, the result goes to the chat.
 *
 * @param message the result
 * @param success true if the action occurred
 */
public record AdminResultPayload(Component message, boolean success) implements CustomPacketPayload {

    public static final Type<AdminResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC, AdminResultPayload::message,
                    ByteBufCodecs.BOOL, AdminResultPayload::success,
                    AdminResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
