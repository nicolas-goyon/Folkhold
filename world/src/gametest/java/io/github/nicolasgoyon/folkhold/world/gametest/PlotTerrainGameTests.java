package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.faction.CultureMap;
import io.github.nicolasgoyon.folkhold.world.level.TerrainColumns;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PlotTerrains;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game measure of the terrain of the plots (see {@link PlotTerrain}) on the vanilla Overworld terrain. The GameTest
 * server has a flat world: the tests make a noise chunk generator of the Overworld with a fixed seed, and the layouts
 * of the villages read its heights. The tests place no block and generate no chunk.
 *
 * <p>Each measured village has all its plots and homes, and no problem of its layout (a missing piece, home beds under
 * the maximum population). The measure reads the box of each building on a grid of {@link #FINE_STEP} blocks, finer
 * than the grid of the check of the plots. A building of a plot with no role over lava or deep water on this grid is
 * in the log: the grid of the check does not see it. The buildings of the bank and of the first jobs keep their plot
 * on bad terrain, and the log counts them.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlotTerrainGameTests {
    /** The seed of the measured world. */
    private static final long SEED = 783L;
    /** The number of measured villages on land, for each style. */
    private static final int LAND_VILLAGES = 2;
    /** The number of measured maritime villages. */
    private static final int MARITIME_VILLAGES = 2;
    /** The largest number of start chunks that a search tries. */
    private static final int TRIES = 4000;
    /** The distance between 2 start chunks on land, in chunks. */
    private static final int LAND_STEP = 37;
    /** The distance between 2 start chunks of the search of the coasts, in chunks. */
    private static final int COAST_STEP = 6;
    /** The number of start chunks on each row of a search. */
    private static final int ROW = 60;
    /** The distance between 2 points of the grid of the measure, in blocks. */
    private static final int FINE_STEP = 2;
    /** The start level of the forced capital. */
    private static final int CAPITAL_LEVEL = 20;

    private PlotTerrainGameTests() {
    }

    /** The villages of the base style: see {@link PlotTerrainGameTests}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain_base")
    public static void baseVillagesHaveTheirHomes(GameTestHelper helper) {
        landVillages(helper, StreetStyle.BASE);
    }

    /** The villages of the Sunreach style: see {@link PlotTerrainGameTests}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain_sunreach")
    public static void sunreachVillagesHaveTheirHomes(GameTestHelper helper) {
        landVillages(helper, StreetStyle.SUNREACH);
    }

    /** The villages of the Valeholm style: see {@link PlotTerrainGameTests}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain_valeholm")
    public static void valeholmVillagesHaveTheirHomes(GameTestHelper helper) {
        landVillages(helper, StreetStyle.VALEHOLM);
    }

    /**
     * The first villages of a style on land, in the start chunks of the search. The culture of the region and the
     * biome select the style before the layout: the search makes the layout of the sites of the style only.
     */
    private static void landVillages(GameTestHelper helper, StreetStyle style) {
        Measure measure = new Measure(helper.getLevel());
        List<String> problems = new ArrayList<>();
        int found = 0;
        for (int i = 0; i < TRIES && found < LAND_VILLAGES; i++) {
            Structure.GenerationContext context = measure.context(new ChunkPos((i % ROW) * LAND_STEP,
                    (i / ROW) * LAND_STEP));
            if (!measure.styleAt(context).equals(style.name())) {
                continue;
            }
            Optional<FortifiedVillageStructure.Layout> layout = measure.structure.layout(context);
            if (layout.isPresent() && layout.get().style().name().equals(style.name())) {
                found++;
                measure.check(context, layout.get(), "", problems);
            }
        }
        measure.end(helper, found, LAND_VILLAGES, problems);
    }

    /** The maritime villages: the sites on a beach that the check of the site accepts as maritime. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain_maritime")
    public static void maritimeVillagesHaveTheirHomes(GameTestHelper helper) {
        Measure measure = new Measure(helper.getLevel());
        List<String> problems = new ArrayList<>();
        int found = 0;
        for (int i = 0; i < TRIES && found < MARITIME_VILLAGES; i++) {
            Structure.GenerationContext context = measure.maritimeSite(i).orElse(null);
            if (context != null) {
                Optional<FortifiedVillageStructure.Layout> layout = measure.structure.layout(context);
                if (layout.isPresent() && isMaritime(layout.get())) {
                    found++;
                    measure.check(context, layout.get(), "", problems);
                }
            }
        }
        measure.end(helper, found, MARITIME_VILLAGES, problems);
    }

    /**
     * A capital of the level 20 on the first site on land and on the first maritime site of the searches: the largest
     * plans. The test is optional: it takes the most time.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain_capital", required = false)
    public static void capitalsHaveTheirHomes(GameTestHelper helper) {
        Measure measure = new Measure(helper.getLevel());
        List<String> problems = new ArrayList<>();
        int found = 0;
        FortifiedVillageStructure structure = measure.structure;
        FortifiedVillageStructure.Variant land = new FortifiedVillageStructure.Variant(HolderSet.direct(),
                structure.startPool(), structure.wall(), structure.homes(), Optional.empty());
        for (int i = 0; i < TRIES; i++) {
            Structure.GenerationContext context = measure.context(new ChunkPos((i % ROW) * LAND_STEP,
                    (i / ROW) * LAND_STEP));
            if (structure.site(context).filter(site -> site == VillageSite.Result.ACCEPTED).isPresent()) {
                measure.check(context, structure.layout(context, land, false, CAPITAL_LEVEL).orElseThrow(),
                        " forced", problems);
                found++;
                break;
            }
        }
        FortifiedVillageStructure.Variant maritime = structure.variants().stream()
                .filter(FortifiedVillageStructure.Variant::maritime).filter(variant -> variant.biomes().size() == 0)
                .findFirst().orElseThrow();
        for (int i = 0; i < TRIES; i++) {
            Optional<Structure.GenerationContext> context = measure.maritimeSite(i);
            if (context.isPresent()) {
                measure.check(context.get(),
                        structure.layout(context.get(), maritime, true, CAPITAL_LEVEL).orElseThrow(), " forced",
                        problems);
                found++;
                break;
            }
        }
        measure.end(helper, found, 2, problems);
    }

    private static boolean isMaritime(FortifiedVillageStructure.Layout layout) {
        return layout.style().name().equals(StreetStyle.MARITIME.name());
    }

    /** The noise terrain of the measure, and the counts of the villages. */
    private static final class Measure {
        private final ServerLevel level;
        private final RegistryAccess registries;
        private final FortifiedVillageStructure structure;
        private final NoiseBasedChunkGenerator generator;
        private final RandomState random;
        private final CultureMap cultures;
        private int badPlots;
        private final Map<PlotTerrain.Result, Integer> roleBuildings = new EnumMap<>(PlotTerrain.Result.class);
        private final Map<PlotTerrain.Result, Integer> missed = new EnumMap<>(PlotTerrain.Result.class);

        Measure(ServerLevel level) {
            this.level = level;
            this.registries = level.registryAccess();
            this.structure = (FortifiedVillageStructure) registries.registryOrThrow(Registries.STRUCTURE)
                    .getOrThrow(Settlements.VILLAGE_STRUCTURE);
            Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                    .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            this.generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(
                    registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), settings);
            this.random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), SEED);
            this.cultures = new CultureMap(SEED, generator.getBiomeSource(), random.sampler());
        }

        /**
         * The style of the variant of the land village at the start chunk of a context, from the culture of the
         * region and the biome at the middle of the chunk. The plaza of the generation has its middle near this point.
         */
        String styleAt(Structure.GenerationContext context) {
            BlockPos middle = context.chunkPos().getMiddleBlockPosition(generator.getSeaLevel());
            String culture = cultures.cultureAt(middle.getX(), middle.getZ());
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(middle.getX()),
                    QuartPos.fromBlock(middle.getY()), QuartPos.fromBlock(middle.getZ()), random.sampler());
            return FortifiedVillageStructure.styleOf(structure.variantAt(culture, biome, false)).name();
        }

        Structure.GenerationContext context(ChunkPos chunk) {
            return new Structure.GenerationContext(registries, generator, generator.getBiomeSource(), random,
                    level.getStructureManager(), SEED, chunk, level, biome -> true);
        }

        /**
         * The context of a start chunk of the search of the coasts, if the chunk is on a beach and the check of its
         * site gives a maritime village. The biome is cheap to read: the check of the site reads the heights.
         */
        Optional<Structure.GenerationContext> maritimeSite(int index) {
            ChunkPos chunk = new ChunkPos((index % ROW) * COAST_STEP, (index / ROW) * COAST_STEP);
            BlockPos middle = chunk.getMiddleBlockPosition(generator.getSeaLevel());
            boolean beach = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(middle.getX()),
                    QuartPos.fromBlock(middle.getY()), QuartPos.fromBlock(middle.getZ()), random.sampler())
                    .is(BiomeTags.IS_BEACH);
            if (!beach) {
                return Optional.empty();
            }
            Structure.GenerationContext context = context(chunk);
            return structure.site(context).filter(site -> site == VillageSite.Result.MARITIME).map(site -> context);
        }

        /** Measures a village and adds its problems. */
        void check(Structure.GenerationContext context, FortifiedVillageStructure.Layout layout, String forced,
                List<String> problems) {
            VillagePlan plan = layout.plan();
            PoolElementStructurePiece plaza = layout.village().get(0);
            String name = "chunk " + context.chunkPos() + " level " + layout.level() + forced + " "
                    + layout.size().tier() + " " + layout.style().name();
            if (plan.missingHomes() > 0 || plan.missingPlots() > 0) {
                problems.add(name + ": " + plan.missingPlots() + " missing plots, " + plan.missingHomes()
                        + " missing homes");
            }
            layout.problems().forEach(problem -> problems.add(name + ": " + problem));
            PlotTerrains terrain = new PlotTerrains(context);
            Map<PlotTerrain.Result, Integer> bad = new EnumMap<>(PlotTerrain.Result.class);
            Map<PlotTerrain.Result, Integer> fineBad = new EnumMap<>(PlotTerrain.Result.class);
            for (PoolElementStructurePiece building : layout.village().stream().skip(1)
                    .filter(piece -> piece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID)
                    .toList()) {
                BoundingBox box = building.getBoundingBox();
                boolean role = plotOf(plan, layout.center(), plaza.getRotation(), box)
                        .map(plot -> plot.role() != VillagePlan.PlotRole.HOME).orElse(false);
                PlotTerrain.Result result = terrain.check(box);
                if (role) {
                    if (result != PlotTerrain.Result.GOOD) {
                        roleBuildings.merge(result, 1, Integer::sum);
                    }
                    continue;
                }
                if (result != PlotTerrain.Result.GOOD) {
                    bad.merge(result, 1, Integer::sum);
                    problems.add(name + ": a building at " + box + " on bad terrain: " + result);
                }
                PlotTerrain.Result fine = fine(context, box);
                if (fine != PlotTerrain.Result.GOOD) {
                    fineBad.merge(fine, 1, Integer::sum);
                    missed.merge(fine, 1, Integer::sum);
                    FolkholdWorld.LOGGER.info("Plot terrain {}: the grid of {} blocks sees {} under the building {}",
                            name, FINE_STEP, fine, box);
                }
            }
            long spareBad = plan.plots().stream().filter(plot -> plot.spare() && !plot.good()).count();
            badPlots += (int) spareBad;
            FolkholdWorld.LOGGER.info("Plot terrain {}: {} plots, {} spare plots with bad terrain, {} missing plots, "
                    + "{} missing homes, {} buildings, buildings on bad terrain {}, buildings on bad terrain on the "
                    + "grid of {} blocks {}, {} problems", name, plan.plots().size(), spareBad, plan.missingPlots(),
                    plan.missingHomes(), layout.built(), bad, FINE_STEP, fineBad, layout.problems().size());
        }

        /** Logs the totals and ends the test. */
        void end(GameTestHelper helper, int found, int wanted, List<String> problems) {
            FolkholdWorld.LOGGER.info("Plot terrain: {} villages, {} spare plots with bad terrain, buildings of the "
                    + "bank and of the first jobs on bad terrain {}, buildings on bad terrain on the grid of {} blocks "
                    + "only {}", found, badPlots, roleBuildings, FINE_STEP, missed);
            helper.assertTrue(found == wanted, "Only " + found + " villages of " + wanted);
            helper.assertTrue(problems.isEmpty(), "Problems of the villages: " + problems);
            helper.succeed();
        }

        /** The terrain of a box on the grid of {@link #FINE_STEP} blocks, with no cache. */
        private PlotTerrain.Result fine(Structure.GenerationContext context, BoundingBox box) {
            int minY = context.heightAccessor().getMinBuildHeight();
            int maxY = context.heightAccessor().getMaxBuildHeight();
            List<PlotTerrain.Column> columns = new ArrayList<>();
            for (int x : fineAxis(box.minX(), box.maxX())) {
                for (int z : fineAxis(box.minZ(), box.maxZ())) {
                    columns.add(TerrainColumns.column(generator.getBaseColumn(x, z, context.heightAccessor(),
                            random), minY, maxY));
                }
            }
            return PlotTerrain.check(columns);
        }

        /** The points from a minimum to a maximum, every {@link #FINE_STEP} blocks, with the maximum. */
        private static List<Integer> fineAxis(int min, int max) {
            List<Integer> points = new ArrayList<>();
            for (int value = min; value < max; value += FINE_STEP) {
                points.add(value);
            }
            points.add(max);
            return points;
        }
    }

    /** The plot of the plan with the middle of a box in its area. */
    private static Optional<VillagePlan.Plot> plotOf(VillagePlan plan, BlockPos center, Rotation rotation,
            BoundingBox box) {
        BlockPos middle = box.getCenter();
        Predicate<VillagePlan.Plot> holds = plot -> {
            BlockPos first = center.atY(0).offset(new BlockPos(plot.area().minX(), 0, plot.area().minZ())
                    .rotate(rotation));
            BlockPos second = center.atY(0).offset(new BlockPos(plot.area().maxX(), 0, plot.area().maxZ())
                    .rotate(rotation));
            BoundingBox area = BoundingBox.fromCorners(first, second);
            return middle.getX() >= area.minX() && middle.getX() <= area.maxX() && middle.getZ() >= area.minZ()
                    && middle.getZ() <= area.maxZ();
        };
        return plan.plots().stream().filter(holds).findFirst();
    }
}
