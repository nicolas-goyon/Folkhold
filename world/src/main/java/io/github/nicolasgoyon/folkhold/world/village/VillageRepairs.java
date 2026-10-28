package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Clusters;
import io.github.nicolasgoyon.folkhold.core.sim.construction.WorkZone;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BlockKinds;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The repairs of the faction villages: the builders of a village give back their reference blocks to the damaged
 * blocks of the village.
 *
 * <ul>
 *   <li>Reference: the structure pieces of the village at the generation (see {@link VillageReference}).</li>
 *   <li>Area: the positions of the pieces inside the palisade, the palisade included (see
 *       {@link VillageReference#area()}). The stairs of the gates are outside the area.</li>
 *   <li>A loaded village heart looks for the damaged blocks of its area at an interval (see
 *       {@link WorldConfig#CONSTRUCTION_REPAIR_CHECK_SECONDS}), when the village is calm, has builders and has no
 *       construction site. The comparison ignores the changes that come with the life of a village (see
 *       {@link RepairRules}).</li>
 *   <li>The repairs keep the blocks that the players placed, the blocks of the construction sites of the village (see
 *       {@link VillageRepairData}), the village heart, and the work zones of the sites of the other villages.</li>
 *   <li>The damaged blocks make groups (see {@link Clusters}). The builders repair the largest group first, with a
 *       repair site (see {@link BuilderSite#repair}): fences around the work zone, materials of the stock, quests for
 *       the rare blocks.</li>
 *   <li>The holes of the streets are damaged blocks too (see {@link StreetBlocks}). The first check records the
 *       surface of the streets.</li>
 *   <li>After a repair, its positions wait {@link #RETRY_TICKS} ticks before a new repair: a block that does not stay
 *       in place does not start a repair at each check.</li>
 * </ul>
 */
public final class VillageRepairs {
    /** Two damaged blocks at this distance or less on each axis are in the same repair. */
    public static final int GROUP_DISTANCE = 3;
    /** The positions of a repair wait this time before a new repair, in ticks: 20 minutes. */
    public static final long RETRY_TICKS = 20L * 60L * 20L;
    /** The blocks of the templates, in the coordinates of the templates. The keys are the templates of the server. */
    private static final Map<StructureTemplate, List<ConstructionTargets.TemplateBlock>> BLOCKS = new WeakHashMap<>();

    private VillageRepairs() {
    }

    /**
     * Looks for the damaged blocks of a village, and starts a repair site for the largest group.
     *
     * @param level the level
     * @param heart the village heart
     * @return the repair site, or empty with no repair
     */
    public static Optional<BuilderSite> check(ServerLevel level, VillageHeartBlockEntity heart) {
        BlockPos heartPos = heart.getBlockPos();
        if (!WorldConfig.CONSTRUCTION_REPAIRS.get() || !VillageLayout.isAreaReady(level, heart.scanArea(level))
                || !BuilderSites.of(level, heartPos).isEmpty()
                || VillageFolk.of(level, heartPos, ModProfessions.BUILDER.get()).isEmpty()) {
            return Optional.empty();
        }
        Optional<VillageReference> reference = heart.repairReference(level).map(found -> withStreets(level, heart,
                found));
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        Set<BlockPos> damaged = damaged(level, heart, reference.get());
        if (damaged.isEmpty()) {
            return Optional.empty();
        }
        List<Set<BuildPos>> groups = Clusters.of(damaged.stream().map(BlockKinds::toBuildPos).toList(),
                GROUP_DISTANCE);
        Set<BlockPos> positions = groups.get(0).stream().map(BlockKinds::toBlockPos)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        FolkholdWorld.LOGGER.debug("Village {}: {} damaged blocks in {} groups, repair of {} blocks", heartPos,
                damaged.size(), groups.size(), positions.size());
        return Optional.of(BuilderSites.startRepair(level, heartPos, reference.get(), positions));
    }

    /**
     * The reference of a village with the surface blocks of its streets. The first call records the streets (see
     * {@link StreetBlocks#record}). The heart keeps the reference with its streets.
     */
    private static VillageReference withStreets(ServerLevel level, VillageHeartBlockEntity heart,
            VillageReference reference) {
        if (!reference.streets().isEmpty() || reference.streetColumns().isEmpty()) {
            return reference;
        }
        VillageRepairData data = VillageRepairData.get(level);
        BlockPos heartPos = heart.getBlockPos();
        Map<BlockPos, BlockState> streets = data.streets(heartPos).orElseGet(() -> {
            Map<BlockPos, BlockState> recorded = StreetBlocks.record(level, reference);
            data.setStreets(heartPos, recorded);
            return recorded;
        });
        VillageReference result = reference.withStreets(streets);
        heart.setRepairReference(result);
        return result;
    }

    /**
     * The damaged positions of a village.
     *
     * @param level     the level
     * @param heart     the village heart
     * @param reference the structure pieces of the village
     * @return the damaged positions
     */
    public static Set<BlockPos> damaged(ServerLevel level, VillageHeartBlockEntity heart,
            VillageReference reference) {
        BlockPos heartPos = heart.getBlockPos();
        Predicate<BlockPos> inArea = area(heart, reference);
        Map<BlockPos, BlockState> expected = expectedBlocks(level, reference);
        expected.keySet().removeIf(inArea.negate());
        VillageRepairData data = VillageRepairData.get(level);
        List<WorkZone> zones = BuilderSites.all(level).stream().flatMap(site -> site.zone().stream()).toList();
        long now = level.getGameTime();
        Set<BlockPos> damaged = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!pos.equals(heartPos) && !data.keeps(heartPos, pos) && !heart.isRecentlyRepaired(pos, now)
                    && zones.stream().noneMatch(zone -> isInZone(zone, pos))
                    && RepairRules.isDamaged(level.getBlockState(pos), entry.getValue())) {
                damaged.add(pos);
            }
        }
        for (BlockPos pos : StreetBlocks.holes(level, reference)) {
            if (!expected.containsKey(pos) && !data.keeps(heartPos, pos) && !heart.isRecentlyRepaired(pos, now)
                    && zones.stream().noneMatch(zone -> isInZone(zone, pos))) {
                damaged.add(pos);
            }
        }
        return damaged;
    }

    /**
     * The blocks of the structure pieces of a village: the blocks of the templates, then the blocks of the palisade. A
     * template piece replaces the blocks of the pieces before it, as at the generation.
     *
     * @param level     the level
     * @param reference the structure pieces of the village
     * @return the block state at each position
     */
    public static Map<BlockPos, BlockState> expectedBlocks(ServerLevel level, VillageReference reference) {
        Map<BlockPos, BlockState> expected = new HashMap<>();
        for (VillageReference.Piece piece : reference.pieces()) {
            Optional<StructureTemplate> template = level.getStructureManager().get(piece.template());
            if (template.isEmpty()) {
                continue;
            }
            StructurePlaceSettings settings = GhostPlacement.settings(piece.rotation(), Mirror.NONE);
            for (ConstructionTargets.TemplateBlock block : blocks(level, template.get())) {
                BlockPos pos = StructureTemplate.calculateRelativePosition(settings, block.pos())
                        .offset(piece.origin());
                expected.put(pos, block.state().rotate(level, pos, settings.getRotation()));
            }
        }
        expected.putAll(reference.palisadeBlocks());
        return expected;
    }

    /**
     * The area of the repairs. A reference with an area gives it: the village inside its palisade, the palisade
     * included. Else the area is a square around the heart, to the farthest gate: the scan area of the heart is the
     * limit (see {@link VillageLayout#SCAN_RADIUS}).
     */
    private static Predicate<BlockPos> area(VillageHeartBlockEntity heart, VillageReference reference) {
        if (reference.area().isPresent()) {
            return reference::isInArea;
        }
        BlockPos heartPos = heart.getBlockPos();
        int gates = heart.getLayout().gates().stream().mapToInt(gate -> squareDistance(heartPos, gate)).max()
                .orElse(VillageLayout.SCAN_RADIUS);
        int radius = Math.min(gates, VillageLayout.SCAN_RADIUS);
        return pos -> squareDistance(heartPos, pos) <= radius;
    }

    private static int squareDistance(BlockPos center, BlockPos pos) {
        return Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ()));
    }

    /** True if a position is in the work zone of a site: the box, the band and the ring, with a margin in height. */
    private static boolean isInZone(WorkZone zone, BlockPos pos) {
        return pos.getX() >= zone.ringMinX() && pos.getX() <= zone.ringMaxX() && pos.getZ() >= zone.ringMinZ()
                && pos.getZ() <= zone.ringMaxZ() && pos.getY() >= zone.minY() - WorkZone.BELOW
                && pos.getY() <= zone.maxY() + WorkZone.ABOVE;
    }

    /** The blocks of a template. The method keeps them for the next checks. */
    private static List<ConstructionTargets.TemplateBlock> blocks(ServerLevel level, StructureTemplate template) {
        return BLOCKS.computeIfAbsent(template, key -> new ArrayList<>(ConstructionTargets.templateBlocks(level,
                key)));
    }

    /**
     * Records the end of a repair site: the heart of the village keeps its positions for {@link #RETRY_TICKS} ticks,
     * and gives the color of the faction to the banners again.
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param positions the positions of the repair
     */
    public static void onRepairEnded(ServerLevel level, BlockPos heart, Set<BlockPos> positions) {
        if (ReadyChunks.isReady(level, heart) && level.getBlockEntity(heart) instanceof VillageHeartBlockEntity village) {
            village.onRepaired(positions, level.getGameTime());
        }
    }

    /** A player places a block in a village: the repairs keep it. */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Player)) {
            return;
        }
        BlockPos pos = event.getPos();
        Optional<BlockPos> heart = VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get());
        if (heart.isEmpty()) {
            return;
        }
        VillageRepairData data = VillageRepairData.get(level);
        data.addPlayerBlock(heart.get(), pos);
        BlockKinds.describe(pos, event.getPlacedBlock(), Optional.empty()).partner().map(BlockKinds::toBlockPos)
                .ifPresent(partner -> data.addPlayerBlock(heart.get(), partner));
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                data.addPlayerBlock(heart.get(), snapshot.getPos());
            }
        }
    }

    /** A player breaks a block in a village: a block of the player is not kept any more. */
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get()).ifPresent(heart -> {
            VillageRepairData data = VillageRepairData.get(level);
            data.removePlayerBlock(heart, pos);
            BlockKinds.describe(pos, event.getState(), Optional.empty()).partner().map(BlockKinds::toBlockPos)
                    .ifPresent(partner -> data.removePlayerBlock(heart, partner));
        });
    }

    /** Forgets the blocks of the templates. */
    public static void onServerStopped(ServerStoppedEvent event) {
        BLOCKS.clear();
    }
}
