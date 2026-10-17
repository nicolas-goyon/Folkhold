package io.github.nicolasgoyon.folkhold.world.entity;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Cat of a faction village. It stays near its anchor (a gate or the center of the village). The creepers run away
 * from it, as from all cats. The player cannot tame it or breed it: fish does nothing. The cat does not despawn.
 */
public class VillageCat extends Cat {
    /** The cat goes back to its anchor when it is farther than this distance, in blocks. */
    public static final double ANCHOR_RADIUS = 10.0;
    private static final String ANCHOR_TAG = "Anchor";
    private static final String VILLAGE_TAG = "Village";

    @Nullable
    private BlockPos anchorPos;
    @Nullable
    private BlockPos villagePos;

    public VillageCat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(4, new StayNearAnchorGoal(this));
    }

    /** The position where the cat stays, or null. */
    @Nullable
    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public void setAnchorPos(@Nullable BlockPos pos) {
        anchorPos = pos == null ? null : pos.immutable();
    }

    /** The position of the heart of the village of the cat, or null. */
    @Nullable
    public BlockPos getVillagePos() {
        return villagePos;
    }

    public void setVillagePos(@Nullable BlockPos pos) {
        villagePos = pos == null ? null : pos.immutable();
    }

    /** Fish does nothing: the player cannot tame or breed a village cat. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isFood(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Override only: callers use {@link net.neoforged.neoforge.event.EventHooks#finalizeMobSpawn}. */
    @Deprecated
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchorPos != null) {
            tag.put(ANCHOR_TAG, NbtUtils.writeBlockPos(anchorPos));
        }
        if (villagePos != null) {
            tag.put(VILLAGE_TAG, NbtUtils.writeBlockPos(villagePos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        anchorPos = NbtUtils.readBlockPos(tag, ANCHOR_TAG).orElse(null);
        villagePos = NbtUtils.readBlockPos(tag, VILLAGE_TAG).orElse(null);
    }

    /** Moves the cat back to its anchor when it is farther than {@link #ANCHOR_RADIUS}. */
    private static final class StayNearAnchorGoal extends Goal {
        private final VillageCat cat;

        StayNearAnchorGoal(VillageCat cat) {
            this.cat = cat;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private double distanceToAnchor() {
            return Math.sqrt(cat.distanceToSqr(Vec3.atBottomCenterOf(cat.anchorPos)));
        }

        @Override
        public boolean canUse() {
            return cat.anchorPos != null && !cat.isOrderedToSit() && distanceToAnchor() > ANCHOR_RADIUS;
        }

        @Override
        public boolean canContinueToUse() {
            return cat.anchorPos != null && distanceToAnchor() > 2.0 && !cat.getNavigation().isDone();
        }

        @Override
        public void start() {
            cat.getNavigation().moveTo(cat.anchorPos.getX() + 0.5, cat.anchorPos.getY(), cat.anchorPos.getZ() + 0.5,
                    1.0);
        }

        @Override
        public void stop() {
            cat.getNavigation().stop();
        }
    }
}
