package io.github.nicolasgoyon.folkhold.world.client;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * The sections of a construction ghost: cubes of 16 × 16 × 16 blocks, relative to the origin of the ghost. The
 * renderer keeps one vertex buffer for each section, and makes it again only when the blocks of the section change
 * (see {@link ConstructionGhostRenderer}).
 */
final class GhostSections {
    private GhostSections() {
    }

    /**
     * The key of the section of a position.
     *
     * @param relative a position relative to the origin of the ghost
     * @return the key of its section
     */
    static long key(BlockPos relative) {
        return SectionPos.asLong(SectionPos.blockToSectionCoord(relative.getX()),
                SectionPos.blockToSectionCoord(relative.getY()), SectionPos.blockToSectionCoord(relative.getZ()));
    }

    /**
     * The first position of a section, relative to the origin of the ghost.
     *
     * @param key the key of the section
     * @return the position of the corner with the lowest coordinates
     */
    static BlockPos origin(long key) {
        return new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.x(key)),
                SectionPos.sectionToBlockCoord(SectionPos.y(key)), SectionPos.sectionToBlockCoord(SectionPos.z(key)));
    }

    /**
     * The indexes of the positions in each section.
     *
     * @param relatives the positions relative to the origin of the ghost
     * @return for each section key, the indexes of its positions in the list, in increasing order
     */
    static Long2ObjectMap<int[]> group(List<BlockPos> relatives) {
        Long2ObjectMap<IntArrayList> lists = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < relatives.size(); i++) {
            lists.computeIfAbsent(key(relatives.get(i)), unused -> new IntArrayList()).add(i);
        }
        Long2ObjectMap<int[]> sections = new Long2ObjectOpenHashMap<>(lists.size());
        for (Long2ObjectMap.Entry<IntArrayList> entry : lists.long2ObjectEntrySet()) {
            sections.put(entry.getLongKey(), entry.getValue().toIntArray());
        }
        return sections;
    }
}
