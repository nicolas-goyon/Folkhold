package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the market square ({@code village/market}, written by {@code tools/structures/village.py}):
 *
 * <ul>
 *   <li>each variant has one market sign with the market post behind it, no job sign and no entities, and the size of
 *       the plot of one house of its style;</li>
 *   <li>the market fits the generated villages of each style: the plot of the market in the plan, or each plot where a
 *       home fits;</li>
 *   <li>the scan of the heart finds the market squares by their market signs, and not the fish market of a maritime
 *       village.</li>
 * </ul>
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private static final int ARENA = 32;
    private static final BlockPos HEART = new BlockPos(24, 1, 24);
    /** The folders of the pieces of the village: the styles of the cultures and the maritime pieces. */
    private static final List<String> STYLES = List.of("", "valeholm/", "sunreach/", "maritime/");
    /** The folders of the biome variants in a style. The base variant has no folder. */
    private static final List<String> VARIANTS = List.of("", "plains/", "birch/", "savanna/", "desert/", "swamp/");
    /** The style with the deep plots of the long houses. */
    private static final String DEEP_STYLE = "valeholm/";
    /** The size of the market on the plots of the base, Sunreach and maritime streets: along the street, deep. */
    private static final Vec3i WIDE = new Vec3i(15, 6, 9);
    /** The size of the market on the plots of the Valeholm streets. */
    private static final Vec3i DEEP = new Vec3i(9, 6, 15);
    /** The first candidate chunk of the generated villages: far from the areas of the other tests. */
    private static final int FIRST_CHUNK = 9000;
    /** The distance between 2 candidate chunks, in chunks. */
    private static final int CANDIDATE_STEP = 12;
    private static final int CANDIDATES = 8;

    private MarketGameTests() {
    }

    /**
     * Each variant of the market square has the size of the plot of one house of its style, one market sign with the
     * market post behind it, no job sign and no entities. The fish market of each maritime variant has a market sign
     * with no market post behind it.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "market_templates")
    public static void eachVariantHasTheMarketSign(GameTestHelper helper) {
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        Block marketSign = ModBlocks.BUILDING_SIGNS.get(BuildingSignKind.MARKET).get();
        List<String> problems = new ArrayList<>();
        for (String style : STYLES) {
            Vec3i size = style.equals(DEEP_STYLE) ? DEEP : WIDE;
            for (String variant : VARIANTS) {
                ResourceLocation id = piece(style + variant, "market");
                Optional<StructureTemplate> template = templates.get(id);
                if (template.isEmpty()) {
                    problems.add("no template " + id);
                    continue;
                }
                if (!template.get().getSize().equals(size)) {
                    problems.add(id + " has the size " + template.get().getSize() + ", expected " + size);
                }
                StructurePlaceSettings settings = new StructurePlaceSettings();
                List<StructureTemplate.StructureBlockInfo> signs = template.get().filterBlocks(BlockPos.ZERO,
                        settings, marketSign);
                int jobSigns = template.get().filterBlocks(BlockPos.ZERO, settings, ModBlocks.JOB_SIGN.get()).size();
                if (signs.size() != 1 || jobSigns != 0) {
                    problems.add(id + " has " + signs.size() + " market signs and " + jobSigns + " job signs");
                } else if (!hasPost(template.get(), signs.get(0))) {
                    problems.add(id + " has no market post behind its market sign");
                }
                if (!template.get().save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND).isEmpty()) {
                    problems.add(id + " has entities");
                }
            }
        }
        for (String variant : VARIANTS) {
            ResourceLocation id = piece("maritime/" + variant, "house_fish_market");
            StructureTemplate fishMarket = templates.get(id).orElseThrow();
            for (StructureTemplate.StructureBlockInfo sign : fishMarket.filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), marketSign)) {
                if (hasPost(fishMarket, sign)) {
                    problems.add(id + " has a market post behind its market sign");
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Problems of the market templates: " + problems);
        helper.succeed();
    }

    /**
     * The market fits the generated villages of each style. The check is on the horizontal plane, as the check of a
     * free plot ({@code VillageGrowth.isFree}) on a flat terrain. The building of the generation on the plot does not
     * count.
     *
     * <ul>
     *   <li>A style with a wide plot for the market in its plan (the base, Sunreach and maritime styles, see
     *       {@code StreetStyle#wide()}): each village has a free plot with a space that holds the market, and on this
     *       plot the market touches no piece of the village.</li>
     *   <li>The other styles (Valeholm: the market fits the plot of a long house): on each plot, the market touches a
     *       piece of the village only if a home on the plot also touches it. Thus the growth can put the market on each
     *       free plot where it can put a home.</li>
     * </ul>
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "market_plots")
    public static void marketFitsEachPlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureTemplateManager templates = level.getStructureManager();
        List<String> problems = new ArrayList<>();
        for (String style : STYLES) {
            Holder<StructureTemplatePool> homes = pool(pools, style + "homes");
            StructurePoolElement market = first(pool(pools, style + "market"));
            StructurePoolElement home = first(homes);
            FortifiedVillageStructure village = new FortifiedVillageStructure(new Structure.StructureSettings(
                    structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                    pool(pools, style + "plaza"), structure.maxDistanceFromCenter(),
                    structure.wall(), homes, List.of());
            int plots = 0;
            for (int i = 0; i < CANDIDATES; i++) {
                ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK);
                Optional<FortifiedVillageStructure.Layout> layout = village.layout(new Structure.GenerationContext(
                        level.registryAccess(), generator, generator.getBiomeSource(),
                        level.getChunkSource().randomState(), templates, level.getSeed(), chunk, level,
                        biome -> true));
                if (layout.isEmpty()) {
                    continue;
                }
                boolean wide = layout.get().style().wide().stream().anyMatch(plan -> plan.name().equals("market"));
                List<StructurePiece> pieces = layout.get().pieces();
                List<BoundingBox> boxes = pieces.stream().filter(PoolElementStructurePiece.class::isInstance)
                        .map(StructurePiece::getBoundingBox).toList();
                int marketPlots = 0;
                for (VillagePlots.Plot plot : VillagePlots.of(templates, pieces)) {
                    int ground = plot.connector().getY();
                    BoundingBox marketBox = VillagePlots.place(templates, market, plot, ground).orElseThrow().box();
                    BoundingBox homeBox = VillagePlots.place(templates, home, plot, ground).orElseThrow().box();
                    plots++;
                    if (wide) {
                        if (!plot.built() && layout.get().reserved().stream()
                                .anyMatch(space -> contains(space, marketBox))) {
                            marketPlots++;
                            boxes.stream().filter(piece -> overlaps(marketBox, piece)).forEach(piece -> problems.add(
                                    style + "market " + marketBox + " on its plot " + plot.connector()
                                            + " touches the piece " + piece));
                        }
                        continue;
                    }
                    for (BoundingBox piece : boxes) {
                        boolean onPlot = covers(piece, plot.front());
                        if (!onPlot && overlaps(marketBox, piece) && !overlaps(homeBox, piece)) {
                            problems.add(style + "market " + marketBox + " on the plot " + plot.connector()
                                    + " touches the piece " + piece + ", a home " + homeBox + " does not");
                        }
                    }
                }
                if (wide && marketPlots == 0) {
                    problems.add("the village " + style + " in " + chunk + " has no free plot for its market");
                }
            }
            helper.assertTrue(plots > 0, "The villages of the style '" + style + "' must have plots");
        }
        helper.assertTrue(problems.isEmpty(), "The market must fit the plots: " + problems);
        helper.succeed();
    }

    /**
     * The market squares of the base, the desert and the Valeholm variants, and the fish market of a maritime village:
     * the plans of the market squares have no warning. After the placement, each fence of a market square has the
     * shape of its neighbors. The scan of the heart finds the market sign of each market square, counts 3 markets, and
     * does not find the market sign of the fish market.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "market_scan", skyAccess = true)
    public static void heartFindsTheMarkets(GameTestHelper helper) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        Block marketSign = ModBlocks.BUILDING_SIGNS.get(BuildingSignKind.MARKET).get();
        List<ResourceLocation> markets = List.of(piece("", "market"), piece("desert/", "market"),
                piece(DEEP_STYLE, "market"));
        List<BlockPos> origins = List.of(new BlockPos(1, 1, 1), new BlockPos(17, 1, 1), new BlockPos(1, 1, 12));
        BlockPos fishOrigin = helper.absolutePos(new BlockPos(12, 1, 12));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<BoundingBox> marketBoxes = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();
        Set<BlockPos> expected = new HashSet<>();
        try {
            for (int i = 0; i < markets.size(); i++) {
                StructureTemplate template = level.getStructureManager().get(markets.get(i)).orElseThrow();
                BlockPos origin = helper.absolutePos(origins.get(i));
                Plan plan = ConstructionPlanner.plan(ConstructionTargets.fromTemplate(level, template, origin,
                        new StructurePlaceSettings()), ConstructionTargets.view(level), Reach.DEFAULT);
                helper.assertTrue(plan.warnings().isEmpty(), "The plan of " + markets.get(i)
                        + " must have no warnings: " + plan.warnings());
                BoundingBox box = template.getBoundingBox(settings, origin);
                marketBoxes.add(box);
                boxes.add(box);
                template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                expected.add(template.filterBlocks(origin, new StructurePlaceSettings(), marketSign).get(0).pos());
            }
            StructureTemplate fishMarket = level.getStructureManager().get(piece("maritime/", "house_fish_market"))
                    .orElseThrow();
            boxes.add(fishMarket.getBoundingBox(settings, fishOrigin));
            fishMarket.placeInWorld(level, fishOrigin, fishOrigin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            JobBuildingTemplateGameTests.checkFences(helper, marketBoxes);
            helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
            VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
            helper.assertTrue(heart.getLayout().scan(level, heart.getBlockPos()), "The scan area must be ready");
            helper.assertTrue(new HashSet<>(heart.getLayout().markets()).equals(expected)
                    && heart.getLayout().markets().size() == expected.size(),
                    "The scan must find the market signs " + expected + ": " + heart.getLayout().markets());
            helper.assertTrue(heart.getLayout().buildings().getOrDefault("market", 0) == expected.size(),
                    "The scan must count " + expected.size() + " markets: " + heart.getLayout().buildings());
        } finally {
            helper.setBlock(HEART, Blocks.AIR);
            // The pieces are higher than the arena: a later test at this place must not find them.
            boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos ->
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
        }
        helper.succeed();
    }

    /** True if the block behind a sign of a template (no rotation) is a market post. */
    private static boolean hasPost(StructureTemplate template, StructureTemplate.StructureBlockInfo sign) {
        Direction back = sign.state().getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        BlockPos behind = sign.pos().relative(back);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        for (Holder<Block> post : BuiltInRegistries.BLOCK.getTagOrEmpty(VillageLayout.MARKET_POSTS)) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings,
                    post.value())) {
                if (info.pos().equals(behind)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if 2 boxes have a common column on the horizontal plane. */
    private static boolean overlaps(BoundingBox a, BoundingBox b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    /** True if a box holds another box on the horizontal plane. */
    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX() && inner.minZ() >= outer.minZ()
                && inner.maxZ() <= outer.maxZ();
    }

    /** True if the column of a position is in a box. */
    private static boolean covers(BoundingBox box, BlockPos pos) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ()
                && pos.getZ() <= box.maxZ();
    }

    private static Holder<StructureTemplatePool> pool(Registry<StructureTemplatePool> pools, String name) {
        return pools.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, piece("", name)));
    }

    /** The first element of a pool with one element. */
    private static StructurePoolElement first(Holder<StructureTemplatePool> pool) {
        return pool.value().getShuffledTemplates(RandomSource.create(0L)).get(0);
    }

    private static ResourceLocation piece(String folder, String name) {
        return ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/" + folder + name);
    }
}
