package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * State of a village: id and alert state. The units of the village keep the position of this block.
 */
public class VillageHeartBlockEntity extends BlockEntity {
    private static final String ID_TAG = "VillageId";
    private static final String ALERT_TAG = "Alert";
    private static final String LAST_THREAT_TAG = "LastThreat";
    /** The heart checks the end of the alert once each second. */
    private static final int CHECK_INTERVAL = 20;

    private UUID villageId = UUID.randomUUID();
    private AlertState alertState = AlertState.CALM;
    private long lastThreatTick;

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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(ID_TAG)) {
            villageId = tag.getUUID(ID_TAG);
        }
        alertState = AlertState.fromSerializedName(tag.getString(ALERT_TAG));
        lastThreatTick = tag.getLong(LAST_THREAT_TAG);
    }
}
