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
import java.util.List;
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
     * at most: the first job sign of the scan order. A job sign in no building, or in a building with no home bed (for
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
        List<BoundingBox> pieces = new ArrayList<>();
        for (VillageReference.Piece piece : reference.pieces()) {
            if (!piece.template().getPath().endsWith(GARRISON_SUFFIX)) {
                level.getStructureManager().get(piece.template()).ifPresent(template -> pieces.add(
                        GhostPlacement.box(template.getSize(), piece.rotation(), Mirror.NONE, piece.origin())));
            }
        }
        List<BoundingBox> garrison = garrisonBuildings(level, reference);
        VillageRepairData repairs = VillageRepairData.get(level);
        List<BoundingBox> homes = new ArrayList<>();
        List<JobPosts.Post> posts = new ArrayList<>();
        for (VillageLayout.JobSign sign : signs) {
            Optional<BoundingBox> building = pieces.stream().filter(box -> box.isInside(sign.pos())).findFirst()
                    .or(() -> repairs.constructionAt(heart, sign.pos()));
            if (building.isEmpty() || homes.contains(building.get())) {
                continue;
            }
            BoundingBox box = building.get();
            int homeBeds = (int) beds.stream().filter(box::isInside)
                    .filter(bed -> garrison.stream().noneMatch(other -> other.isInside(bed))).count();
            if (homeBeds > 0) {
                homes.add(box);
                posts.add(new JobPosts.Post(sign.jobId(), sign.pos().getX(), sign.pos().getY(), sign.pos().getZ(),
                        homeBeds));
            }
        }
        return posts;
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
