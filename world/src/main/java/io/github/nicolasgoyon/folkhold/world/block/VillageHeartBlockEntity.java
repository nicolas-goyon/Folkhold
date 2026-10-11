package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.Garrison;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * State of a village: id, alert state and garrison. The units of the village keep the position of this block.
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
    private final VillageLayout layout = new VillageLayout();
    private static final String LAYOUT_TAG = "Layout";

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
        lastThreatTick = level.getGameTime();
        if (alertState == AlertState.CALM) {
            setAlertState(AlertState.ALERT);
        }
        if (attacker != null) {
            for (FactionUnit unit : getUnits()) {
                if (unit.getTarget() == null && unit != attacker && unit.canTargetFromHome(attacker)) {
                    unit.setTarget(attacker);
                }
            }
        }
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
        if (!layout.isScanned()) {
            layout.scan(serverLevel, worldPosition);
            setChanged();
        }
        if (garrison.ensure(serverLevel, worldPosition, alertState == AlertState.CALM, now, layout.posts())) {
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
    }
}
