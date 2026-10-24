package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Territories;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The detachments of the vanilla villages: a few guards of a faction in a vanilla village inside its territory. The
 * vanilla village gets nothing more: no heart, no garrison of a faction village, no quest board, no economy, no
 * claim.
 *
 * <ul>
 *   <li>A vanilla village is a bell (point of interest {@code minecraft:meeting}) with {@link #MIN_HOMES} beds or
 *       more in {@link #HOME_RADIUS} blocks around it. A bell of a faction village does not count: a loaded heart or
 *       an active settlement is at {@code villageRadius} blocks or nearer.</li>
 *   <li>Every {@link #SCAN_INTERVAL} ticks, the method checks the bells in {@link #SCAN_RADIUS} blocks around each
 *       player in the Overworld, in the loaded chunks.</li>
 *   <li>A vanilla village in a chunk that an active settlement claims gets a detachment of the faction of this
 *       settlement: {@code detachmentGuards} guards (default 2), with the equipment of a hamlet. The guards spawn
 *       near the bell and patrol around it. They use the colors and the reputation of the faction.</li>
 *   <li>The detachment follows the rules of a garrison: a dead guard comes back after {@code refillDelaySeconds},
 *       when no guard of the detachment fights (no guard has a target).</li>
 *   <li>When the owner of the chunk changes, the guards change their faction. When no settlement claims the chunk,
 *       or when the bell is not there any more, the detachment leaves: its guards go away. A guard of a detachment
 *       that does not exist any more does not join the level.</li>
 * </ul>
 */
public final class VillageDetachments {
    /** The scan of the bells around the players runs at this interval, in ticks. */
    public static final int SCAN_INTERVAL = 200;
    /** The radius of the scan around a player, in blocks. */
    public static final int SCAN_RADIUS = 64;
    /** A bell is the center of a vanilla village when this number of beds or more is near it. */
    public static final int MIN_HOMES = 2;
    /** The radius around a bell where the beds of its village are, in blocks. */
    public static final int HOME_RADIUS = 32;
    /** The data tag of the guards of a detachment. */
    public static final String DETACHMENT_TAG = "FolkholdDetachment";

    private VillageDetachments() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : overworld.players()) {
            scanAround(overworld, player.blockPosition(), SCAN_RADIUS);
        }
    }

    /**
     * Checks the bells around a position in the loaded chunks, and updates their detachments. A detachment in the
     * radius whose bell is not there any more leaves.
     *
     * @param level  the Overworld
     * @param center the center of the scan
     * @param radius the radius of the scan, in blocks
     * @return the bells of the vanilla villages in the radius that have a detachment now
     */
    public static List<BlockPos> scanAround(ServerLevel level, BlockPos center, int radius) {
        return scanAround(level, center, radius, level.getGameTime());
    }

    /**
     * As {@link #scanAround(ServerLevel, BlockPos, int)}, at a given game time.
     *
     * @param level  the Overworld
     * @param center the center of the scan
     * @param radius the radius of the scan, in blocks
     * @param now    the game time for the refill delay of the guards
     * @return the bells of the vanilla villages in the radius that have a detachment now
     */
    public static List<BlockPos> scanAround(ServerLevel level, BlockPos center, int radius, long now) {
        List<BlockPos> bells = level.getPoiManager()
                .findAll(type -> type.is(PoiTypes.MEETING), pos -> true, center, radius, PoiManager.Occupancy.ANY)
                .map(BlockPos::immutable)
                .toList();
        DetachmentData data = DetachmentData.get(level.getServer());
        for (BlockPos bell : bells) {
            update(level, data, bell, now);
        }
        removeLostBells(level, data, center, radius);
        return bells.stream().filter(bell -> data.get(bell).isPresent()).toList();
    }

    /**
     * Removes the detachments of the bells that are not there any more, in the radius of a scan: for example, a
     * player broke or moved the bell. The method checks only the bells in ready chunks (see {@link ReadyChunks}).
     */
    private static void removeLostBells(ServerLevel level, DetachmentData data, BlockPos center, int radius) {
        double radiusSquared = (double) radius * radius;
        List<DetachmentData.Detachment> lost = data.all().stream()
                .filter(detachment -> detachment.bell().distSqr(center) <= radiusSquared
                        && ReadyChunks.isReady(level, detachment.bell())
                        && !(level.getBlockState(detachment.bell()).getBlock() instanceof BellBlock))
                .toList();
        for (DetachmentData.Detachment detachment : lost) {
            dismiss(level, detachment);
            data.remove(detachment.bell());
        }
        if (!lost.isEmpty()) {
            data.setDirty();
        }
    }

    /** Creates, changes or removes the detachment of a bell. */
    private static void update(ServerLevel level, DetachmentData data, BlockPos bell, long now) {
        Optional<String> faction = ownerFaction(level, bell);
        Optional<DetachmentData.Detachment> existing = data.get(bell);
        if (faction.isEmpty()) {
            existing.ifPresent(detachment -> {
                dismiss(level, detachment);
                data.remove(bell);
                data.setDirty();
            });
            return;
        }
        DetachmentData.Detachment detachment = existing.orElseGet(() -> data.create(bell, faction.get()));
        boolean changed = existing.isEmpty();
        if (!faction.get().equals(detachment.factionId())) {
            detachment.setFactionId(faction.get());
            changed = true;
        }
        int guards = WorldConfig.DETACHMENT_GUARDS.get();
        GarrisonTraits traits = new GarrisonTraits(Map.of(GarrisonRole.GUARD.serializedName(), guards),
                GarrisonEquipment.DEFAULT_SET);
        changed |= detachment.garrison().ensure(level, bell, faction.get(), isCalm(level, detachment), now,
                Map.of(), traits);
        // For each loaded guard: the data tag of the detachment, and the colors of its faction.
        for (Garrison.Entry entry : detachment.garrison().entries()) {
            if (!entry.isDead() && level.getEntity(entry.unitId()) instanceof FactionUnit unit) {
                unit.getPersistentData().putBoolean(DETACHMENT_TAG, true);
                if (!faction.get().equals(unit.getFactionId())) {
                    unit.setFactionId(faction.get());
                    unit.equip(unit.getEquipmentSet());
                }
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /**
     * The faction of the detachment of a bell: the faction of the active settlement that claims the chunk of the
     * bell. Empty when the detachments are off, when the bell is not the center of a vanilla village, or when no
     * settlement claims the chunk.
     */
    private static Optional<String> ownerFaction(ServerLevel level, BlockPos bell) {
        if (WorldConfig.DETACHMENT_GUARDS.get() <= 0 || level.dimension() != Level.OVERWORLD
                || !isVanillaVillage(level, bell)) {
            return Optional.empty();
        }
        return Territories.settlementAt(level.getServer(), bell)
                .filter(Settlement::isActive)
                .map(Settlement::factionId);
    }

    /**
     * True if a bell is the center of a vanilla village: {@link #MIN_HOMES} beds or more near it, and no faction
     * village near it (a loaded heart or an active settlement). The bell of a village in ruins counts: the faction
     * that claims the ruins can guard them.
     *
     * @param level the level
     * @param bell  the position of the bell
     * @return true for a vanilla village
     */
    public static boolean isVanillaVillage(ServerLevel level, BlockPos bell) {
        int radius = WorldConfig.VILLAGE_RADIUS.get();
        if (VillageHearts.findNearest(level, bell, radius).isPresent()) {
            return false;
        }
        long radiusSquared = (long) radius * radius;
        boolean factionVillage = SettlementData.get(level.getServer()).registry().all().stream()
                .anyMatch(settlement -> settlement.isActive()
                        && settlement.distanceSquared(bell.getX(), bell.getZ()) <= radiusSquared);
        if (factionVillage) {
            return false;
        }
        return level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), bell, HOME_RADIUS,
                PoiManager.Occupancy.ANY) >= MIN_HOMES;
    }

    /** True if no guard of a detachment fights: no loaded guard has a target. */
    private static boolean isCalm(ServerLevel level, DetachmentData.Detachment detachment) {
        for (Garrison.Entry entry : detachment.garrison().entries()) {
            if (!entry.isDead() && level.getEntity(entry.unitId()) instanceof FactionUnit unit
                    && unit.getTarget() != null) {
                return false;
            }
        }
        return true;
    }

    /** The guards of a detachment go away: the loaded guards leave the level now. */
    private static void dismiss(ServerLevel level, DetachmentData.Detachment detachment) {
        for (Garrison.Entry entry : detachment.garrison().entries()) {
            Entity entity = level.getEntity(entry.unitId());
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** A guard of a detachment that does not exist any more, or that does not list it, does not join the level. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof FactionUnit unit)
                || !unit.getPersistentData().getBoolean(DETACHMENT_TAG)) {
            return;
        }
        BlockPos bell = unit.getVillagePos();
        boolean member = bell != null && DetachmentData.get(level.getServer()).get(bell)
                .filter(detachment -> detachment.contains(unit.getUUID())).isPresent();
        if (!member) {
            event.setCanceled(true);
        }
    }

    /** The death of a guard of a detachment starts its refill delay. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof FactionUnit unit) || !(unit.level() instanceof ServerLevel level)
                || !unit.getPersistentData().getBoolean(DETACHMENT_TAG) || unit.getVillagePos() == null) {
            return;
        }
        DetachmentData data = DetachmentData.get(level.getServer());
        data.get(unit.getVillagePos()).ifPresent(detachment -> {
            if (detachment.garrison().onUnitDied(unit.getUUID(), level.getGameTime())) {
                data.setDirty();
            }
        });
    }
}
