package io.github.nicolasgoyon.folkhold.world.village.schedule;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DayPhase;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHomes;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The places of the daily schedule of the workers of one village (see {@link WorkerBrains}). The heart keeps one
 * instance. It is not saved.
 *
 * <ul>
 *   <li>The job site (memory {@code JOB_SITE}) of a worker is the job sign of the home of its bed: the household of a
 *       home does the job of its post (see {@link VillageHomes}). A worker with no bed of a post (for example a worker
 *       of a legacy job slot) has no job sign, thus no activity {@code WORK}: it goes to the market and to its
 *       bed.</li>
 *   <li>The meeting point (memory {@code MEETING_POINT}) of the workers is the market sign of the village. A village
 *       with no market trades at its plaza: the meeting point is the bell of the plaza (see
 *       {@link VillageMarket#tradePlace}).</li>
 *   <li>The places come from the posts, the beds and the trade place of the village. The heart calculates them again
 *       only when the phase of the village, the posts, the places of the inhabitants or the scan change. At each check
 *       of the heart, the loaded workers get the places with no new search.</li>
 *   <li>The vanilla brain erases a place when the walk to it takes too long (see {@link #setPlace}). The next check
 *       of the heart gives the place again, and erases the time of the walk that failed: the worker walks to the place
 *       again.</li>
 *   <li>A sign in a point of interest section with no record for the sign gets its record from the calculation,
 *       because the vanilla brain erases a memory with no point of interest.</li>
 *   <li>A place of the cache with no sign or bell block at the place (a broken sign before the next scan) does not
 *       go to the workers.</li>
 *   <li>At each check, each loaded inhabitant loses a job sign as its job site when this sign is not the sign of its
 *       home (see {@link WorkerBrains#forgetJobSign}): an inhabitant with no job, a builder, a handyman, or a worker
 *       of a post that is not in the posts of the village.</li>
 *   <li>At each check, each loaded inhabitant releases a vanilla workstation that it holds (see
 *       {@link WorkerBrains#releaseWorkstation}).</li>
 * </ul>
 */
public final class VillageWorkers {
    /** The inputs of the last calculation, or null before the first calculation. */
    @Nullable
    private Key key;
    /** The job sign of each home bed of a post. */
    private Map<BlockPos, GlobalPos> jobSigns = Map.of();
    /** The meeting point of the workers, or null. */
    @Nullable
    private GlobalPos meetingPoint;
    /** The block of the meeting point: a market sign or a bell. */
    private Block meetingBlock = Blocks.BELL;
    /** False when a place was in a chunk that was not loaded: the next check calculates the places again. */
    private boolean complete;
    /** The number of calculations of the places. For the tests. */
    private int calculations;

    /**
     * Gives the schedule and the places of the schedule to the loaded workers of a village.
     *
     * @param level       the level
     * @param heart       the village heart
     * @param inhabitants the loaded inhabitants of the village
     * @param seats       the places of the inhabitants (see {@link VillageFolk#seats})
     * @param beds        the bed of each place, or null (see {@link VillageHomes#beds})
     * @param posts       the posts of the village, or an empty list
     * @return the number of changes in the brains of the workers
     */
    public int update(ServerLevel level, VillageHeartBlockEntity heart, List<Villager> inhabitants,
            List<VillageFolk.Seat> seats, List<BlockPos> beds, List<JobPosts.Post> posts) {
        Key inputs = new Key(VillageSchedule.phase(level), List.copyOf(posts), List.copyOf(seats),
                Collections.unmodifiableList(new ArrayList<>(beds)), List.copyOf(heart.getLayout().markets()),
                List.copyOf(heart.getLayout().bells()));
        if (!inputs.equals(key) || !complete) {
            key = inputs;
            calculate(level, heart, seats, beds, posts);
        }
        int changes = 0;
        Map<BlockPos, Boolean> present = new HashMap<>();
        for (Villager villager : inhabitants) {
            if (WorkerBrains.releaseWorkstation(villager, villager.getBrain())) {
                changes++;
            }
            if (WorkerBrains.isWorker(villager)) {
                changes += apply(level, villager, present);
            } else if (WorkerBrains.forgetJobSign(villager, villager.getBrain(), null)) {
                changes++;
            }
        }
        return changes;
    }

    /**
     * The meeting point of the workers, when its block stands at its position: the market sign or the bell. A position
     * in a chunk that is not loaded counts as present.
     *
     * @param level the level of the village
     * @return the meeting point, or empty with no meeting point, before the first calculation, or with no sign or bell
     *         block at the position (a broken market sign before the next scan)
     */
    public Optional<GlobalPos> validMeetingPoint(ServerLevel level) {
        GlobalPos point = meetingPoint;
        if (point == null || !point.dimension().equals(level.dimension())
                || !holds(level, point.pos(), meetingBlock, new HashMap<>())) {
            return Optional.empty();
        }
        return Optional.of(point);
    }

    /** The number of calculations of the places since the creation of this instance. */
    public int calculations() {
        return calculations;
    }

    /**
     * The workers of a village hear the alarm bells: the vanilla brain stops the schedule and the workers hide
     * (activity {@code HIDE}). A worker far from the bells of the village also hears them.
     *
     * @param level       the level
     * @param inhabitants the loaded inhabitants of the village
     */
    public static void hearAlarm(ServerLevel level, List<Villager> inhabitants) {
        for (Villager villager : inhabitants) {
            if (WorkerBrains.isWorker(villager)) {
                villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
            }
        }
    }

    private void calculate(ServerLevel level, VillageHeartBlockEntity heart, List<VillageFolk.Seat> seats,
            List<BlockPos> beds, List<JobPosts.Post> posts) {
        calculations++;
        complete = true;
        Map<BlockPos, GlobalPos> signs = new HashMap<>();
        for (int i = 0; i < seats.size() && i < beds.size(); i++) {
            int post = seats.get(i).post();
            BlockPos bed = beds.get(i);
            if (bed == null || post < 0 || post >= posts.size()) {
                continue;
            }
            JobPosts.Post found = posts.get(post);
            BlockPos sign = new BlockPos(found.x(), found.y(), found.z());
            if (ensurePoi(level, sign, ModPoiTypes.JOB_SIGN_KEY, ModPoiTypes.JOB_SIGN, ModBlocks.JOB_SIGN.get())) {
                signs.put(bed, GlobalPos.of(level.dimension(), sign));
            }
        }
        jobSigns = Map.copyOf(signs);
        meetingPoint = meetingPoint(level, heart).map(point -> GlobalPos.of(level.dimension(), point)).orElse(null);
        FolkholdWorld.LOGGER.debug("Village {}: the places of the workers for the phase {}: {} beds with a job sign, "
                + "meeting point {}, complete {}", heart.getBlockPos(), key == null ? null : key.phase(),
                jobSigns.size(), meetingPoint, complete);
    }

    /** The market sign of the village, or the bell of its plaza (the bell nearest to the heart with no plaza bell). */
    private Optional<BlockPos> meetingPoint(ServerLevel level, VillageHeartBlockEntity heart) {
        VillageMarket.TradePlace place = heart.tradePlace(level);
        meetingBlock = place.market() ? ModBlocks.buildingSign(BuildingSignKind.MARKET) : Blocks.BELL;
        if (place.market()) {
            return ensurePoi(level, place.point(), ModPoiTypes.MARKET_SIGN_KEY, ModPoiTypes.MARKET_SIGN,
                    ModBlocks.buildingSign(BuildingSignKind.MARKET)) ? Optional.of(place.point()) : Optional.empty();
        }
        List<BlockPos> bells = heart.getLayout().bells();
        Optional<BlockPos> bell = bells.stream().filter(place.area()::isInside).findFirst()
                .or(() -> bells.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(heart.getBlockPos()))));
        if (bell.isPresent() && !level.isLoaded(bell.get())) {
            complete = false;
            return Optional.empty();
        }
        return bell.filter(pos -> level.getPoiManager().exists(pos, type -> type.is(PoiTypes.MEETING)));
    }

    /**
     * True if a point of interest of a type is at a position. A block of the type with no point of interest gets one.
     * A position in a chunk that is not loaded makes the calculation incomplete.
     */
    private boolean ensurePoi(ServerLevel level, BlockPos pos, ResourceKey<PoiType> key, Holder<PoiType> type,
            Block block) {
        if (!level.isLoaded(pos)) {
            complete = false;
            return false;
        }
        PoiManager poi = level.getPoiManager();
        if (poi.exists(pos, holder -> holder.is(key))) {
            return true;
        }
        if (!level.getBlockState(pos).is(block)) {
            return false;
        }
        poi.add(pos, type);
        return true;
    }

    /**
     * Gives the schedule and the places to one worker.
     *
     * @param present the blocks of the places that this check found, to read each block one time
     */
    private int apply(ServerLevel level, Villager villager, Map<BlockPos, Boolean> present) {
        int changes = 0;
        Brain<Villager> brain = villager.getBrain();
        Schedule schedule = WorkerBrains.scheduleOf(villager);
        if (brain.getSchedule() != schedule) {
            WorkerBrains.setSchedule(villager, brain, schedule);
            changes++;
        }
        GlobalPos sign = VillageHomes.home(level, villager).map(jobSigns::get)
                .filter(pos -> holds(level, pos.pos(), ModBlocks.JOB_SIGN.get(), present)).orElse(null);
        if (WorkerBrains.forgetJobSign(villager, brain, sign)) {
            changes++;
        }
        if (sign != null && brain.getMemory(MemoryModuleType.JOB_SITE).filter(sign::equals).isEmpty()) {
            setPlace(brain, MemoryModuleType.JOB_SITE, sign);
            changes++;
        }
        if (meetingPoint != null && holds(level, meetingPoint.pos(), meetingBlock, present)
                && brain.getMemory(MemoryModuleType.MEETING_POINT).filter(meetingPoint::equals).isEmpty()) {
            setPlace(brain, MemoryModuleType.MEETING_POINT, meetingPoint);
            changes++;
        }
        return changes;
    }

    /**
     * Gives a place to a worker and erases the memory {@code CANT_REACH_WALK_TARGET_SINCE}. The path to a sign never
     * ends on the sign block, so the vanilla brain keeps this memory during the walk to a sign, and also after the
     * walk. When this memory is older than a limit and the worker has no walk target, the vanilla behavior
     * {@code SetWalkTargetFromBlockMemory} erases the place (200 ticks for {@code MEETING_POINT}, 1200 ticks for
     * {@code JOB_SITE}). With the old memory, the brain erases the place again at once each time that the heart gives
     * it, and the worker stays at its position for all the phase. With no memory, the worker walks to the place again.
     */
    private static void setPlace(Brain<Villager> brain, MemoryModuleType<GlobalPos> memory, GlobalPos place) {
        brain.setMemory(memory, place);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    /**
     * True if the block of a place is still at its position. A place in a chunk that is not loaded counts as present:
     * the check does not load a chunk.
     */
    private static boolean holds(ServerLevel level, BlockPos pos, Block block, Map<BlockPos, Boolean> present) {
        return present.computeIfAbsent(pos, at -> !level.isLoaded(at) || level.getBlockState(at).is(block));
    }

    /** The inputs of the places. */
    private record Key(DayPhase phase, List<JobPosts.Post> posts, List<VillageFolk.Seat> seats, List<BlockPos> beds,
            List<BlockPos> markets, List<BlockPos> bells) {
    }
}
