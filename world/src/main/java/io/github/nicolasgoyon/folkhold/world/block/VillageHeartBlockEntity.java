package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.faction.VillageNames;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.PostAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
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
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.Garrison;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * State of a village: id, faction, alert state and garrison. The units of the village keep the position of this block.
 *
 * <p>When a player is in the activation radius, the heart creates the missing units of its garrison.
 *
 * <p>The work of the heart reads and changes the blocks of the village. The heart does this work only when all the
 * chunks of the scan area are ready ({@link VillageLayout#isScanAreaReady}): the server thread never waits for the
 * generation of a chunk.
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
    private static final String CAT_SPAWN_TAG = "LastCatSpawn";
    private static final String VILLAGERS_TAG = "VillagersSpawned";
    private static final String VILLAGER_SPAWN_TAG = "LastVillagerSpawn";
    private static final String FACTION_TAG = "Faction";
    private static final String NAME_TAG = "Name";
    /** Name of the village, or null before the village has a faction. */
    @Nullable
    private String villageName;
    /** Id of the faction that owns the village, or null before the first activation. */
    @Nullable
    private String factionId;
    /** A guard on night watch stays at this distance from the gate, toward the heart. */
    private static final int GATE_WATCH_DISTANCE = 2;
    /** True after the heart closed the gates and lit the braziers for the night. */
    private boolean nightMode;
    /** True when the stairs of the gates are built (see {@link VillageExits}). */
    private boolean exitStairs;
    /** True after the first cats of the village appeared. */
    private boolean catsSpawned;
    /** Game time of the last creation of cats. */
    private long lastCatSpawn;
    /** True after the first villagers (merchants and inhabitants) of the village appeared. */
    private boolean villagersSpawned;
    /** Game time of the last creation of villagers. */
    private long lastVillagerSpawn;
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
     * bell in a chunk that is not ready does not ring.
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
     * Creates the missing units of the garrison now (see {@link Garrison#ensure}). Before the first garrison, the
     * heart scans the village. The method does nothing while a chunk of the scan area is not ready
     * ({@link VillageLayout#isScanAreaReady}).
     *
     * @param now the game time to use
     */
    public void ensureGarrison(long now) {
        if (!(level instanceof ServerLevel serverLevel) || !VillageLayout.isScanAreaReady(serverLevel, worldPosition)) {
            return;
        }
        ensureSettlement();
        if (!layout.isScanned()) {
            layout.scan(serverLevel, worldPosition);
            paintBanners();
            setChanged();
        }
        layout.questBoards().forEach(board -> QuestBoardFeature.startUpdates(serverLevel, board));
        if (!exitStairs && VillageExits.build(serverLevel, worldPosition, layout.gates()) >= 0) {
            exitStairs = true;
            setChanged();
        }
        if (alertState == AlertState.CALM && ensureCats(serverLevel, now)) {
            setChanged();
        }
        if (alertState == AlertState.CALM && ensureVillagers(serverLevel, now)) {
            setChanged();
        }
        if (garrison.ensure(serverLevel, worldPosition, factionId, alertState == AlertState.CALM, now,
                layout.validPosts())) {
            setChanged();
        }
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
        for (BlockPos gate : layout.gates()) {
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
     * Creates the missing villagers of the village: the merchants ({@link Merchants#merchantCount}), then the
     * inhabitants ({@link VillageFolk#inhabitantCount}) with the professions of the jobs of the settlement
     * ({@link VillageFolk#professions}). The first villagers appear at once. A dead villager comes back after the
     * refill delay of the garrison. When the jobs change, the inhabitants get the new professions at once
     * ({@link VillageFolk#assign}).
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
        int missingMerchants = Merchants.merchantCount(settlement.get())
                - VillageFolk.of(serverLevel, worldPosition, ModProfessions.MERCHANT.get()).size();
        List<VillagerProfession> wanted = VillageFolk.professions(settlement.get(),
                VillageFolk.inhabitantCount(settlement.get()));
        List<VillagerProfession> missingInhabitants =
                VillageFolk.assign(VillageFolk.inhabitants(serverLevel, worldPosition), wanted);
        long refill = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
        if ((missingMerchants <= 0 && missingInhabitants.isEmpty())
                || (villagersSpawned && now - lastVillagerSpawn < refill)) {
            return false;
        }
        for (int i = 0; i < missingMerchants; i++) {
            VillageFolk.spawn(serverLevel, worldPosition, ModProfessions.MERCHANT.get());
        }
        for (VillagerProfession profession : missingInhabitants) {
            VillageFolk.spawn(serverLevel, worldPosition, profession);
        }
        villagersSpawned = true;
        lastVillagerSpawn = now;
        return true;
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
     * Applies the day or the night state to the village.
     *
     * <ul>
     *   <li>Night: the gates close, the braziers burn, and the guards (not the captain, not the units with a
     *       post) watch the gates.</li>
     *   <li>Day: the gates open, the braziers go out, and the guards patrol around their home again.</li>
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
        for (BlockPos brazier : layout.braziers()) {
            BlockState brazierState = level.getBlockState(brazier);
            if (brazierState.getBlock() instanceof CampfireBlock) {
                // A campfire burns the entities: the brazier of the block library takes its place.
                level.setBlock(brazier, ModBlocks.BRAZIER.get().defaultBlockState().setValue(BrazierBlock.LIT, night),
                        Block.UPDATE_ALL);
            } else if (brazierState.getBlock() instanceof BrazierBlock
                    && brazierState.getValue(BrazierBlock.LIT) != night) {
                level.setBlock(brazier, brazierState.setValue(BrazierBlock.LIT, night), Block.UPDATE_ALL);
            }
        }
        assignGateWatch(night);
    }

    private void assignGateWatch(boolean night) {
        List<BlockPos> gates = layout.gates();
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

    /** The position at {@link #GATE_WATCH_DISTANCE} blocks from the gate, toward the heart. */
    private BlockPos gateWatchPosition(BlockPos gate) {
        int dx = worldPosition.getX() - gate.getX();
        int dz = worldPosition.getZ() - gate.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return gate.offset(Integer.signum(dx) * GATE_WATCH_DISTANCE, 0, 0);
        }
        return gate.offset(0, 0, Integer.signum(dz) * GATE_WATCH_DISTANCE);
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
        long time = level.getGameTime() + Math.floorMod(pos.hashCode(), ROTATION_INTERVAL);
        if (time % CHECK_INTERVAL != 0) {
            return;
        }
        // The registration in onLoad can happen before the level is ready. Register again: it costs nothing.
        VillageHearts.register(level, pos);
        if (!VillageLayout.isScanAreaReady(level, pos)) {
            return;
        }
        if (time % GARRISON_INTERVAL == 0) {
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
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) {
            VillageHearts.unregister(level, worldPosition);
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
        tag.putLong(CAT_SPAWN_TAG, lastCatSpawn);
        tag.putBoolean(VILLAGERS_TAG, villagersSpawned);
        tag.putLong(VILLAGER_SPAWN_TAG, lastVillagerSpawn);
        if (factionId != null) {
            tag.putString(FACTION_TAG, factionId);
        }
        if (villageName != null) {
            tag.putString(NAME_TAG, villageName);
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
        lastCatSpawn = tag.getLong(CAT_SPAWN_TAG);
        villagersSpawned = tag.getBoolean(VILLAGERS_TAG);
        lastVillagerSpawn = tag.getLong(VILLAGER_SPAWN_TAG);
        factionId = tag.contains(FACTION_TAG) ? tag.getString(FACTION_TAG) : null;
        villageName = tag.contains(NAME_TAG) ? tag.getString(NAME_TAG) : null;
    }
}
