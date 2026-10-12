package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an action of the admin screen (see
 * {@link io.github.nicolasgoyon.folkhold.world.admin.AdminActions}).
 *
 * @param action       the action name
 * @param settlementId the settlement of the action, or an empty text for a global action
 * @param value        the value of the action (for example the level), or 0
 */
public record AdminActionPayload(String action, String settlementId, int value) implements CustomPacketPayload {

    public static final Type<AdminActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::settlementId,
                    ByteBufCodecs.VAR_INT, AdminActionPayload::value,
                    AdminActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
