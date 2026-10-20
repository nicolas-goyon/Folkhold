package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server to client: the blocks of a structure template, for the ghost of an operator. The client closes the
 * construction menu and shows the ghost.
 *
 * @param template the id of the template
 * @param size     the size of the template
 * @param palette  the block states of the template
 * @param blocks   the blocks of the template that are not air, in the coordinates of the template
 */
public record ConstructionPreviewPayload(ResourceLocation template, Vec3i size, List<BlockState> palette,
        List<Entry> blocks) implements CustomPacketPayload {

    /** The client reads this number of blocks at most. */
    public static final int MAX_BLOCKS = 65536;

    public static final Type<ConstructionPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_preview"));

    /**
     * A block of the template.
     *
     * @param x     block X in the template
     * @param y     block Y in the template
     * @param z     block Z in the template
     * @param state the index of the state in the palette
     */
    public record Entry(int x, int y, int z, int state) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, payload.template);
                buf.writeVarInt(payload.size.getX());
                buf.writeVarInt(payload.size.getY());
                buf.writeVarInt(payload.size.getZ());
                buf.writeVarInt(payload.palette.size());
                payload.palette.forEach(state -> buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(state)));
                buf.writeVarInt(payload.blocks.size());
                for (Entry entry : payload.blocks) {
                    buf.writeVarInt(entry.x);
                    buf.writeVarInt(entry.y);
                    buf.writeVarInt(entry.z);
                    buf.writeVarInt(entry.state);
                }
            },
            buf -> {
                ResourceLocation template = ResourceLocation.STREAM_CODEC.decode(buf);
                Vec3i size = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                int paletteSize = buf.readVarInt();
                if (paletteSize < 0 || paletteSize > MAX_BLOCKS) {
                    throw new DecoderException("Too many block states in the construction preview: " + paletteSize);
                }
                List<BlockState> palette = new ArrayList<>(paletteSize);
                for (int i = 0; i < paletteSize; i++) {
                    BlockState state = Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt());
                    if (state == null) {
                        throw new DecoderException("Unknown block state in the construction preview");
                    }
                    palette.add(state);
                }
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_BLOCKS) {
                    throw new DecoderException("Too many blocks in the construction preview: " + count);
                }
                List<Entry> blocks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Entry entry = new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                    if (entry.state < 0 || entry.state >= paletteSize) {
                        throw new DecoderException("Unknown palette index in the construction preview");
                    }
                    blocks.add(entry);
                }
                return new ConstructionPreviewPayload(template, size, palette, blocks);
            });

    public ConstructionPreviewPayload {
        palette = List.copyOf(palette);
        blocks = List.copyOf(blocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
