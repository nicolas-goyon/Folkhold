package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PlotTerrains;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
 * server has a flat world: the test makes a noise chunk generator of the Overworld with a fixed seed, and the layouts of
 * the villages read its heights. The test places no block and generates no chunk.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlotTerrainGameTests {
    /** The seed of the measured world. */
    private static final long SEED = 783L;
    /** The number of measured villages. */
    private static final int VILLAGES = 16;
    /** The largest number of start chunks that the test tries. */
    private static final int TRIES = 400;
    /** The distance between 2 start chunks, in chunks. */
    private static final int STEP = 37;

    private PlotTerrainGameTests() {
    }

    /**
     * The villages of the start chunks of a fixed world, with the site check, the variant and the start level of the
     * generation. No home of a plot with no role stands over lava or deep water. The log gives, for each village, the
     * plots with bad terrain (spare plots), the missing homes, and the buildings on bad terrain with their role. The
     * buildings of the bank and of the first jobs keep their plot on bad terrain.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "plot_terrain")
    public static void noHomeOverLavaOrDeepWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess registries = level.registryAccess();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) registries
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(
                registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), settings);
        RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        List<String> problems = new ArrayList<>();
        int villages = 0;
        int badPlots = 0;
        int missingHomes = 0;
        Map<PlotTerrain.Result, Integer> roleBuildings = new EnumMap<>(PlotTerrain.Result.class);
        for (int i = 0; i < TRIES && villages < VILLAGES; i++) {
            ChunkPos chunk = new ChunkPos((i % 20) * STEP, (i / 20) * STEP);
            Structure.GenerationContext context = new Structure.GenerationContext(registries, generator,
                    generator.getBiomeSource(), random, level.getStructureManager(), SEED, chunk, level,
                    biome -> true);
            long time = System.nanoTime();
            Optional<FortifiedVillageStructure.Layout> found = structure.layout(context);
            time = System.nanoTime() - time;
            if (found.isEmpty()) {
                continue;
            }
            villages++;
            FortifiedVillageStructure.Layout layout = found.get();
            VillagePlan plan = layout.plan();
            PlotTerrains terrain = new PlotTerrains(context);
            PoolElementStructurePiece plaza = layout.village().get(0);
            String name = "chunk " + chunk + " level " + layout.level() + " " + layout.size().tier() + " "
                    + layout.style().name();
            Map<PlotTerrain.Result, Integer> bad = new EnumMap<>(PlotTerrain.Result.class);
            for (PoolElementStructurePiece building : layout.village().stream().skip(1)
                    .filter(piece -> piece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID)
                    .toList()) {
                PlotTerrain.Result result = terrain.check(building.getBoundingBox());
                if (result == PlotTerrain.Result.GOOD) {
                    continue;
                }
                Optional<VillagePlan.Plot> plot = plotOf(plan, layout.center(), plaza.getRotation(),
                        building.getBoundingBox());
                boolean role = plot.map(value -> value.role() != VillagePlan.PlotRole.HOME).orElse(false);
                if (role) {
                    roleBuildings.merge(result, 1, Integer::sum);
                } else {
                    bad.merge(result, 1, Integer::sum);
                    if (result == PlotTerrain.Result.LAVA || result == PlotTerrain.Result.DEEP_WATER) {
                        problems.add(name + ": a building at " + building.getBoundingBox() + " over " + result);
                    }
                }
            }
            long spareBad = plan.plots().stream().filter(plot -> plot.spare() && !plot.good()).count();
            badPlots += (int) spareBad;
            missingHomes += plan.missingHomes();
            FolkholdWorld.LOGGER.info("Plot terrain {}: {} plots, {} spare plots with bad terrain, {} missing plots, "
                    + "{} missing homes, {} buildings, buildings on bad terrain {}, {} problems, {} ms", name,
                    plan.plots().size(), spareBad, plan.missingPlots(), plan.missingHomes(), layout.built(), bad,
                    layout.problems().size(), time / 1_000_000);
        }
        FolkholdWorld.LOGGER.info("Plot terrain: {} villages, {} spare plots with bad terrain, {} missing homes, "
                + "buildings of the bank and of the first jobs on bad terrain {}", villages, badPlots, missingHomes,
                roleBuildings);
        helper.assertTrue(villages == VILLAGES, "Only " + villages + " villages in " + TRIES + " start chunks");
        helper.assertTrue(problems.isEmpty(), "Homes on bad terrain: " + problems);
        helper.succeed();
    }

    /** The plot of the plan with the middle of a box in its area. */
    private static Optional<VillagePlan.Plot> plotOf(VillagePlan plan, BlockPos center, Rotation rotation,
            BoundingBox box) {
        BlockPos middle = box.getCenter();
        return plan.plots().stream().filter(plot -> {
            BlockPos first = center.atY(0).offset(new BlockPos(plot.area().minX(), 0, plot.area().minZ())
                    .rotate(rotation));
            BlockPos second = center.atY(0).offset(new BlockPos(plot.area().maxX(), 0, plot.area().maxZ())
                    .rotate(rotation));
            BoundingBox area = BoundingBox.fromCorners(first, second);
            return middle.getX() >= area.minX() && middle.getX() <= area.maxX() && middle.getZ() >= area.minZ()
                    && middle.getZ() <= area.maxZ();
        }).findFirst();
    }
}
