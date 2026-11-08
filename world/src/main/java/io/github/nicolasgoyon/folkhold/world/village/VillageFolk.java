package io.github.nicolasgoyon.folkhold.world.village;

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
 *       (see {@link EconomyData#profession(String)}). In a scanned village, the active jobs are the jobs of the
 *       active job posts (see {@link Economies#activeJobIds}). The posts with a food job that trades come first in
 *       the order of the posts, then the posts with an other job that trades, and the first valid post always has an
 *       inhabitant: thus a village with a valid post that trades has a trader. The other inhabitants have the
 *       inhabitant profession: they give life to the village (see {@link #professions(Settlement, int)}).</li>
 *   <li>Only the workers of the active jobs trade, with the goods of their jobs (see {@link Merchants}). A player
 *       cannot trade with the other inhabitants. The vanilla trades of a profession do not apply: they would bypass
 *       the economy of the village.</li>
 *   <li>A villager with the profession {@link ModProfessions#MERCHANT} is an inhabitant: the next check of the heart
 *       gives it a profession of the settlement (see {@link #assign}).</li>
 *   <li>A villager has 1 experience point, so it keeps its profession with no job site block and does not take
 *       the workstations of the village.</li>
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
        return level.getEntitiesOfClass(Villager.class, VillageHearts.area(heartPos),
                villager -> villager.getVillagerData().getProfession() == profession
                        && villager.getPersistentData().getLong(Merchants.VILLAGE_TAG) == heartPos.asLong()
                        && isVillageFolk(villager));
    }

    /** The loaded inhabitants of a village: all its villagers. */
    public static List<Villager> inhabitants(ServerLevel level, BlockPos heartPos) {
        return level.getEntitiesOfClass(Villager.class, VillageHearts.area(heartPos),
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
     * The professions of the inhabitants of a settlement: one for each inhabitant, in this order.
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
    public static List<VillagerProfession> professions(Settlement settlement, int count) {
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
        setProfession(villager, profession);
        villager.getPersistentData().putLong(Merchants.VILLAGE_TAG, heartPos.asLong());
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
