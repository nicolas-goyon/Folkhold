package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.village.PlotUses;
import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.faction.CultureMap;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageBuildingTable;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageSizes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The faction village: the streets and the buildings of the plan of its tier, in a closed wall.
 *
 * <ul>
 *   <li>The start piece (the plaza) goes on the terrain. Its origin is the north-west corner of the start chunk, and
 *       its rotation is random.</li>
 *   <li>The terrain around the middle of the start piece must accept a village (see {@link VillageSite}): the plaza
 *       is not on water, the water does not cover half of the place, and the slope is not one of the steepest. The
 *       discovery of the settlements uses the same check: a settlement has a village.</li>
 *   <li>The start level of the village comes from the world seed and the start chunk (see
 *       {@link Settlements#startLevel(long, ChunkPos)}). Its tier gives the size of the village (see
 *       {@link VillageSizes}): the plot target and the maximum radius of the plan (see {@link VillagePlan}).</li>
 *   <li>The plan uses the plots with good terrain (see {@link PlotTerrains}): no lava, no deep water and no steep
 *       slope. A plot with bad terrain is a spare plot. The plots of the bank and of the first jobs next to the plaza
 *       keep their building on bad terrain. A building over a ravine or a lava lake (the noise of the terrain does not
 *       show them) stands on the foundation of its template.</li>
 *   <li>The street pieces and the buildings on the plots come from the plan (see {@link VillagePlanPieces}). The
 *       village has the buildings of its start level (see {@link VillageBuildingTable}) and the job posts of its start
 *       level: one house-workshop for each job slot of the level, at most one for each job that the level and the
 *       biome allow (see {@link Economies#eligibleJobs(Holder, int, String)}). The other plots get homes (see
 *       {@link PlotUses}). The spare plots stay free.</li>
 *   <li>Then the structure adds the wall around all the pieces and around the space of each free plot (see
 *       {@link VillageWall}). The wall has towers, a gate on each axis of the start piece and the palisade. Paths go
 *       from the gates to the streets.</li>
 * </ul>
 *
 * <p>A saved village keeps its pieces: only a new village start uses this generation.
 *
 * <p>Type {@code folkhold_world:fortified_village}. The JSON object has the fields of all the structures, and these
 * fields:
 *
 * <ul>
 *   <li>{@code start_pool}: the pool of the start piece.</li>
 *   <li>{@code max_distance_from_center}: the largest radius of the plan, in blocks. The plan of a tier with a
 *       larger radius uses this radius.</li>
 *   <li>{@code wall}: the settings of the wall (see {@link WallSettings}).</li>
 *   <li>{@code homes}: the pool of the homes that the builders of the village build on the free plots (see
 *       {@link VillagePlots}). The pools of the streets and of the buildings of the plan are in its folder.</li>
 *   <li>{@code variants}: the biome variants of the village (see {@link Variant}). The biome at the middle of the start
 *       piece selects the first variant with this biome. In the other biomes, the village uses the start pool, the
 *       wall and the homes of the structure: the base variant. A site on the coast selects a maritime variant (see
 *       {@link VillageSite.Result#MARITIME}).</li>
 * </ul>
 */
public final class FortifiedVillageStructure extends Structure {
    /** The largest distance of the pieces from the middle, with the wall and the terrain adaptation, in blocks. */
    private static final int MAX_RANGE = JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE;
    /** The terrain adaptation changes the terrain at this distance from a piece, in blocks. */
    private static final int ADAPTATION_RANGE = 12;

    public static final MapCodec<FortifiedVillageStructure> CODEC = RecordCodecBuilder
            .<FortifiedVillageStructure>mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(FortifiedVillageStructure::startPool),
                    Codec.intRange(1, MAX_RANGE).fieldOf("max_distance_from_center")
                            .forGetter(FortifiedVillageStructure::maxDistanceFromCenter),
                    WallSettings.CODEC.fieldOf("wall").forGetter(FortifiedVillageStructure::wall),
                    StructureTemplatePool.CODEC.fieldOf("homes").forGetter(FortifiedVillageStructure::homes),
                    Variant.CODEC.listOf().optionalFieldOf("variants", List.of())
                            .forGetter(FortifiedVillageStructure::variants))
                    .apply(instance, FortifiedVillageStructure::new))
            .validate(FortifiedVillageStructure::verifyRange);

    /**
     * A variant of the village: the pieces of a culture (its style), or the same village with other materials, for
     * example the local wood.
     *
     * @param biomes    the biomes of the variant. A variant of a culture with no biome is the base variant of the
     *                  culture, in all the biomes.
     * @param startPool the pool of the start piece
     * @param wall      the settings of the wall
     * @param homes     the pool of the homes that the builders build on the free plots
     * @param culture   the id of the culture of the variant, or empty for a variant of all the cultures
     * @param maritime  true for a variant of a maritime village: a village on the coast or on an island. Only a
     *                  maritime site selects a maritime variant.
     */
    public record Variant(HolderSet<Biome> biomes, Holder<StructureTemplatePool> startPool, WallSettings wall,
            Holder<StructureTemplatePool> homes, Optional<String> culture, boolean maritime) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Variant::biomes),
                StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(Variant::startPool),
                WallSettings.CODEC.fieldOf("wall").forGetter(Variant::wall),
                StructureTemplatePool.CODEC.fieldOf("homes").forGetter(Variant::homes),
                Codec.STRING.optionalFieldOf("culture").forGetter(Variant::culture),
                Codec.BOOL.optionalFieldOf("maritime", false).forGetter(Variant::maritime))
                .apply(instance, Variant::new));

        /** A variant that is not maritime. */
        public Variant(HolderSet<Biome> biomes, Holder<StructureTemplatePool> startPool, WallSettings wall,
                Holder<StructureTemplatePool> homes, Optional<String> culture) {
            this(biomes, startPool, wall, homes, culture, false);
        }

        /** True if the variant is for a culture: a variant with no culture is for all the cultures. */
        boolean isFor(String id) {
            return culture.map(id::equals).orElse(true);
        }
    }

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDistanceFromCenter;
    private final WallSettings wall;
    private final Holder<StructureTemplatePool> homes;
    private final List<Variant> variants;
    /** The base variant: the start pool, the wall and the homes of the structure, in all the biomes. */
    private final Variant base;

    public FortifiedVillageStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool,
            int maxDistanceFromCenter, WallSettings wall, Holder<StructureTemplatePool> homes,
            List<Variant> variants) {
        super(settings);
        this.startPool = startPool;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.wall = wall;
        this.homes = homes;
        this.variants = List.copyOf(variants);
        this.base = new Variant(HolderSet.direct(), startPool, wall, homes, Optional.empty());
    }

    /**
     * The game places the pieces of a structure only in a range of 8 chunks from the start chunk. The wall is outside
     * the pieces of the plan: it needs the margin, the line of the palisade and the outer row of the gates.
     */
    private static DataResult<FortifiedVillageStructure> verifyRange(FortifiedVillageStructure structure) {
        int adaptation = structure.terrainAdaptation() == TerrainAdjustment.NONE ? 0 : ADAPTATION_RANGE;
        int margin = structure.variants.stream().mapToInt(variant -> variant.wall().margin())
                .reduce(structure.wall.margin(), Math::max);
        int range = structure.maxDistanceFromCenter + margin + 2 + adaptation;
        return range > MAX_RANGE
                ? DataResult.error(() -> "The village with its wall and its terrain adaptation goes " + range
                        + " blocks from its middle: the limit is " + MAX_RANGE)
                : DataResult.success(structure);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<Start> start = start(context);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        Start village = start.get();
        return Optional.of(new GenerationStub(village.center(), builder -> layout(context, village,
                Settlements.startLevel(context.seed(), context.chunkPos())).pieces().forEach(builder::addPiece)));
    }

    /**
     * The start of a village at the start chunk of a context: the plaza, its variant and its site. Empty if the site
     * does not accept a village.
     */
    private Optional<Start> start(GenerationContext context) {
        // The culture of the region, the biome at the middle of the plaza of the base variant and the site select the
        // variant. The two placements start with the same random. Thus the plaza of a variant has the same rotation and
        // the same middle (the plazas have the same size), and the village has the same layout in these variants.
        restartRandom(context);
        Optional<Start> start = plaza(context, base, false, Optional.empty());
        if (start.isEmpty()) {
            return Optional.empty();
        }
        BlockPos middle = start.get().center();
        VillageSite.Result site = checkSite(context, middle);
        if (site != VillageSite.Result.ACCEPTED && site != VillageSite.Result.MARITIME) {
            return Optional.empty();
        }
        boolean maritime = site == VillageSite.Result.MARITIME;
        CultureMap cultures = new CultureMap(context.seed(), context.biomeSource(), context.randomState().sampler());
        Variant variant = variantAt(cultures.cultureAt(middle.getX(), middle.getZ()), biomeAt(context, middle),
                maritime);
        if (variant == base) {
            return start;
        }
        restartRandom(context);
        return plaza(context, variant, maritime, Optional.empty());
    }

    /**
     * The plaza of a variant at the start chunk, from the random of the context: the random rotation, then the random
     * element of the start pool. The plaza goes on the surface of the terrain at its middle. A given rotation takes the
     * place of the random rotation: the random gives the same element of the start pool.
     */
    private static Optional<Start> plaza(GenerationContext context, Variant variant, boolean maritime,
            Optional<Rotation> turn) {
        StructureTemplateManager templates = context.structureTemplateManager();
        ChunkPos chunk = context.chunkPos();
        Rotation rotation = turn.orElse(Rotation.getRandom(context.random()));
        StructurePoolElement element = variant.startPool().value().getRandomTemplate(context.random());
        if (element == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
        PoolElementStructurePiece plaza = new PoolElementStructurePiece(templates, element, origin,
                element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, origin, rotation),
                LiquidSettings.APPLY_WATERLOGGING);
        BoundingBox box = plaza.getBoundingBox();
        int x = (box.minX() + box.maxX()) / 2;
        int z = (box.minZ() + box.maxZ()) / 2;
        int ground = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        plaza.move(0, ground - (box.minY() + plaza.getGroundLevelDelta()), 0);
        return Optional.of(new Start(plaza, new BlockPos(x, ground, z), variant, maritime));
    }

    /**
     * The start of a village.
     *
     * @param plaza    the start piece
     * @param center   the middle of the plaza, on the surface of the terrain
     * @param variant  the variant
     * @param maritime true for a maritime site
     */
    private record Start(PoolElementStructurePiece plaza, BlockPos center, Variant variant, boolean maritime) {
    }

    /**
     * The layout of a village: all its pieces, without the placement of blocks.
     *
     * @param level    the start level of the village
     * @param size     the size of the tier of the village
     * @param style    the style of the street pieces
     * @param plan     the plan of the streets and of the plots
     * @param center   the middle of the plaza
     * @param village  the plaza, the street pieces and the buildings
     * @param reserved the spaces of the free plots: the spare plots, and the wide plots with no home of the pools
     * @param wall     the pieces of the wall
     * @param built    the number of plots with a building
     * @param free     the number of free plots
     * @param posts    the job ids of the house-workshops, in the order of the posts
     * @param problems the pieces and the plots of the plan with no element of their pool: empty for valid pools
     */
    public record Layout(int level, VillageSize size, StreetStyle style, VillagePlan plan, BlockPos center,
            List<PoolElementStructurePiece> village, List<BoundingBox> reserved, List<StructurePiece> wall, int built,
            int free, List<String> posts, List<String> problems) {
        public Layout {
            village = List.copyOf(village);
            posts = List.copyOf(posts);
            reserved = List.copyOf(reserved);
            wall = List.copyOf(wall);
            problems = List.copyOf(problems);
        }

        /** All the pieces of the village: the plaza first, then the streets, the buildings and the wall. */
        public List<StructurePiece> pieces() {
            List<StructurePiece> pieces = new ArrayList<>(village);
            pieces.addAll(wall);
            return pieces;
        }
    }

    /**
     * The layout of the village at the start chunk of a context, as the generation makes it.
     *
     * @param context the context of the generation
     * @return the layout, or empty if the site does not accept a village
     */
    public Optional<Layout> layout(GenerationContext context) {
        return start(context).map(start -> layout(context, start,
                Settlements.startLevel(context.seed(), context.chunkPos())));
    }

    /**
     * The layout of the village at the start chunk of a context, as the generation makes it, with a given start level
     * in place of the start level of the chunk. The command {@code /folkhold village generate} uses it.
     *
     * @param context the context of the generation: its biome test is the biome rule of the structure
     * @param level   the start level: its tier gives the size of the village
     * @param variant the test of the variant of the site
     * @return the layout, or empty if the site does not accept a village, if the biome at the middle of the plaza is
     *         not valid (as {@code Structure.generate} checks it), or if the variant fails the test
     */
    public Optional<Layout> layout(GenerationContext context, int level, Predicate<Variant> variant) {
        return start(context).filter(start -> context.validBiome().test(biomeAt(context, start.center())))
                .filter(start -> variant.test(start.variant())).map(start -> layout(context, start, level));
    }

    /**
     * The layout of a village of a variant and of a start level at the start chunk of a context, with no check of the
     * site. The tests and the commands use this method to make a village of a level.
     *
     * @param context  the context of the generation
     * @param variant  the variant
     * @param maritime true for a maritime site: the palisade stands on the land side only
     * @param level    the start level: its tier gives the size of the village
     * @return the layout, or empty if the start pool of the variant gives no plaza
     */
    public Optional<Layout> layout(GenerationContext context, Variant variant, boolean maritime, int level) {
        return layout(context, variant, maritime, level, Optional.empty());
    }

    /**
     * As {@link #layout(GenerationContext, Variant, boolean, int)}, with a given rotation of the plaza in place of the
     * random rotation.
     *
     * @param context  the context of the generation
     * @param variant  the variant
     * @param maritime true for a maritime site
     * @param level    the start level
     * @param rotation the rotation of the plaza, or empty for the random rotation
     * @return the layout, or empty if the start pool of the variant gives no plaza
     */
    public Optional<Layout> layout(GenerationContext context, Variant variant, boolean maritime, int level,
            Optional<Rotation> rotation) {
        restartRandom(context);
        return plaza(context, variant, maritime, rotation).map(start -> layout(context, start, level));
    }

    /**
     * The check of the site at the start chunk of a context, with the plaza of the base variant, as the generation
     * does it (see {@link VillageSite}). The tests use it to find a site with no layout.
     *
     * @param context the context of the generation
     * @return the result, or empty if the start pool gives no plaza
     */
    public Optional<VillageSite.Result> site(GenerationContext context) {
        restartRandom(context);
        return plaza(context, base, false, Optional.empty()).map(start -> checkSite(context, start.center()));
    }

    /**
     * The size of the village at a start chunk: the size of the tier of its start level.
     *
     * @param worldSeed the seed of the world
     * @param chunk     the start chunk
     * @return the size
     */
    public static VillageSize sizeAt(long worldSeed, ChunkPos chunk) {
        return VillageSizes.forLevel(Settlements.startLevel(worldSeed, chunk));
    }

    /**
     * The pieces of the plan, then the pieces of the wall around them. The wall also keeps the space of each free plot.
     * A maritime village has the palisade on its land side only (see {@link VillageWall}). The plan has a wide plot
     * for each building of the level that needs one (see {@link PlotUses#withWide}).
     */
    private Layout layout(GenerationContext context, Start start, int level) {
        VillageSize size = VillageSizes.forLevel(level);
        VillagePlanPieces.Builder pieces = VillagePlanPieces.builder(context, start.plaza(), start.center(),
                start.variant().homes(), VillageBuildingTable.get().stream().map(VillageBuildings.Requirement::kind)
                        .collect(Collectors.toSet()));
        List<PlotUses.Building> buildings = pieces.buildings(
                VillageBuildings.kinds(VillageBuildingTable.get(), level), styleOf(start.variant()));
        StreetStyle style = PlotUses.withWide(styleOf(start.variant()), buildings);
        int radius = Math.min(size.maxRadius(), maxDistanceFromCenter);
        VillageSize limited = new VillageSize(size.tier(), size.plots(), size.bedsPerPlot(), size.spareShare(), radius,
                size.rings(), size.wall(), size.minTerritoryRadius());
        int plazaSize = start.plaza().getBoundingBox().getXSpan();
        long seed = VillagePlanPieces.seed(context.seed(), context.chunkPos());
        VillagePlan plan = VillagePlan.plan(limited, plotTarget(size), style, plazaSize,
                VillagePlan.Start.around(plazaSize),
                new PlotTerrains(context).plan(start.center(), start.plaza().getRotation()), seed);
        VillagePlanPieces.Posts posts = posts(context, start.center(), level, seed);
        int beds = Math.max(0, LevelTables.get().traits(level).maxPopulation() - VillageBeds.staff());
        VillagePlanPieces.Result result = pieces.place(style, plan, PlotUses.assign(plan, buildings, posts.count()),
                posts, beds, RandomSource.create(seed));
        List<StructurePiece> village = new ArrayList<>(result.pieces());
        List<StructurePiece> wall = VillageWall.add(context, village, result.reserved(), start.center(),
                start.variant().wall(), start.maritime());
        if (!result.problems().isEmpty() || plan.missingPlots() > 0 || plan.missingHomes() > 0) {
            FolkholdWorld.LOGGER.warn("The village of the level {} in the start chunk {} has {} missing plots, {} missing"
                    + " homes and these problems: {}", level, context.chunkPos(), plan.missingPlots(),
                    plan.missingHomes(), result.problems());
        }
        return new Layout(level, size, style, plan, start.center(), result.pieces(), result.reserved(), wall,
                result.built(), result.free(), result.posts(), result.problems());
    }

    /**
     * The job posts of a village of a level: one for each job slot of the level, at most one for each job that the
     * level and the biome at the middle of the plaza allow. The culture of the region changes the weights of the jobs.
     */
    private static VillagePlanPieces.Posts posts(GenerationContext context, BlockPos center, int level, long seed) {
        CultureMap cultures = new CultureMap(context.seed(), context.biomeSource(), context.randomState().sampler());
        List<Job> eligible = Economies.eligibleJobs(biomeAt(context, center), level,
                cultures.cultureAt(center.getX(), center.getZ()));
        int slots = LevelTables.get().traits(level).jobSlots();
        return new VillagePlanPieces.Posts(eligible, Math.min(slots, eligible.size()), seed);
    }

    /**
     * The plot target of a size: the plots of the size, or the beds rule with the maximum population of the top level
     * of the tier and the places of the hall (see {@link VillageSize#plots(int, int)}).
     */
    private static int plotTarget(VillageSize size) {
        LevelTable levels = LevelTables.get();
        int population = levels.tiers().stream().filter(tier -> tier.id().equals(size.tier())).findFirst()
                .map(tier -> levels.traits(tier.maxLevel()).maxPopulation()).orElse(0);
        return size.plots(population, VillageBeds.staff());
    }

    /**
     * The style of the street pieces of a variant, from the folder of the pool of its homes: the style with the name of
     * a folder of the path (for example {@code village/sunreach/desert/homes}), else the style of all the cultures.
     *
     * @param variant the variant
     * @return the style
     */
    public static StreetStyle styleOf(Variant variant) {
        List<String> folders = variant.homes().unwrapKey().map(key -> List.of(key.location().getPath().split("/")))
                .orElse(List.of());
        return StreetStyle.ALL.stream().filter(style -> folders.contains(style.name())).findFirst()
                .orElse(StreetStyle.BASE);
    }

    /** Sets the random of the context to its start value: the random of the structure at the chunk of the context. */
    private static void restartRandom(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        context.random().setLargeFeatureSeed(context.seed(), chunk.x, chunk.z);
    }

    /** The biome at a position, as the game checks the biome of a structure. */
    private static Holder<Biome> biomeAt(GenerationContext context, BlockPos pos) {
        return context.biomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()), context.randomState().sampler());
    }

    /**
     * The first variant of all the cultures with a biome, or the base variant. The maritime variants are not in the
     * search.
     */
    public Variant variantAt(Holder<Biome> biome) {
        return variants.stream().filter(variant -> !variant.maritime())
                .filter(variant -> variant.culture().isEmpty() && variant.biomes().contains(biome))
                .findFirst().orElse(base);
    }

    /**
     * The variant of a village of a culture in a biome. The order of the search:
     *
     * <ol>
     *   <li>a variant of the culture with the biome;</li>
     *   <li>the base variant of the culture: a variant of the culture with no biome;</li>
     *   <li>a variant of all the cultures with the biome;</li>
     *   <li>the base variant of the structure.</li>
     * </ol>
     *
     * @param culture the id of the culture
     * @param biome   the biome
     * @return the variant
     */
    public Variant variantAt(String culture, Holder<Biome> biome) {
        List<Variant> ofCulture = variants.stream().filter(variant -> !variant.maritime())
                .filter(variant -> variant.culture().isPresent())
                .filter(variant -> variant.isFor(culture)).toList();
        return ofCulture.stream().filter(variant -> variant.biomes().contains(biome)).findFirst()
                .or(() -> ofCulture.stream().filter(variant -> variant.biomes().size() == 0).findFirst())
                .orElseGet(() -> variantAt(biome));
    }

    /**
     * The variant of a village of a culture in a biome, on a site. On a maritime site, the variant is the first
     * maritime variant with the biome, else the first maritime variant with no biome. With no maritime variant, or on
     * another site, the variant is {@link #variantAt(String, Holder)}.
     *
     * @param culture  the id of the culture
     * @param biome    the biome
     * @param maritime true for a maritime site
     * @return the variant
     */
    public Variant variantAt(String culture, Holder<Biome> biome, boolean maritime) {
        if (maritime) {
            List<Variant> atSea = variants.stream().filter(Variant::maritime).filter(variant -> variant.isFor(culture))
                    .toList();
            Optional<Variant> found = atSea.stream().filter(variant -> variant.biomes().contains(biome)).findFirst()
                    .or(() -> atSea.stream().filter(variant -> variant.biomes().size() == 0).findFirst());
            if (found.isPresent()) {
                return found.get();
            }
        }
        return variantAt(culture, biome);
    }

    /**
     * The variant of a village from its pieces: the variant with the template of the start piece in its start pool. A
     * village with another start piece gets the base variant.
     *
     * @param level  the level
     * @param pieces the pieces of the village structure, the start piece first
     * @return the variant
     */
    public Variant variantOf(ServerLevel level, List<StructurePiece> pieces) {
        Optional<ResourceLocation> start = pieces.stream().findFirst()
                .filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast)
                .flatMap(piece -> VillageReference.templateOf(level, piece.getElement()));
        if (start.isEmpty()) {
            return base;
        }
        RandomSource random = RandomSource.create(0L);
        return variants.stream()
                .filter(variant -> variant.startPool().value().getShuffledTemplates(random).stream()
                        .map(element -> VillageReference.templateOf(level, element))
                        .anyMatch(start::equals))
                .findFirst()
                .orElse(base);
    }

    /**
     * Checks the terrain around the middle of the start piece, before the generation of the chunks (see
     * {@link VillageSite}). The structure check of the game and the discovery of the settlements call this method
     * on the server thread: it reads few heights.
     *
     * @param context the context of the generation
     * @param middle  the middle of the start piece, on the surface of the terrain
     * @return the result of the check
     */
    static VillageSite.Result checkSite(GenerationContext context, BlockPos middle) {
        ChunkGenerator generator = context.chunkGenerator();
        int floor = generator.getBaseHeight(middle.getX(), middle.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        return VillageSite.check(middle.getY(), floor, generator.getSeaLevel(),
                (dx, dz) -> generator.getBaseHeight(middle.getX() + dx, middle.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()),
                (dx, dz) -> generator.getBaseHeight(middle.getX() + dx, middle.getZ() + dz,
                        Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()));
    }

    /**
     * The spaces of a new home on each free plot connector of some pieces: the generation does not use this method (it
     * keeps the space of the free plots of the plan, see {@link Layout#reserved()}). The tests use it for a village of
     * hand-placed pieces. The space holds each piece of the pool of the homes of the variant and of its pool of house-workshops
     * (see {@link VillagePlots#reserved} and {@link VillagePlots#homePools}).
     *
     * @param registries the registries of the server
     * @param templates  the structure templates of the server
     * @param homes      the pool of the homes of the variant
     * @param pieces     the pieces of the pools of the village
     * @return the spaces, one for each free plot
     */
    public static List<BoundingBox> reservedSpaces(RegistryAccess registries, StructureTemplateManager templates,
            Holder<StructureTemplatePool> homes, List<StructurePiece> pieces) {
        return VillagePlots.reserved(templates, VillagePlots.homePools(registries, homes), pieces);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.FORTIFIED_VILLAGE.get();
    }

    /** The pool of the start piece. */
    public Holder<StructureTemplatePool> startPool() {
        return startPool;
    }

    /** The largest radius of the plan of a village, in blocks. */
    public int maxDistanceFromCenter() {
        return maxDistanceFromCenter;
    }

    /** The settings of the wall. */
    public WallSettings wall() {
        return wall;
    }

    /** The pool of the homes of the base variant (see {@link VillagePlots}). */
    public Holder<StructureTemplatePool> homes() {
        return homes;
    }

    /** The biome variants, with no base variant. */
    public List<Variant> variants() {
        return variants;
    }
}
