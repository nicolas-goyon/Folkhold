package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.village.PlotUses;
import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Dir;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Piece;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.Util;
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
 *   <li>Each plot gets the building of its use (see {@link PlotUses}): the bank (pool {@code bank}), a building of the
 *       level (the piece {@code house_<kind>} of the pool {@code houses}, or the piece of the pool {@code market}), a
 *       house-workshop (the piece {@code job_<id>} of the pool {@code jobs}) or a home (a piece {@link #HOME} of the
 *       pool {@code houses}). The building must fit the area of the plot in the plan. A plot that is not wide takes a
 *       piece that fits the home of the style. A wide plot takes a piece that fits the wide home and not the home of
 *       the style. A free plot stays free: the wall keeps its space. A plot with no building for its use is a problem
 *       of the result.</li>
 *   <li>The job of each post comes from {@link JobAssignment#next} with the jobs of the earlier posts, as the growth
 *       chooses the job of a new post. A job with no piece in the variant, or with no piece that fits the plot, gives
 *       its place to the next job: {@link VillageGrowth#WORKSHOP_JOBS} jobs at most.</li>
 *   <li>The junctions of the jigsaw connect the pieces: a plot with a building has a junction in front of its connector
 *       (see {@link VillagePlots#of}).</li>
 * </ul>
 */
final class VillagePlanPieces {
    /** The connector of the start of a street piece, in the template. */
    private static final BlockPos STREET_START = new BlockPos(StreetStyle.WIDTH / 2, 0, 0);
    /** The free plots keep their space from the connector of the plot to this number of blocks above it. */
    private static final int SPACE_HEIGHT = 8;
    /** The name of the template of a home starts with this text: the cottages of the pool {@code houses}. */
    private static final String HOME = "house_cottage";
    /** The name of the template of a building of a level: this text and the kind. */
    private static final String BUILDING = "house_";
    /** The name of the template of a house-workshop: this text and the path of the job id. */
    private static final String JOB = "job_";
    private static final String HOUSES = "houses";

    /**
     * The pieces of a plan.
     *
     * @param pieces   the plaza, the street pieces and the buildings
     * @param reserved the spaces of the free plots
     * @param built    the number of plots with a building
     * @param free     the number of free plots
     * @param posts    the job ids of the house-workshops, in the order of the posts
     * @param problems the pieces and the plots of the plan with no element of their pool: empty for valid pools
     */
    record Result(List<PoolElementStructurePiece> pieces, List<BoundingBox> reserved, int built, int free,
            List<String> posts, List<String> problems) {
    }

    /**
     * The job posts of a village.
     *
     * @param eligible the jobs that the level and the biome allow
     * @param count    the number of posts
     * @param seed     the seed of the jobs
     */
    record Posts(List<Job> eligible, int count, long seed) {
        Posts {
            eligible = List.copyOf(eligible);
        }
    }

    private VillagePlanPieces() {
    }

    /**
     * Makes the builder of the pieces of a village.
     *
     * @param context the context of the generation
     * @param plaza   the start piece
     * @param center  the middle of the plaza
     * @param homes   the pool of the homes of the variant: the other pools are in its folder
     * @return the builder
     */
    static Builder builder(Structure.GenerationContext context, PoolElementStructurePiece plaza, BlockPos center,
            Holder<StructureTemplatePool> homes) {
        return new Builder(context, plaza, center, homes);
    }

    /** The builder of the pieces of a village. */
    static final class Builder {
        private final Structure.GenerationContext context;
        private final StructureTemplateManager templates;
        private final PoolElementStructurePiece plaza;
        private final BlockPos center;
        private final Rotation rotation;
        private final ResourceLocation homes;
        private final List<StructureTemplatePool> homePools;
        private final Registry<StructureTemplatePool> pools;
        private final Map<StructurePoolElement, String> names = new HashMap<>();
        private final List<PoolElementStructurePiece> pieces = new ArrayList<>();
        private final List<BoundingBox> reserved = new ArrayList<>();
        private final List<String> postJobs = new ArrayList<>();
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

        /**
         * The buildings of a level, with the home of the style that holds the piece of each one. A kind with no piece in
         * the variant, or with a piece that fits no home of the style, is a problem of the result.
         *
         * @param kinds the kinds of the buildings of the level, in their order
         * @param style the style of the plan
         * @return the buildings
         */
        List<PlotUses.Building> buildings(List<String> kinds, StreetStyle style) {
            List<PlotUses.Building> buildings = new ArrayList<>();
            for (String kind : kinds) {
                Optional<Vec3i> size = candidates(kind).stream().findFirst()
                        .map(element -> element.getSize(templates, Rotation.NONE));
                Optional<StreetStyle.Home> home = size.flatMap(found -> Stream.concat(Stream.of(style.home()),
                        style.wide().stream()).filter(candidate -> fits(found, candidate)).findFirst());
                if (home.isEmpty()) {
                    problems.add("no piece of the building " + kind + " that fits a plot");
                } else {
                    buildings.add(new PlotUses.Building(kind, home.get().name()));
                }
            }
            return buildings;
        }

        /**
         * Makes the pieces of a plan.
         *
         * @param style  the style of the plan
         * @param plan   the plan
         * @param uses   the uses of the plots of the plan
         * @param posts  the job posts
         * @param random the random of the choice of the buildings
         * @return the pieces
         */
        Result place(StreetStyle style, VillagePlan plan, PlotUses.Result uses, Posts posts, RandomSource random) {
            uses.missing().forEach(kind -> problems.add("no plot for the building " + kind));
            if (uses.posts() < posts.count()) {
                problems.add("plots for " + uses.posts() + " job posts of " + posts.count());
            }
            pieces.add(plaza);
            List<PoolElementStructurePiece> streets = new ArrayList<>();
            for (Piece piece : plan.pieces()) {
                streets.add(street(piece, piece.parent() < 0 ? plaza : streets.get(piece.parent())));
            }
            streets.stream().filter(street -> street != null).forEach(pieces::add);
            List<Job> eligible = new ArrayList<>(posts.eligible());
            int built = 0;
            int free = 0;
            for (int i = 0; i < plan.plots().size(); i++) {
                Plot plot = plan.plots().get(i);
                PlotUses.Use use = uses.uses().get(i);
                PoolElementStructurePiece street = streets.get(plot.piece());
                Optional<VillagePlots.Plot> connector = street == null ? Optional.empty() : plotOf(street, plot);
                if (connector.isEmpty()) {
                    problems.add("no plot connector at " + plot.x() + " " + plot.z());
                    continue;
                }
                Site site = new Site(sizeTest(style, plot), street, connector.get(),
                        area(plot.area(), connector.get().connector().getY()));
                boolean placed = switch (use.type()) {
                    case FREE -> false;
                    case BANK -> build(site, shuffled("bank", random), "the bank");
                    case BUILDING -> build(site, shuffled(candidates(use.kind()), random), "the building " + use.kind());
                    case POST -> post(site, eligible, posts.seed(), random)
                            || build(site, shuffled(cottages(), random), "a home");
                    case HOME -> build(site, shuffled(cottages(), random), "a home");
                };
                if (placed) {
                    built++;
                } else {
                    free++;
                    List<BoundingBox> space = new ArrayList<>(List.of(site.area()));
                    VillagePlots.space(templates, homePools, site.connector()).ifPresent(space::add);
                    reserved.add(BoundingBox.encapsulatingBoxes(space).orElseThrow());
                }
            }
            return new Result(pieces, reserved, built, free, postJobs, problems);
        }

        /**
         * A plot of the plan in the world.
         *
         * @param fits      true for the size of a piece that fits the plot
         * @param street    the street piece of the plot
         * @param connector the connector of the plot
         * @param area      the box of the area of the plot
         */
        private record Site(Predicate<Vec3i> fits, PoolElementStructurePiece street, VillagePlots.Plot connector,
                BoundingBox area) {
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
            Optional<StructurePoolElement> element = elements(name).stream()
                    .filter(candidate -> candidate.getSize(templates, Rotation.NONE).getZ() == piece.length())
                    .findFirst();
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
         * Places a house-workshop on a plot: the job comes from {@link JobAssignment#next} with the jobs of the earlier
         * posts. A job with no piece that fits the plot leaves the eligible jobs.
         *
         * @return true if the plot has a house-workshop
         */
        private boolean post(Site site, List<Job> eligible, long seed, RandomSource random) {
            for (int i = 0; i < VillageGrowth.WORKSHOP_JOBS; i++) {
                Optional<String> job = JobAssignment.next(postJobs, eligible, EconomyData.catalog(), Merchants::trades,
                        JobAssignment.postSeed(seed, postJobs.size()));
                if (job.isEmpty()) {
                    break;
                }
                ResourceLocation id = ResourceLocation.tryParse(job.get());
                List<StructurePoolElement> workshops = id == null ? List.of() : shuffled(VillagePlots.JOBS, random)
                        .stream().filter(element -> name(element).equals(JOB + id.getPath())).toList();
                if (place(site, workshops)) {
                    postJobs.add(job.get());
                    return true;
                }
                eligible.removeIf(candidate -> candidate.id().equals(job.get()));
            }
            problems.add("no house-workshop for the plot " + site.connector().connector());
            return false;
        }

        /**
         * Places the first element of a list that fits a plot.
         *
         * @return true if the plot has a building
         */
        private boolean build(Site site, List<StructurePoolElement> elements, String building) {
            if (place(site, elements)) {
                return true;
            }
            problems.add("no piece of " + building + " for the plot " + site.connector().connector());
            return false;
        }

        /** Places the first element of a list that fits a plot, with the junctions of a jigsaw connection. */
        private boolean place(Site site, List<StructurePoolElement> elements) {
            int ground = height(site.connector().connector());
            for (StructurePoolElement element : elements) {
                if (!site.fits().test(element.getSize(templates, Rotation.NONE))) {
                    continue;
                }
                Optional<VillagePlots.Placement> placement = VillagePlots.place(templates, element, site.connector(),
                        ground);
                if (placement.isEmpty() || !inside(placement.get().box(), site.area())) {
                    continue;
                }
                PoolElementStructurePiece building = new PoolElementStructurePiece(templates, element,
                        placement.get().origin(), site.street().getGroundLevelDelta(), placement.get().rotation(),
                        placement.get().box(), LiquidSettings.APPLY_WATERLOGGING);
                BlockPos front = site.connector().front();
                BlockPos connector = site.connector().connector();
                site.street().addJunction(new JigsawJunction(front.getX(), ground, front.getZ(), 0,
                        StructureTemplatePool.Projection.RIGID));
                building.addJunction(new JigsawJunction(connector.getX(), ground, connector.getZ(), 0,
                        StructureTemplatePool.Projection.TERRAIN_MATCHING));
                pieces.add(building);
                return true;
            }
            return false;
        }

        /** The test of the size of a piece on a plot: the home of the style, or the wide home and not the home. */
        private static Predicate<Vec3i> sizeTest(StreetStyle style, Plot plot) {
            return plot.wide() ? size -> fits(size, plot.homes().get(1)) && !fits(size, style.home())
                    : size -> fits(size, style.home());
        }

        /** The pieces of a building of a level: the pieces of the pool of the market, else the pieces {@code house_<kind>}. */
        private List<StructurePoolElement> candidates(String kind) {
            List<StructurePoolElement> market = elements(VillageBuildings.MARKET);
            return kind.equals(VillageBuildings.MARKET) ? market : elements(HOUSES).stream()
                    .filter(element -> name(element).equals(BUILDING + kind)).toList();
        }

        /** The pieces of the homes: the pieces {@link #HOME} of the pool {@code houses}. */
        private List<StructurePoolElement> cottages() {
            return elements(HOUSES).stream().filter(element -> name(element).startsWith(HOME)).toList();
        }

        /** The elements of a pool of the variant, with no empty element, in a fixed order. */
        private List<StructurePoolElement> elements(String name) {
            return shuffled(name, RandomSource.create(0L));
        }

        /** The elements of a pool of the variant, with no empty element, in a random order. */
        private List<StructurePoolElement> shuffled(String name, RandomSource random) {
            return pools.getOptional(ResourceKey.create(Registries.TEMPLATE_POOL, VillagePlots.sibling(homes, name)))
                    .map(pool -> pool.getShuffledTemplates(random).stream()
                            .filter(element -> element != EmptyPoolElement.INSTANCE).toList())
                    .orElse(List.of());
        }

        /** Some elements in a random order. */
        private static List<StructurePoolElement> shuffled(List<StructurePoolElement> elements, RandomSource random) {
            List<StructurePoolElement> result = new ArrayList<>(elements);
            Util.shuffle(result, random);
            return result;
        }

        /** The name of the template of an element: the last part of its path, or an empty text. */
        private String name(StructurePoolElement element) {
            return names.computeIfAbsent(element, key -> VillageReference.templateOf(context.registryAccess(), key)
                    .map(id -> id.getPath().substring(id.getPath().lastIndexOf('/') + 1)).orElse(""));
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
