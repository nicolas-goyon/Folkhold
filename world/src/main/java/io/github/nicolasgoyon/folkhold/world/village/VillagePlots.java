package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The plots of a village: the places for a house along its streets.
 *
 * <ul>
 *   <li>A street template has a jigsaw connector for each plot, with the name {@link #PLOT}. The target of the
 *       connector is the name of the connector of a house.</li>
 *   <li>The generation puts a house of the pool of the connector on a plot, or leaves the plot free. A plot with a
 *       house has a junction of the jigsaw structure in front of its connector.</li>
 *   <li>The wall of the village keeps the space of a new home on each free plot (see {@link #reserved} and
 *       {@link io.github.nicolasgoyon.folkhold.world.village.structure.VillageWall}). This space holds each piece of
 *       the pools of the homes and of the house-workshops of the variant (see {@link #homePools}). The builders of the
 *       village build a house-workshop on a free plot when the village needs places (see {@link VillageGrowth}).</li>
 * </ul>
 */
public final class VillagePlots {
    /** The name of the connector of a plot, in the street templates. */
    public static final ResourceLocation PLOT = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "plot");
    /** The name of the pool of the house-workshops of a variant, in the folder of the pool of its homes. */
    public static final String JOBS = "jobs";
    /** The order of the connectors of a template does not matter here: a fixed random source is enough. */
    private static final long SEED = 0L;

    private VillagePlots() {
    }

    /**
     * A plot of a village.
     *
     * @param connector the position of the connector of the plot, in the street
     * @param facing    the direction from the street to the plot
     * @param target    the name of the connector of a house for this plot
     * @param built     true if the generation put a building on the plot
     */
    public record Plot(BlockPos connector, Direction facing, ResourceLocation target, boolean built) {
        public Plot {
            connector = connector.immutable();
        }

        /** The position in front of the connector of the plot: the connector of the house goes there. */
        public BlockPos front() {
            return connector.relative(facing);
        }
    }

    /**
     * The placement of a template on a plot.
     *
     * @param origin   the position of the origin of the template
     * @param rotation the turn of the template
     * @param box      the box of the template
     */
    public record Placement(BlockPos origin, Rotation rotation, BoundingBox box) {
    }

    /**
     * The plots of the pieces of a village structure.
     *
     * @param templates the structure templates of the server
     * @param pieces    the pieces of the structure
     * @return the plots, with and without a house
     */
    public static List<Plot> of(StructureTemplateManager templates, List<StructurePiece> pieces) {
        List<Plot> plots = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            Set<Long> junctions = new HashSet<>();
            for (JigsawJunction junction : pool.getJunctions()) {
                junctions.add(ChunkPos.asLong(junction.getSourceX(), junction.getSourceZ()));
            }
            for (StructureTemplate.StructureBlockInfo info : connectors(templates, pool.getElement(),
                    pool.getPosition(), pool.getRotation())) {
                if (!PLOT.equals(name(info, "name"))) {
                    continue;
                }
                Direction facing = JigsawBlock.getFrontFacing(info.state());
                BlockPos front = info.pos().relative(facing);
                ResourceLocation target = name(info, "target");
                if (target != null) {
                    plots.add(new Plot(info.pos(), facing, target,
                            junctions.contains(ChunkPos.asLong(front.getX(), front.getZ()))));
                }
            }
        }
        return plots;
    }

    /**
     * Places a pool element on a plot: the connector of the element with the target name of the plot goes in front of
     * the connector of the plot, and faces it.
     *
     * @param templates the structure templates of the server
     * @param element   the pool element
     * @param plot      the plot
     * @param ground    the Y of the first free block above the ground at the connector of the plot. The connector of
     *                  the element goes at this Y.
     * @return the placement, or empty if the element has no connector for the plot
     */
    public static Optional<Placement> place(StructureTemplateManager templates, StructurePoolElement element, Plot plot,
            int ground) {
        if (element == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }
        for (Rotation rotation : Rotation.values()) {
            for (StructureTemplate.StructureBlockInfo info : connectors(templates, element, BlockPos.ZERO, rotation)) {
                if (JigsawBlock.getFrontFacing(info.state()) == plot.facing().getOpposite()
                        && plot.target().equals(name(info, "name"))) {
                    BlockPos front = plot.front();
                    BlockPos origin = new BlockPos(front.getX() - info.pos().getX(), ground - info.pos().getY(),
                            front.getZ() - info.pos().getZ());
                    return Optional.of(new Placement(origin, rotation,
                            element.getBoundingBox(templates, origin, rotation)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The space of the new homes on a plot: the box around the placements of all the elements of the pools. The
     * connector of each element goes at the Y of the connector of the plot.
     *
     * @param templates the structure templates of the server
     * @param pools     the pools of the new homes (see {@link #homePools})
     * @param plot      the plot
     * @return the box, or empty if no element of the pools fits the plot
     */
    public static Optional<BoundingBox> space(StructureTemplateManager templates, List<StructureTemplatePool> pools,
            Plot plot) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructureTemplatePool pool : pools) {
            for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(SEED))) {
                place(templates, element, plot, plot.connector().getY())
                        .ifPresent(placement -> boxes.add(placement.box()));
            }
        }
        return BoundingBox.encapsulatingBoxes(boxes);
    }

    /**
     * The pools of the new homes of a variant: the pool of its homes, and the pool {@link #JOBS} in the same folder
     * when it exists. Each new home of the growth is a house-workshop of the pool {@link #JOBS} (see
     * {@link VillageGrowth}). The pieces of the 2 pools can have different sizes: the space of a plot holds all of
     * them. A variant with no pool {@link #JOBS} gets a warning in the log: then the space holds only the homes.
     *
     * @param registries the registries of the server
     * @param homes      the pool of the homes of the variant
     * @return the pools, the pool of the homes first
     */
    public static List<StructureTemplatePool> homePools(RegistryAccess registries,
            Holder<StructureTemplatePool> homes) {
        List<StructureTemplatePool> pools = new ArrayList<>();
        pools.add(homes.value());
        Optional<ResourceLocation> jobs = homes.unwrapKey().map(key -> sibling(key.location(), JOBS));
        Optional<StructureTemplatePool> pool = jobs
                .flatMap(id -> registries.registryOrThrow(Registries.TEMPLATE_POOL).getOptional(id));
        if (pool.isPresent()) {
            pools.add(pool.get());
        } else {
            FolkholdWorld.LOGGER.warn("The pool of the homes {} has no pool {} in its folder: the space of a free plot"
                    + " holds only the homes", homes.unwrapKey().map(key -> key.location().toString())
                    .orElse("(no id)"), jobs.map(ResourceLocation::toString).orElse(JOBS));
        }
        return pools;
    }

    /**
     * The spaces that the wall of a village keeps for its growth: the space of the new homes on each free plot of the
     * pieces (see {@link #space}).
     *
     * @param templates the structure templates of the server
     * @param pools     the pools of the new homes (see {@link #homePools})
     * @param pieces    the pieces of the village structure
     * @return the spaces, one for each free plot that an element of the pools fits
     */
    public static List<BoundingBox> reserved(StructureTemplateManager templates, List<StructureTemplatePool> pools,
            List<StructurePiece> pieces) {
        return of(templates, pieces).stream()
                .filter(plot -> !plot.built())
                .flatMap(plot -> space(templates, pools, plot).stream())
                .toList();
    }

    /**
     * The id of a pool of a variant, in the folder of the pool of its homes.
     *
     * @param homes the id of the pool of the homes of the variant
     * @param name  the name of the pool
     * @return the id of the pool with this name in the same folder
     */
    public static ResourceLocation sibling(ResourceLocation homes, String name) {
        String path = homes.getPath();
        return homes.withPath(path.substring(0, path.lastIndexOf('/') + 1) + name);
    }

    /**
     * The Y of a connector of a piece: the connector with a name at a position on the horizontal plane.
     *
     * @param templates the structure templates of the server
     * @param piece     the piece
     * @param name      the name of the connector
     * @param x         the X of the connector
     * @param z         the Z of the connector
     * @return the Y of the connector, or empty if the piece has no such connector
     */
    public static OptionalInt connectorY(StructureTemplateManager templates, PoolElementStructurePiece piece,
            ResourceLocation name, int x, int z) {
        for (StructureTemplate.StructureBlockInfo info : connectors(templates, piece.getElement(),
                piece.getPosition(), piece.getRotation())) {
            if (info.pos().getX() == x && info.pos().getZ() == z && name.equals(name(info, "name"))) {
                return OptionalInt.of(info.pos().getY());
            }
        }
        return OptionalInt.empty();
    }

    private static List<StructureTemplate.StructureBlockInfo> connectors(StructureTemplateManager templates,
            StructurePoolElement element, BlockPos pos, Rotation rotation) {
        return element.getShuffledJigsawBlocks(templates, pos, rotation, RandomSource.create(SEED));
    }

    /** A name of the data of a jigsaw connector, or null. */
    @Nullable
    private static ResourceLocation name(StructureTemplate.StructureBlockInfo info, String key) {
        return info.nbt() == null ? null : ResourceLocation.tryParse(info.nbt().getString(key));
    }
}
