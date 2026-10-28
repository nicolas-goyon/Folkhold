package io.github.nicolasgoyon.folkhold.world.village;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
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
 *   <li>The surface blocks of the streets (see {@link StreetBlocks}): the heart records them once.</li>
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
    /** The surface blocks of the streets, for each village heart. */
    private final Map<Long, Map<BlockPos, BlockState>> streets = new HashMap<>();

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

    /**
     * The surface blocks of the streets of a village.
     *
     * @param heart the position of the village heart
     * @return the blocks, or empty before the record of the streets
     */
    public Optional<Map<BlockPos, BlockState>> streets(BlockPos heart) {
        return Optional.ofNullable(streets.get(heart.asLong()));
    }

    /** Records the surface blocks of the streets of a village. */
    public void setStreets(BlockPos heart, Map<BlockPos, BlockState> blocks) {
        streets.put(heart.asLong(), Map.copyOf(blocks));
        setDirty();
    }

    /** Forgets the data of a village: the village has no heart any more. */
    public void forget(BlockPos heart) {
        boolean changed = playerBlocks.remove(heart.asLong()) != null;
        changed |= constructions.remove(heart.asLong()) != null;
        changed |= streets.remove(heart.asLong()) != null;
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
            Map<BlockPos, BlockState> streetBlocks = streets.get(heart);
            if (streetBlocks != null) {
                ListTag streetList = new ListTag();
                streetBlocks.forEach((pos, state) -> {
                    CompoundTag street = new CompoundTag();
                    street.putLong("Pos", pos.asLong());
                    street.put("State", NbtUtils.writeBlockState(state));
                    streetList.add(street);
                });
                village.put("Streets", streetList);
            }
            villages.add(village);
        }
        tag.put("Villages", villages);
        return tag;
    }

    private List<Long> villageKeys() {
        Set<Long> keys = new LinkedHashSet<>(playerBlocks.keySet());
        keys.addAll(constructions.keySet());
        keys.addAll(streets.keySet());
        return new ArrayList<>(keys);
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
            if (village.contains("Streets", Tag.TAG_LIST)) {
                Map<BlockPos, BlockState> streetBlocks = new HashMap<>();
                ListTag streetList = village.getList("Streets", Tag.TAG_COMPOUND);
                for (int j = 0; j < streetList.size(); j++) {
                    CompoundTag street = streetList.getCompound(j);
                    streetBlocks.put(BlockPos.of(street.getLong("Pos")), NbtUtils.readBlockState(
                            registries.lookupOrThrow(Registries.BLOCK), street.getCompound("State")));
                }
                data.streets.put(heart, streetBlocks);
            }
        }
        return data;
    }
}
