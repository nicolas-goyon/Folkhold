package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The villagers of a faction village: the inhabitants. They are vanilla villagers with a Folkhold profession or the
 * profession of a job, so the vanilla systems work with them (raids, bell, beds, iron golems, zombies).
 *
 * <ul>
 *   <li>Each villager records the position of the heart of its village ({@link Merchants#VILLAGE_TAG}).</li>
 *   <li>The number of inhabitants is the population of the settlement. The places of the
 *       village are the maximum: the beds of its homes and the places of the hall (see {@link VillageBeds}). The
 *       setting {@link WorldConfig#INHABITANT_LIMIT} can set a lower maximum.</li>
 *   <li>The first inhabitants are the builders and the handymen of the village ({@link WorldConfig#BUILDERS},
 *       {@link WorldConfig#HANDYMEN}): they work for the village, also in a small village. The builders build the
 *       construction sites of the village (see {@link BuilderSites}).</li>
 *   <li>The workers of the settlement show its active jobs: the profession of a worker is the profession of its job
 *       (see {@link EconomyData#profession(String)}). In a scanned village, the household of the home of a valid post
 *       does the job of the post and sleeps in this home (see {@link #seats} and {@link VillageHomes}). The posts
 *       with a food job that trade come first in the order of the posts, then the posts with an other job that trades,
 *       and the first valid post always has an inhabitant: thus a village with a valid post that trades has a trader.
 *       The inhabitants of the other homes have the inhabitant profession: they give life to the village.</li>
 *   <li>Only the workers of the active jobs trade, with the goods of their jobs (see {@link Merchants}). A player
 *       cannot trade with the other inhabitants. The vanilla trades of a profession do not apply: they would bypass
 *       the economy of the village.</li>
 *   <li>A villager with the profession {@link ModProfessions#MERCHANT} is an inhabitant: the next check of the heart
 *       gives it a profession of the settlement (see {@link #assign}).</li>
 *   <li>A villager has 1 experience point, so it keeps its profession with no job site block. A villager of a faction
 *       village does not take a vanilla workstation (see
 *       {@link io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains#withoutWorkstation}).</li>
 *   <li>The simulation sets the population: the villagers of a faction village do not breed, and a dead villager
 *       removes 1 from the population of the settlement.</li>
 * </ul>
 */
public final class VillageFolk {

    private VillageFolk() {
    }

    /** True if the villager belongs to a faction village. */
    public static boolean isVillageFolk(Villager villager) {
        return villager.getPersistentData().contains(Merchants.VILLAGE_TAG);
    }

    /** The loaded villagers of a village with a profession. */
    public static List<Villager> of(ServerLevel level, BlockPos heartPos, VillagerProfession profession) {
        return level.getEntitiesOfClass(Villager.class, VillageHearts.area(level, heartPos),
                villager -> villager.getVillagerData().getProfession() == profession
                        && villager.getPersistentData().getLong(Merchants.VILLAGE_TAG) == heartPos.asLong()
                        && isVillageFolk(villager));
    }

    /** The loaded inhabitants of a village: all its villagers. */
    public static List<Villager> inhabitants(ServerLevel level, BlockPos heartPos) {
        return level.getEntitiesOfClass(Villager.class, VillageHearts.area(level, heartPos),
                villager -> villager.getPersistentData().getLong(Merchants.VILLAGE_TAG) == heartPos.asLong()
                        && isVillageFolk(villager));
    }

    /**
     * The jobs that the inhabitants show, in proportion to the workers of each job. The jobs with the most workers
     * come first.
     *
     * @param jobs  the job of each worker of the settlement
     * @param count the number of inhabitants
     * @return {@code count} jobs, or no job if the settlement has no workers
     */
    public static List<String> pickJobs(List<String> jobs, int count) {
        if (jobs.isEmpty() || count <= 0) {
            return List.of();
        }
        Map<String, Integer> workers = new TreeMap<>();
        jobs.forEach(job -> workers.merge(job, 1, Integer::sum));
        List<String> sorted = new ArrayList<>();
        workers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.addAll(Collections.nCopies(entry.getValue(), entry.getKey())));
        List<String> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            picked.add(sorted.get((int) ((2L * i + 1) * sorted.size() / (2L * count))));
        }
        return picked;
    }

    /**
     * The profession that shows a job: the profession of the job file, or the inhabitant profession.
     *
     * @param jobId the job id
     * @return the profession
     */
    public static VillagerProfession professionOf(String jobId) {
        return EconomyData.profession(jobId)
                .flatMap(BuiltInRegistries.VILLAGER_PROFESSION::getOptional)
                .orElse(ModProfessions.INHABITANT.get());
    }

    /**
     * The place of an inhabitant in its village.
     *
     * @param profession the profession of the inhabitant
     * @param post       the index of the post of its home in the posts of the settlement, {@link JobPosts#NO_POST}
     *                   for an inhabitant of an other home, or {@link #NO_HOME} for an inhabitant with no home of the
     *                   heart (the builders and the handymen, the inhabitants of a village with no list of posts)
     */
    public record Seat(VillagerProfession profession, int post) {
    }

    /** The {@link Seat#post} of an inhabitant with no home of the heart: the heart does not give it a bed. */
    public static final int NO_HOME = -2;

    /**
     * The professions of the inhabitants of a settlement: one for each inhabitant (see {@link #seats}).
     *
     * @param settlement the settlement
     * @param count      the number of inhabitants
     * @return the professions
     */
    public static List<VillagerProfession> professions(Settlement settlement, int count) {
        return seats(settlement, count).stream().map(Seat::profession).toList();
    }

    /**
     * The places of the inhabitants of a settlement: one for each inhabitant, in this order.
     *
     * <ul>
     *   <li>A village with a list of posts (see {@link JobPosts#households}): first the first inhabitant of the
     *       homes, then the builders and the handymen, then the other inhabitants of the homes. The household of the
     *       home of a valid post does the job of the post: the first valid post comes first and trades when the village
     *       has a valid post that trades. The other inhabitants of the homes have no job. In a legacy village, its
     *       legacy job slots go to the inhabitants with no post, {@code workersPerJob} for each job slot (see
     *       {@link #pickJobs}).</li>
     *   <li>A village with no list of posts: see {@link #slotProfessions}. The heart gives no bed to these
     *       inhabitants.</li>
     * </ul>
     *
     * @param settlement the settlement
     * @param count      the number of inhabitants
     * @return the places
     */
    public static List<Seat> seats(Settlement settlement, int count) {
        if (settlement.jobPosts().isEmpty()) {
            return slotProfessions(settlement, count).stream().map(profession -> new Seat(profession, NO_HOME))
                    .toList();
        }
        List<JobPosts.Post> posts = settlement.jobPosts().get();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<Integer> households = JobPosts.households(posts, job -> Economies.isValidJob(server, settlement, job),
                Economies.homeInhabitants(settlement));
        List<VillagerProfession> homes = new ArrayList<>(households.size());
        households.forEach(post -> homes.add(post == JobPosts.NO_POST ? ModProfessions.INHABITANT.get()
                : professionOf(posts.get(post).jobId())));
        int free = (int) households.stream().filter(post -> post == JobPosts.NO_POST).count();
        if (settlement.legacyJobs() && free > 0) {
            List<String> jobs = Economies.activeJobIds(settlement);
            int postJobs = (int) households.stream().filter(post -> post != JobPosts.NO_POST).distinct().count();
            List<String> legacyJobs = jobs.subList(Math.min(postJobs, jobs.size()), jobs.size());
            List<String> picked = pickJobs(legacyJobs, workerCount(legacyJobs.size(), free,
                    WorldConfig.ECONOMY_WORKERS_PER_JOB.get()));
            for (int i = 0; i < picked.size(); i++) {
                homes.set(homes.size() - free + i, professionOf(picked.get(i)));
            }
        }
        List<Seat> seats = new ArrayList<>(Math.max(0, count));
        if (!homes.isEmpty() && count > 0) {
            seats.add(new Seat(homes.get(0), households.get(0)));
        }
        addStaff(seats, count);
        for (int i = 1; i < homes.size() && seats.size() < count; i++) {
            seats.add(new Seat(homes.get(i), households.get(i)));
        }
        while (seats.size() < count) {
            seats.add(new Seat(ModProfessions.INHABITANT.get(), JobPosts.NO_POST));
        }
        return seats;
    }

    /** Adds the builders and the handymen: they live in the hall. */
    private static void addStaff(List<Seat> seats, int count) {
        for (int i = 0; i < WorldConfig.BUILDERS.get() && seats.size() < count; i++) {
            seats.add(new Seat(ModProfessions.BUILDER.get(), NO_HOME));
        }
        for (int i = 0; i < WorldConfig.HANDYMEN.get() && seats.size() < count; i++) {
            seats.add(new Seat(ModProfessions.HANDYMAN.get(), NO_HOME));
        }
    }

    /**
     * The professions of the inhabitants of a village with no list of posts: one for each inhabitant, in this order.
     *
     * <ol>
     *   <li>One worker of an active job that trades (see {@link Merchants#traders}), so that a small village has a
     *       trader. It is the first job of {@link #pickJobs} with a profession that trades.</li>
     *   <li>The builders and the handymen.</li>
     *   <li>The other workers of the settlement (see {@link #workerCount}): they show its active jobs, in proportion
     *       to the active job slots.</li>
     *   <li>The inhabitant profession for the other inhabitants.</li>
     * </ol>
     *
     * @param settlement the settlement
     * @param count      the number of inhabitants
     * @return the professions
     */
    private static List<VillagerProfession> slotProfessions(Settlement settlement, int count) {
        List<VillagerProfession> professions = new ArrayList<>(Math.max(0, count));
        List<String> jobs = Economies.activeJobIds(settlement);
        int workers = workerCount(jobs.size(), settlement.economy().population(),
                WorldConfig.ECONOMY_WORKERS_PER_JOB.get());
        List<VillagerProfession> picked = new ArrayList<>(pickJobs(jobs, Math.max(1, workers)).stream()
                .map(VillageFolk::professionOf).toList());
        picked.stream().filter(profession -> profession != ModProfessions.INHABITANT.get()).findFirst()
                .ifPresent(trader -> {
                    picked.remove(trader);
                    addUpTo(professions, count, trader, 1);
                });
        int firstWorkers = professions.size();
        addUpTo(professions, count, ModProfessions.BUILDER.get(), WorldConfig.BUILDERS.get());
        addUpTo(professions, count, ModProfessions.HANDYMAN.get(), WorldConfig.HANDYMEN.get());
        for (VillagerProfession profession : picked.subList(0, Math.max(0, Math.min(picked.size(),
                workers - firstWorkers)))) {
            addUpTo(professions, count, profession, 1);
        }
        addUpTo(professions, count, ModProfessions.INHABITANT.get(), count);
        return professions;
    }

    /** Adds a profession to a list, at most a number of times, with a maximum size of the list. */
    private static void addUpTo(List<VillagerProfession> professions, int maxSize, VillagerProfession profession,
            int times) {
        for (int i = 0; i < times && professions.size() < maxSize; i++) {
            professions.add(profession);
        }
    }

    /**
     * The number of workers of a settlement, as in the economy: {@code workersPerJob} inhabitants for each active job.
     * The number of active jobs is the population divided by {@code workersPerJob}, with a maximum of the job slots.
     *
     * @param jobSlots      the number of job slots of the settlement
     * @param population    the population of the settlement
     * @param workersPerJob the inhabitants for one active job
     * @return the number of workers
     */
    public static int workerCount(int jobSlots, int population, int workersPerJob) {
        int perJob = Math.max(1, workersPerJob);
        return Math.min(jobSlots, Math.max(0, population) / perJob) * perJob;
    }

    /**
     * Gives the wanted professions to the inhabitants. An inhabitant with a wanted profession keeps it. The other
     * inhabitants get the wanted professions that no inhabitant has. An inhabitant in excess keeps its profession,
     * but a villager with the profession {@link ModProfessions#MERCHANT} becomes an inhabitant with no job.
     *
     * @param inhabitants the inhabitants
     * @param wanted      the wanted professions
     * @return the wanted professions that no inhabitant has: the heart spawns these inhabitants
     */
    public static List<VillagerProfession> assign(List<Villager> inhabitants, List<VillagerProfession> wanted) {
        List<VillagerProfession> missing = new ArrayList<>(wanted);
        List<Villager> others = new ArrayList<>();
        for (Villager inhabitant : inhabitants) {
            if (!missing.remove(inhabitant.getVillagerData().getProfession())) {
                others.add(inhabitant);
            }
        }
        for (Villager inhabitant : others) {
            if (!missing.isEmpty()) {
                setProfession(inhabitant, missing.remove(0));
            } else if (inhabitant.getVillagerData().getProfession() == ModProfessions.MERCHANT.get()) {
                setProfession(inhabitant, ModProfessions.INHABITANT.get());
            }
        }
        return missing;
    }

    private static void setProfession(Villager villager, VillagerProfession profession) {
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));
        villager.setVillagerXp(1);
        villager.setOffers(new MerchantOffers());
        if (villager.level() instanceof ServerLevel level) {
            villager.refreshBrain(level);
        }
    }

    /**
     * The target number of inhabitants: the population, with the places of the village as the
     * maximum (see {@link VillagePlaces}). The setting {@link WorldConfig#INHABITANT_LIMIT} can set a lower maximum
     * (0: no maximum).
     *
     * @param settlement the settlement
     * @return the number of inhabitants
     */
    public static int inhabitantCount(Settlement settlement) {
        int count = VillagePlaces.inhabitants(
                settlement.economy().population(), settlement.beds(), VillageBeds.staff());
        int limit = WorldConfig.INHABITANT_LIMIT.get();
        return limit > 0 ? Math.min(limit, count) : count;
    }

    /**
     * Creates a villager of the village on the ground near the heart.
     *
     * @param level      the level
     * @param heartPos   the position of the heart
     * @param profession the profession of the villager
     * @return the villager, or null
     */
    @Nullable
    public static Villager spawn(ServerLevel level, BlockPos heartPos, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        BlockPos pos = Garrison.groundSpawnPos(level, heartPos);
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(villager, level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        // The tag comes before the profession: the refresh of the brain gives the worker schedule to a villager of a
        // village (see WorkerBrains).
        villager.getPersistentData().putLong(Merchants.VILLAGE_TAG, heartPos.asLong());
        setProfession(villager, profession);
        level.addFreshEntity(villager);
        return villager;
    }

    /** A dead villager of a faction village removes 1 from the population of its settlement. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !isVillageFolk(villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos heartPos = BlockPos.of(villager.getPersistentData().getLong(Merchants.VILLAGE_TAG));
        if (ReadyChunks.isReady(level, heartPos)
                && level.getBlockEntity(heartPos) instanceof VillageHeartBlockEntity heart) {
            Settlements.of(level.getServer(), heart).ifPresent(settlement -> {
                settlement.economy().setPopulation(Math.max(0, settlement.economy().population() - 1));
                SettlementData.get(level.getServer()).setDirty();
            });
        }
    }

    /**
     * A player cannot trade with an inhabitant that is not a trader (see {@link Merchants#isTrader}). The player can
     * still use a name tag or a lead on it.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)
                || !isVillageFolk(villager) || !(player.level() instanceof ServerLevel level)
                || Merchants.isTrader(level, villager)) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.is(Items.NAME_TAG) || stack.is(Items.LEAD)) {
            return;
        }
        player.displayClientMessage(Component.translatable("message.folkhold_world.inhabitant_no_trade",
                villager.getDisplayName()), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** The villagers of a faction village do not breed: the simulation sets the population. */
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if ((event.getParentA() instanceof Villager a && isVillageFolk(a))
                || (event.getParentB() instanceof Villager b && isVillageFolk(b))) {
            event.setCanceled(true);
        }
    }
}
