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
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Important positions of a village, found once with a scan of the blocks around the heart:
 * <ul>
 *   <li><b>posts</b>: the position above each watch post block (the archer stands there), with its view score
 *       (see {@link #viewScore});</li>
 *   <li><b>gates</b>: the fence gates;</li>
 *   <li><b>braziers</b>: the campfires;</li>
 *   <li><b>banners</b>: the banners (standing and on walls). The heart gives them the banner of the faction;</li>
 *   <li><b>bank chests</b>: the chests of the village bank, in the scan order;</li>
 *   <li><b>bells</b>: the alarm bells. The heart rings them in an alert.</li>
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
    static final int VERSION = 5;
    /** Number of horizontal directions of the view score. */
    public static final int VIEW_DIRECTIONS = 16;
    /** A post with a lower view score is not valid: no archer goes there. */
    public static final int MIN_VIEW = 4;
    /** Length of a line of sight of the view score, in blocks. */
    private static final double VIEW_DISTANCE = 20.0;
    /** The lines of sight go down by this value for each block, to see the ground outside the walls. */
    private static final double VIEW_SLOPE = 0.15;
    private static final double EYE_HEIGHT = 1.6;

    private boolean scanned;
    private final List<BlockPos> posts = new ArrayList<>();
    private final List<Integer> views = new ArrayList<>();
    private final List<BlockPos> gates = new ArrayList<>();
    private final List<BlockPos> braziers = new ArrayList<>();
    private final List<BlockPos> banners = new ArrayList<>();
    private final List<BlockPos> bankChests = new ArrayList<>();
    private final List<BlockPos> bells = new ArrayList<>();

    public boolean isScanned() {
        return scanned;
    }

    public List<BlockPos> posts() {
        return Collections.unmodifiableList(posts);
    }

    /** The view score of each post, in the order of {@link #posts()}. */
    public List<Integer> views() {
        return Collections.unmodifiableList(views);
    }

    /** The posts with a view score of {@link #MIN_VIEW} or more. */
    public List<BlockPos> validPosts() {
        List<BlockPos> valid = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            if (i < views.size() && views.get(i) >= MIN_VIEW) {
                valid.add(posts.get(i));
            }
        }
        return valid;
    }

    /**
     * The view score of a post: the number of open lines of sight from the eyes of an archer, in
     * {@link #VIEW_DIRECTIONS} horizontal directions. A line of sight is open when no block with a collision shape
     * is on its first {@link #VIEW_DISTANCE} blocks. A post where an archer cannot stand (a solid block at the feet
     * or at the head) has a score of 0. A block in a chunk that is not loaded does not close a line of sight.
     *
     * @param level the level
     * @param stand the position where the archer stands
     * @return the score, from 0 to {@link #VIEW_DIRECTIONS}
     */
    public static int viewScore(Level level, BlockPos stand) {
        if (!isFree(level, stand) || !isFree(level, stand.above())) {
            return 0;
        }
        Vec3 eye = Vec3.atBottomCenterOf(stand).add(0.0, EYE_HEIGHT, 0.0);
        int open = 0;
        for (int i = 0; i < VIEW_DIRECTIONS; i++) {
            double angle = i * 2.0 * Math.PI / VIEW_DIRECTIONS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean clear = true;
            for (double distance = 1.5; distance <= VIEW_DISTANCE && clear; distance += 0.5) {
                BlockPos pos = BlockPos.containing(eye.x + dx * distance, eye.y - distance * VIEW_SLOPE,
                        eye.z + dz * distance);
                clear = !level.isLoaded(pos) || isFree(level, pos);
            }
            if (clear) {
                open++;
            }
        }
        return open;
    }

    private static boolean isFree(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
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

    public List<BlockPos> bells() {
        return Collections.unmodifiableList(bells);
    }

    public List<BlockPos> bankChests() {
        return Collections.unmodifiableList(bankChests);
    }

    /**
     * Scans the blocks around the heart and replaces the lists.
     *
     * @param level    the level
     * @param heartPos the position of the heart
     */
    public void scan(Level level, BlockPos heartPos) {
        posts.clear();
        views.clear();
        gates.clear();
        braziers.clear();
        banners.clear();
        bankChests.clear();
        bells.clear();
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
            } else if (state.is(ModBlocks.BANK_CHEST.get())) {
                bankChests.add(pos.immutable());
            } else if (state.getBlock() instanceof BellBlock) {
                bells.add(pos.immutable());
            }
        }
        for (BlockPos post : posts) {
            views.add(viewScore(level, post));
        }
        scanned = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Scanned", scanned);
        tag.putInt("Version", VERSION);
        tag.put("Posts", savePositions(posts));
        tag.putIntArray("Views", views.stream().mapToInt(Integer::intValue).toArray());
        tag.put("Gates", savePositions(gates));
        tag.put("Braziers", savePositions(braziers));
        tag.put("Banners", savePositions(banners));
        tag.put("BankChests", savePositions(bankChests));
        tag.put("Bells", savePositions(bells));
        return tag;
    }

    public void load(CompoundTag tag) {
        scanned = tag.getBoolean("Scanned") && tag.getInt("Version") == VERSION;
        loadPositions(tag, "Posts", posts);
        views.clear();
        for (int view : tag.getIntArray("Views")) {
            views.add(view);
        }
        loadPositions(tag, "Gates", gates);
        loadPositions(tag, "Braziers", braziers);
        loadPositions(tag, "Banners", banners);
        loadPositions(tag, "BankChests", bankChests);
        loadPositions(tag, "Bells", bells);
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
