package io.github.nicolasgoyon.folkhold.world.village;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The blocks of the villages that the repairs keep (see {@link VillageRepairs}), in one level. Saved in the data of
 * the level ({@code folkhold_village_repairs.dat}).
 *
 * <ul>
 *   <li>The blocks that the players placed in a village. When a player breaks such a block, the position is not kept
 *       any more. The construction sites of the builders also keep these blocks.</li>
 *   <li>The boxes of the construction sites of the builders: the new buildings of the village.</li>
 * </ul>
 */
public final class VillageRepairData extends SavedData {
    private static final String FILE_ID = "folkhold_village_repairs";
    private static final SavedData.Factory<VillageRepairData> FACTORY =
            new SavedData.Factory<>(VillageRepairData::new, VillageRepairData::load, null);

    /** The positions of the blocks of the players, for each village heart. */
    private final Map<Long, LongSet> playerBlocks = new HashMap<>();
    /** The boxes of the construction sites, for each village heart. */
    private final Map<Long, List<BoundingBox>> constructions = new HashMap<>();

    private VillageRepairData() {
    }

    /** The data of a level. The method makes the data when the level has none. */
    public static VillageRepairData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * True if the repairs of a village keep a position: a block of a player, or a block of a construction site.
     *
     * @param heart the position of the village heart
     * @param pos   the position
     * @return true if the repairs do not change the position
     */
    public boolean keeps(BlockPos heart, BlockPos pos) {
        LongSet blocks = playerBlocks.get(heart.asLong());
        if (blocks != null && blocks.contains(pos.asLong())) {
            return true;
        }
        List<BoundingBox> boxes = constructions.get(heart.asLong());
        return boxes != null && boxes.stream().anyMatch(box -> box.isInside(pos));
    }

    /**
     * True if a player placed the block at a position of a village. The builders never change such a block.
     *
     * @param heart the position of the village heart
     * @param pos   the position
     * @return true for a block of a player
     */
    public boolean isPlayerBlock(BlockPos heart, BlockPos pos) {
        LongSet blocks = playerBlocks.get(heart.asLong());
        return blocks != null && blocks.contains(pos.asLong());
    }

    /** Records a block that a player placed in a village. */
    public void addPlayerBlock(BlockPos heart, BlockPos pos) {
        if (playerBlocks.computeIfAbsent(heart.asLong(), key -> new LongOpenHashSet()).add(pos.asLong())) {
            setDirty();
        }
    }

    /** Forgets a block of a player: the player broke it. */
    public void removePlayerBlock(BlockPos heart, BlockPos pos) {
        LongSet blocks = playerBlocks.get(heart.asLong());
        if (blocks != null && blocks.remove(pos.asLong())) {
            if (blocks.isEmpty()) {
                playerBlocks.remove(heart.asLong());
            }
            setDirty();
        }
    }

    /** Records the box of a construction site of a village. */
    public void addConstruction(BlockPos heart, BoundingBox box) {
        List<BoundingBox> boxes = constructions.computeIfAbsent(heart.asLong(), key -> new ArrayList<>());
        if (!boxes.contains(box)) {
            boxes.add(box);
            setDirty();
        }
    }

    /** Forgets the data of a village: the village has no heart any more. */
    public void forget(BlockPos heart) {
        boolean changed = playerBlocks.remove(heart.asLong()) != null;
        changed |= constructions.remove(heart.asLong()) != null;
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag villages = new ListTag();
        for (long heart : villageKeys()) {
            CompoundTag village = new CompoundTag();
            village.putLong("Heart", heart);
            LongSet blocks = playerBlocks.get(heart);
            if (blocks != null) {
                village.putLongArray("PlayerBlocks", blocks.toLongArray());
            }
            ListTag boxes = new ListTag();
            for (BoundingBox box : constructions.getOrDefault(heart, List.of())) {
                boxes.add(new IntArrayTag(new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                        box.maxZ()}));
            }
            village.put("Constructions", boxes);
            villages.add(village);
        }
        tag.put("Villages", villages);
        return tag;
    }

    private List<Long> villageKeys() {
        List<Long> keys = new ArrayList<>(playerBlocks.keySet());
        constructions.keySet().stream().filter(key -> !playerBlocks.containsKey(key)).forEach(keys::add);
        return keys;
    }

    private static VillageRepairData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageRepairData data = new VillageRepairData();
        ListTag villages = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villages.size(); i++) {
            CompoundTag village = villages.getCompound(i);
            long heart = village.getLong("Heart");
            long[] blocks = village.getLongArray("PlayerBlocks");
            if (blocks.length > 0) {
                data.playerBlocks.put(heart, new LongOpenHashSet(blocks));
            }
            ListTag boxes = village.getList("Constructions", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < boxes.size(); j++) {
                int[] box = boxes.getIntArray(j);
                if (box.length == 6) {
                    data.constructions.computeIfAbsent(heart, key -> new ArrayList<>())
                            .add(new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
                }
            }
        }
        return data;
    }
}
