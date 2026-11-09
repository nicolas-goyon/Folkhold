package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlock;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The beds of the homes of a village: they give the places of the inhabitants (see {@link VillagePlaces}).
 *
 * <ul>
 *   <li>The scan of the heart finds the beds of the village (see {@link VillageLayout#beds()}): the beds of the
 *       structure and the beds that a player places.</li>
 *   <li>The beds of a garrison building are for the units of the garrison: they give no place. A garrison building is
 *       a template piece of the village structure with a template name that ends with {@link #GARRISON_SUFFIX}.</li>
 *   <li>The builders and the handymen have places in the hall of the village (see {@link #staff()}).</li>
 *   <li>A home is a building with 1 home bed or more: a template piece of the village structure, or the box of a
 *       construction site of the builders. A job sign in a home makes a job post (see {@link #posts}).</li>
 *   <li>A bed or a job sign that a player places or breaks in a village starts a new scan of the village.</li>
 * </ul>
 */
public final class VillageBeds {
    /** The name of the template of a garrison building ends with this text. */
    public static final String GARRISON_SUFFIX = "barracks";

    private VillageBeds() {
    }

    /**
     * The number of beds of the homes of a village: the beds that are not in a garrison building.
     *
     * @param level     the level
     * @param beds      the head part of each bed of the village
     * @param reference the reference of the village structure, or null for a village with no structure
     * @return the number of beds
     */
    public static int homeBeds(ServerLevel level, List<BlockPos> beds, @Nullable VillageReference reference) {
        List<BoundingBox> garrison = reference == null ? List.of() : garrisonBuildings(level, reference);
        return (int) beds.stream().filter(bed -> garrison.stream().noneMatch(box -> box.isInside(bed))).count();
    }

    /**
     * The boxes of the garrison buildings of a village structure.
     *
     * @param level     the level
     * @param reference the reference of the village structure
     * @return the boxes
     */
    public static List<BoundingBox> garrisonBuildings(ServerLevel level, VillageReference reference) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (VillageReference.Piece piece : reference.pieces()) {
            if (piece.template().getPath().endsWith(GARRISON_SUFFIX)) {
                level.getStructureManager().get(piece.template()).ifPresent(template -> boxes.add(
                        GhostPlacement.box(template.getSize(), piece.rotation(), Mirror.NONE, piece.origin())));
            }
        }
        return boxes;
    }

    /**
     * The job posts of a village (see {@link JobPosts}), in the scan order. The building of a job sign is the template
     * piece of the village structure that holds the sign (not a garrison building), else the box of a construction
     * site of the builders that holds it. The building is a home when it holds 1 home bed or more. A home has 1 post
     * at most: the first job sign of the scan order with a job, else its first job sign with no job. A job sign with
     * no job in a home is a post with an empty job id: the heart chooses its job (see
     * {@link VillageHeartBlockEntity#renewJobPosts}). A job sign in no building, or in a building with no home bed (for
     * example a forge), is not a post.
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param signs     the job signs of the village, in the scan order
     * @param beds      the head part of each bed of the village
     * @param reference the reference of the village structure
     * @return the posts
     */
    public static List<JobPosts.Post> posts(ServerLevel level, BlockPos heart, List<VillageLayout.JobSign> signs,
            List<BlockPos> beds, VillageReference reference) {
        return homes(level, heart, signs, beds, reference).posts();
    }

    /**
     * The homes of a village: its job posts (see {@link #posts}), the home beds of each post, all its home beds, and
     * the number of homes with no post.
     *
     * @param posts    the posts, in the scan order
     * @param postBeds the home beds of the home of each post, by the position of its job sign
     * @param beds     the head part of each home bed of the village, in the scan order: the beds of the homes with
     *                 a post and with no post, and the beds that are not in a building
     * @param cottages the number of homes with no post: the buildings (pieces of the village structure or boxes of the
     *                 construction sites) with 1 home bed or more and no post. A bed that is not in a building does not
     *                 count
     */
    public record Homes(List<JobPosts.Post> posts, Map<BlockPos, List<BlockPos>> postBeds, List<BlockPos> beds,
            int cottages) {
        /** No homes: a village with no structure. */
        public static final Homes NONE = new Homes(List.of(), Map.of(), List.of(), 0);

        /**
         * The home beds of the home of a post.
         *
         * @param post the post
         * @return the beds, or an empty list for a post that is not in these homes
         */
        public List<BlockPos> bedsOf(JobPosts.Post post) {
            return postBeds.getOrDefault(new BlockPos(post.x(), post.y(), post.z()), List.of());
        }
    }

    /**
     * The homes of a village (see {@link #posts} for the rules of the posts).
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param signs     the job signs of the village, in the scan order
     * @param beds      the head part of each bed of the village
     * @param reference the reference of the village structure
     * @return the homes
     */
    public static Homes homes(ServerLevel level, BlockPos heart, List<VillageLayout.JobSign> signs,
            List<BlockPos> beds, VillageReference reference) {
        List<BoundingBox> pieces = new ArrayList<>();
        for (VillageReference.Piece piece : reference.pieces()) {
            if (!piece.template().getPath().endsWith(GARRISON_SUFFIX)) {
                level.getStructureManager().get(piece.template()).ifPresent(template -> pieces.add(
                        GhostPlacement.box(template.getSize(), piece.rotation(), Mirror.NONE, piece.origin())));
            }
        }
        List<BoundingBox> garrison = garrisonBuildings(level, reference);
        List<BlockPos> homeBeds = beds.stream()
                .filter(bed -> garrison.stream().noneMatch(other -> other.isInside(bed))).toList();
        VillageRepairData repairs = VillageRepairData.get(level);
        List<BoundingBox> homes = new ArrayList<>();
        List<JobPosts.Post> posts = new ArrayList<>();
        Map<BlockPos, List<BlockPos>> postBeds = new HashMap<>();
        List<VillageLayout.JobSign> withJobFirst = new ArrayList<>(signs);
        withJobFirst.sort(Comparator.comparing(sign -> sign.jobId().isEmpty()));
        for (VillageLayout.JobSign sign : withJobFirst) {
            Optional<BoundingBox> building = pieces.stream().filter(box -> box.isInside(sign.pos())).findFirst()
                    .or(() -> repairs.constructionAt(heart, sign.pos()));
            if (building.isEmpty() || homes.contains(building.get())) {
                continue;
            }
            BoundingBox box = building.get();
            List<BlockPos> inHome = homeBeds.stream().filter(box::isInside).toList();
            if (!inHome.isEmpty()) {
                homes.add(box);
                posts.add(new JobPosts.Post(sign.jobId(), sign.pos().getX(), sign.pos().getY(), sign.pos().getZ(),
                        inHome.size()));
                postBeds.put(sign.pos().immutable(), inHome);
            }
        }
        posts.sort(Comparator.comparingInt(post -> signIndex(signs, post)));
        int cottages = (int) homeBeds.stream()
                .filter(bed -> homes.stream().noneMatch(box -> box.isInside(bed)))
                .map(bed -> pieces.stream().filter(box -> box.isInside(bed)).findFirst()
                        .or(() -> repairs.constructionAt(heart, bed)))
                .flatMap(Optional::stream)
                .distinct()
                .count();
        return new Homes(List.copyOf(posts), Map.copyOf(postBeds), homeBeds, cottages);
    }

    private static int signIndex(List<VillageLayout.JobSign> signs, JobPosts.Post post) {
        for (int i = 0; i < signs.size(); i++) {
            BlockPos pos = signs.get(i).pos();
            if (pos.getX() == post.x() && pos.getY() == post.y() && pos.getZ() == post.z()) {
                return i;
            }
        }
        return signs.size();
    }

    /** The number of builders and handymen of a village: they have places in the hall. */
    public static int staff() {
        return WorldConfig.BUILDERS.get() + WorldConfig.HANDYMEN.get();
    }

    /**
     * The places of a settlement (see {@link VillagePlaces#places}).
     *
     * @param settlement the settlement
     * @return the number of places, or {@link VillagePlaces#UNKNOWN} before the first count of the beds
     */
    public static int places(Settlement settlement) {
        return VillagePlaces.places(settlement.beds(), staff());
    }

    /** A player places a bed or a job sign: the village of the block scans its blocks again. */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            requestScan(level, event.getPos(), event.getPlacedBlock());
        }
    }

    /** A player breaks a bed or a job sign: the village of the block scans its blocks again. */
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            requestScan(level, event.getPos(), event.getState());
        }
    }

    private static void requestScan(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock) && !(state.getBlock() instanceof JobSignBlock)) {
            return;
        }
        VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get())
                .filter(heart -> ReadyChunks.isReady(level, heart))
                .ifPresent(heart -> {
                    if (level.getBlockEntity(heart) instanceof VillageHeartBlockEntity village
                            && village.scanArea(level).isInside(pos)) {
                        village.requestScan();
                    }
                });
    }
}
