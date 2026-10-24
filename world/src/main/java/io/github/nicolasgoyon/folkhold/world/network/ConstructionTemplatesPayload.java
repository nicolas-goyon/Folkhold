package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the structure templates of the server, for the construction menu of an operator. The client opens
 * the construction menu, or updates it if it is open.
 *
 * @param templates the ids of the templates, in their order in the menu
 */
public record ConstructionTemplatesPayload(List<ResourceLocation> templates) implements CustomPacketPayload {

    public static final Type<ConstructionTemplatesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_templates"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionTemplatesPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ConstructionTemplatesPayload::templates, ConstructionTemplatesPayload::new);

    public ConstructionTemplatesPayload {
        templates = List.copyOf(templates);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
