package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.core.sim.construction.SiteSchedule;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Step;
import io.github.nicolasgoyon.folkhold.core.sim.construction.StepType;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.core.sim.construction.WalkSearch;
import io.github.nicolasgoyon.folkhold.core.sim.construction.WorkZone;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionEndPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A construction site of the builders of a village (see {@link BuilderSites}): the builders build a structure
 * template block by block (see {@link BuilderWorker}).
 *
 * <ol>
 *   <li>Fences: the builders put a ring of fences around the work zone (see {@link WorkZone}), with an opening on the
 *       side of the village.</li>
 *   <li>Construction: the builders apply the steps of the plan of the structure (see {@link ConstructionPlanner}), in
 *       their order.</li>
 *   <li>Cleanup: the builders remove the scaffolding of the site that stays, from the top.</li>
 *   <li>Removal of the fences.</li>
 * </ol>
 *
 * <ul>
 *   <li>The builders work only during the day and when the village is calm. At night or during an alert, they go
 *       down from the scaffolding and stop.</li>
 *   <li>When a player is in the work zone, the builders stop, look at the player and ask the player to go out.</li>
 *   <li>The sites of one village come one after the other: the builders work only on the oldest site.</li>
 *   <li>The fences can go in any order: each builder takes the fence nearest to it.</li>
 *   <li>A block of the plan does not go into an entity. The entity moves out after some time.</li>
 *   <li>The site keeps its template, its position, its phase, its fences and its scaffolding. After a load, the site
 *       makes a new plan from the blocks of the level: the blocks in place give no steps.</li>
 *   <li>The builders take the materials of the blocks of the plan from the stock of the village (see
 *       {@link SiteMaterials}). The fences and the scaffolding use no materials. A removed block drops no item.</li>
 *   <li>A repair site (see {@link VillageRepairs}) builds only some positions of the structure pieces of its village:
 *       the damaged blocks. Its box is the box of these positions.</li>
 *   <li>At the end of a construction site, the repairs of the village keep the blocks of its box (see
 *       {@link VillageRepairData}).</li>
 *   <li>The builders never change a block that a player placed (see {@link VillageRepairData#isPlayerBlock}): its
 *       step goes with no change.</li>
 *   <li>Chunks not loaded: during the day, the builders of the oldest site of a village go on with the work in the
 *       abstract. The site counts the steps of this work (see {@link WorldConfig#BUILDERS} and
 *       {@link WorldConfig#CONSTRUCTION_BUILDER_TICKS}). When the chunks load, the site applies these steps, with a
 *       limit for each tick ({@link WorldConfig#CONSTRUCTION_CATCH_UP_STEPS}). Then the builders go on. A step that
 *       an entity blocks, or that waits for materials, stops this work.</li>
 * </ul>
 */
public final class BuilderSite {
    /** The template id of the repair sites, in the messages and in the saved data. */
    public static final ResourceLocation REPAIR = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "repair");
    /** The block of the fences around the work zone. */
    public static final Block FENCE = Blocks.SPRUCE_FENCE;
    /** The message of the builders to a player in the work zone. */
    public static final String LEAVE_KEY = "message.folkhold_world.builders.leave";
    /** A step that an entity blocks waits this number of ticks, then the entity moves out. */
    static final int BLOCKED_TIMEOUT = 40;
    /** The builders ask a player in the work zone to go out every this number of ticks. */
    static final int WARN_TICKS = 100;
    private static final String KEY = "message.folkhold_world.construction.";
    private static final String FENCE_STATE = "minecraft:spruce_fence";
    /** The site looks for the builders of its village every this number of ticks. */
    private static final int SCAN_TICKS = 20;
    /** The ground of a fence is at most this number of blocks above the bottom of the box. */
    private static final int FENCE_SEARCH_UP = 1;
    /** An entity in the block of a step moves to a free position at this horizontal distance or nearer. */
    private static final int ASIDE_RADIUS = 3;
    /** A builder does not stand on a block of its step: the space below its feet, in blocks. */
    private static final double FLOOR_MARGIN = 0.25;
    /** A scaffolding column has this number of blocks at most. */
    private static final int MAX_COLUMN = 64;
    /** The height of the space of an entity on the top of a column, in blocks. */
    private static final int ENTITY_SPACE = 3;
    /**
     * The search of a way from the ground on the blocks of the level stays for this number of ticks at most. The site
     * tells it about the blocks of its steps, but not about the other changes of the level.
     */
    private static final int WAYS_TICKS = 200;
    /** The time of the head shake of a builder that says no, in ticks. */
    private static final int UNHAPPY_TICKS = 40;
    /** The site checks the quests for its materials every this number of ticks. */
    private static final int QUEST_TICKS = 200;
    /** A site keeps this number of steps at most of the work of the builders while its chunks are not loaded. */
    static final double MAX_PENDING_STEPS = 100_000.0;
    /** A need of a material below this part of an item is no need. */
    private static final double NEED_EPSILON = 1.0E-6;

    /** The phase of a site. */
    public enum Phase {
        /** The builders put the fences around the work zone. */
        FENCES,
        /** The builders apply the steps of the plan. */
        BUILD,
        /** The builders remove the scaffolding of the site that stays, from the top. */
        CLEANUP,
        /** The builders remove the fences. */
        REMOVE_FENCES;

        /** The name of the phase in the messages and in the saved data. */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the site does now. */
    public enum Status {
        /** The site did not start its first tick. */
        STARTING,
        /** An older site of the village is not finished. */
        QUEUED,
        /** A chunk of the site or of the heart of the village is not ready. */
        NOT_LOADED,
        /** It is night. */
        NIGHT,
        /** The village is in alert. */
        ALERT,
        /** A player is in the work zone. */
        PLAYER,
        /** No builder of the village is near. */
        NO_BUILDERS,
        /** The builders wait for materials of the stock of the village (see {@link SiteMaterials}). */
        MATERIALS,
        /** The builders work. */
        WORKING,
        /** A command stopped the site: the site removes its fences and its scaffolding when its chunks are ready. */
        STOPPING,
        /** The site applies the work of the builders while its chunks were not loaded. */
        CATCHING_UP;

        /** The name of the status in the messages. */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The result of a step that a builder applies. */
    enum Applied {
        /** The step applied. */
        APPLIED,
        /** An entity is in the block of the step, or a step before it did not apply. */
        BLOCKED,
        /** The stock of the village does not have the materials of the step. */
        WAITING
    }

    private final int id;
    private final ResourceLocation template;
    private final BlockPos origin;
    private final Rotation rotation;
    private final Mirror mirror;
    private final BlockPos heart;
    @Nullable
    private final UUID operator;
    /** The structure pieces of the village for a repair site, or null for a construction site. */
    @Nullable
    private final VillageReference repairReference;
    /** The damaged positions of a repair site. Empty for a construction site. */
    private final Set<BlockPos> repairPositions;
    private Phase phase = Phase.FENCES;
    private boolean stopping;
    /** The steps of the work of the builders while the chunks of the site are not loaded. */
    private double pendingSteps;
    /** The fences that the site put in the level. */
    private final Set<BlockPos> fences = new LinkedHashSet<>();
    /** The scaffolding blocks that the site put in the level. */
    private final Set<BlockPos> scaffolding = new LinkedHashSet<>();

    @Nullable
    private BoundingBox box;
    @Nullable
    private WorkZone zone;
    private Reach reach = Reach.DEFAULT;
    @Nullable
    private List<Step> steps;
    @Nullable
    private ConstructionRun run;
    @Nullable
    private SiteSchedule<UUID> schedule;
    private final Map<UUID, BuilderWorker> workers = new LinkedHashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final SiteMaterials materials = new SiteMaterials();
    /** The village of the builders in this tick, or null with no economy. */
    @Nullable
    private Settlement settlement;
    private Status status = Status.STARTING;
    private boolean dirty;
    /** The search of a way from the ground on the blocks of the level, or null before the next search. */
    @Nullable
    private WalkSearch ways;
    private long waysTime;

    BuilderSite(int id, ResourceLocation template, BlockPos origin, Rotation rotation, Mirror mirror, BlockPos heart,
            @Nullable UUID operator) {
        this(id, template, origin, rotation, mirror, heart, operator, null, Set.of());
    }

    private BuilderSite(int id, ResourceLocation template, BlockPos origin, Rotation rotation, Mirror mirror,
            BlockPos heart, @Nullable UUID operator, @Nullable VillageReference repairReference,
            Set<BlockPos> repairPositions) {
        this.id = id;
        this.template = template;
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.mirror = mirror;
        this.heart = heart.immutable();
        this.operator = operator;
        this.repairReference = repairReference;
        this.repairPositions = Collections.unmodifiableSet(new LinkedHashSet<>(repairPositions));
    }

    /**
     * A repair site: the builders give their reference blocks to the damaged positions of a village.
     *
     * @param id        the id of the site in its level
     * @param heart     the position of the village heart
     * @param reference the structure pieces of the village
     * @param positions the damaged positions, 1 or more
     * @return the site
     */
    static BuilderSite repair(int id, BlockPos heart, VillageReference reference, Set<BlockPos> positions) {
        BlockPos min = BoundingBox.encapsulatingPositions(positions)
                .map(box -> new BlockPos(box.minX(), box.minY(), box.minZ()))
                .orElseThrow(() -> new IllegalArgumentException("A repair site needs positions"));
        return new BuilderSite(id, REPAIR, min, Rotation.NONE, Mirror.NONE, heart, null, reference, positions);
    }

    /** True for a repair site (see {@link VillageRepairs}). */
    public boolean isRepair() {
        return repairReference != null;
    }

    /** The damaged positions of a repair site, or no positions for a construction site. */
    public Set<BlockPos> repairPositions() {
        return repairPositions;
    }

    /** The id of the site in its level. */
    public int id() {
        return id;
    }

    public ResourceLocation template() {
        return template;
    }

    /** The position of the origin of the template in the level. */
    public BlockPos origin() {
        return origin;
    }

    /** The position of the heart of the village of the builders. */
    public BlockPos heart() {
        return heart;
    }

    public Phase phase() {
        return phase;
    }

    public Status status() {
        return status;
    }

    /** The number of builders on the site now. */
    public int builderCount() {
        return workers.size();
    }

    /** The number of applied steps of the current phase. */
    public int appliedSteps() {
        return schedule == null ? 0 : schedule.appliedCount();
    }

    /** The number of steps of the current phase, or 0 before the site prepares the phase. */
    public int totalSteps() {
        return schedule == null ? 0 : schedule.total();
    }

    /** The fences of the site in the level. */
    public Set<BlockPos> fences() {
        return Set.copyOf(fences);
    }

    /** The scaffolding blocks of the site in the level. */
    public Set<BlockPos> scaffolding() {
        return Set.copyOf(scaffolding);
    }

    /** The work zone, or empty before the first tick of the site. */
    public Optional<WorkZone> zone() {
        return Optional.ofNullable(zone);
    }

    SiteSchedule<UUID> schedule() {
        if (schedule == null) {
            throw new IllegalStateException("The site has no steps now");
        }
        return schedule;
    }

    @Nullable
    BuilderWorker worker(UUID builder) {
        return workers.get(builder);
    }

    /**
     * One tick of the site.
     *
     * @param level the level of the site
     * @param data  the sites of the level: the site marks them as changed
     * @param first true if the site is the oldest site of its village: the builders work only on this site
     * @return true when the site ended: the builders finished it, a command stopped it, or it cannot continue
     */
    boolean tick(ServerLevel level, BuilderSiteData data, boolean first) {
        boolean ended = tickPhase(level, first);
        if (dirty || ended) {
            data.setDirty();
            dirty = false;
        }
        return ended;
    }

    private boolean tickPhase(ServerLevel level, boolean first) {
        if (box == null && !resolveBox(level)) {
            FolkholdWorld.LOGGER.warn("Builder site {}: the template {} does not exist, the site stops", id, template);
            end(level, false);
            return true;
        }
        if (!isAreaReady(level) || !ReadyChunks.isReady(level, heart)) {
            if (first && !stopping && isWorkTime(level)) {
                addAbstractWork();
            }
            pause(level, stopping ? Status.STOPPING : Status.NOT_LOADED);
            return false;
        }
        if (stopping) {
            end(level, false);
            return true;
        }
        if (!(level.getBlockEntity(heart) instanceof VillageHeartBlockEntity village)) {
            FolkholdWorld.LOGGER.info("Builder site {}: the village at {} has no heart, the site stops", id, heart);
            end(level, false);
            return true;
        }
        if (!first) {
            pause(level, Status.QUEUED);
            return false;
        }
        if (pendingSteps >= 1.0) {
            settlement = economyOf(level, village);
            if (schedule == null) {
                prepare(level);
            }
            if (schedule().finished()) {
                if (!nextPhase()) {
                    end(level, true);
                    return true;
                }
                return false;
            }
            pause(level, Status.CATCHING_UP);
            catchUp(level);
            return false;
        }
        if (!isWorkTime(level)) {
            pause(level, Status.NIGHT);
            return false;
        }
        if (village.getAlertState() != AlertState.CALM) {
            pause(level, Status.ALERT);
            return false;
        }
        settlement = economyOf(level, village);
        if (schedule == null) {
            prepare(level);
        }
        if (schedule().finished()) {
            if (!nextPhase()) {
                end(level, true);
                return true;
            }
            return false;
        }
        long now = level.getGameTime();
        if (Math.floorMod(now + id, SCAN_TICKS) == 0) {
            findBuilders(level);
        }
        Settlement economy = settlement;
        if (phase == Phase.BUILD && economy != null && Math.floorMod(now + id, QUEST_TICKS) == 0) {
            materials.offerQuests(level.getServer(), economy, needs(level, economy), now, isRepair());
        }
        List<ServerPlayer> intruders = playersInZone(level);
        status = !intruders.isEmpty() ? Status.PLAYER : workers.isEmpty() ? Status.NO_BUILDERS : Status.WORKING;
        materials.clearMissing();
        for (BuilderWorker worker : List.copyOf(workers.values())) {
            Villager villager = villager(level, worker);
            if (villager == null) {
                removeWorker(worker);
                continue;
            }
            worker.tick(level, this, villager, nearest(intruders, villager));
        }
        if (status == Status.WORKING && !materials.missing().isEmpty()) {
            status = Status.MATERIALS;
        }
        warn(level, intruders, now);
        return false;
    }

    /** The village of a heart with an economy, or null: the builders of a village with no economy use no materials. */
    @Nullable
    private static Settlement economyOf(ServerLevel level, VillageHeartBlockEntity village) {
        return Settlements.of(level.getServer(), village).filter(Settlement::isActive)
                .filter(found -> found.economy().isInitialized()).orElse(null);
    }

    /**
     * The materials of the steps of the plan that did not apply, by item id. For a rare or a valuable block, the
     * material is the block. For the other blocks, the materials are items of the economy, less the parts of items
     * that the site keeps.
     */
    private Map<String, SiteMaterials.Need> needs(ServerLevel level, Settlement village) {
        Map<String, Double> quantities = new TreeMap<>();
        Map<String, Double> rarePrices = new HashMap<>();
        SiteSchedule<UUID> current = schedule();
        for (int i = current.next(); i < current.total(); i++) {
            if (current.isApplied(i)) {
                continue;
            }
            BuilderMaterials.Material material = materialOf(level, i);
            switch (material.kind()) {
                case RARE -> {
                    quantities.merge(material.item(), 1.0, Double::sum);
                    rarePrices.put(material.item(), material.price());
                }
                case COMMON -> material.cost().forEach((item, quantity) -> quantities.merge(item, quantity,
                        Double::sum));
                case FREE -> {
                }
            }
        }
        Map<String, Double> kept = materials.buffer();
        ItemCatalog catalog = EconomyData.catalog();
        Map<String, SiteMaterials.Need> needs = new TreeMap<>();
        quantities.forEach((item, quantity) -> {
            int count = (int) Math.ceil(quantity - kept.getOrDefault(item, 0.0) - NEED_EPSILON);
            if (count <= 0) {
                return;
            }
            boolean rare = rarePrices.containsKey(item);
            double price = rare ? rarePrices.get(item) : Prices.price(catalog.info(item),
                    village.economy().stock().count(item), village.economy().population(), Economies.settings());
            needs.put(item, new SiteMaterials.Need(count, price, rare));
        });
        return needs;
    }

    /** The material of a step: the block of a placement of the plan. The other steps use no materials. */
    private BuilderMaterials.Material materialOf(ServerLevel level, int index) {
        Step step = stepAt(index);
        ConstructionRun plan = run;
        if (phase != Phase.BUILD || step.type() != StepType.PLACE || plan == null
                || (isRepair() && WorldConfig.CONSTRUCTION_FREE_REPAIRS.get())) {
            return BuilderMaterials.Material.NONE;
        }
        return plan.state(step.state())
                .map(state -> BuilderMaterials.of(level.getServer(), state.getBlock().asItem()))
                .orElse(BuilderMaterials.Material.NONE);
    }

    /** True if the stock of the village has the materials of a step (see {@link SiteMaterials}). */
    boolean hasMaterials(ServerLevel level, int index) {
        return materials.has(materialOf(level, index), settlement, level.getGameTime());
    }

    /** The items that the builders wait for now, by item id. */
    public Set<String> missingMaterials() {
        return materials.missing();
    }

    /** The ids of the open delivery quests of the site, by item id. */
    public Map<String, String> materialQuests() {
        return materials.quests();
    }

    /** One tick of work of the builders while the chunks of the site are not loaded. */
    private void addAbstractWork() {
        double perTick = WorldConfig.BUILDERS.get() / (double) WorldConfig.CONSTRUCTION_BUILDER_TICKS.get();
        pendingSteps = Math.min(MAX_PENDING_STEPS, pendingSteps + perTick);
        dirty = true;
    }

    /**
     * Applies the steps of the work of the builders while the chunks of the site were not loaded: at most
     * {@link WorldConfig#CONSTRUCTION_CATCH_UP_STEPS} steps in this tick. A step that an entity blocks, or that waits
     * for materials, stops this work: the builders go on.
     */
    private void catchUp(ServerLevel level) {
        SiteSchedule<UUID> current = schedule();
        int budget = (int) Math.min(WorldConfig.CONSTRUCTION_CATCH_UP_STEPS.get(), pendingSteps);
        dirty = true;
        for (int i = 0; i < budget && !current.finished(); i++) {
            if (apply(level, current.next(), false) != Applied.APPLIED) {
                pendingSteps = 0.0;
                return;
            }
            pendingSteps -= 1.0;
        }
    }

    /**
     * The steps of the work of the builders that the site did not apply yet: the work while the chunks of the site were
     * not loaded.
     */
    public double pendingSteps() {
        return pendingSteps;
    }

    /** True during the work time of the builders: the day. */
    private static boolean isWorkTime(ServerLevel level) {
        return level.isDay();
    }

    /** Finds the box and the work zone: from the size of the template, or from the positions of a repair. */
    private boolean resolveBox(ServerLevel level) {
        if (repairReference != null) {
            box = BoundingBox.encapsulatingPositions(repairPositions).orElse(null);
            reach = WorldConfig.constructionReach();
            BoundingBox repairBox = box;
            if (repairBox == null) {
                return false;
            }
            zone = new WorkZone(repairBox.minX(), repairBox.minY(), repairBox.minZ(), repairBox.maxX(),
                    repairBox.maxY(), repairBox.maxZ(), reach.horizontal());
            return true;
        }
        Optional<StructureTemplate> found = level.getStructureManager().get(template);
        Vec3i size = found.map(StructureTemplate::getSize).orElse(Vec3i.ZERO);
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return false;
        }
        box = GhostPlacement.box(size, rotation, mirror, origin);
        reach = WorldConfig.constructionReach();
        zone = new WorkZone(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
                reach.horizontal());
        return true;
    }

    /** True if the chunks of the work zone and of the positions around it are ready (see {@link ReadyChunks}). */
    private boolean isAreaReady(ServerLevel level) {
        WorkZone current = zone;
        return current != null && ReadyChunks.isAreaReady(level, current.ringMinX() - 1, current.ringMinZ() - 1,
                current.ringMaxX() + 1, current.ringMaxZ() + 1);
    }

    /**
     * Stops the work: the builders go down from the scaffolding, then the vanilla behaviors of the villagers run
     * again.
     */
    private void pause(ServerLevel level, Status reason) {
        status = reason;
        Iterator<BuilderWorker> iterator = workers.values().iterator();
        while (iterator.hasNext()) {
            BuilderWorker worker = iterator.next();
            Villager villager = villager(level, worker);
            if (villager == null || worker.leave(level, villager)) {
                if (villager != null) {
                    worker.release(villager);
                }
                if (schedule != null) {
                    schedule.release(worker.id());
                }
                iterator.remove();
            }
        }
        warned.clear();
    }

    /** Adds the builders of the village that can work, and removes the builders that cannot work any more. */
    private void findBuilders(ServerLevel level) {
        Set<UUID> available = new HashSet<>();
        for (Villager villager : VillageFolk.of(level, heart, ModProfessions.BUILDER.get())) {
            if (BuilderWorker.isAvailable(villager)) {
                available.add(villager.getUUID());
                workers.computeIfAbsent(villager.getUUID(), BuilderWorker::new);
            }
        }
        for (BuilderWorker worker : List.copyOf(workers.values())) {
            if (!available.contains(worker.id()) && !worker.isOnTheWay()) {
                Villager villager = villager(level, worker);
                if (villager != null) {
                    worker.release(villager);
                }
                removeWorker(worker);
            }
        }
    }

    private void removeWorker(BuilderWorker worker) {
        workers.remove(worker.id());
        if (schedule != null) {
            schedule.release(worker.id());
        }
    }

    @Nullable
    private static Villager villager(ServerLevel level, BuilderWorker worker) {
        return level.getEntity(worker.id()) instanceof Villager villager && villager.isAlive() ? villager : null;
    }

    /** The players in the work zone, except the spectators. */
    private List<ServerPlayer> playersInZone(ServerLevel level) {
        List<ServerPlayer> found = new ArrayList<>();
        WorkZone current = zone;
        if (current == null) {
            return found;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()
                    && current.contains(player.getX(), player.getY(), player.getZ())) {
                found.add(player);
            }
        }
        return found;
    }

    @Nullable
    private static ServerPlayer nearest(List<ServerPlayer> players, Entity entity) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double distance = player.distanceToSqr(entity);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Asks the players in the work zone to go out. The nearest builder says no with its head. The player gets the
     * message above the hotbar, every {@link #WARN_TICKS} ticks.
     */
    private void warn(ServerLevel level, List<ServerPlayer> intruders, long now) {
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer player : intruders) {
            inside.add(player.getUUID());
            Long last = warned.get(player.getUUID());
            if (last != null && now - last < WARN_TICKS) {
                continue;
            }
            Villager speaker = nearestBuilder(level, player);
            if (speaker == null) {
                continue;
            }
            warned.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(LEAVE_KEY, speaker.getDisplayName()), true);
            speaker.setUnhappyCounter(UNHAPPY_TICKS);
            speaker.makeSound(SoundEvents.VILLAGER_NO);
        }
        warned.keySet().retainAll(inside);
    }

    @Nullable
    private Villager nearestBuilder(ServerLevel level, Entity entity) {
        Villager best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BuilderWorker worker : workers.values()) {
            Villager villager = villager(level, worker);
            if (villager != null && villager.distanceToSqr(entity) < bestDistance) {
                best = villager;
                bestDistance = villager.distanceToSqr(entity);
            }
        }
        return best;
    }

    /** Makes the steps of the current phase. */
    private void prepare(ServerLevel level) {
        switch (phase) {
            case FENCES -> prepareFences(level);
            case BUILD -> prepareBuild(level);
            case CLEANUP -> prepareCleanup(level);
            case REMOVE_FENCES -> prepareFenceRemoval(level);
        }
    }

    /** The fences of the ring of the work zone, on the ground, from the opening on the side of the village. */
    private void prepareFences(ServerLevel level) {
        List<Step> list = new ArrayList<>();
        for (WorkZone.Column column : zone().orElseThrow().fenceColumns(heart.getX(), heart.getZ())) {
            fenceGround(level, column).filter(pos -> !fences.contains(pos))
                    .ifPresent(pos -> list.add(simpleStep(StepType.PLACE, pos, FENCE_STATE)));
        }
        setSteps(list, false, null);
    }

    /**
     * The position of the fence of a column of the ring: the first free position on a floor. The search goes from
     * {@link #FENCE_SEARCH_UP} blocks above the bottom of the box down to {@link WorkZone#BELOW} blocks below it. A
     * column with no such position gets no fence.
     */
    private Optional<BlockPos> fenceGround(ServerLevel level, WorkZone.Column column) {
        BoundingBox current = box;
        if (current == null) {
            return Optional.empty();
        }
        for (int y = current.minY() + FENCE_SEARCH_UP; y >= current.minY() - WorkZone.BELOW; y--) {
            BlockPos pos = new BlockPos(column.x(), y, column.z());
            BlockState state = level.getBlockState(pos);
            BlockPos below = pos.below();
            if (state.canBeReplaced() && state.getFluidState().isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** The plan of the structure, from the blocks of the level now. */
    private void prepareBuild(ServerLevel level) {
        Target target;
        if (repairReference != null) {
            target = repairTarget(level, repairReference);
        } else {
            Optional<StructureTemplate> found = level.getStructureManager().get(template);
            if (found.isEmpty()) {
                setSteps(List.of(), true, null);
                return;
            }
            target = ConstructionTargets.fromTemplate(level, found.get(), origin,
                    GhostPlacement.settings(rotation, mirror));
        }
        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), reach);
        FolkholdWorld.LOGGER.debug("Builder site {}: plan of {} steps with {} warnings", id, plan.steps().size(),
                plan.warnings().size());
        setSteps(plan.steps(), true, new ConstructionRun(level, plan, 1));
    }

    /**
     * The target of a repair site: the blocks of the structure pieces of the village at the damaged positions. A piece
     * replaces the blocks of the pieces before it. The palisades come after the template pieces, and the surface of
     * the streets last.
     */
    private Target repairTarget(ServerLevel level, VillageReference reference) {
        Map<BuildPos, BuildBlock> blocks = new HashMap<>();
        for (VillageReference.Piece piece : reference.pieces()) {
            level.getStructureManager().get(piece.template()).ifPresent(found -> blocks.putAll(
                    ConstructionTargets.fromTemplate(level, found, piece.origin(),
                            GhostPlacement.settings(piece.rotation(), Mirror.NONE), repairPositions::contains)
                            .blocks()));
        }
        reference.palisadeBlocks().forEach((pos, state) -> {
            if (repairPositions.contains(pos)) {
                blocks.put(BlockKinds.toBuildPos(pos), BlockKinds.describe(pos, state, Optional.empty()));
            }
        });
        reference.streets().forEach((pos, state) -> {
            if (repairPositions.contains(pos)) {
                blocks.putIfAbsent(BlockKinds.toBuildPos(pos), BlockKinds.describe(pos, state, Optional.empty()));
            }
        });
        return new Target(blocks);
    }

    /**
     * The removal of the scaffolding of the site that stays, from the top. A new plan after a load does not remove
     * the scaffolding of the plan before it.
     */
    private void prepareCleanup(ServerLevel level) {
        List<Step> list = new ArrayList<>();
        List<BlockPos> cells = scaffolding.stream().filter(pos -> isScaffolding(level, pos))
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()).toList();
        scaffolding.retainAll(cells);
        for (BlockPos cell : cells) {
            list.add(simpleStep(StepType.REMOVE_SCAFFOLDING, cell, BuildBlock.AIR_STATE));
        }
        dirty = true;
        setSteps(list, true, null);
    }

    /** The removal of the fences of the site, in any order. */
    private void prepareFenceRemoval(ServerLevel level) {
        List<Step> list = new ArrayList<>();
        fences.removeIf(pos -> !level.getBlockState(pos).is(FENCE));
        for (BlockPos fence : fences) {
            list.add(simpleStep(StepType.REMOVE, fence, BuildBlock.AIR_STATE));
        }
        dirty = true;
        setSteps(list, false, null);
    }

    private static Step simpleStep(StepType type, BlockPos pos, String state) {
        return new Step(type, BlockKinds.toBuildPos(pos), state, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private void setSteps(List<Step> list, boolean ordered, @Nullable ConstructionRun newRun) {
        steps = List.copyOf(list);
        run = newRun;
        schedule = new SiteSchedule<>(steps.size(), ordered);
    }

    /**
     * Goes to the next phase. The builders claim the steps of the new phase at the next ticks.
     *
     * @return false at the end of the last phase
     */
    private boolean nextPhase() {
        Phase next = switch (phase) {
            case FENCES -> Phase.BUILD;
            case BUILD -> Phase.CLEANUP;
            case CLEANUP -> Phase.REMOVE_FENCES;
            case REMOVE_FENCES -> null;
        };
        if (next == null) {
            return false;
        }
        phase = next;
        steps = null;
        run = null;
        schedule = null;
        ways = null;
        dirty = true;
        return true;
    }

    private Step stepAt(int index) {
        List<Step> current = steps;
        if (current == null) {
            throw new IllegalStateException("The site has no steps now");
        }
        return current.get(index);
    }

    /** The blocks of a step: its block, and the other part of a block of two parts. */
    private List<BlockPos> cells(int index) {
        Step step = stepAt(index);
        BlockPos pos = BlockKinds.toBlockPos(step.pos());
        return step.partner().map(partner -> List.of(pos, BlockKinds.toBlockPos(partner))).orElse(List.of(pos));
    }

    /** The blocks of a step and of the steps before it that wait: a builder does not stand in them. */
    private List<BlockPos> busyCells(int index) {
        SiteSchedule<UUID> current = schedule();
        if (!current.ordered()) {
            return cells(index);
        }
        List<BlockPos> busy = new ArrayList<>();
        for (int i = current.next(); i <= index; i++) {
            if (i == index || !current.isApplied(i)) {
                busy.addAll(cells(i));
            }
        }
        return busy;
    }

    /** The block that a builder looks at during a step. */
    BlockPos lookPos(int index) {
        return BlockKinds.toBlockPos(stepAt(index).pos());
    }

    /** The cost of a step for a builder: the squared distance from the builder to the block of the step. */
    double claimCost(int index, Villager villager) {
        return lookPos(index).distToCenterSqr(villager.position());
    }

    /** The time of the work on a step, in ticks. The steps of the scaffolding take half the time. */
    int ticksFor(int index) {
        int ticks = WorldConfig.CONSTRUCTION_BUILDER_TICKS.get();
        return isScaffoldingStep(stepAt(index)) ? Math.max(1, ticks / 2) : ticks;
    }

    private static boolean isScaffoldingStep(Step step) {
        return step.type() == StepType.PLACE_SCAFFOLDING || step.type() == StepType.REMOVE_SCAFFOLDING;
    }

    /**
     * The block that must be in the reach zone of a builder for a step. For a step of the scaffolding, it is the
     * bottom of the column: a builder adds or removes the blocks of a column from its bottom, as a player does. For
     * the other steps, it is the block of the step.
     */
    BlockPos reachTarget(ServerLevel level, int index) {
        Step step = stepAt(index);
        BlockPos pos = BlockKinds.toBlockPos(step.pos());
        if (isScaffoldingStep(step) && isScaffolding(level, pos.below())) {
            return columnBase(level, pos.below());
        }
        return pos;
    }

    /**
     * True if a builder can work on a step from where it is. The builder stands on a floor, and the block of the step
     * is in its reach zone. The builder is not in the blocks of the step or on them, and not in the blocks of the
     * steps before it that wait. It is not in a scaffolding block: another builder can climb the column.
     */
    boolean canWorkFrom(ServerLevel level, Villager villager, int index) {
        if (schedule == null || !villager.onGround() || isScaffolding(level, villager.blockPosition())) {
            return false;
        }
        BlockPos target = reachTarget(level, index);
        if (!reach.contains(BlockKinds.toBuildPos(villager.blockPosition()), BlockKinds.toBuildPos(target))) {
            return false;
        }
        AABB body = villager.getBoundingBox();
        AABB space = new AABB(body.minX, body.minY - FLOOR_MARGIN, body.minZ, body.maxX, body.maxY, body.maxZ);
        for (BlockPos cell : busyCells(index)) {
            if (space.intersects(new AABB(cell))) {
                return false;
            }
        }
        return true;
    }

    /** The position to stand of the plan for a step, or empty: a step of the scaffolding has no such position. */
    Optional<BlockPos> plannedStand(int index) {
        return stepAt(index).stand().map(BlockKinds::toBlockPos);
    }

    /**
     * The position where a builder stands for a step: the position of the plan if it is free. Else, the free position
     * nearest to the builder with the block of the step in its reach zone, and with a way from the ground (see
     * {@link WalkSearch}). On a scaffolding column, the way goes to the bottom of the column. When the step can apply
     * and no free position has a way, it is the nearest free position: the builder goes there directly (see
     * {@link BuilderWorker}).
     *
     * @return the position, or null with no such position
     */
    @Nullable
    BlockPos standFor(ServerLevel level, int index, Villager villager) {
        Optional<BlockPos> planned = plannedStand(index);
        if (planned.isPresent() && isStandFor(level, planned.get(), index)) {
            return planned.get();
        }
        BlockPos target = reachTarget(level, index);
        List<BlockPos> free = new ArrayList<>();
        int horizontal = reach.horizontal();
        for (int dy = -reach.above(); dy <= reach.below(); dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    BlockPos feet = target.offset(dx, dy, dz);
                    if (isStandFor(level, feet, index)) {
                        free.add(feet);
                    }
                }
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        free.sort(Comparator.comparingDouble(feet -> feet.distToCenterSqr(villager.position())));
        for (BlockPos feet : free) {
            if (hasWay(level, feet)) {
                return feet;
            }
        }
        return schedule().canApply(index) ? free.get(0) : null;
    }

    /**
     * True if a builder can go from the ground to a position to stand, on the blocks of the level now (see
     * {@link WalkSearch}). On a scaffolding column, the way goes to the bottom of the column.
     */
    boolean hasWay(ServerLevel level, BlockPos feet) {
        return ways(level).canReach(BlockKinds.toBuildPos(feet));
    }

    /**
     * The search of a way from the ground on the blocks of the level now, in the box of the site and the reach around
     * it. The search stays for some ticks: each applied step tells it about its blocks (see {@link #apply}).
     */
    private WalkSearch ways(ServerLevel level) {
        WalkSearch current = ways;
        BoundingBox bounds = box;
        long now = level.getGameTime();
        if (current == null || now - waysTime > WAYS_TICKS) {
            List<BuildPos> corners = bounds == null ? List.of()
                    : List.of(new BuildPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                            new BuildPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
            current = WalkSearch.around(corners, reach,
                    pos -> BlockKinds.walk(level.getBlockState(BlockKinds.toBlockPos(pos))),
                    pos -> isScaffolding(level, BlockKinds.toBlockPos(pos)));
            ways = current;
            waysTime = now;
        }
        return current;
    }

    /**
     * The position near which a builder waits for a step when no position to stand is free (see {@link #standFor}).
     * For a position of the plan, it is the ground below the position: the bottom of its scaffolding column. Else, it
     * is the block of the step, or the bottom of its column for a step of the scaffolding.
     */
    BlockPos waitPos(ServerLevel level, int index) {
        return plannedStand(index).map(planned -> groundBelow(level, planned))
                .orElseGet(() -> reachTarget(level, index));
    }

    /**
     * True if a builder can stand at a position for a step. The position is free (see {@link #isStandFree}) and not in
     * the blocks of the step. The block of the step is in the reach zone.
     */
    boolean isStandFor(ServerLevel level, BlockPos feet, int index) {
        if (!reach.contains(BlockKinds.toBuildPos(feet), BlockKinds.toBuildPos(reachTarget(level, index)))) {
            return false;
        }
        List<BlockPos> busy = busyCells(index);
        if (busy.contains(feet) || busy.contains(feet.above()) || busy.contains(feet.below())) {
            return false;
        }
        return isStandFree(level, feet);
    }

    /**
     * True if an entity can stand at a position. The feet and the head have no collision, no lava and no
     * scaffolding, and a floor is below. A floor is a block with its top at the top of the block (a full block, a top
     * slab), or scaffolding.
     */
    public static boolean isStandFree(Level level, BlockPos feet) {
        return isPassable(level, feet) && isPassable(level, feet.above()) && isFloor(level, feet.below());
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA)
                && !state.is(Blocks.SCAFFOLDING);
    }

    private static boolean isFloor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SCAFFOLDING)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && Math.abs(shape.max(Direction.Axis.Y) - 1.0) < 1.0E-6;
    }

    /**
     * True if another builder of the site climbs or goes down the column of a scaffolding block, or is 1 block or more
     * above its bottom. Two builders at the bottom do not wait for each other: the first one climbs.
     */
    boolean isColumnTaken(ServerLevel level, BlockPos base, BuilderWorker self) {
        for (BuilderWorker other : workers.values()) {
            Villager villager = other == self ? null : villager(level, other);
            if (villager != null && villager.getBlockX() == base.getX() && villager.getBlockZ() == base.getZ()
                    && (other.isOnTheWay() || villager.getY() >= base.getY() + 1.0)) {
                return true;
            }
        }
        return false;
    }

    /** True if a step puts a block with a collision box: an entity in the block of this step blocks it. */
    private boolean placesSolidBlock(ServerLevel level, int index) {
        Step step = stepAt(index);
        return switch (phase) {
            case FENCES -> true;
            case BUILD -> step.type() == StepType.PLACE && run != null && run.state(step.state())
                    .map(state -> !state.getCollisionShape(level, BlockKinds.toBlockPos(step.pos())).isEmpty())
                    .orElse(false);
            case CLEANUP, REMOVE_FENCES -> false;
        };
    }

    /**
     * The living entities that block a step: the entities in the blocks of a step that puts a block with a collision
     * box, and the entities on the column of a step that removes a scaffolding block. An entity on the column is in the
     * removed block or in a block of the column above it, or on the top of the column: it falls when the block goes. An
     * entity on the floor of the removed block does not fall.
     */
    private List<LivingEntity> blockers(ServerLevel level, int index) {
        if (stepAt(index).type() == StepType.REMOVE_SCAFFOLDING) {
            BlockPos cell = BlockKinds.toBlockPos(stepAt(index).pos());
            BlockPos top = cell;
            for (int i = 0; i < MAX_COLUMN && isScaffolding(level, top.above()); i++) {
                top = top.above();
            }
            return level.getEntitiesOfClass(LivingEntity.class, new AABB(cell.getX(), cell.getY(), cell.getZ(),
                    cell.getX() + 1, top.getY() + ENTITY_SPACE, cell.getZ() + 1),
                    entity -> canBlock(entity) && entity.getY() > cell.getY() + FLOOR_MARGIN);
        }
        if (!placesSolidBlock(level, index)) {
            return List.of();
        }
        List<LivingEntity> found = new ArrayList<>();
        for (BlockPos cell : cells(index)) {
            found.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(cell), BuilderSite::canBlock));
        }
        return found;
    }

    private static boolean canBlock(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand);
    }

    /** True if an entity is in the blocks of a step: the step waits. */
    boolean isBlocked(ServerLevel level, int index) {
        return !blockers(level, index).isEmpty();
    }

    /**
     * Moves the entities in the blocks of a step to a free position near them. A player does not move: the builders do
     * not work while a player is in the work zone. A builder on the column of a removed scaffolding block goes down the
     * column.
     */
    void moveBlockersAside(ServerLevel level, int index) {
        Set<BlockPos> busy = new HashSet<>(cells(index));
        boolean removal = stepAt(index).type() == StepType.REMOVE_SCAFFOLDING;
        for (LivingEntity entity : blockers(level, index)) {
            if (entity instanceof Player) {
                continue;
            }
            BuilderWorker builder = workers.get(entity.getUUID());
            if (removal && builder != null && entity instanceof Villager villager) {
                builder.leaveColumn(level, villager);
                continue;
            }
            BlockPos aside = asidePos(level, entity.blockPosition(), busy);
            if (aside != null) {
                FolkholdWorld.LOGGER.debug("Builder site {}: {} moves out of step {} to {}", id, entity, index, aside);
                teleport(entity, aside);
            }
            if (builder != null) {
                builder.forgetPosition();
            }
        }
    }

    @Nullable
    private static BlockPos asidePos(ServerLevel level, BlockPos from, Set<BlockPos> busy) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -ASIDE_RADIUS; dx <= ASIDE_RADIUS; dx++) {
                for (int dz = -ASIDE_RADIUS; dz <= ASIDE_RADIUS; dz++) {
                    BlockPos feet = from.offset(dx, dy, dz);
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance && !busy.contains(feet) && !busy.contains(feet.above())
                            && isStandFree(level, feet)) {
                        best = feet;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Applies a step. The builders take the materials of the step from the stock of the village. Then the block
     * changes, with the sound of a placement or the particles of a break.
     *
     * @return the result: the step applied, or why it waits
     */
    Applied apply(ServerLevel level, int index) {
        return apply(level, index, true);
    }

    /**
     * Applies a step, as {@link #apply(ServerLevel, int)}.
     *
     * @param effects true for the sound and the particles of the change
     */
    private Applied apply(ServerLevel level, int index, boolean effects) {
        SiteSchedule<UUID> current = schedule();
        if (!current.canApply(index) || isBlocked(level, index)) {
            return Applied.BLOCKED;
        }
        Step step = stepAt(index);
        BlockPos pos = BlockKinds.toBlockPos(step.pos());
        BlockState before = level.getBlockState(pos);
        switch (phase) {
            case FENCES -> placeFence(level, pos);
            case BUILD -> {
                ConstructionRun plan = run;
                if (plan == null || plan.applied() != index) {
                    FolkholdWorld.LOGGER.error("Builder site {}: step {} is not the next step of the plan", id, index);
                    return Applied.BLOCKED;
                }
                if (VillageRepairData.get(level).isPlayerBlock(heart, pos)) {
                    // The builders never change a block that a player placed: the step goes with no change.
                    dirty = true;
                    plan.skipNext();
                    current.markApplied(index);
                    return Applied.APPLIED;
                }
                if (!materials.take(materialOf(level, index), settlement, level.getServer(), level.getGameTime())) {
                    return Applied.WAITING;
                }
                dirty = true;
                plan.applyNext();
                if (step.type() == StepType.PLACE_SCAFFOLDING) {
                    dirty |= scaffolding.add(pos);
                } else if (step.type() == StepType.REMOVE_SCAFFOLDING) {
                    dirty |= scaffolding.remove(pos);
                }
            }
            case CLEANUP -> {
                if (before.is(Blocks.SCAFFOLDING)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                dirty |= scaffolding.remove(pos);
            }
            case REMOVE_FENCES -> {
                if (before.is(FENCE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                dirty |= fences.remove(pos);
            }
        }
        if (effects) {
            effects(level, pos, before);
        }
        current.markApplied(index);
        WalkSearch search = ways;
        if (search != null) {
            for (BlockPos cell : cells(index)) {
                search.blockChanged(BlockKinds.toBuildPos(cell));
            }
        }
        return Applied.APPLIED;
    }

    /** Puts a fence at a position that is still free. The fence connects to the fences next to it. */
    private void placeFence(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(FENCE.defaultBlockState(), level, pos),
                    Block.UPDATE_ALL);
            dirty |= fences.add(pos);
        }
    }

    /** The sound of a placement, of a fluid, or the particles and the sound of a break. */
    private static void effects(ServerLevel level, BlockPos pos, BlockState before) {
        BlockState after = level.getBlockState(pos);
        if (after == before) {
            return;
        }
        if (after.isAir()) {
            if (!before.isAir()) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(before));
            }
            return;
        }
        if (!after.getFluidState().isEmpty() && (after.getBlock() instanceof LiquidBlock
                || after.getBlock() == before.getBlock())) {
            level.playSound(null, pos, after.getFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA
                    : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        SoundType sound = after.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * 0.8F);
    }

    /**
     * Marks the site as stopped by a command. When its chunks are ready, the site removes its fences and its
     * scaffolding, and ends.
     */
    void requestStop() {
        stopping = true;
        dirty = true;
    }

    /**
     * Ends the site now if its chunks are ready: the builders leave, and the fences and the scaffolding of the site go
     * away. Else the site ends at a later tick (see {@link #requestStop}).
     *
     * @return true if the site ended
     */
    boolean stopNow(ServerLevel level) {
        requestStop();
        if (box == null && !resolveBox(level)) {
            end(level, false);
            return true;
        }
        if (!isAreaReady(level)) {
            return false;
        }
        end(level, false);
        return true;
    }

    /**
     * The end of the site. The builders leave. A site that did not finish removes its fences and its scaffolding now.
     * After a finished site, the heart scans the village again: a new building can give places (see
     * {@link VillageHeartBlockEntity#requestScan()}). The operator that placed the site gets the end of the
     * construction: its client removes the ghost.
     */
    private void end(ServerLevel level, boolean finished) {
        for (BuilderWorker worker : workers.values()) {
            Villager villager = villager(level, worker);
            if (villager != null) {
                worker.release(villager);
            }
        }
        workers.clear();
        if (!finished) {
            removeBlocks(level);
        }
        VillageHeartBlockEntity heartEntity = ReadyChunks.isReady(level, heart)
                && level.getBlockEntity(heart) instanceof VillageHeartBlockEntity found ? found : null;
        Settlement village = heartEntity != null ? economyOf(level, heartEntity) : null;
        if (village != null) {
            materials.giveBack(village, level.getServer());
        }
        if (finished && heartEntity != null) {
            heartEntity.requestScan();
        }
        materials.withdrawOffers(level.getServer());
        if (repairReference != null) {
            VillageRepairs.onRepairEnded(level, heart, repairPositions);
        } else if (box != null) {
            VillageRepairData.get(level).addConstruction(heart, box);
        }
        UUID player = operator;
        ServerPlayer online = player == null ? null : level.getServer().getPlayerList().getPlayer(player);
        if (online != null) {
            ModNetwork.send(online, new ConstructionEndPayload(template, origin, finished));
            if (finished) {
                online.sendSystemMessage(Component.translatable(KEY + "builders_finished", template.toString()));
            }
        }
    }

    /** Removes the scaffolding of the site from the top, then its fences. */
    private void removeBlocks(ServerLevel level) {
        List<BlockPos> cells = scaffolding.stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed())
                .toList();
        for (BlockPos cell : cells) {
            if (ReadyChunks.isReady(level, cell) && isScaffolding(level, cell)) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos fence : fences) {
            if (ReadyChunks.isReady(level, fence) && level.getBlockState(fence).is(FENCE)) {
                level.setBlock(fence, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        scaffolding.clear();
        fences.clear();
    }

    static boolean isScaffolding(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.SCAFFOLDING);
    }

    /** True if an entity stands on scaffolding, or is in a scaffolding column above its bottom. */
    static boolean isOnScaffolding(Level level, Entity entity) {
        return isScaffolding(level, entity.blockPosition().below());
    }

    /** The bottom block of the scaffolding column of a scaffolding block. */
    static BlockPos columnBase(Level level, BlockPos cell) {
        BlockPos.MutableBlockPos pos = cell.mutable();
        for (int i = 0; i < MAX_COLUMN && isScaffolding(level, pos.below()); i++) {
            pos.move(Direction.DOWN);
        }
        return pos.immutable();
    }

    /** The position of the feet on the ground below a position: in the bottom block of a scaffolding column. */
    static BlockPos groundBelow(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos feet = pos.mutable();
        for (int i = 0; i < MAX_COLUMN && feet.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = feet.below();
            if (!isScaffolding(level, below) && isFloor(level, below)) {
                break;
            }
            feet.move(Direction.DOWN);
        }
        return feet.immutable();
    }

    /** Moves an entity to a position directly, with no fall damage. */
    static void teleport(LivingEntity entity, BlockPos feet) {
        entity.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        entity.resetFallDistance();
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /** The data of the site in the saved data of the level. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Template", template.toString());
        tag.putLong("Origin", origin.asLong());
        tag.putString("Rotation", rotation.name());
        tag.putString("Mirror", mirror.name());
        tag.putLong("Heart", heart.asLong());
        if (operator != null) {
            tag.putUUID("Operator", operator);
        }
        tag.putString("Phase", phase.serializedName());
        tag.putBoolean("Stopping", stopping);
        if (pendingSteps > 0.0) {
            tag.putDouble("PendingSteps", pendingSteps);
        }
        tag.putLongArray("Fences", fences.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Scaffolding", scaffolding.stream().mapToLong(BlockPos::asLong).toArray());
        tag.put("Materials", materials.save());
        if (repairReference != null) {
            CompoundTag repair = repairReference.save();
            repair.putLongArray("Positions", repairPositions.stream().mapToLong(BlockPos::asLong).toArray());
            tag.put("Repair", repair);
        }
        return tag;
    }

    /**
     * A site from the saved data of the level.
     *
     * @param tag the data of the site
     * @return the site, or empty for data that is not valid
     */
    static Optional<BuilderSite> load(CompoundTag tag) {
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("Template"));
        Optional<Rotation> rotation = enumValue(Rotation.class, tag.getString("Rotation"));
        Optional<Mirror> mirror = enumValue(Mirror.class, tag.getString("Mirror"));
        Optional<Phase> phase = enumValue(Phase.class, tag.getString("Phase").toUpperCase(Locale.ROOT));
        if (template == null || template.getPath().isEmpty() || rotation.isEmpty() || mirror.isEmpty()
                || phase.isEmpty() || !tag.contains("Origin", Tag.TAG_LONG) || !tag.contains("Heart", Tag.TAG_LONG)) {
            return Optional.empty();
        }
        UUID operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
        VillageReference reference = null;
        Set<BlockPos> positions = new LinkedHashSet<>();
        if (tag.contains("Repair", Tag.TAG_COMPOUND)) {
            CompoundTag repair = tag.getCompound("Repair");
            for (long pos : repair.getLongArray("Positions")) {
                positions.add(BlockPos.of(pos));
            }
            reference = VillageReference.load(BuiltInRegistries.BLOCK.asLookup(), repair).orElse(null);
            if (reference == null || positions.isEmpty()) {
                return Optional.empty();
            }
        }
        BuilderSite site = new BuilderSite(tag.getInt("Id"), template, BlockPos.of(tag.getLong("Origin")),
                rotation.get(), mirror.get(), BlockPos.of(tag.getLong("Heart")), operator, reference, positions);
        site.phase = phase.get();
        site.stopping = tag.getBoolean("Stopping");
        site.pendingSteps = Math.clamp(tag.getDouble("PendingSteps"), 0.0, MAX_PENDING_STEPS);
        for (long pos : tag.getLongArray("Fences")) {
            site.fences.add(BlockPos.of(pos));
        }
        for (long pos : tag.getLongArray("Scaffolding")) {
            site.scaffolding.add(BlockPos.of(pos));
        }
        site.materials.load(tag.getCompound("Materials"));
        return Optional.of(site);
    }

    private static <E extends Enum<E>> Optional<E> enumValue(Class<E> type, String name) {
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
