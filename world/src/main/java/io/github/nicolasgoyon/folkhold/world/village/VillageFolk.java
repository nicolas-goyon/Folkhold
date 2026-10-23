package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
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
 * The villagers of a faction village: the merchants and the inhabitants. They are vanilla villagers with a
 * Folkhold profession, so the vanilla systems work with them (raids, bell, beds, iron golems, zombies).
 *
 * <ul>
 *   <li>Each villager records the position of the heart of its village ({@link Merchants#VILLAGE_TAG}).</li>
 *   <li>The number of inhabitants is the population of the settlement, minus the merchants. The mod sets no
 *       maximum, except with the setting {@link WorldConfig#INHABITANT_LIMIT}.</li>
 *   <li>The workers of the settlement show its jobs: the profession of a worker is the profession of its job (see
 *       {@link EconomyData#profession(String)}). Of the other inhabitants, some are builders and handymen
 *       ({@link WorldConfig#BUILDERS}, {@link WorldConfig#HANDYMEN}). The rest have the inhabitant profession: they
 *       give life to the village (see {@link #professions(Settlement, int)}).</li>
 *   <li>Only the merchants trade. A player cannot trade with an inhabitant: the vanilla trades of its profession
 *       would bypass the economy of the village.</li>
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

    /** The loaded inhabitants of a village: its villagers that are not merchants. */
    public static List<Villager> inhabitants(ServerLevel level, BlockPos heartPos) {
        return level.getEntitiesOfClass(Villager.class, VillageHearts.area(heartPos),
                villager -> villager.getVillagerData().getProfession() != ModProfessions.MERCHANT.get()
                        && villager.getPersistentData().getLong(Merchants.VILLAGE_TAG) == heartPos.asLong()
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
     * The professions of the inhabitants of a settlement: one for each inhabitant. The workers of the settlement (see
     * {@link #workerCount}) show its jobs, in proportion to the job slots. Then come the builders and the handymen, if
     * the settlement has enough inhabitants. The other inhabitants have the inhabitant profession.
     *
     * @param settlement the settlement
     * @param count      the number of inhabitants
     * @return the professions
     */
    public static List<VillagerProfession> professions(Settlement settlement, int count) {
        List<String> jobs = settlement.economy().jobs();
        int workers = Math.min(count, workerCount(jobs.size(), settlement.economy().population(),
                WorldConfig.ECONOMY_WORKERS_PER_JOB.get()));
        List<VillagerProfession> professions = new ArrayList<>(Math.max(0, count));
        for (String job : pickJobs(jobs, workers)) {
            professions.add(professionOf(job));
        }
        addUpTo(professions, count, ModProfessions.BUILDER.get(), WorldConfig.BUILDERS.get());
        addUpTo(professions, count, ModProfessions.HANDYMAN.get(), WorldConfig.HANDYMEN.get());
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
     * inhabitants get the wanted professions that no inhabitant has. An inhabitant in excess keeps its profession.
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
            if (missing.isEmpty()) {
                break;
            }
            setProfession(inhabitant, missing.remove(0));
        }
        return missing;
    }

    private static void setProfession(Villager villager, VillagerProfession profession) {
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));
        villager.setVillagerXp(1);
        villager.setOffers(new MerchantOffers());
    }

    /**
     * The target number of inhabitants: the population minus the merchants. The setting
     * {@link WorldConfig#INHABITANT_LIMIT} can set a maximum (0: no maximum).
     *
     * @param settlement the settlement
     * @return the number of inhabitants
     */
    public static int inhabitantCount(Settlement settlement) {
        int count = Math.max(0, settlement.economy().population() - Merchants.merchantCount(settlement));
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
     * A player cannot trade with an inhabitant. The player can still use a name tag or a lead on it.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)
                || !isVillageFolk(villager)
                || villager.getVillagerData().getProfession() == ModProfessions.MERCHANT.get()) {
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
