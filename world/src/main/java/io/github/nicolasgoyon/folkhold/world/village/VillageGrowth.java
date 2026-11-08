package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
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
 * The growth of a village: its builders build new homes and the buildings of its level on the free plots of its
 * streets (see {@link VillagePlots}).
 *
 * <ul>
 *   <li>From the level 5, a village with no market gets its market first (see {@link VillageBuildings#steps}): the
 *       piece of the pool of the market of the village structure. A village keeps one market. With no free plot for the
 *       market, the village has no market and trades at the plaza (see {@link VillageMarket}). The growth then goes on
 *       with the next step. The market comes when a plot is free again.</li>
 *   <li>A village that needs places gets a new home: the population of the settlement is more than the places (see
 *       {@link VillageBeds}). The home comes from the pool of the homes of the village structure.</li>
 *   <li>Else, a village with a missing building of its level gets this building (see {@link VillageBuildings} and
 *       {@link VillageBuildingTable}). The scan of the heart counts the building signs of each kind. The building comes
 *       from the pool of the houses of the variant of the village: the piece {@code house_<kind>}. A variant with no
 *       such piece gets no building of this kind.</li>
 *   <li>The village has builders and no construction site. The heart looks for a free plot after the check of the
 *       repairs: a repair comes first.</li>
 *   <li>A free plot has no building of the generation. The space of the home has no piece of the village structure, no
 *       palisade, no earlier construction site and no block of a player. The nearest free plot to the heart comes
 *       first.</li>
 *   <li>The connector of the building goes in front of the connector of the plot, on the ground of the street.</li>
 *   <li>The construction uses the stock of the village and the delivery quests, as the other sites (see
 *       {@link BuilderSites}). At the end, the heart scans the village again: the new beds give places, and the new
 *       building sign counts.</li>
 *   <li>A level that goes down does not remove a building.</li>
 * </ul>
 */
public final class VillageGrowth {
    private VillageGrowth() {
    }

    /** The name of the pool of the houses of a variant, in the folder of the pool of its homes. */
    private static final String HOUSES = "houses";
    /** The name of the piece of a building of a kind starts with this text. */
    private static final String BUILDING_PREFIX = "house_";
    /** The name of the pool of the market of a variant, in the folder of the pool of its homes. */
    private static final String MARKET = "market";

    /**
     * Starts the construction of the market, of a home or of a missing building of the level on a free plot. The steps
     * come in the order of {@link VillageBuildings#steps}: the first step with a piece and a free plot starts.
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
        if (settlement.isEmpty() || reference.isEmpty() || reference.get().homes().isEmpty()) {
            return Optional.empty();
        }
        VillageLayout layout = heart.getLayout();
        List<VillageBuildings.Step> steps = VillageBuildings.steps(VillageBuildingTable.get(),
                settlement.get().level(), layout.buildings(), layout.isScanned(), needsPlaces(settlement.get()));
        List<VillagePlots.Plot> plots = reference.get().plots().stream()
                .filter(plot -> !plot.built())
                .sorted(Comparator.comparingDouble(plot -> plot.connector().distSqr(heartPos)))
                .toList();
        for (VillageBuildings.Step step : steps) {
            Optional<StructurePoolElement> element = piece(level, step, reference.get().homes().get());
            Optional<BuilderSite> site = element.flatMap(found -> start(level, heart, reference.get(), plots, found));
            if (step.type() == VillageBuildings.StepType.MARKET) {
                heart.setMarketNoSpace(element.isPresent() && site.isEmpty());
            }
            if (site.isPresent()) {
                return site;
            }
        }
        return Optional.empty();
    }

    /**
     * Starts the construction of a piece on the first free plot.
     *
     * @return the construction site, or empty when no plot is free for the piece
     */
    private static Optional<BuilderSite> start(ServerLevel level, VillageHeartBlockEntity heart,
            VillageReference reference, List<VillagePlots.Plot> plots, StructurePoolElement element) {
        BlockPos heartPos = heart.getBlockPos();
        Optional<ResourceLocation> template = VillageReference.templateOf(level, element);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        for (VillagePlots.Plot plot : plots) {
            if (!ReadyChunks.isReady(level, plot.connector())) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, plot.connector().getX(),
                    plot.connector().getZ());
            Optional<VillagePlots.Placement> placement = VillagePlots.place(level.getStructureManager(), element, plot,
                    ground);
            if (placement.isPresent() && isFree(level, heartPos, reference, placement.get().box())) {
                FolkholdWorld.LOGGER.debug("Village {}: a new building {} on the plot {}", heartPos, template.get(),
                        plot.connector());
                return Optional.of(BuilderSites.start(level, template.get(), placement.get().origin(),
                        placement.get().rotation(), Mirror.NONE, heartPos, null));
            }
        }
        return Optional.empty();
    }

    private static Optional<StructureTemplatePool> pool(ServerLevel level, ResourceLocation id) {
        return level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getOptional(id);
    }

    /** The id of a pool of a variant, in the folder of the pool of its homes. */
    private static ResourceLocation sibling(ResourceLocation homes, String name) {
        String path = homes.getPath();
        return homes.withPath(path.substring(0, path.lastIndexOf('/') + 1) + name);
    }

    /**
     * The piece of a step of the growth, from the pools of the variant of the village.
     *
     * @param level the level
     * @param step  the step
     * @param homes the id of the pool of the homes of the variant of the village
     * @return the piece, or empty when the variant has no piece for the step
     */
    static Optional<StructurePoolElement> piece(ServerLevel level, VillageBuildings.Step step, ResourceLocation homes) {
        return switch (step.type()) {
            case HOME -> pool(level, homes).map(pool -> pool.getRandomTemplate(level.random));
            case MARKET -> pool(level, sibling(homes, MARKET)).map(pool -> pool.getRandomTemplate(level.random));
            case BUILDING -> {
                String suffix = "/" + BUILDING_PREFIX + step.kind();
                yield pool(level, sibling(homes, HOUSES)).flatMap(pool -> pool.getShuffledTemplates(level.random)
                        .stream()
                        .filter(element -> VillageReference.templateOf(level, element)
                                .filter(template -> template.getPath().endsWith(suffix)).isPresent())
                        .findFirst());
            }
        };
    }

    /**
     * True if a village needs places: its population is more than its places. A village with an unknown number
     * of beds does not need places.
     *
     * @param settlement the settlement of the village
     * @return true if the village needs places
     */
    public static boolean needsPlaces(Settlement settlement) {
        int places = VillageBeds.places(settlement);
        return places != VillagePlaces.UNKNOWN
                && settlement.economy().population() > places;
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
