package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.StreetBlocks;
import io.github.nicolasgoyon.folkhold.world.village.VillageClearing;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.OptionalReplaceProcessor;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village structure of the faction villages ({@code folkhold_world:village}): the pieces of the
 * pools in a closed wall. The tests generate the structure in chunks far from the other tests, on the flat terrain of
 * the GameTest world.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FortifiedVillageGameTests {
    /** The first candidate chunk of the village: far from the spawn and from the areas of the other tests. */
    private static final int FIRST_CHUNK = 6000;
    /** The distance between 2 candidate chunks, in chunks. */
    private static final int CANDIDATE_STEP = 12;
    private static final int CANDIDATES = 8;
    private static final String TOWER = "tower";
    private static final String GATE = "gate";
    /** The templates of the plots: the houses and the bank. */
    private static final Set<String> HOUSES = Set.of("house_cottage", "house_barracks", "house_forge",
            "house_storehouse", "bank");
    /** The walk on the ground falls this number of blocks at most. */
    private static final int MAX_FALL = 8;
    /** A walk of fewer positions did not start correctly. */
    private static final int MIN_WALK = 1000;

    private FortifiedVillageGameTests() {
    }

    /**
     * A generated village has its heart on the plaza, a tower at each corner, a gate on each side and a palisade with
     * no gap. From the heart, a walk on the ground does not go out while the gates stay closed. With the gates open,
     * the walk goes out.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_wall")
    public static void generatedVillageHasAClosedWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = generate(helper, level);
        VillageReference reference = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        BoundingBox area = reference.area().orElseThrow();

        helper.assertTrue(level.getBlockState(village.heart()).is(ModBlocks.VILLAGE_HEART.get()),
                "The heart must be in the middle of the plaza: " + village.heart());
        long towers = count(reference, TOWER);
        long gates = count(reference, GATE);
        helper.assertTrue(towers >= 4, "A tower must be at each corner: " + towers);
        helper.assertTrue(gates == 4, "A gate must be on each side: " + gates);
        helper.assertTrue(village.start().getPieces().stream().anyMatch(PalisadePiece.class::isInstance),
                "The wall must have a palisade");
        helper.assertTrue(village.start().getPieces().stream().anyMatch(VillagePathPiece.class::isInstance),
                "A path must go from a gate to the streets");
        // The heart records the surface of the streets: most columns of the streets in the area have a surface block.
        long columns = reference.streetColumns().stream().filter(reference::isInArea).count();
        int surface = StreetBlocks.record(level, reference).size();
        helper.assertTrue(columns > 0 && surface * 10L >= columns * 9L, "The streets must have their surface: "
                + surface + " of " + columns + " columns");

        Walk closed = walk(level, village.heart(), area);
        helper.assertTrue(closed.positions() >= MIN_WALK, "The walk must go through the village: "
                + closed.positions() + " positions");
        helper.assertTrue(closed.exit().isEmpty(), "The walk must not go out of the palisade "
                + area + ": out at " + closed.exit().orElse(null));

        List<BlockPos> fenceGates = blocksIn(level, area, state -> state.getBlock() instanceof FenceGateBlock);
        helper.assertTrue(fenceGates.size() == 4 * 3, "Each gate must have 3 fence gates: " + fenceGates.size());
        for (BlockPos gate : fenceGates) {
            level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
        }
        Walk open = walk(level, village.heart(), area);
        helper.assertTrue(open.exit().isPresent(), "With the gates open, the walk must go out");
        BlockPos exit = open.exit().get();
        helper.assertTrue(fenceGates.stream().anyMatch(gate -> gate.distManhattan(exit.atY(gate.getY())) <= 2),
                "The walk must go out through a gate: " + exit);
        helper.succeed();
    }

    /**
     * The culture and the biome select the variant: first a variant of the culture with the biome, then the base
     * variant of the culture, then a variant of all the cultures with the biome, then the base variant. The test uses
     * the pools of the biome variants as the pools of the variants of a culture.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_variants")
    public static void cultureSelectsTheVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<Biome> plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = biomes.getHolderOrThrow(Biomes.DESERT);
        FortifiedVillageStructure.Variant northPlains = new FortifiedVillageStructure.Variant(HolderSet.direct(plains),
                pool(pools, "village/birch/plaza"), structure.wall(), structure.homes(), Optional.of("valeholm"));
        FortifiedVillageStructure.Variant northBase = new FortifiedVillageStructure.Variant(HolderSet.direct(),
                pool(pools, "village/savanna/plaza"), structure.wall(), structure.homes(), Optional.of("valeholm"));
        FortifiedVillageStructure.Variant allPlains = new FortifiedVillageStructure.Variant(HolderSet.direct(plains),
                pool(pools, "village/plains/plaza"), structure.wall(), structure.homes(), Optional.empty());
        FortifiedVillageStructure cultures = new FortifiedVillageStructure(new Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                structure.startPool(), structure.size(), structure.maxDistanceFromCenter(), structure.wall(),
                structure.homes(), List.of(allPlains, northBase, northPlains));

        helper.assertTrue(cultures.variantAt("valeholm", plains) == northPlains,
                "A variant of the culture with the biome comes first");
        helper.assertTrue(cultures.variantAt("valeholm", desert) == northBase,
                "With no variant of the culture for the biome, the base variant of the culture");
        helper.assertTrue(cultures.variantAt("ashmark", plains) == allPlains,
                "With no variant of the culture, a variant of all the cultures with the biome");
        helper.assertTrue(cultures.variantAt("ashmark", desert).startPool().equals(structure.startPool()),
                "With no other variant, the base variant of the structure");
        helper.succeed();
    }

    /**
     * A maritime site selects a maritime variant: the maritime variant of the biome, else the maritime base variant.
     * Another site never selects a maritime variant.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_variants")
    public static void maritimeSiteSelectsAMaritimeVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, String> expected = Map.of(Biomes.PLAINS, "village/maritime/plains/plaza",
                Biomes.DESERT, "village/maritime/desert/plaza", Biomes.TAIGA, "village/maritime/plaza",
                Biomes.BEACH, "village/maritime/plaza");
        for (String culture : List.of("valeholm", "ashmark", "sunreach")) {
            expected.forEach((biome, plaza) -> {
                Holder<Biome> holder = biomes.getHolderOrThrow(biome);
                String found = structure.variantAt(culture, holder, true).startPool().unwrapKey()
                        .map(key -> key.location().getPath()).orElse("");
                helper.assertTrue(found.equals(plaza), "The maritime variant of " + biome.location() + " must start "
                        + "with " + plaza + ", not " + found);
                helper.assertFalse(structure.variantAt(culture, holder, false).maritime(),
                        "Another site must not select a maritime variant");
            });
        }
        helper.succeed();
    }

    /**
     * The optional replacement changes a block when the output block exists, and keeps the properties that the two
     * blocks have. With no output block (a mod that is not present), the block of the template stays.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_variants")
    public static void optionalReplaceNeedsTheOutputBlock(GameTestHelper helper) {
        OptionalReplaceProcessor processor = new OptionalReplaceProcessor(List.of(
                new OptionalReplaceProcessor.Replacement(Blocks.OAK_STAIRS,
                        ResourceLocation.withDefaultNamespace("spruce_stairs")),
                new OptionalReplaceProcessor.Replacement(Blocks.STONE,
                        ResourceLocation.fromNamespaceAndPath("folkhold_absent_mod", "mossy_stone"))));
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        BlockState replaced = process(helper, processor, stairs);
        helper.assertTrue(replaced.is(Blocks.SPRUCE_STAIRS), "The output block must replace the input block");
        helper.assertTrue(replaced.getValue(StairBlock.FACING) == Direction.WEST, "The facing must stay");
        helper.assertTrue(process(helper, processor, Blocks.STONE.defaultBlockState()).is(Blocks.STONE),
                "With no output block, the input block must stay");
        helper.assertTrue(process(helper, processor, Blocks.DIRT.defaultBlockState()).is(Blocks.DIRT),
                "A block with no replacement must stay");
        helper.succeed();
    }

    private static BlockState process(GameTestHelper helper, OptionalReplaceProcessor processor, BlockState state) {
        StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state,
                null);
        StructureTemplate.StructureBlockInfo result = processor.process(helper.getLevel(), BlockPos.ZERO,
                BlockPos.ZERO, info, info, new StructurePlaceSettings(), null);
        return result == null ? Blocks.AIR.defaultBlockState() : result.state();
    }

    private static Holder<StructureTemplatePool> pool(Registry<StructureTemplatePool> pools, String path) {
        return pools.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL,
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path)));
    }

    /**
     * The biome selects the variant of the village: oak in the plains, the meadow, the cherry grove and the forest,
     * birch in the birch forest, acacia in the savanna, sandstone in the desert, mangrove in the swamps, and the base
     * variant (spruce) in the taiga, the snowy biomes and the windswept hills. The structure has all these biomes. A generated village in the plains of the test
     * world has the pieces, the palisade and the homes of the plains, and the layout of the base variant at the same
     * place.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_variants")
    public static void biomeSelectsTheVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, String> expected = Map.ofEntries(Map.entry(Biomes.PLAINS, "village/plains/plaza"),
                Map.entry(Biomes.SUNFLOWER_PLAINS, "village/plains/plaza"),
                Map.entry(Biomes.MEADOW, "village/plains/plaza"), Map.entry(Biomes.FOREST, "village/plains/plaza"),
                Map.entry(Biomes.BIRCH_FOREST, "village/birch/plaza"),
                Map.entry(Biomes.SAVANNA, "village/savanna/plaza"), Map.entry(Biomes.DESERT, "village/desert/plaza"),
                Map.entry(Biomes.SWAMP, "village/swamp/plaza"), Map.entry(Biomes.MANGROVE_SWAMP, "village/swamp/plaza"),
                Map.entry(Biomes.TAIGA, "village/plaza"), Map.entry(Biomes.SNOWY_TAIGA, "village/plaza"),
                Map.entry(Biomes.SNOWY_PLAINS, "village/plaza"), Map.entry(Biomes.WINDSWEPT_HILLS, "village/plaza"),
                Map.entry(Biomes.WINDSWEPT_FOREST, "village/plaza"),
                Map.entry(Biomes.WINDSWEPT_GRAVELLY_HILLS, "village/plaza"),
                Map.entry(Biomes.CHERRY_GROVE, "village/plains/plaza"));
        expected.forEach((biome, plaza) -> {
            Holder<Biome> holder = biomes.getHolderOrThrow(biome);
            helper.assertTrue(structure.biomes().contains(holder), "The village must have the biome "
                    + biome.location());
            String found = structure.variantAt(holder).startPool().unwrapKey()
                    .map(key -> key.location().getPath()).orElse("");
            helper.assertTrue(found.equals(plaza), "The variant of " + biome.location() + " must start with "
                    + plaza + ", not " + found);
        });

        Village village = generate(helper, level);
        VillageReference reference = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        helper.assertTrue(reference.pieces().stream().allMatch(piece -> piece.template().getPath()
                .startsWith("village/plains/")), "The pieces must be the pieces of the plains: "
                + reference.pieces().stream().map(VillageReference.Piece::template).distinct().toList());
        helper.assertTrue(reference.palisades().stream().allMatch(palisade -> palisade.log().is(Blocks.OAK_LOG)),
                "The palisade of the plains must be of oak logs");
        Optional<ResourceLocation> homes = structure.variantOf(level, village.start().getPieces()).homes().unwrapKey()
                .map(ResourceKey::location);
        helper.assertTrue(homes.map(pool -> pool.getPath().equals("village/plains/homes")).orElse(false),
                "The homes of the village must be the homes of the plains: " + homes);

        FortifiedVillageStructure baseOnly = new FortifiedVillageStructure(new Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                structure.startPool(), structure.size(), structure.maxDistanceFromCenter(), structure.wall(),
                structure.homes(), List.of());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart base = baseOnly.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                village.start().getChunkPos(), 0, level, biome -> true);
        List<String> layout = layout(level, village.start().getPieces());
        List<String> baseLayout = layout(level, base.getPieces());
        helper.assertTrue(layout.equals(baseLayout), "The village must have the layout of the base variant: "
                + layout.size() + " pieces, " + baseLayout.size() + " pieces in the base variant");
        helper.succeed();
    }

    /**
     * Each variant has all the pieces of the base variant (the style of a culture can have more pieces), with its
     * materials: no piece of a biome variant has a block of the spruce wood of the base variant. The base variant of a
     * culture keeps the spruce.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_variants")
    public static void variantPiecesHaveTheirMaterials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        StructureTemplateManager templates = level.getStructureManager();
        // The compound of the GameTests is in the folder of the base variant. It is not a piece of the variants.
        Set<String> basePieces = new HashSet<>(pieceNames(templates, "village/"));
        basePieces.remove("center");
        helper.assertTrue(basePieces.contains("plaza") && basePieces.contains(TOWER),
                "The base variant must have its pieces: " + basePieces);
        helper.assertFalse(structure.variants().isEmpty(), "The village must have variants");
        for (FortifiedVillageStructure.Variant variant : structure.variants()) {
            String start = variant.startPool().unwrapKey().orElseThrow().location().getPath();
            String folder = start.substring(0, start.lastIndexOf('/') + 1);
            Set<String> pieces = pieceNames(templates, folder);
            helper.assertTrue(pieces.containsAll(basePieces), "The variant " + folder + " must have the pieces of the"
                    + " base variant: " + pieces);
            // The base variant of a culture and the maritime base variant keep the spruce of the base variant.
            boolean spruce = (variant.culture().isPresent() || variant.maritime()) && variant.biomes().size() == 0;
            for (String piece : spruce ? Set.<String>of() : pieces) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, folder + piece);
                ListTag palette = templates.getOrCreate(id).save(new CompoundTag()).getList("palette",
                        Tag.TAG_COMPOUND);
                for (int i = 0; i < palette.size(); i++) {
                    String block = palette.getCompound(i).getString("Name");
                    helper.assertFalse(block.contains("spruce"), "The piece " + id + " must not have " + block);
                }
            }
        }
        helper.succeed();
    }

    /** The names of the pieces of the village in a folder, with no subfolder. */
    private static Set<String> pieceNames(StructureTemplateManager templates, String folder) {
        return templates.listTemplates()
                .filter(id -> id.getNamespace().equals(FolkholdWorld.MOD_ID) && id.getPath().startsWith(folder)
                        && id.getPath().indexOf('/', folder.length()) < 0)
                .map(id -> id.getPath().substring(folder.length()))
                .collect(Collectors.toSet());
    }

    /**
     * The heart of a generated village scans the area of the structure. It finds a post in each tower and the fence
     * gates of the 4 gates. The reference of the repairs has the palisade, and it matches the blocks after the
     * generation: no block is damaged. A missing log of the palisade is damaged.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_heart")
    public static void heartScansTheStructureAndRepairsThePalisade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = generate(helper, level);
        VillageReference reference = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        helper.assertTrue(level.getBlockEntity(village.heart()) instanceof VillageHeartBlockEntity,
                "The heart must have its block entity");
        VillageHeartBlockEntity heart = (VillageHeartBlockEntity) level.getBlockEntity(village.heart());
        heart.setRepairReference(reference);

        helper.assertTrue(heart.getLayout().scan(level, heart.scanArea(level)), "The heart must scan the village");
        VillageLayout layout = heart.getLayout();
        long towers = count(reference, TOWER);
        helper.assertTrue(layout.posts().size() == towers, "Each tower must have a post: " + layout.posts().size()
                + " posts, " + towers + " towers");
        helper.assertTrue(layout.gates().size() == 4 * 3, "The scan must find the 4 gates: " + layout.gates());
        helper.assertFalse(layout.bells().isEmpty(), "The scan must find the bell of the plaza");
        helper.assertTrue(layout.questBoards().size() == 1, "The scan must find the quest board");

        Set<BlockPos> damaged = VillageRepairs.damaged(level, heart, reference);
        helper.assertTrue(damaged.isEmpty(), "No block must be damaged after the generation: "
                + damaged.stream().limit(10).toList());
        VillageReference.Palisade palisade = reference.palisades().get(0);
        BlockPos log = palisade.start().above(PalisadeHeights.LOGS);
        helper.assertTrue(level.getBlockState(log).is(palisade.log().getBlock()), "A log of the palisade at " + log);
        level.setBlock(log, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(VillageRepairs.damaged(level, heart, reference).contains(log),
                "A missing log of the palisade must be damaged: " + log);
        helper.succeed();
    }

    /**
     * The plots of the generated villages: a plot with a house has a junction, a free plot has none. The wall keeps
     * the space of a home on each free plot: the space is inside the area of the village, and has no palisade.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_plots")
    public static void wallKeepsTheSpaceOfTheFreePlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(Settlements.VILLAGE_STRUCTURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int free = 0;
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK + CANDIDATE_STEP);
            StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            VillageReference reference = VillageReference.of(level, start.getPieces()).orElseThrow();
            BoundingBox area = reference.area().orElseThrow();
            StructureTemplatePool homes = ((FortifiedVillageStructure) structure)
                    .variantOf(level, start.getPieces()).homes().value();
            long houses = reference.pieces().stream().filter(piece -> HOUSES.contains(name(piece.template())))
                    .count();
            long built = reference.plots().stream().filter(VillagePlots.Plot::built).count();
            helper.assertTrue(built == houses, "Each house must have its plot: " + houses + " houses, " + built
                    + " built plots in " + chunk);
            for (VillagePlots.Plot plot : reference.plots()) {
                if (plot.built()) {
                    continue;
                }
                free++;
                BoundingBox space = VillagePlots.space(level.getStructureManager(), homes, plot).orElseThrow();
                helper.assertTrue(space.minX() > area.minX() && space.maxX() < area.maxX()
                        && space.minZ() > area.minZ() && space.maxZ() < area.maxZ(),
                        "The space of a free plot must be inside the palisade: " + space + " in " + area);
                boolean palisade = reference.palisadeBlocks().keySet().stream()
                        .anyMatch(pos -> pos.getX() >= space.minX() && pos.getX() <= space.maxX()
                                && pos.getZ() >= space.minZ() && pos.getZ() <= space.maxZ());
                helper.assertFalse(palisade, "The space of a free plot must have no palisade: " + space);
            }
        }
        helper.assertTrue(free > 0, "The villages of the test must have a free plot");
        helper.succeed();
    }

    /**
     * The heart removes the trees of its village: the logs and the leaves between the buildings and in the band around
     * the area, the roots of the mangroves and the cacti. Waterlogged leaves leave their water. The heart keeps the
     * blocks of the structure pieces, the columns of the palisade, the blocks of the players and the trees out of the
     * band. It also keeps a stilt under a template piece and a post of a pier, and it removes a log next to the pier.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "fortified_village_clearing")
    public static void heartClearsTheTreesOfTheVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = generate(helper, level);
        VillageReference generated = VillageReference.of(level, village.start().getPieces()).orElseThrow();
        BoundingBox generatedArea = generated.area().orElseThrow();
        BlockPos heartPos = village.heart();
        // The GameTest world has no water: the reference gets the box of a pier in the band around the area.
        BoundingBox pier = new BoundingBox(generatedArea.maxX() + 1, heartPos.getY() - 4, heartPos.getZ() + 8,
                generatedArea.maxX() + 5, heartPos.getY() + 1, heartPos.getZ() + 10);
        VillageReference reference = new VillageReference(generated.pieces(), generated.palisades(),
                generated.area(), generated.plots(), generated.homes(), List.of(pier));
        VillageHeartBlockEntity heart = (VillageHeartBlockEntity) level.getBlockEntity(village.heart());
        heart.setRepairReference(reference);
        BoundingBox clearing = VillageClearing.box(reference).orElseThrow();
        for (int x = clearing.minX() >> 4; x <= (clearing.maxX() + 2) >> 4; x++) {
            for (int z = clearing.minZ() >> 4; z <= clearing.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockPos aboveHeart = village.heart().above(5);
        BlockPos playerLog = village.heart().offset(2, 5, 2);
        BoundingBox area = reference.area().orElseThrow();
        BlockPos inBand = new BlockPos(area.maxX() + 3, village.heart().getY() + 3, village.heart().getZ());
        BlockPos outOfBand = new BlockPos(clearing.maxX() + 2, village.heart().getY() + 3, village.heart().getZ());
        BlockPos column = reference.palisades().get(0).start().above(12);
        Map.Entry<BlockPos, BlockState> templateLog = VillageRepairs.expectedBlocks(level, reference).entrySet()
                .stream().filter(entry -> entry.getValue().is(BlockTags.LOGS)).findFirst().orElseThrow();
        level.setBlock(aboveHeart, leaves, Block.UPDATE_CLIENTS);
        level.setBlock(playerLog, log, Block.UPDATE_CLIENTS);
        VillageRepairData.get(level).addPlayerBlock(village.heart(), playerLog);
        level.setBlock(inBand, log, Block.UPDATE_CLIENTS);
        BlockPos roots = inBand.above();
        BlockPos wetLeaves = inBand.above(2);
        BlockPos cactus = inBand.above(3);
        level.setBlock(roots, Blocks.MANGROVE_ROOTS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(wetLeaves, Blocks.MANGROVE_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_CLIENTS);
        level.setBlock(cactus, Blocks.CACTUS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(outOfBand, log, Block.UPDATE_CLIENTS);
        level.setBlock(column, log, Block.UPDATE_CLIENTS);
        BoundingBox plaza = village.start().getPieces().get(0).getBoundingBox();
        BlockPos stilt = new BlockPos(plaza.minX() + 1, plaza.minY() - 2, plaza.minZ() + 1);
        BlockPos post = new BlockPos(area.maxX() + 3, heartPos.getY() - 2, heartPos.getZ() + 9);
        BlockPos besidePier = post.south(3);
        level.setBlock(stilt, log, Block.UPDATE_CLIENTS);
        level.setBlock(post, log, Block.UPDATE_CLIENTS);
        level.setBlock(besidePier, log, Block.UPDATE_CLIENTS);

        heart.clearTrees(level);

        helper.assertTrue(level.getBlockState(aboveHeart).isAir(), "The leaves above the plaza must go");
        helper.assertTrue(level.getBlockState(inBand).isAir(), "A log in the band around the area must go");
        helper.assertTrue(level.getBlockState(roots).isAir(), "The roots of a mangrove must go");
        helper.assertTrue(level.getBlockState(wetLeaves).is(Blocks.WATER), "Waterlogged leaves must leave water");
        helper.assertTrue(level.getBlockState(cactus).isAir(), "A cactus must go");
        helper.assertTrue(level.getBlockState(playerLog).is(Blocks.OAK_LOG), "The log of a player must stay");
        helper.assertTrue(level.getBlockState(outOfBand).is(Blocks.OAK_LOG), "A log out of the band must stay");
        helper.assertTrue(level.getBlockState(column).is(Blocks.OAK_LOG),
                "A log on a column of the palisade must stay");
        helper.assertTrue(level.getBlockState(templateLog.getKey()).equals(templateLog.getValue()),
                "A log of a template must stay: " + templateLog.getKey());
        helper.assertTrue(level.getBlockState(stilt).is(Blocks.OAK_LOG), "A stilt under a template piece must stay");
        helper.assertTrue(level.getBlockState(post).is(Blocks.OAK_LOG), "A post of a pier must stay");
        helper.assertTrue(level.getBlockState(besidePier).isAir(), "A log next to a pier must go");
        helper.succeed();
    }

    /** A generated village and the position of its heart. */
    private record Village(StructureStart start, BlockPos heart) {
    }

    /** The result of a walk: the number of positions, and a position out of the palisade, if any. */
    private record Walk(int positions, Optional<BlockPos> exit) {
    }

    /**
     * Generates a village and places it in the level. The village goes to the first candidate chunk where no other
     * structure is: the GameTest world generates the vanilla villages.
     */
    private static Village generate(GameTestHelper helper, ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(Settlements.VILLAGE_STRUCTURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK);
            StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            helper.assertTrue(start.isValid(), "The village must start in " + chunk);
            BoundingBox box = start.getBoundingBox();
            if (hasOtherStructures(level, box)) {
                continue;
            }
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                            new BoundingBox(x << 4, level.getMinBuildHeight(), z << 4, (x << 4) + 15,
                                    level.getMaxBuildHeight(), (z << 4) + 15),
                            new ChunkPos(x, z));
                }
            }
            BoundingBox plaza = start.getPieces().get(0).getBoundingBox();
            BlockPos heart = new BlockPos((plaza.minX() + plaza.maxX()) / 2, plaza.minY() + 1,
                    (plaza.minZ() + plaza.maxZ()) / 2);
            return new Village(start, heart);
        }
        throw new IllegalStateException("No candidate chunk is free of other structures");
    }

    /** True if a chunk of a box has a structure, or a part of one. The method generates the chunks. */
    private static boolean hasOtherStructures(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (!level.getChunk(x, z).getAllReferences().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The layout of a village: for each piece, the name of its template (or the type of the piece for the wall), its
     * rotation and its box.
     */
    private static List<String> layout(ServerLevel level, List<StructurePiece> pieces) {
        return pieces.stream().map(piece -> (piece instanceof PoolElementStructurePiece element
                ? VillageReference.templateOf(level, element.getElement()).map(FortifiedVillageGameTests::name)
                        .orElse("")
                : piece.getClass().getSimpleName()) + " " + piece.getRotation() + " " + piece.getBoundingBox())
                .toList();
    }

    /** The number of pieces of a template, in the folder of any variant. */
    private static long count(VillageReference reference, String template) {
        return reference.pieces().stream().filter(piece -> name(piece.template()).equals(template)).count();
    }

    /** The name of a template with no folder, for example {@code tower} for {@code village/plains/tower}. */
    private static String name(ResourceLocation template) {
        String path = template.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** The positions of the blocks of an area that pass a test. */
    private static List<BlockPos> blocksIn(ServerLevel level, BoundingBox area, Predicate<BlockState> test) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(),
                area.maxZ())) {
            if (test.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /**
     * A walk on the ground from a position next to the heart. A step goes to a block side by side: 1 block up at
     * most, or down by a fall of {@link #MAX_FALL} blocks at most. The walk does not go on stairs: the stairs of the
     * towers go to the top of the palisade. A walk on the ground does not use them. The walk stops at the first
     * position out of the area.
     */
    private static Walk walk(ServerLevel level, BlockPos heart, BoundingBox area) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos start = heart.relative(direction);
            if (canStand(level, start) && seen.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (pos.getX() < area.minX() || pos.getX() > area.maxX() || pos.getZ() < area.minZ()
                    || pos.getZ() > area.maxZ()) {
                return new Walk(seen.size(), Optional.of(pos));
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                step(level, pos, pos.relative(direction)).filter(seen::add).ifPresent(queue::add);
            }
        }
        return new Walk(seen.size(), Optional.empty());
    }

    /** The position after a step from a position to the column of a block side by side, if the step can occur. */
    private static Optional<BlockPos> step(ServerLevel level, BlockPos from, BlockPos side) {
        BlockPos up = side.above();
        if (isFree(level, from.above(2)) && canStand(level, up)) {
            return Optional.of(up);
        }
        for (int fall = 0; fall <= MAX_FALL; fall++) {
            BlockPos target = side.below(fall);
            if (!isFree(level, target) || !isFree(level, target.above())) {
                return Optional.empty();
            }
            if (canStand(level, target)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    /** True if a mob can stand with its feet at a position: 2 free blocks on a support that is not a stair. */
    private static boolean canStand(ServerLevel level, BlockPos feet) {
        if (!isFree(level, feet) || !isFree(level, feet.above())) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState support = level.getBlockState(below);
        VoxelShape shape = support.getCollisionShape(level, below);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0 && !(support.getBlock() instanceof StairBlock);
    }

    /**
     * True if a mob can be in a position: the block has no collision shape. Also, the block below it is not higher
     * than 1 block: a fence or a fence gate is 1.5 blocks high.
     */
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        BlockPos below = pos.below();
        VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1.0;
    }
}
