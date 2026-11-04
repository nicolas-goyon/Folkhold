package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the client opens the map of the admin screen, or updates the open map. The server sends the
 * content of the map in the layer {@code folkhold_world:admin} of Cartofold (see
 * {@link io.github.nicolasgoyon.folkhold.world.admin.AdminMap}). The payload gives the box of all the settlements and
 * their territories, in blocks, for the button "All". With no settlement, the minimum is larger than the maximum.
 *
 * @param minX the minimum X
 * @param minZ the minimum Z
 * @param maxX the maximum X
 * @param maxZ the maximum Z
 */
public record AdminMapPayload(int minX, int minZ, int maxX, int maxZ) implements CustomPacketPayload {
    /** The box of a map with no settlement. */
    public static final AdminMapPayload EMPTY = new AdminMapPayload(1, 1, 0, 0);

    public static final Type<AdminMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminMapPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AdminMapPayload::minX,
            ByteBufCodecs.VAR_INT, AdminMapPayload::minZ,
            ByteBufCodecs.VAR_INT, AdminMapPayload::maxX,
            ByteBufCodecs.VAR_INT, AdminMapPayload::maxZ,
            AdminMapPayload::new);

    /** True when the map has no settlement. */
    public boolean empty() {
        return minX > maxX || minZ > maxZ;
    }

    /**
     * The kind of a marker of the admin map. The identifier of a marker is the name of its kind, ":", and the id of
     * the settlement or of the group.
     */
    public enum Kind {
        /** A village in the world: its heart exists. */
        VILLAGE,
        /** A settlement that exists only in the simulation: the game did not generate its chunks. */
        SIMULATED,
        /** A ruin. */
        RUIN,
        /** A caravan. */
        CARAVAN,
        /** A traveling merchant. */
        MERCHANT;

        /** True for a settlement, false for a group. */
        public boolean settlement() {
            return this == VILLAGE || this == SIMULATED || this == RUIN;
        }

        /** The name of the kind in the identifiers of the markers and in the translation keys. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The identifier of the marker of a settlement or of a group.
         *
         * @param id the id of the settlement or of the group
         * @return the identifier of the marker
         */
        public String markerId(String id) {
            return key() + ":" + id;
        }

        /**
         * The kind of a marker.
         *
         * @param markerId the identifier of the marker
         * @return the kind, or empty for an identifier that is not an identifier of the admin map
         */
        public static Optional<Kind> of(String markerId) {
            int colon = markerId.indexOf(':');
            for (Kind kind : values()) {
                if (colon > 0 && kind.name().equalsIgnoreCase(markerId.substring(0, colon))) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        /**
         * The id of the settlement or of the group of a marker.
         *
         * @param markerId the identifier of the marker
         * @return the id
         */
        public static String id(String markerId) {
            return markerId.substring(markerId.indexOf(':') + 1);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
