package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Dir;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Piece;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The pieces of a village from its plan (see {@link VillagePlan}): the street pieces and the buildings on the plots.
 *
 * <ul>
 *   <li>The plan uses the frame of the plaza template: its north is the side of the street of the bank. The plan turns
 *       with the plaza: the middle of the plaza is (0, 0) of the plan.</li>
 *   <li>Each street piece is the element of its pool with the length of the piece (rotation {@code NONE}, along z):
 *       {@code streets_bank}, {@code streets_jobs} and {@code streets_first} for the first piece of the street to the
 *       north, to the south and to the east or west, {@code streets_next} for a segment or a crossroad,
 *       {@code streets_side} for the start of a side street and {@code street_ends} for a street end. The pools are in
 *       the folder of the pool of the homes of the variant. The piece follows the terrain, as in a jigsaw
 *       structure.</li>
 *   <li>A plot gets a building of the pool of its role: {@code bank}, {@code jobs_food}, {@code jobs_start} or
 *       {@code houses} for a home (with the fallback pools). The building must fit the area of the plot in the plan.
 *       A home plot takes a home that fits the home of the style. A wide plot takes a home that fits the wide home and
 *       not the home of the style. A spare plot and the wide plot of the market ({@link #MARKET}) stay free: the wall
 *       keeps their space. Another plot with no building is a problem of the result.</li>
 *   <li>The junctions of the jigsaw connect the pieces: a plot with a building has a junction in front of its connector
 *       (see {@link VillagePlots#of}).</li>
 * </ul>
 */
final class VillagePlanPieces {
    /** The connector of the start of a street piece, in the template. */
    private static final BlockPos STREET_START = new BlockPos(StreetStyle.WIDTH / 2, 0, 0);
    /** The free plots keep their space from the connector of the plot to this number of blocks above it. */
    private static final int SPACE_HEIGHT = 8;
    /** The wide home of the plot of the market: the pool {@code houses} has no market, and the plot stays free. */
    static final String MARKET = "market";

    private VillagePlanPieces() {
    }

    /**
     * The pieces of a plan.
     *
     * @param pieces   the plaza, the street pieces and the buildings
     * @param reserved the spaces of the free plots
     * @param built    the number of plots with a building
     * @param free     the number of free plots
     * @param problems the pieces and the plots of the plan with no element of their pool: empty for valid pools
     */
    record Result(List<PoolElementStructurePiece> pieces, List<BoundingBox> reserved, int built, int free,
            List<String> problems) {
    }

    /**
     * Makes the pieces of a plan.
     *
     * @param context the context of the generation
     * @param plaza   the start piece
     * @param center  the middle of the plaza
     * @param homes   the pool of the homes of the variant: the other pools are in its folder
     * @param style   the style of the plan
     * @param plan    the plan
     * @param random  the random of the choice of the buildings
     * @return the pieces
     */
    static Result place(Structure.GenerationContext context, PoolElementStructurePiece plaza, BlockPos center,
            Holder<StructureTemplatePool> homes, StreetStyle style, VillagePlan plan, RandomSource random) {
        return new VillagePlanPieces.Builder(context, plaza, center, homes).place(style, plan, random);
    }

    private static final class Builder {
        private final Structure.GenerationContext context;
        private final StructureTemplateManager templates;
        private final PoolElementStructurePiece plaza;
        private final BlockPos center;
        private final Rotation rotation;
        private final ResourceLocation homes;
        private final List<StructureTemplatePool> homePools;
        private final Registry<StructureTemplatePool> pools;
        private final List<PoolElementStructurePiece> pieces = new ArrayList<>();
        private final List<BoundingBox> reserved = new ArrayList<>();
        private final List<String> problems = new ArrayList<>();

        Builder(Structure.GenerationContext context, PoolElementStructurePiece plaza, BlockPos center,
                Holder<StructureTemplatePool> homes) {
            this.context = context;
            this.templates = context.structureTemplateManager();
            this.plaza = plaza;
            this.center = center;
            this.rotation = plaza.getRotation();
            this.homes = homes.unwrapKey().orElseThrow().location();
            this.homePools = VillagePlots.homePools(context.registryAccess(), homes);
            this.pools = context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        }

        Result place(StreetStyle style, VillagePlan plan, RandomSource random) {
            pieces.add(plaza);
            List<PoolElementStructurePiece> streets = new ArrayList<>();
            for (Piece piece : plan.pieces()) {
                streets.add(street(piece, piece.parent() < 0 ? plaza : streets.get(piece.parent())));
            }
            streets.stream().filter(street -> street != null).forEach(pieces::add);
            int built = 0;
            int free = 0;
            for (Plot plot : plan.plots()) {
                PoolElementStructurePiece street = streets.get(plot.piece());
                Optional<VillagePlots.Plot> connector = street == null ? Optional.empty() : plotOf(street, plot);
                if (connector.isEmpty()) {
                    problems.add("no plot connector at " + plot.x() + " " + plot.z());
                    continue;
                }
                if (building(style, plot, street, connector.get(), random)) {
                    built++;
                } else {
                    free++;
                }
            }
            return new Result(pieces, reserved, built, free, problems);
        }

        /** The piece of a street, connected to the piece before it. Null if its pool has no element of its length. */
        private PoolElementStructurePiece street(Piece piece, PoolElementStructurePiece parent) {
            String name = switch (piece.kind()) {
                case SEGMENT, CROSS -> piece.parent() >= 0 ? "streets_next" : switch (piece.dir()) {
                    case NORTH -> "streets_bank";
                    case SOUTH -> "streets_jobs";
                    case EAST, WEST -> "streets_first";
                };
                case SIDE -> "streets_side";
                case END -> "street_ends";
            };
            Optional<StructurePoolElement> element = pool(name).flatMap(pool -> pool.getShuffledTemplates(
                    RandomSource.create(0L)).stream()
                    .filter(candidate -> candidate != EmptyPoolElement.INSTANCE)
                    .filter(candidate -> candidate.getSize(templates, Rotation.NONE).getZ() == piece.length())
                    .findFirst());
            if (element.isEmpty() || parent == null) {
                problems.add("no " + piece.kind() + " of length " + piece.length() + " in " + name);
                return null;
            }
            BlockPos start = world(piece.x(), piece.z());
            BlockPos before = world(piece.x() - piece.dir().dx(), piece.z() - piece.dir().dz());
            int ground = height(before);
            Rotation turn = rotation.getRotated(turns(piece.dir()));
            BlockPos origin = start.atY(ground).subtract(STREET_START.rotate(turn));
            PoolElementStructurePiece street = new PoolElementStructurePiece(templates, element.get(), origin,
                    element.get().getGroundLevelDelta(), turn,
                    element.get().getBoundingBox(templates, origin, turn), LiquidSettings.APPLY_WATERLOGGING);
            // The junctions of a jigsaw connection, at the level of the plaza after the plaza.
            int level = parent == plaza ? plaza.getBoundingBox().minY() + plaza.getGroundLevelDelta()
                    : ground + street.getGroundLevelDelta();
            parent.addJunction(new JigsawJunction(start.getX(), level, start.getZ(), 0,
                    StructureTemplatePool.Projection.TERRAIN_MATCHING));
            street.addJunction(new JigsawJunction(before.getX(), level, before.getZ(), 0,
                    parent.getElement().getProjection()));
            return street;
        }

        /** The plot of a street piece at the connector of a plot of the plan. */
        private Optional<VillagePlots.Plot> plotOf(PoolElementStructurePiece street, Plot plot) {
            BlockPos at = world(plot.x(), plot.z());
            return VillagePlots.of(templates, List.of(street)).stream()
                    .filter(candidate -> candidate.connector().getX() == at.getX()
                            && candidate.connector().getZ() == at.getZ())
                    .findFirst();
        }

        /**
         * Places the building of a plot, or keeps the space of a free plot.
         *
         * @return true if the plot has a building
         */
        private boolean building(StreetStyle style, Plot plot, PoolElementStructurePiece street,
                VillagePlots.Plot connector, RandomSource random) {
            BoundingBox area = area(plot.area(), connector.connector().getY());
            Predicate<Vec3i> fits = switch (plot.role()) {
                case HOME -> plot.wide()
                        ? size -> fits(size, plot.homes().get(1)) && !fits(size, style.home())
                        : size -> fits(size, style.home());
                case BANK, JOBS_FOOD, JOBS_START -> size -> true;
            };
            String name = switch (plot.role()) {
                case HOME -> "houses";
                case BANK -> "bank";
                case JOBS_FOOD -> "jobs_food";
                case JOBS_START -> "jobs_start";
            };
            int ground = height(connector.connector());
            Optional<StructureTemplatePool> pool = plot.spare() ? Optional.empty() : pool(name);
            for (int depth = 0; pool.isPresent() && depth < 4; depth++) {
                for (StructurePoolElement element : pool.get().getShuffledTemplates(random)) {
                    if (element == EmptyPoolElement.INSTANCE || !fits.test(element.getSize(templates, Rotation.NONE))) {
                        continue;
                    }
                    Optional<VillagePlots.Placement> placement = VillagePlots.place(templates, element, connector,
                            ground);
                    if (placement.isPresent() && inside(placement.get().box(), area)) {
                        add(street, connector, element, placement.get(), ground);
                        return true;
                    }
                }
                pool = Optional.of(pool.get().getFallback().value());
            }
            if (!plot.spare() && !(plot.wide() && plot.homes().get(1).name().equals(MARKET))) {
                problems.add("no building of " + name + " for the plot " + connector.connector());
            }
            List<BoundingBox> space = new ArrayList<>(List.of(area));
            VillagePlots.space(templates, homePools, connector).ifPresent(space::add);
            reserved.add(BoundingBox.encapsulatingBoxes(space).orElseThrow());
            return false;
        }

        /** Adds a building on a plot, with the junctions of a jigsaw connection. */
        private void add(PoolElementStructurePiece street, VillagePlots.Plot plot, StructurePoolElement element,
                VillagePlots.Placement placement, int ground) {
            PoolElementStructurePiece building = new PoolElementStructurePiece(templates, element, placement.origin(),
                    street.getGroundLevelDelta(), placement.rotation(), placement.box(),
                    LiquidSettings.APPLY_WATERLOGGING);
            BlockPos front = plot.front();
            street.addJunction(new JigsawJunction(front.getX(), ground, front.getZ(), 0,
                    StructureTemplatePool.Projection.RIGID));
            building.addJunction(new JigsawJunction(plot.connector().getX(), ground, plot.connector().getZ(), 0,
                    StructureTemplatePool.Projection.TERRAIN_MATCHING));
            pieces.add(building);
        }

        private Optional<StructureTemplatePool> pool(String name) {
            return pools.getOptional(ResourceKey.create(Registries.TEMPLATE_POOL, VillagePlots.sibling(homes, name)));
        }

        /** A position of the plan in the world, at Y 0. */
        private BlockPos world(int x, int z) {
            return center.atY(0).offset(new BlockPos(x, 0, z).rotate(rotation));
        }

        /** The box of an area of the plan in the world, from a Y to {@link #SPACE_HEIGHT} blocks above it. */
        private BoundingBox area(Box box, int y) {
            BlockPos first = world(box.minX(), box.minZ());
            BlockPos second = world(box.maxX(), box.maxZ());
            return BoundingBox.fromCorners(first.atY(y - 1), second.atY(y + SPACE_HEIGHT));
        }

        /** The Y of the first free block above the terrain, as the jigsaw reads it. */
        private int height(BlockPos pos) {
            return context.chunkGenerator().getFirstFreeHeight(pos.getX(), pos.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        }
    }

    /** True if a template with a size fits a home: its width along the street and its depth. */
    private static boolean fits(Vec3i size, StreetStyle.Home home) {
        return size.getX() <= home.frontage() && size.getZ() <= home.depth();
    }

    /** True if a box is inside an area on the horizontal plane. */
    private static boolean inside(BoundingBox box, BoundingBox area) {
        return box.minX() >= area.minX() && box.maxX() <= area.maxX() && box.minZ() >= area.minZ()
                && box.maxZ() <= area.maxZ();
    }

    /** The turn of a street piece that goes in a direction: the template goes south. */
    private static Rotation turns(Dir dir) {
        return switch (dir.clockwiseTurns()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The seed of the choices of a village: from the seed of the world and the start chunk. */
    static long seed(long worldSeed, ChunkPos chunk) {
        return worldSeed ^ (chunk.toLong() * 0x9E3779B97F4A7C15L);
    }
}
