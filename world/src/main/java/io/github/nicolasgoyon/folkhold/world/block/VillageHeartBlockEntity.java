package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.core.sim.faction.VillageNames;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.Garrison;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * State of a village: id, faction, alert state and garrison. The units of the village keep the position of this block.
 *
 * <p>When a player is in the activation radius, the heart creates the missing units of its garrison.
 */
public class VillageHeartBlockEntity extends BlockEntity {
    private static final String ID_TAG = "VillageId";
    private static final String ALERT_TAG = "Alert";
    private static final String LAST_THREAT_TAG = "LastThreat";
    private static final String GARRISON_TAG = "Garrison";
    /** The heart checks its garrison every 2 seconds. */
    private static final int GARRISON_INTERVAL = 40;
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
    private static final String NIGHT_TAG = "NightMode";
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
        player.sendSystemMessage(Component.translatable("message.folkhold_world.warning", name));
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

    /** Changes the alert state and saves the change. */
    public void setAlertState(AlertState state) {
        if (alertState != state) {
            alertState = state;
            setChanged();
        }
    }

    /** The loaded units of this village: units in the village radius that are linked to this heart. */
    public List<FactionUnit> getUnits() {
        if (level == null) {
            return List.of();
        }
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        AABB area = new AABB(worldPosition).inflate(radius, 32.0, radius);
        return level.getEntitiesOfClass(FactionUnit.class, area, unit -> worldPosition.equals(unit.getVillagePos()));
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

    /** Gives the village to the faction that owns its position, if the village has no faction. */
    public void ensureFaction() {
        if (factionId == null && level instanceof ServerLevel serverLevel) {
            factionId = FactionData.get(serverLevel.getServer()).ownerAt(worldPosition).id();
            setChanged();
        }
    }

    /** The posts, gates and braziers of the village. The heart scans them before the first garrison. */
    public VillageLayout getLayout() {
        return layout;
    }

    /**
     * Creates the missing units of the garrison now (see {@link Garrison#ensure}).
     *
     * @param now the game time to use
     */
    public void ensureGarrison(long now) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ensureFaction();
        if (!layout.isScanned()) {
            layout.scan(serverLevel, worldPosition);
            setChanged();
        }
        if (garrison.ensure(serverLevel, worldPosition, factionId, alertState == AlertState.CALM, now,
                layout.posts())) {
            setChanged();
        }
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

    /** True if the heart applied the night state (gates closed, braziers lit). */
    public boolean isNightMode() {
        return nightMode;
    }

    /**
     * Applies the day or the night state to the village.
     *
     * <ul>
     *   <li>Night: the gates close, the braziers burn, and the guards (not the captain, not the units with a
     *       post) watch the gates.</li>
     *   <li>Day: the gates open, the braziers go out, and the guards patrol around their home again.</li>
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
            if (brazierState.getBlock() instanceof CampfireBlock && brazierState.getValue(CampfireBlock.LIT) != night) {
                level.setBlock(brazier, brazierState.setValue(CampfireBlock.LIT, night), Block.UPDATE_ALL);
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

    /** Called when the block is removed. The units lose their village. */
    void onDestroyed() {
        for (FactionUnit unit : getUnits()) {
            unit.setVillagePos(null);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VillageHeartBlockEntity heart) {
        if (level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        // The registration in onLoad can happen before the level is ready. Register again: it costs nothing.
        VillageHearts.register(level, pos);
        if (level.getGameTime() % GARRISON_INTERVAL == 0) {
            double activation = WorldConfig.GARRISON_ACTIVATION_RADIUS.get();
            if (level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, activation)) {
                heart.ensureGarrison(level.getGameTime());
            }
        }
        if (heart.layout.isScanned() && level.isNight() != heart.nightMode) {
            heart.applyDayNight(level.isNight());
        }
        long calmDelayTicks = WorldConfig.CALM_DELAY_SECONDS.get() * 20L;
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
        alertState = AlertState.fromSerializedName(tag.getString(ALERT_TAG));
        lastThreatTick = tag.getLong(LAST_THREAT_TAG);
        garrison.load(tag.getList(GARRISON_TAG, Garrison.TAG_TYPE));
        layout.load(tag.getCompound(LAYOUT_TAG));
        nightMode = tag.getBoolean(NIGHT_TAG);
        factionId = tag.contains(FACTION_TAG) ? tag.getString(FACTION_TAG) : null;
        villageName = tag.contains(NAME_TAG) ? tag.getString(NAME_TAG) : null;
    }
}
