package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * The growth of a village: its builders build new homes on the free plots of its streets (see {@link VillagePlots}).
 *
 * <ul>
 *   <li>The village needs places: the population of the settlement, minus the merchants, is more than the places (see
 *       {@link VillageBeds}).</li>
 *   <li>The village has builders and no construction site. The heart looks for a free plot after the check of the
 *       repairs: a repair comes first.</li>
 *   <li>A free plot has no building of the generation. The space of the home has no piece of the village structure, no
 *       palisade, no earlier construction site and no block of a player. The nearest free plot to the heart comes
 *       first.</li>
 *   <li>The home comes from the pool of the homes of the village structure. Its connector goes in front of the
 *       connector of the plot, on the ground of the street.</li>
 *   <li>The construction uses the stock of the village and the delivery quests, as the other sites (see
 *       {@link BuilderSites}). At the end, the heart scans the village again: the new beds give places.</li>
 *   <li>A level that goes down does not remove a home.</li>
 * </ul>
 */
public final class VillageGrowth {
    private VillageGrowth() {
    }

    /**
     * Starts the construction of a home on a free plot, if the village needs places.
     *
     * @param level the level
     * @param heart the village heart
     * @return the construction site, or empty with no construction
     */
    public static Optional<BuilderSite> check(ServerLevel level, VillageHeartBlockEntity heart) {
        BlockPos heartPos = heart.getBlockPos();
        if (!WorldConfig.CONSTRUCTION_GROWTH.get() || !BuilderSites.of(level, heartPos).isEmpty()
                || VillageFolk.of(level, heartPos, ModProfessions.BUILDER.get()).isEmpty()) {
            return Optional.empty();
        }
        Optional<Settlement> settlement = Settlements.of(level.getServer(), heart).filter(Settlement::isActive);
        Optional<VillageReference> reference = heart.repairReference(level);
        if (settlement.isEmpty() || !needsPlaces(settlement.get()) || reference.isEmpty()
                || reference.get().homes().isEmpty()) {
            return Optional.empty();
        }
        Optional<StructureTemplatePool> homes = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .getOptional(reference.get().homes().get());
        if (homes.isEmpty()) {
            return Optional.empty();
        }
        StructurePoolElement element = homes.get().getRandomTemplate(level.random);
        Optional<ResourceLocation> template = VillageReference.templateOf(level, element);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        List<VillagePlots.Plot> plots = reference.get().plots().stream()
                .filter(plot -> !plot.built())
                .sorted(Comparator.comparingDouble(plot -> plot.connector().distSqr(heartPos)))
                .toList();
        for (VillagePlots.Plot plot : plots) {
            if (!ReadyChunks.isReady(level, plot.connector())) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, plot.connector().getX(),
                    plot.connector().getZ());
            Optional<VillagePlots.Placement> placement = VillagePlots.place(level.getStructureManager(), element, plot,
                    ground);
            if (placement.isPresent() && isFree(level, heartPos, reference.get(), placement.get().box())) {
                FolkholdWorld.LOGGER.debug("Village {}: a new home {} on the plot {}", heartPos, template.get(),
                        plot.connector());
                return Optional.of(BuilderSites.start(level, template.get(), placement.get().origin(),
                        placement.get().rotation(), Mirror.NONE, heartPos, null));
            }
        }
        return Optional.empty();
    }

    /**
     * True if a village needs places: its population, minus the merchants, is more than its places. A village with an
     * unknown number of beds does not need places.
     *
     * @param settlement the settlement of the village
     * @return true if the village needs places
     */
    public static boolean needsPlaces(Settlement settlement) {
        int places = VillageBeds.places(settlement);
        return places != VillagePlaces.UNKNOWN
                && settlement.economy().population() - Merchants.merchantCount(settlement) > places;
    }

    /**
     * True if a space of a village is free for a new building: its chunks are ready, and it has no piece of the village
     * structure, no palisade, no earlier construction site and no block of a player.
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param reference the reference of the village structure
     * @param box       the space
     * @return true if the builders can build there
     */
    public static boolean isFree(ServerLevel level, BlockPos heart, VillageReference reference, BoundingBox box) {
        if (!ReadyChunks.isAreaReady(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return false;
        }
        for (VillageReference.Piece piece : reference.pieces()) {
            boolean taken = level.getStructureManager().get(piece.template())
                    .map(found -> GhostPlacement.box(found.getSize(), piece.rotation(), Mirror.NONE, piece.origin()))
                    .filter(box::intersects)
                    .isPresent();
            if (taken) {
                return false;
            }
        }
        for (BlockPos column : reference.palisadeBlocks().keySet()) {
            if (column.getX() >= box.minX() && column.getX() <= box.maxX() && column.getZ() >= box.minZ()
                    && column.getZ() <= box.maxZ()) {
                return false;
            }
        }
        VillageRepairData data = VillageRepairData.get(level);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                box.maxZ())) {
            if (data.keeps(heart, pos)) {
                return false;
            }
        }
        return true;
    }
}
