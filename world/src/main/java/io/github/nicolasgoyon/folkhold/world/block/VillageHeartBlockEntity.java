package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.faction.VillageNames;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.PostAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.VillageCat;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.Garrison;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import io.github.nicolasgoyon.folkhold.world.village.Handymen;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageBraziers;
import io.github.nicolasgoyon.folkhold.world.village.VillageClearing;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageHomes;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import io.github.nicolasgoyon.folkhold.world.village.schedule.VillageWorkers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.EventHooks;

/**
 * State of a village: id, faction, alert state and garrison. The units of the village keep the position of this block.
 *
 * <p>When a player is in the activation radius, the heart creates the missing units of its garrison.
 *
 * <p>The work of the heart reads and changes the blocks of the village. The heart does this work only when all the
 * chunks of the scan area are ready ({@link #scanArea}): the server thread never waits for the generation of a
 * chunk.
 */
public class VillageHeartBlockEntity extends BlockEntity {
    private static final String ID_TAG = "VillageId";
    private static final String ALERT_TAG = "Alert";
    private static final String LAST_THREAT_TAG = "LastThreat";
    private static final String GARRISON_TAG = "Garrison";
    /** The heart checks its garrison every 2 seconds. */
    private static final int GARRISON_INTERVAL = 40;
    /** In an alert, the heart rings the bells every 5 seconds. */
    private static final int BELL_INTERVAL = 100;
    /** A unit with a target shares it with the other units of the village at most once each second. */
    private static final int SHARE_INTERVAL = 20;
    /** The heart checks the rotation of the archers every 20 seconds. */
    private static final int ROTATION_INTERVAL = 400;
    /** The heart checks the end of the alert once each second. */
    private static final int CHECK_INTERVAL = 20;

    private UUID villageId = UUID.randomUUID();
    private AlertState alertState = AlertState.CALM;
    private long lastThreatTick;
    private final Garrison garrison = new Garrison();
    private final Map<UUID, Offense> offenses = new HashMap<>();
    /**
     * Players that attacked the village, with the game time when the hostility ends. All units of the village
     * attack these players on sight. Not saved: a server restart clears the list.
     */
    private final Map<UUID, Long> hostilePlayers = new HashMap<>();
    private final VillageLayout layout = new VillageLayout();
    private static final String LAYOUT_TAG = "Layout";
    private static final String EXITS_TAG = "ExitStairs";
    private static final String NIGHT_TAG = "NightMode";
    private static final String CATS_TAG = "CatsSpawned";
    private static final String CLEARED_TAG = "Cleared";
    private static final String SNOW_CLEARED_TAG = "SnowCleared";
    private static final String CAT_SPAWN_TAG = "LastCatSpawn";
    private static final String VILLAGERS_TAG = "VillagersSpawned";
    private static final String VILLAGER_SPAWN_TAG = "LastVillagerSpawn";
    private static final String VILLAGERS_FILLING_TAG = "VillagersFilling";
    /** The heart creates this number of villagers at most at each check of the garrison. */
    public static final int VILLAGER_GROUP = 4;
    private static final String FACTION_TAG = "Faction";
    private static final String NAME_TAG = "Name";
    private static final String MARKET_PLOT_TAG = "MarketPlot";

    /** Name of the village, or null before the village has a faction. */
    @Nullable
    private String villageName;
    /** Id of the faction that owns the village, or null before the first activation. */
    @Nullable
    private String factionId;
    /** A guard on night watch stays at this distance from the gate, toward the heart. */
    private static final int GATE_WATCH_DISTANCE = 2;
    /** A faction golem guards a gate at this distance from the middle of the gate, toward the heart. */
    private static final int GOLEM_GATE_DISTANCE = 4;
    /** True after the heart closed the gates and lit the braziers for the night. */
    private boolean nightMode;
    /** The period of the day of the last check of the braziers, or null before the first check. Not saved. */
    @Nullable
    private VillageBraziers.Period brazierPeriod;
    /** The handymen at work on the braziers. Not saved: the heart gives the work again after a load. */
    private final Handymen handymen = new Handymen();
    /** The structure pieces of the village for the repairs, or null. Not saved: the heart finds them after a load. */
    @Nullable
    private VillageReference repairReference;
    /** True after the search of {@link #repairReference}. */
    private boolean repairReferenceSearched;
    /** True if the last check of the growth found no free plot for the market. Not saved: the next check sets it. */
    private boolean marketNoSpace;
    /**
     * True if the last check of the growth found no free plot for a new home: no house-workshop of the tried jobs has
     * a piece and a free plot. Not saved: the next check sets it.
     */
    private boolean homeNoSpace;
    /** The plot of the market of the village, or null before the growth starts the market. */
    @Nullable
    private VillageMarket.Plot marketPlot;
    /** The positions of the last repairs, with the game time of their end. Not saved. */
    private final Map<BlockPos, Long> repaired = new HashMap<>();
    /** True when the stairs of the gates are built (see {@link VillageExits}). */
    private boolean exitStairs;
    /** True after the first cats of the village appeared. */
    private boolean catsSpawned;
    /** True after the heart removed the trees of the village (see {@link VillageClearing}). */
    private boolean cleared;
    /** True after the heart replaced the powder snow of the village (see {@link VillageClearing}). */
    private boolean snowCleared;
    /** Game time of the last creation of cats. */
    private long lastCatSpawn;
    /** True after the first inhabitants of the village appeared. */
    private boolean villagersSpawned;
    /** True while the heart creates the villagers of an incomplete fill (see {@link #ensureVillagers}). */
    private boolean villagersFilling;
    /** Game time of the last creation of villagers. */
    private long lastVillagerSpawn;
    /** The homes of the last record of the job posts (see {@link #recordJobPosts}), or null. Not saved. */
    @Nullable
    private VillageBeds.Homes homes;
    /** The places of the daily schedule of the workers. Not saved: the next check calculates them again. */
    private final VillageWorkers workers = new VillageWorkers();
    /** Game time of the last ring of the bells. Not saved. */
    private long lastBellTick = Long.MIN_VALUE / 2;
    /** Game time of the last shared target. Not saved. */
    private long lastShareTick = Long.MIN_VALUE / 2;
    /** True after the heart is linked to its settlement. Not saved: the heart links again after a load. */
    private boolean settlementBound;
    /** Players who pillaged the bank of the village. They open the bank chests with no confirmation. Not saved. */
    private final Set<UUID> pillagers = new HashSet<>();
    /** The player who breaks the heart, or null. Not saved. */
    @Nullable
    private ServerPlayer breaker;

    public VillageHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_HEART.get(), pos, state);
    }

    public UUID getVillageId() {
        return villageId;
    }

    public AlertState getAlertState() {
        return alertState;
    }

    /**
     * Records a threat. The village goes to {@link AlertState#ALERT} if it is calm.
     * The units of the village that have no target attack the attacker, if there is one.
     *
     * @param attacker the entity that attacked a unit of the village, or null if the threat is only seen
     */
    public void reportThreat(@Nullable LivingEntity attacker) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (attacker instanceof Player player && level.getServer() != null
                && Reputations.tier(level.getServer(), player, this).isFriendly()) {
            return;
        }
        lastThreatTick = level.getGameTime();
        if (alertState == AlertState.CALM) {
            setAlertState(AlertState.ALERT);
        }
        if (attacker instanceof Player player) {
            markHostile(player);
        }
        if (attacker != null) {
            for (FactionUnit unit : getUnits()) {
                if (unit.getTarget() == null && unit != attacker && unit.canTargetFromHome(attacker)) {
                    unit.setTarget(attacker);
                }
            }
        }
    }

    /**
     * Puts the player in the list of hostile players of the village, or extends the hostility. The hostility
     * ends after the calm delay (see {@link WorldConfig#CALM_DELAY_SECONDS}) without a new threat from the player.
     *
     * @param player the player
     */
    public void markHostile(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        hostilePlayers.put(player.getUUID(), level.getGameTime() + WorldConfig.CALM_DELAY_SECONDS.get() * 20L);
    }

    /**
     * True if all units of the village attack this player on sight: the player attacked the village, the
     * hostility did not end, and the reputation of the player is not friendly.
     *
     * @param player the player
     * @return true if the player is hostile to the village
     */
    public boolean isHostilePlayer(Player player) {
        Long until = hostilePlayers.get(player.getUUID());
        if (until == null || level == null) {
            return false;
        }
        boolean friendly = level.getServer() != null
                && Reputations.tier(level.getServer(), player, this).isFriendly();
        if (level.getGameTime() > until || friendly) {
            hostilePlayers.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Records an offense of a neutral player in the village (a broken block, an opened container).
     * The first offense in the offense window gives a warning. The next offenses make the units attack.
     * Each offense lowers the reputation.
     *
     * @param player the player
     */
    public void recordOffense(ServerPlayer player) {
        if (level == null || level.isClientSide() || factionId == null) {
            return;
        }
        long now = level.getGameTime();
        long window = WorldConfig.REPUTATION_OFFENSE_WINDOW_SECONDS.get() * 20L;
        Offense offense = offenses.computeIfAbsent(player.getUUID(), id -> new Offense());
        if (now - offense.lastTick > window) {
            offense.count = 0;
        }
        offense.count++;
        offense.lastTick = now;
        Reputations.apply(player.server, player, factionId, villageId.toString(),
                WorldConfig.REPUTATION_OFFENSE.get());
        if (offense.count == 1) {
            warn(player);
        } else {
            reportThreat(player);
        }
    }

    /** Number of offenses of the player in the current offense window. */
    public int offenseCount(Player player) {
        Offense offense = offenses.get(player.getUUID());
        return offense == null ? 0 : offense.count;
    }

    private void warn(ServerPlayer player) {
        List<FactionUnit> units = getUnits();
        FactionUnit speaker = units.stream()
                .filter(FactionUnit::canSpeak)
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                .orElse(null);
        Component name = speaker != null ? speaker.getDisplayName() : Component.translatable("entity.folkhold_world.guard");
        Notices.send(player, Component.translatable("message.folkhold_world.warning", name),
                Component.translatable("message.folkhold_world.warning.details"), Notices.Tone.BAD);
        if (speaker != null) {
            speaker.getLookControl().setLookAt(player);
            speaker.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
        }
    }

    /** Offenses of one player. Not saved: a server restart clears them. */
    private static final class Offense {
        private int count;
        private long lastTick;
    }

    /**
     * Changes the alert state and saves the change. When the village leaves the calm state, each archer goes to the
     * nearest free tower (see {@link #assignAlertPosts()}).
     */
    public void setAlertState(AlertState state) {
        if (alertState != state) {
            boolean wasCalm = alertState == AlertState.CALM;
            alertState = state;
            setChanged();
            if (wasCalm) {
                assignAlertPosts();
                ringBells();
            }
        }
    }

    /**
     * Rings the alarm bells of the village. The vanilla villagers near a bell go into the houses, as in a raid. A
     * bell in a chunk that is not ready does not ring. The loaded workers of the village also hear the bells far from
     * a bell: they stop their daily schedule and hide (see {@link VillageWorkers#hearAlarm}).
     */
    public void ringBells() {
        if (level == null || level.isClientSide()) {
            return;
        }
        lastBellTick = level.getGameTime();
        for (BlockPos bell : layout.bells()) {
            if (!ReadyChunks.isReady(level, bell)) {
                continue;
            }
            BlockState state = level.getBlockState(bell);
            if (state.getBlock() instanceof BellBlock bellBlock) {
                bellBlock.attemptToRing(null, level, bell, null);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            VillageWorkers.hearAlarm(serverLevel, VillageFolk.inhabitants(serverLevel, worldPosition));
        }
    }

    /**
     * Gives the target of one unit to the other units of the village that have no target, also to the units far
     * from the target. A unit of the village calls this method when it has a target.
     *
     * @param target the target
     */
    public void shareTarget(LivingEntity target) {
        if (level == null || level.isClientSide() || level.getGameTime() - lastShareTick < SHARE_INTERVAL) {
            return;
        }
        lastShareTick = level.getGameTime();
        for (FactionUnit unit : getUnits()) {
            if (unit.getTarget() == null && unit != target && unit.canTargetFromHome(target)) {
                unit.setTarget(target);
            }
        }
    }

    /** The loaded archers of the village. */
    private List<ArcherEntity> archers() {
        return getUnits().stream().filter(ArcherEntity.class::isInstance).map(ArcherEntity.class::cast).toList();
    }

    private List<PostAssignment.Post> postList() {
        List<PostAssignment.Post> result = new ArrayList<>();
        for (int i = 0; i < layout.posts().size(); i++) {
            BlockPos post = layout.posts().get(i);
            int view = i < layout.views().size() ? layout.views().get(i) : 0;
            result.add(new PostAssignment.Post(post.getX(), post.getY(), post.getZ(), view));
        }
        return result;
    }

    private void movePost(FactionUnit unit, BlockPos post) {
        if (!post.equals(unit.getPostPos())) {
            unit.setPostPos(post);
            unit.setHomePos(post);
            garrison.setPost(unit.getUUID(), post);
            setChanged();
        }
    }

    /**
     * Each loaded archer goes to the nearest free valid tower. If possible, only one archer goes on each tower.
     */
    public void assignAlertPosts() {
        if (!layout.isScanned()) {
            return;
        }
        List<ArcherEntity> archers = archers();
        List<PostAssignment.Position> positions = archers.stream()
                .map(archer -> new PostAssignment.Position(archer.getBlockX(), archer.getBlockY(), archer.getBlockZ()))
                .toList();
        int[] posts = PostAssignment.assignNearest(positions, postList(), VillageLayout.MIN_VIEW);
        for (int i = 0; i < archers.size(); i++) {
            if (posts[i] >= 0) {
                movePost(archers.get(i), layout.posts().get(posts[i]));
            }
        }
    }

    /**
     * Calm rotation: each loaded archer moves to another free valid tower with the given chance. The view score
     * of a tower is its weight.
     *
     * @param random the random source
     * @param chance the chance for each archer to move (0 to 1)
     */
    public void rotateArchers(RandomSource random, double chance) {
        if (!layout.isScanned()) {
            return;
        }
        List<PostAssignment.Post> posts = postList();
        List<ArcherEntity> archers = archers();
        for (ArcherEntity archer : archers) {
            if (archer.getTarget() != null || random.nextDouble() >= chance) {
                continue;
            }
            Set<Integer> taken = new HashSet<>();
            for (ArcherEntity other : archers) {
                if (other != archer && other.getPostPos() != null) {
                    taken.add(layout.posts().indexOf(other.getPostPos()));
                }
            }
            int current = archer.getPostPos() == null ? -1 : layout.posts().indexOf(archer.getPostPos());
            SplittableRandom pick = new SplittableRandom(random.nextLong());
            PostAssignment.pickCalm(posts, taken, current, VillageLayout.MIN_VIEW, pick)
                    .ifPresent(index -> movePost(archer, layout.posts().get(index)));
        }
    }

    /** The loaded units of this village: units in the village radius that are linked to this heart. */
    public List<FactionUnit> getUnits() {
        if (level == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(FactionUnit.class, VillageHearts.area(worldPosition),
                unit -> worldPosition.equals(unit.getVillagePos()));
    }

    public Garrison getGarrison() {
        return garrison;
    }

    /** The id of the faction that owns the village, or null before the first activation. */
    @Nullable
    public String getFactionId() {
        return factionId;
    }

    /**
     * The name of the village, from the culture of its faction (see {@link VillageNames}). The heart makes the name
     * the first time and saves it.
     *
     * @return the name, or null before the village has a faction (or on the client)
     */
    @Nullable
    public String getVillageName() {
        if (villageName == null && level != null) {
            FactionVisuals.faction(level.getServer(), factionId)
                    .flatMap(faction -> Cultures.byId(faction.cultureId()))
                    .ifPresent(culture -> {
                        villageName = VillageNames.name(culture.info(),
                                villageId.getMostSignificantBits() ^ villageId.getLeastSignificantBits());
                        setChanged();
                    });
        }
        return villageName;
    }

    /**
     * Links the heart to its settlement (see {@link Settlements#bind}). After this call, the village has a
     * faction and a name.
     */
    public void ensureSettlement() {
        if (!settlementBound && level instanceof ServerLevel serverLevel) {
            Settlements.bind(serverLevel, this);
            settlementBound = true;
        }
    }

    /**
     * Gives the id, the faction and the name of the settlement to the heart. {@link Settlements#bind} calls this
     * method, and {@link Settlements#restore} calls it before the binding, so that a new heart links to the
     * settlement it restores.
     *
     * @param id        the id of the settlement
     * @param faction   the id of the faction of the settlement
     * @param name      the name of the settlement
     */
    public void adoptSettlement(UUID id, String faction, String name) {
        if (!id.equals(villageId) || !faction.equals(factionId) || !name.equals(villageName)) {
            villageId = id;
            factionId = faction;
            villageName = name;
            setChanged();
        }
    }

    /** The posts, gates and braziers of the village. The heart scans them before the first garrison. */
    public VillageLayout getLayout() {
        return layout;
    }

    /**
     * Puts the gates of the palisade into the settlement: the routes of the caravans go through them (see
     * {@link io.github.nicolasgoyon.folkhold.core.sim.group.RouteGates}). A gate in a chunk that is not loaded is not
     * in the list.
     */
    public void recordGates() {
        if (!(level instanceof ServerLevel serverLevel) || !layout.isScanned()) {
            return;
        }
        List<Settlement.Gate> gates = new ArrayList<>();
        for (VillageExits.Approach approach : VillageExits.approaches(serverLevel, worldPosition, layout.gates())) {
            BlockPos middle = approach.gates().get(approach.gates().size() / 2);
            gates.add(new Settlement.Gate(middle.getX(), middle.getZ(), approach.point().getX(),
                    approach.point().getZ()));
        }
        Settlements.of(serverLevel.getServer(), this).ifPresent(settlement -> {
            if (!gates.isEmpty() && settlement.setGates(gates)) {
                SettlementData.get(serverLevel.getServer()).setDirty();
            }
        });
    }

    /**
     * Records the beds of the homes of the village in the settlement (see {@link VillageBeds}), from the last scan of
     * the village.
     */
    public void recordBeds() {
        if (!(level instanceof ServerLevel serverLevel) || !layout.isScanned()) {
            return;
        }
        int beds = VillageBeds.homeBeds(serverLevel, layout.beds(), repairReference(serverLevel).orElse(null));
        Settlements.of(serverLevel.getServer(), this).ifPresent(settlement -> {
            if (settlement.setBeds(beds)) {
                SettlementData.get(serverLevel.getServer()).setDirty();
            }
        });
    }

    /**
     * Records the job posts of the village in the settlement (see {@link JobPosts}), from the last scan of the village.
     * The posts with a food job that trades come first, then the posts with an other job that trades (see
     * {@link JobPosts#order}). A heart with no village structure has no homes to find: its
     * posts stay unknown, and the village keeps the job slots of its level.
     */
    public void recordJobPosts() {
        if (!(level instanceof ServerLevel serverLevel) || !layout.isScanned()) {
            return;
        }
        Optional<VillageReference> reference = repairReference(serverLevel);
        if (reference.isEmpty()) {
            return;
        }
        homes = VillageBeds.homes(serverLevel, worldPosition, layout.jobSigns(), layout.beds(), reference.get());
        List<JobPosts.Post> scanned = homes.posts();
        Settlements.of(serverLevel.getServer(), this).ifPresent(settlement -> {
            List<JobPosts.Post> posts = JobPosts.order(settlement.jobPosts(), scanned, Economies::producesFood,
                    Merchants::trades);
            if (settlement.setJobPosts(Optional.of(posts))) {
                SettlementData.get(serverLevel.getServer()).setDirty();
            }
        });
    }

    /**
     * Gives a new job to each job post that needs a job (see {@link Economies#needsNewJob} and
     * {@link JobAssignment#renew}): a job sign with no job (for example a blank job sign that a player places in a
     * home), or a job that is not valid in the biome. The heart writes the new job and its icon (the first output item
     * of the job) on the job sign. The blocks of the building do not change. The other jobs do not change: a post with
     * a minimum level above the level of the village, or with no job file, is inactive. The heart does not change the
     * blocks of a legacy village. A post with a sign in a chunk that is not loaded keeps its job until a later check.
     */
    public void renewJobPosts() {
        if (!(level instanceof ServerLevel serverLevel) || !layout.isScanned()) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        Settlements.of(server, this).ifPresent(settlement -> {
            if (settlement.legacyJobs() || settlement.jobPosts().isEmpty()) {
                return;
            }
            List<JobPosts.Post> posts = settlement.jobPosts().get();
            Predicate<String> needsJob = job -> Economies.needsNewJob(server, settlement, job);
            if (posts.stream().map(JobPosts.Post::jobId).noneMatch(needsJob)) {
                return;
            }
            List<JobPosts.Post> renewed = JobAssignment.renew(posts, needsJob,
                    Economies.eligibleJobs(server, settlement), EconomyData.catalog(), Merchants::trades,
                    Settlements.seed(settlement.id()), post -> writeJobSign(serverLevel, post));
            if (!renewed.equals(posts)) {
                settlement.setJobPosts(Optional.of(JobPosts.order(Optional.of(renewed), renewed,
                        Economies::producesFood, Merchants::trades)));
                SettlementData.get(server).setDirty();
                setChanged();
            }
        });
    }

    /** Writes the job of a post and its icon on its job sign. Returns false when the sign is not there. */
    private boolean writeJobSign(ServerLevel serverLevel, JobPosts.Post post) {
        BlockPos pos = new BlockPos(post.x(), post.y(), post.z());
        if (!serverLevel.isLoaded(pos) || !(serverLevel.getBlockEntity(pos) instanceof JobSignBlockEntity sign)) {
            return false;
        }
        Job job = EconomyData.jobs().get(post.jobId());
        ResourceLocation icon = job == null || job.recipe().outputs().isEmpty() ? null
                : ResourceLocation.tryParse(job.recipe().outputs().get(0).item());
        String prefix = FolkholdWorld.MOD_ID + ":";
        String signJob = post.jobId().startsWith(prefix) ? post.jobId().substring(prefix.length()) : post.jobId();
        FolkholdWorld.LOGGER.info("Village {}: the job post at {} gets the job {} (job of the sign: '{}')",
                worldPosition, pos, post.jobId(), sign.job());
        sign.setJob(signJob, icon);
        layout.setJob(pos, post.jobId());
        return true;
    }

    /**
     * Removes the trees of the village and replaces its powder snow with snow blocks, once, before its first garrison
     * (see {@link VillageClearing}). Each of the 2 steps occurs once, and a call does one step at most: the 2 large
     * reads of blocks do not occur in the same tick. A heart with no village structure has nothing to clear. The heart
     * waits while a chunk of a step is not ready.
     *
     * @param serverLevel the level of the heart
     */
    public void clearTrees(ServerLevel serverLevel) {
        Optional<VillageReference> reference = repairReference(serverLevel);
        if (reference.isEmpty()) {
            cleared = true;
            snowCleared = true;
            setChanged();
            return;
        }
        if (!cleared) {
            Optional<Integer> removed = VillageClearing.clear(serverLevel, worldPosition, reference.get());
            if (removed.isPresent()) {
                FolkholdWorld.LOGGER.debug("Village {}: {} blocks of trees removed", worldPosition, removed.get());
                cleared = true;
                setChanged();
            }
            return;
        }
        if (!snowCleared) {
            Optional<Integer> replaced = VillageClearing.replacePowderSnow(serverLevel, worldPosition,
                    reference.get());
            if (replaced.isPresent()) {
                FolkholdWorld.LOGGER.debug("Village {}: {} blocks of powder snow replaced", worldPosition,
                        replaced.get());
                snowCleared = true;
                setChanged();
            }
        }
    }

    /**
     * Asks for a new scan of the village (see {@link VillageLayout}): the next check of the garrison scans the
     * blocks again. Examples: the end of a construction site, a bed that a player places or breaks.
     */
    public void requestScan() {
        layout.invalidate();
        setChanged();
    }

    /**
     * Creates the missing units of the garrison now (see {@link Garrison#ensure}). Before the first garrison and after
     * a request (see {@link #requestScan()}), the heart scans the village. The method does nothing while a chunk of
     * the scan area is not ready ({@link #scanArea}).
     *
     * @param now the game time to use
     */
    public void ensureGarrison(long now) {
        if (!(level instanceof ServerLevel serverLevel)
                || !VillageLayout.isAreaReady(serverLevel, scanArea(serverLevel))) {
            return;
        }
        ensureSettlement();
        if (!layout.isScanned()) {
            layout.scan(serverLevel, scanArea(serverLevel));
            recordMarketPlot(serverLevel);
            paintBanners();
            setChanged();
        }
        layout.questBoards().forEach(board -> QuestBoardFeature.startUpdates(serverLevel, board));
        if (!exitStairs && VillageExits.build(serverLevel, worldPosition, layout.gates()) >= 0) {
            exitStairs = true;
            setChanged();
        }
        recordGates();
        recordBeds();
        recordJobPosts();
        renewJobPosts();
        if (alertState == AlertState.CALM && ensureCats(serverLevel, now)) {
            setChanged();
        }
        if (alertState == AlertState.CALM && ensureVillagers(serverLevel, now)) {
            setChanged();
        }
        Map<GarrisonRole, List<BlockPos>> posts = Map.of(GarrisonRole.ARCHER, layout.validPosts(),
                GarrisonRole.GOLEM, golemPosts());
        if (garrison.ensure(serverLevel, worldPosition, factionId, alertState == AlertState.CALM, now, posts,
                garrisonTraits())) {
            setChanged();
        }
    }

    /**
     * The garrison of the level of the settlement of the heart (see {@code garrison} in the level table). A heart with
     * no settlement uses the level 1.
     */
    public GarrisonTraits garrisonTraits() {
        int settlementLevel = 1;
        if (level != null && level.getServer() != null) {
            settlementLevel = Settlements.of(level.getServer(), this).map(Settlement::level).orElse(1);
        }
        return LevelTables.get().traits(settlementLevel).garrison();
    }

    /** The loaded cats of this village. */
    public List<VillageCat> getCats() {
        if (level == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(VillageCat.class, VillageHearts.area(worldPosition),
                cat -> worldPosition.equals(cat.getVillagePos()));
    }

    /**
     * Number of cats of the village: the number of the level (see {@code cats} in the level table), and at least
     * one cat for each gate.
     */
    public int catCount() {
        int fromLevel = 1;
        if (level != null && level.getServer() != null) {
            fromLevel = Settlements.of(level.getServer(), this)
                    .map(settlement -> LevelTables.get().traits(settlement.level()).cats())
                    .orElse(1);
        }
        return Math.max(fromLevel, catAnchors().size() - 1);
    }

    /**
     * The anchors of the cats: one position inside each gate, then the position next to the heart. The gate
     * blocks of one gate are side by side, so the method keeps one block for each group of gate blocks.
     */
    public List<BlockPos> catAnchors() {
        List<BlockPos> anchors = new ArrayList<>();
        List<BlockPos> seen = new ArrayList<>();
        for (BlockPos gate : floorGates()) {
            boolean sameGate = seen.stream().anyMatch(other -> other.distManhattan(gate) <= 2);
            seen.add(gate);
            if (!sameGate) {
                anchors.add(gateWatchPosition(gate));
            }
        }
        anchors.add(worldPosition.offset(2, 0, 2));
        return anchors;
    }

    /**
     * Creates the missing cats of the village. The first cats appear at once. A dead cat comes back after the
     * refill delay of the garrison.
     *
     * @param serverLevel the level
     * @param now         the current game time
     * @return true if the heart must save
     */
    private boolean ensureCats(ServerLevel serverLevel, long now) {
        List<VillageCat> cats = getCats();
        int missing = catCount() - cats.size();
        long refill = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        if (missing <= 0 || (catsSpawned && now - lastCatSpawn < refill)) {
            return false;
        }
        List<BlockPos> anchors = catAnchors();
        for (int i = 0; i < missing; i++) {
            BlockPos anchor = anchors.get((cats.size() + i) % anchors.size());
            VillageCat cat = ModEntities.VILLAGE_CAT.get().create(serverLevel);
            if (cat == null) {
                continue;
            }
            cat.moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5,
                    serverLevel.random.nextFloat() * 360.0F, 0.0F);
            cat.setAnchorPos(anchor);
            cat.setVillagePos(worldPosition);
            EventHooks.finalizeMobSpawn(cat, serverLevel, serverLevel.getCurrentDifficultyAt(anchor), MobSpawnType.STRUCTURE, null);
            serverLevel.addFreshEntity(cat);
        }
        catsSpawned = true;
        lastCatSpawn = now;
        return true;
    }

    /**
     * Creates the missing villagers of the village: the inhabitants ({@link VillageFolk#inhabitantCount}) with the
     * professions of the settlement ({@link VillageFolk#professions}). When the jobs change, the inhabitants get the
     * new professions at once ({@link VillageFolk#assign}). Then the inhabitants get their beds ({@link VillageHomes})
     * and the workers get the places of their daily schedule ({@link VillageWorkers}).
     *
     * <ul>
     *   <li>The villagers appear in groups of {@link #VILLAGER_GROUP} at most, one group at each call. A large
     *       village fills in some seconds, with no spike of work on the server.</li>
     *   <li>The first villagers of the village and the rest of an incomplete fill come at the next calls. After a
     *       complete fill, the missing villagers (a dead villager, a larger population) come after the refill delay
     *       of the garrison.</li>
     * </ul>
     *
     * @param serverLevel the level
     * @param now         the current game time
     * @return true if the heart must save
     */
    private boolean ensureVillagers(ServerLevel serverLevel, long now) {
        Optional<Settlement> settlement = Settlements.of(serverLevel.getServer(), this).filter(Settlement::isActive);
        if (settlement.isEmpty()) {
            return false;
        }
        List<VillageFolk.Seat> seats = VillageFolk.seats(settlement.get(),
                VillageFolk.inhabitantCount(settlement.get()));
        List<Villager> inhabitants = VillageFolk.inhabitants(serverLevel, worldPosition);
        List<VillagerProfession> missingInhabitants =
                VillageFolk.assign(inhabitants, seats.stream().map(VillageFolk.Seat::profession).toList());
        List<BlockPos> beds = homeBeds(settlement.get(), seats);
        VillageHomes.claim(serverLevel, worldPosition, inhabitants, seats, beds);
        workers.update(serverLevel, this, inhabitants, seats, beds, settlement.get().jobPosts().orElse(List.of()));
        int missing = missingInhabitants.size();
        if (missing == 0) {
            boolean changed = villagersFilling;
            villagersFilling = false;
            return changed;
        }
        long refill = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        if (villagersSpawned && !villagersFilling && now - lastVillagerSpawn < refill) {
            return false;
        }
        int spawned = 0;
        for (VillagerProfession profession : missingInhabitants) {
            if (spawned >= VILLAGER_GROUP) {
                break;
            }
            VillageFolk.spawn(serverLevel, worldPosition, profession);
            spawned++;
        }
        villagersSpawned = true;
        villagersFilling = spawned < missing;
        lastVillagerSpawn = now;
        return true;
    }

    /**
     * Gives to the loaded inhabitants the beds of their places (see {@link VillageHomes}): the household of a home
     * sleeps in the home. A village with no list of posts keeps the vanilla rules for the beds.
     *
     * @return the number of villagers with a new bed
     */
    public int claimHomeBeds() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return Settlements.of(serverLevel.getServer(), this).map(settlement -> {
            List<VillageFolk.Seat> seats = VillageFolk.seats(settlement, VillageFolk.inhabitantCount(settlement));
            return VillageHomes.claim(serverLevel, worldPosition, VillageFolk.inhabitants(serverLevel, worldPosition),
                    seats, homeBeds(settlement, seats));
        }).orElse(0);
    }

    /**
     * Gives to the loaded workers their daily schedule, their job sign and their meeting point (see
     * {@link VillageWorkers}).
     *
     * @return the number of changes in the brains of the workers
     */
    public int updateWorkers() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return Settlements.of(serverLevel.getServer(), this).map(settlement -> {
            List<VillageFolk.Seat> seats = VillageFolk.seats(settlement, VillageFolk.inhabitantCount(settlement));
            return workers.update(serverLevel, this, VillageFolk.inhabitants(serverLevel, worldPosition), seats,
                    homeBeds(settlement, seats), settlement.jobPosts().orElse(List.of()));
        }).orElse(0);
    }

    /** The places of the daily schedule of the workers. */
    public VillageWorkers workers() {
        return workers;
    }

    /** The bed of each place (see {@link VillageHomes#beds}). A village with no list of posts gives no bed. */
    private List<BlockPos> homeBeds(Settlement settlement, List<VillageFolk.Seat> seats) {
        if (homes == null || settlement.jobPosts().isEmpty()) {
            return List.of();
        }
        return VillageHomes.beds(seats, settlement.jobPosts().get(), homes);
    }

    /** Gives the banner of the faction to the banners of the village. */
    private void paintBanners() {
        if (level == null) {
            return;
        }
        FactionVisuals.faction(level.getServer(), factionId)
                .ifPresent(faction -> layout.banners().forEach(pos -> FactionVisuals.paintBanner(level, pos, faction)));
    }

    /**
     * Records the death of a unit of this village.
     *
     * @param unitId the id of the unit
     */
    public void onUnitDied(UUID unitId) {
        if (level != null && garrison.onUnitDied(unitId, level.getGameTime())) {
            setChanged();
        }
    }

    /**
     * The scan area of the village (see {@link VillageLayout#area}): the area of the village structure, or a box around
     * the heart.
     *
     * @param serverLevel the level of the heart
     * @return the box
     */
    public BoundingBox scanArea(ServerLevel serverLevel) {
        return VillageLayout.area(worldPosition, repairReference(serverLevel).orElse(null));
    }

    /**
     * The structure pieces of the village for the repairs (see {@link VillageRepairs}). The heart looks for them once
     * after a load.
     *
     * @param serverLevel the level of the heart
     * @return the pieces, or empty for a heart outside a village structure
     */
    public Optional<VillageReference> repairReference(ServerLevel serverLevel) {
        if (!repairReferenceSearched) {
            repairReferenceSearched = true;
            repairReference = VillageReference.find(serverLevel, worldPosition).orElse(null);
        }
        return Optional.ofNullable(repairReference);
    }

    /**
     * Sets the structure pieces of the village for the repairs, for example for a village that a test places.
     *
     * @param reference the pieces, or null for no repairs
     */
    public void setRepairReference(@Nullable VillageReference reference) {
        repairReference = reference;
        repairReferenceSearched = true;
    }

    /** True if the last check of the growth found no free plot for the market (see {@link VillageMarket.State}). */
    public boolean isMarketNoSpace() {
        return marketNoSpace;
    }

    /** Records the result of the last check of the growth for the market (see {@link VillageGrowth}). */
    public void setMarketNoSpace(boolean noSpace) {
        marketNoSpace = noSpace;
    }

    /**
     * True if the last check of the growth found no free plot for a new home (see {@link VillageGrowth}): the village
     * needs a home or a first post, and no house-workshop of the tried jobs has a piece and a free plot. The heart
     * screen shows it.
     */
    public boolean isHomeNoSpace() {
        return homeNoSpace;
    }

    /** Records the result of the last check of the growth for a new home (see {@link VillageGrowth}). */
    public void setHomeNoSpace(boolean noSpace) {
        homeNoSpace = noSpace;
    }

    /**
     * The plot of the market of the village (see {@link VillageMarket.Plot}). The growth builds the market again on
     * this plot when the market is broken or not finished.
     *
     * @return the plot, or empty before the growth starts the market
     */
    public Optional<VillageMarket.Plot> marketPlot() {
        return Optional.ofNullable(marketPlot);
    }

    /**
     * Scans the village again now when a market sign of the scan is not a market any more: a player, a raid or an
     * explosion broke the market sign or the market post. No block event tells the heart about all these changes.
     * Then the market is not in the scan, and the growth builds it again on its plot (see {@link VillageGrowth}). The
     * growth calls this method before its steps: with the setting {@code construction.growth} on, builders and no
     * site. A market sign or a market post in a chunk that is not ready is not broken.
     *
     * @param serverLevel the level of the heart
     * @return true if the heart scanned the village
     */
    public boolean checkMarkets(ServerLevel serverLevel) {
        boolean broken = layout.markets().stream().anyMatch(pos -> VillageLayout.isMarketBroken(serverLevel, pos));
        if (!broken || !VillageLayout.isAreaReady(serverLevel, scanArea(serverLevel))) {
            return false;
        }
        layout.scan(serverLevel, scanArea(serverLevel));
        recordMarketPlot(serverLevel);
        paintBanners();
        setChanged();
        return true;
    }

    /**
     * Records the plot of a market of the village structure after a scan: the market piece that holds the first market
     * sign. A heart with a plot keeps it.
     */
    private void recordMarketPlot(ServerLevel serverLevel) {
        if (marketPlot != null || layout.markets().isEmpty()) {
            return;
        }
        repairReference(serverLevel)
                .flatMap(reference -> VillageMarket.generatedPlot(serverLevel, reference, layout.markets().get(0)))
                .ifPresent(this::setMarketPlot);
    }

    /**
     * Sets the plot of the market of the village (see {@link VillageGrowth}).
     *
     * @param plot the plot, or null for no plot
     */
    public void setMarketPlot(@Nullable VillageMarket.Plot plot) {
        if (!Objects.equals(marketPlot, plot)) {
            marketPlot = plot;
            setChanged();
        }
    }

    /**
     * The trade place of the village: the area of its market, or the area of its plaza with no market (see
     * {@link VillageMarket}).
     *
     * @param serverLevel the level of the heart
     * @return the trade place
     */
    public VillageMarket.TradePlace tradePlace(ServerLevel serverLevel) {
        return VillageMarket.tradePlace(serverLevel, this);
    }

    /**
     * The state of the market of the village (see {@link VillageMarket}).
     *
     * @param serverLevel the level of the heart
     * @return the state
     */
    public VillageMarket.State marketState(ServerLevel serverLevel) {
        return VillageMarket.state(serverLevel, this);
    }

    /** True if a repair of the village ended at a position less than {@link VillageRepairs#RETRY_TICKS} ago. */
    public boolean isRecentlyRepaired(BlockPos pos, long now) {
        Long end = repaired.get(pos);
        return end != null && now - end < VillageRepairs.RETRY_TICKS;
    }

    /**
     * Records the end of a repair: the heart keeps its positions, and gives the color of the faction to the banners
     * again.
     *
     * @param positions the positions of the repair
     * @param now       the game time
     */
    public void onRepaired(Set<BlockPos> positions, long now) {
        repaired.values().removeIf(end -> now - end >= VillageRepairs.RETRY_TICKS);
        positions.forEach(pos -> repaired.put(pos.immutable(), now));
        paintBanners();
    }

    /**
     * Applies the day or the night state to the village.
     *
     * <ul>
     *   <li>Night: the gates close, the braziers burn, and the guards (not the captain, not the units with a
     *       post) watch the gates.</li>
     *   <li>Day: the gates open, and the guards patrol around their home again. In a village with no handymen, the
     *       braziers go out. In a village with handymen, the braziers burn until the handymen put them out in the
     *       morning (see {@link VillageBraziers}).</li>
     *   <li>A campfire of the layout becomes a brazier of the block library, which does not burn the entities.</li>
     * </ul>
     *
     * @param night true for the night state
     */
    public void applyDayNight(boolean night) {
        if (level == null || level.isClientSide()) {
            return;
        }
        nightMode = night;
        setChanged();
        boolean soundPlayed = false;
        for (BlockPos gate : layout.gates()) {
            BlockState gateState = level.getBlockState(gate);
            if (gateState.getBlock() instanceof FenceGateBlock && gateState.getValue(FenceGateBlock.OPEN) == night) {
                level.setBlock(gate, gateState.setValue(FenceGateBlock.OPEN, !night), Block.UPDATE_ALL);
                if (!soundPlayed) {
                    level.playSound(null, gate, night ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    soundPlayed = true;
                }
            }
        }
        if (night || !(level instanceof ServerLevel serverLevel) || !Handymen.exist(serverLevel, worldPosition)) {
            setBraziers(night);
        }
        assignGateWatch(night);
    }

    /** Lights all the braziers of the village, or puts them all out. */
    private void setBraziers(boolean lit) {
        if (level == null) {
            return;
        }
        for (BlockPos brazier : layout.braziers()) {
            VillageBraziers.set(level, brazier, lit);
        }
    }

    /**
     * The work of the braziers at each check. At the start of the day, the heart puts out the braziers that burn: the
     * braziers that the handymen did not put out in the morning. Then the heart gives the work of the current period
     * to the handymen: in the evening they light the braziers, in the morning they put them out.
     */
    private void updateBraziers(ServerLevel serverLevel) {
        VillageBraziers.Period period = VillageBraziers.period(serverLevel.getDayTime(), nightMode);
        if (period != brazierPeriod) {
            brazierPeriod = period;
            if (period == VillageBraziers.Period.DAY) {
                setBraziers(false);
            }
        }
        Optional<Boolean> lit = switch (period) {
            case EVENING -> Optional.of(true);
            case MORNING -> Optional.of(false);
            case DAY, NIGHT -> Optional.empty();
        };
        handymen.update(serverLevel, worldPosition, layout.braziers(), lit);
    }

    private void assignGateWatch(boolean night) {
        List<BlockPos> gates = floorGates();
        int index = 0;
        for (FactionUnit unit : getUnits()) {
            if (!(unit instanceof GuardEntity) || unit instanceof CaptainEntity || unit.getPostPos() != null) {
                continue;
            }
            if (night && !gates.isEmpty()) {
                unit.setNightPos(gateWatchPosition(gates.get(index % gates.size())));
                index++;
            } else {
                unit.setNightPos(null);
            }
        }
    }

    /**
     * The fence gates of the village at the floor level: the fence gates with no fence gate of the village below them.
     * A gate can have fence gates stacked in its opening, and the stacked fence gates are above the floor.
     */
    private List<BlockPos> floorGates() {
        List<BlockPos> gates = layout.gates();
        return gates.stream().filter(gate -> !gates.contains(gate.below())).toList();
    }

    /** The position at {@link #GATE_WATCH_DISTANCE} blocks from the gate, toward the heart. */
    private BlockPos gateWatchPosition(BlockPos gate) {
        return towardHeart(gate, GATE_WATCH_DISTANCE);
    }

    /** The position at the given distance from a gate block, toward the heart, on the main axis to the heart. */
    private BlockPos towardHeart(BlockPos gate, int distance) {
        int dx = worldPosition.getX() - gate.getX();
        int dz = worldPosition.getZ() - gate.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return gate.offset(Integer.signum(dx) * distance, 0, 0);
        }
        return gate.offset(0, 0, Integer.signum(dz) * distance);
    }

    /**
     * The posts of the faction golems: one position inside each gate, at {@link #GOLEM_GATE_DISTANCE} blocks from the
     * middle of the gate, toward the heart. A position where a golem does not fit is not a post. The golems with no
     * post stay near the heart.
     */
    public List<BlockPos> golemPosts() {
        List<BlockPos> posts = new ArrayList<>();
        if (level == null) {
            return posts;
        }
        for (List<BlockPos> gate : gateGroups()) {
            BlockPos middle = gate.get(gate.size() / 2);
            int bottom = gate.stream().mapToInt(BlockPos::getY).min().orElse(middle.getY());
            BlockPos post = towardHeart(middle.atY(bottom), GOLEM_GATE_DISTANCE);
            if (ReadyChunks.isReady(level, post) && FactionGolemEntity.fitsAt(level, post)) {
                posts.add(post);
            }
        }
        return posts;
    }

    /**
     * The gate blocks of the village, in one list for each gate. The gate blocks of one gate are side by side. Each
     * list keeps the order of the scan, so the middle block of the list is the middle of the gate.
     */
    private List<List<BlockPos>> gateGroups() {
        List<List<BlockPos>> groups = new ArrayList<>();
        for (BlockPos gate : layout.gates()) {
            List<BlockPos> group = groups.stream()
                    .filter(blocks -> blocks.stream().anyMatch(other -> other.distManhattan(gate) <= 1))
                    .findFirst()
                    .orElse(null);
            if (group == null) {
                group = new ArrayList<>();
                groups.add(group);
            }
            group.add(gate);
        }
        return groups;
    }

    /** True if the player pillaged the bank of this village since the heart loaded. */
    public boolean hasPillaged(Player player) {
        return pillagers.contains(player.getUUID());
    }

    /** Records that the player pillaged the bank of this village. */
    public void addPillager(Player player) {
        pillagers.add(player.getUUID());
    }

    /**
     * Records the player who breaks the heart.
     *
     * @param player the player
     */
    void setBreaker(ServerPlayer player) {
        breaker = player;
    }

    /**
     * Called when the block is removed. The units lose their village.
     *
     * <ul>
     *   <li>No living captain in the village: the village becomes ruins (see {@link Settlements#ruin}).</li>
     *   <li>A living captain: the village becomes ruins when this captain dies.</li>
     * </ul>
     */
    void onDestroyed() {
        List<FactionUnit> units = getUnits();
        if (level instanceof ServerLevel serverLevel) {
            VillageRepairData.get(serverLevel).forget(worldPosition);
        }
        if (level instanceof ServerLevel serverLevel && factionId != null) {
            List<CaptainEntity> captains = units.stream()
                    .filter(unit -> unit instanceof CaptainEntity && unit.isAlive())
                    .map(CaptainEntity.class::cast)
                    .toList();
            if (captains.isEmpty()) {
                Settlements.ruin(serverLevel, villageId.toString(), worldPosition, breaker);
            } else {
                captains.forEach(captain -> captain.setRuinsOnDeath(villageId.toString()));
            }
        }
        for (FactionUnit unit : units) {
            unit.setVillagePos(null);
        }
    }

    /**
     * The periodic work of a heart. The gates of the intervals have an offset from the position of the heart, so
     * the hearts of a world do their work on different ticks.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VillageHeartBlockEntity heart) {
        if (level instanceof ServerLevel serverLevel) {
            heart.handymen.tick(serverLevel);
        }
        long time = level.getGameTime() + Math.floorMod(pos.hashCode(), ROTATION_INTERVAL);
        if (time % CHECK_INTERVAL != 0) {
            return;
        }
        // The registration in onLoad can happen before the level is ready. Register again: it costs nothing.
        VillageHearts.register(level, pos);
        if (!(level instanceof ServerLevel areaLevel) || !VillageLayout.isAreaReady(level, heart.scanArea(areaLevel))) {
            return;
        }
        if (!heart.cleared || !heart.snowCleared) {
            heart.clearTrees(areaLevel);
        }
        // The first garrison waits for the clearing: the units do not appear on powder snow.
        if (time % GARRISON_INTERVAL == 0 && heart.cleared && heart.snowCleared) {
            double activation = WorldConfig.GARRISON_ACTIVATION_RADIUS.get();
            if (level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, activation)) {
                heart.ensureGarrison(level.getGameTime());
            }
        }
        int rotationSeconds = WorldConfig.ARCHER_ROTATION_SECONDS.get();
        if (rotationSeconds > 0 && heart.alertState == AlertState.CALM && heart.layout.isScanned()
                && time % ROTATION_INTERVAL == 0) {
            heart.rotateArchers(level.random, (double) ROTATION_INTERVAL / (rotationSeconds * 20.0));
        }
        if (heart.layout.isScanned() && level.isNight() != heart.nightMode) {
            heart.applyDayNight(level.isNight());
        }
        if (heart.layout.isScanned() && level instanceof ServerLevel serverLevel) {
            heart.updateBraziers(serverLevel);
            long repairTicks = WorldConfig.CONSTRUCTION_REPAIR_CHECK_SECONDS.get() * 20L;
            if (heart.alertState == AlertState.CALM && time % repairTicks < CHECK_INTERVAL
                    && VillageRepairs.check(serverLevel, heart).isEmpty()) {
                VillageGrowth.check(serverLevel, heart);
            }
        }
        long calmDelayTicks = WorldConfig.CALM_DELAY_SECONDS.get() * 20L;
        if (heart.alertState != AlertState.CALM && level.getGameTime() - heart.lastBellTick >= BELL_INTERVAL) {
            heart.ringBells();
        }
        if (heart.alertState != AlertState.CALM && level.getGameTime() - heart.lastThreatTick > calmDelayTicks) {
            heart.setAlertState(AlertState.CALM);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            VillageHearts.register(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            VillageHearts.unregister(level, worldPosition);
        }
        if (level instanceof ServerLevel serverLevel) {
            handymen.stop(serverLevel);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) {
            VillageHearts.unregister(level, worldPosition);
        }
        if (level instanceof ServerLevel serverLevel) {
            handymen.stop(serverLevel);
        }
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(ID_TAG, villageId);
        tag.putString(ALERT_TAG, alertState.serializedName());
        tag.putLong(LAST_THREAT_TAG, lastThreatTick);
        tag.put(GARRISON_TAG, garrison.save());
        tag.put(LAYOUT_TAG, layout.save());
        tag.putBoolean(NIGHT_TAG, nightMode);
        tag.putBoolean(EXITS_TAG, exitStairs);
        tag.putBoolean(CATS_TAG, catsSpawned);
        tag.putBoolean(CLEARED_TAG, cleared);
        tag.putBoolean(SNOW_CLEARED_TAG, snowCleared);
        tag.putLong(CAT_SPAWN_TAG, lastCatSpawn);
        tag.putBoolean(VILLAGERS_TAG, villagersSpawned);
        tag.putLong(VILLAGER_SPAWN_TAG, lastVillagerSpawn);
        tag.putBoolean(VILLAGERS_FILLING_TAG, villagersFilling);
        if (factionId != null) {
            tag.putString(FACTION_TAG, factionId);
        }
        if (villageName != null) {
            tag.putString(NAME_TAG, villageName);
        }
        if (marketPlot != null) {
            tag.put(MARKET_PLOT_TAG, marketPlot.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(ID_TAG)) {
            villageId = tag.getUUID(ID_TAG);
        }
        alertState = AlertState.fromSerializedName(tag.getString(ALERT_TAG)).orElseGet(() -> {
            if (tag.contains(ALERT_TAG)) {
                FolkholdWorld.LOGGER.warn("Unknown alert state '{}' at {}: the village is calm",
                        tag.getString(ALERT_TAG), worldPosition);
            }
            return AlertState.CALM;
        });
        lastThreatTick = tag.getLong(LAST_THREAT_TAG);
        garrison.load(tag.getList(GARRISON_TAG, Garrison.TAG_TYPE));
        layout.load(tag.getCompound(LAYOUT_TAG));
        nightMode = tag.getBoolean(NIGHT_TAG);
        exitStairs = tag.getBoolean(EXITS_TAG);
        catsSpawned = tag.getBoolean(CATS_TAG);
        cleared = tag.getBoolean(CLEARED_TAG);
        snowCleared = tag.getBoolean(SNOW_CLEARED_TAG);
        lastCatSpawn = tag.getLong(CAT_SPAWN_TAG);
        villagersSpawned = tag.getBoolean(VILLAGERS_TAG);
        lastVillagerSpawn = tag.getLong(VILLAGER_SPAWN_TAG);
        villagersFilling = tag.getBoolean(VILLAGERS_FILLING_TAG);
        factionId = tag.contains(FACTION_TAG) ? tag.getString(FACTION_TAG) : null;
        villageName = tag.contains(NAME_TAG) ? tag.getString(NAME_TAG) : null;
        marketPlot = tag.contains(MARKET_PLOT_TAG, Tag.TAG_COMPOUND)
                ? VillageMarket.Plot.load(tag.getCompound(MARKET_PLOT_TAG)).orElse(null)
                : null;
    }
}
