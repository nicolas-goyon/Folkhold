package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Important positions of a village, found once with a scan of the blocks around the heart:
 * <ul>
 *   <li><b>posts</b>: the position above each watch post block (the archer stands there);</li>
 *   <li><b>gates</b>: the fence gates;</li>
 *   <li><b>braziers</b>: the campfires;</li>
 *   <li><b>banners</b>: the banners (standing and on walls). The heart gives them the banner of the faction.</li>
 * </ul>
 *
 * <p>A saved layout with an other {@link #VERSION} is not valid: the heart scans the village again.
 */
public final class VillageLayout {
    /** Horizontal scan radius around the heart, in blocks. The village structure is 39 × 39 blocks. */
    static final int SCAN_RADIUS = 24;
    private static final int SCAN_BELOW = 6;
    private static final int SCAN_ABOVE = 14;
    /** Version of the scan. Increase it when the scan finds new kinds of positions. */
    static final int VERSION = 2;

    private boolean scanned;
    private final List<BlockPos> posts = new ArrayList<>();
    private final List<BlockPos> gates = new ArrayList<>();
    private final List<BlockPos> braziers = new ArrayList<>();
    private final List<BlockPos> banners = new ArrayList<>();

    public boolean isScanned() {
        return scanned;
    }

    public List<BlockPos> posts() {
        return Collections.unmodifiableList(posts);
    }

    public List<BlockPos> gates() {
        return Collections.unmodifiableList(gates);
    }

    public List<BlockPos> braziers() {
        return Collections.unmodifiableList(braziers);
    }

    public List<BlockPos> banners() {
        return Collections.unmodifiableList(banners);
    }

    /**
     * Scans the blocks around the heart and replaces the lists.
     *
     * @param level    the level
     * @param heartPos the position of the heart
     */
    public void scan(Level level, BlockPos heartPos) {
        posts.clear();
        gates.clear();
        braziers.clear();
        banners.clear();
        BlockPos min = heartPos.offset(-SCAN_RADIUS, -SCAN_BELOW, -SCAN_RADIUS);
        BlockPos max = heartPos.offset(SCAN_RADIUS, SCAN_ABOVE, SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.WATCH_POST.get())) {
                posts.add(pos.above().immutable());
            } else if (state.getBlock() instanceof FenceGateBlock) {
                gates.add(pos.immutable());
            } else if (state.getBlock() instanceof CampfireBlock) {
                braziers.add(pos.immutable());
            } else if (state.getBlock() instanceof AbstractBannerBlock) {
                banners.add(pos.immutable());
            }
        }
        scanned = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Scanned", scanned);
        tag.putInt("Version", VERSION);
        tag.put("Posts", savePositions(posts));
        tag.put("Gates", savePositions(gates));
        tag.put("Braziers", savePositions(braziers));
        tag.put("Banners", savePositions(banners));
        return tag;
    }

    public void load(CompoundTag tag) {
        scanned = tag.getBoolean("Scanned") && tag.getInt("Version") == VERSION;
        loadPositions(tag, "Posts", posts);
        loadPositions(tag, "Gates", gates);
        loadPositions(tag, "Braziers", braziers);
        loadPositions(tag, "Banners", banners);
    }

    private static ListTag savePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        return list;
    }

    private static void loadPositions(CompoundTag tag, String key, List<BlockPos> target) {
        target.clear();
        ListTag list = tag.getList(key, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            int[] xyz = list.getIntArray(i);
            if (xyz.length == 3) {
                target.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
        }
    }
}
