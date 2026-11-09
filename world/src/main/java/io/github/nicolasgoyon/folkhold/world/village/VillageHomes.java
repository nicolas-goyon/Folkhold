package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * The beds of the inhabitants: the household of a home sleeps in the home. The vanilla memory {@code HOME} of a
 * villager is the bed where it sleeps, and the bed is a point of interest with 1 ticket.
 *
 * <ul>
 *   <li>Each place of an inhabitant of the homes (see {@link VillageFolk#seats}) gets a home bed. A place of a post
 *       gets a bed of the home of the post. A place with no post gets a bed of the other homes: the homes with no post,
 *       the homes of the posts that are not valid, and the beds that are not in a building.</li>
 *   <li>A place with no free bed (more inhabitants than beds) gets no bed. The builders, the handymen and the
 *       inhabitants of a village with no list of posts get no bed: the vanilla rules apply to them.</li>
 *   <li>The villagers keep the beds of their places when they can: a villager with a bed of a place of its profession
 *       keeps this bed. The other places go to the other villagers of their profession: first the beds with a free
 *       ticket, then the beds that a villager holds (for example a villager that is not loaded).</li>
 *   <li>A villager that holds the bed of an other villager (an inhabitant with no place, a vanilla villager that
 *       claims a free bed) loses this bed. A bed with no villager of its place stays with its holder.</li>
 *   <li>The memory {@code HOME} stays in the save of the villager: it is the persistent home of the villager.</li>
 * </ul>
 */
public final class VillageHomes {
    private VillageHomes() {
    }

    /**
     * The bed of each place.
     *
     * @param seats the places of the inhabitants
     * @param posts the posts of the settlement
     * @param homes the homes of the village
     * @return the bed of each place, or null for a place with no bed
     */
    public static List<BlockPos> beds(List<VillageFolk.Seat> seats, List<JobPosts.Post> posts,
            VillageBeds.Homes homes) {
        Map<Integer, Deque<BlockPos>> postBeds = new LinkedHashMap<>();
        Set<BlockPos> taken = new HashSet<>();
        for (VillageFolk.Seat seat : seats) {
            if (seat.post() >= 0 && seat.post() < posts.size() && !postBeds.containsKey(seat.post())) {
                List<BlockPos> beds = homes.bedsOf(posts.get(seat.post()));
                postBeds.put(seat.post(), new ArrayDeque<>(beds));
                taken.addAll(beds);
            }
        }
        Deque<BlockPos> otherBeds = new ArrayDeque<>(homes.beds().stream().filter(bed -> !taken.contains(bed))
                .toList());
        List<BlockPos> result = new ArrayList<>(seats.size());
        for (VillageFolk.Seat seat : seats) {
            Deque<BlockPos> free = seat.post() == JobPosts.NO_POST ? otherBeds : postBeds.get(seat.post());
            result.add(free == null ? null : free.poll());
        }
        return result;
    }

    /**
     * Gives the beds of the places to the inhabitants (see the rules of the class).
     *
     * @param level       the level
     * @param heart       the position of the village heart
     * @param inhabitants the loaded inhabitants of the village, with their professions
     * @param seats       the places of the inhabitants
     * @param beds        the bed of each place (see {@link #beds})
     * @return the number of villagers with a new bed
     */
    public static int claim(ServerLevel level, BlockPos heart, List<Villager> inhabitants,
            List<VillageFolk.Seat> seats, List<BlockPos> beds) {
        Map<BlockPos, VillagerProfession> bedProfessions = new LinkedHashMap<>();
        for (int i = 0; i < seats.size() && i < beds.size(); i++) {
            if (beds.get(i) != null) {
                bedProfessions.put(beds.get(i), seats.get(i).profession());
            }
        }
        if (bedProfessions.isEmpty()) {
            return 0;
        }
        List<Villager> sorted = new ArrayList<>(inhabitants);
        sorted.sort(Comparator.comparing(Villager::getUUID));
        Map<BlockPos, Villager> owners = new LinkedHashMap<>();
        for (Villager villager : sorted) {
            home(level, villager).filter(bed -> bedProfessions.get(bed) == villager.getVillagerData().getProfession())
                    .filter(bed -> !owners.containsKey(bed)).ifPresent(bed -> owners.put(bed, villager));
        }
        List<BlockPos> freeFirst = new ArrayList<>(bedProfessions.keySet());
        freeFirst.sort(Comparator.comparing(bed -> !hasFreeTicket(level, bed)));
        for (BlockPos bed : freeFirst) {
            if (!owners.containsKey(bed)) {
                VillagerProfession profession = bedProfessions.get(bed);
                sorted.stream().filter(villager -> villager.getVillagerData().getProfession() == profession)
                        .filter(villager -> !owners.containsValue(villager)).findFirst()
                        .ifPresent(villager -> owners.put(bed, villager));
            }
        }
        // The holder of the bed of an other villager loses its memory only. The claim of the owner releases the
        // ticket of the bed before it takes it.
        for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHearts.area(heart))) {
            home(level, villager).filter(owners::containsKey).filter(bed -> owners.get(bed) != villager)
                    .ifPresent(bed -> villager.getBrain().eraseMemory(MemoryModuleType.HOME));
        }
        int claimed = 0;
        for (Map.Entry<BlockPos, Villager> entry : owners.entrySet()) {
            if (home(level, entry.getValue()).filter(entry.getKey()::equals).isEmpty()
                    && claim(level, entry.getValue(), entry.getKey())) {
                claimed++;
            }
        }
        return claimed;
    }

    /** The bed of a villager in this level, from its memory {@code HOME}. */
    public static Optional<BlockPos> home(ServerLevel level, Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension() == level.dimension()).map(GlobalPos::pos);
    }

    /** True if the bed has a free ticket: no villager holds it. */
    private static boolean hasFreeTicket(ServerLevel level, BlockPos bed) {
        return level.isLoaded(bed) && level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bed, 1,
                PoiManager.Occupancy.HAS_SPACE).anyMatch(record -> record.getPos().equals(bed));
    }

    /** Gives a bed to a villager: it releases its old bed and takes the ticket of the new bed. */
    private static boolean claim(ServerLevel level, Villager villager, BlockPos bed) {
        PoiManager poi = level.getPoiManager();
        if (!level.isLoaded(bed) || !poi.exists(bed, type -> type.is(PoiTypes.HOME))) {
            return false;
        }
        villager.releasePoi(MemoryModuleType.HOME);
        poi.release(bed);
        poi.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
        return true;
    }
}
