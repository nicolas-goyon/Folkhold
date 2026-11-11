package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.TemplateSigns;
import io.github.nicolasgoyon.folkhold.world.village.VillageBuildingTable;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the villages from the plan of their tier: for each start level and style, the layout of a village
 * (see {@link FortifiedVillageStructure#layout(Structure.GenerationContext, FortifiedVillageStructure.Variant, boolean,
 * int)}) is valid and has the buildings and the job posts of its level. The tests place no block: they read the heights
 * of the terrain from the chunk generator, and the signs from the templates of the pieces.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillagePlanGameTests {
    /** The first start chunk of the villages: far from the areas of the other tests. */
    private static final int FIRST_CHUNK = 20000;
    /** The distance between 2 start chunks, in chunks. */
    private static final int STEP = 20;
    /** The game places the pieces of a structure only in this range of chunks from the start chunk. */
    private static final int CHUNK_RANGE = 8;
    /** The name of the template of a gate. */
    private static final String GATE = "/gate";
    /** The rotations of the plaza, one for each tier in turn. */
    private static final String MARKET = VillageBuildings.MARKET;
    private static final String STOREHOUSE = "storehouse";
    private static final String BARRACKS = "barracks";
    private static final String FORGE = "forge";
    /** The kind of the building sign of the homes and of the house-workshops. */
    private static final String HOME = "house";
    private static final List<Rotation> ROTATIONS = List.of(Rotation.NONE, Rotation.CLOCKWISE_90,
            Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);

    private VillagePlanGameTests() {
    }

    /** The villages of the base style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_plan_base")
    public static void baseVillageOfEachTierIsValid(GameTestHelper helper) {
        checkStyle(helper, structure -> new FortifiedVillageStructure.Variant(HolderSet.direct(),
                structure.startPool(), structure.wall(), structure.homes(), Optional.empty()), 0);
    }

    /** The maritime villages: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_plan_maritime")
    public static void maritimeVillageOfEachTierIsValid(GameTestHelper helper) {
        checkStyle(helper, structure -> baseOf(structure, variant -> variant.maritime()), 1);
    }

    /** The villages of the Sunreach style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_plan_sunreach")
    public static void sunreachVillageOfEachTierIsValid(GameTestHelper helper) {
        checkStyle(helper, structure -> baseOf(structure,
                variant -> variant.culture().equals(Optional.of("sunreach"))), 2);
    }

    /** The villages of the Valeholm style: see {@link #checkStyle}. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_plan_valeholm")
    public static void valeholmVillageOfEachTierIsValid(GameTestHelper helper) {
        checkStyle(helper, structure -> baseOf(structure,
                variant -> variant.culture().equals(Optional.of("valeholm"))), 3);
    }

    /** The variant of a style with no biome. */
    private static FortifiedVillageStructure.Variant baseOf(FortifiedVillageStructure structure,
            Predicate<FortifiedVillageStructure.Variant> style) {
        return structure.variants().stream().filter(style).filter(variant -> variant.biomes().size() == 0)
                .findFirst().orElseThrow();
    }

    /**
     * A village of each start level of a style is valid:
     *
     * <ul>
     *   <li>the plan has all its plots and homes, and each piece and plot of the plan has an element of its pool;</li>
     *   <li>each plot of the plan has a building or is free, the free plots are the spare plots and the plot of the
     *       market under the level of the market, and each free plot keeps its space;</li>
     *   <li>the village has the buildings of its level and the job posts of its level (see {@link #checkLevel});</li>
     *   <li>the same seed gives the same village (the top level of each tier);</li>
     *   <li>each home of the pool {@code houses} of the variant is in a village of the style: the pieces with no
     *       building sign of a level kind, and with no job sign or a job sign of an allowed job (see
     *       {@link #checkVariety});</li>
     *   <li>the pieces of the plan stay in the maximum distance from the middle, and all the pieces stay in the range of
     *       chunks of the game;</li>
     *   <li>the wall has 4 gates and 4 arms;</li>
     *   <li>no 2 template pieces touch, and no template piece and no palisade is in the space of a free plot.</li>
     * </ul>
     *
     * @param variantOf the variant of the style
     * @param row       the row of the start chunks of the style
     */
    private static void checkStyle(GameTestHelper helper,
            Function<FortifiedVillageStructure, FortifiedVillageStructure.Variant> variantOf,
            int row) {
        ServerLevel level = helper.getLevel();
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        FortifiedVillageStructure.Variant variant = variantOf.apply(structure);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        List<String> problems = new ArrayList<>();
        LevelTable levels = LevelTables.get();
        Set<String> placed = new HashSet<>();
        List<String> eligible = List.of();
        for (int i = 0; i < levels.maxLevel(); i++) {
            int start = i + 1;
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * STEP, FIRST_CHUNK + row * STEP);
            Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                    generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                    level.getSeed(), chunk, level, biome -> true);
            long time = System.nanoTime();
            // Each rotation of the plaza: the levels turn the plaza 4 times at least.
            Rotation rotation = ROTATIONS.get(i % ROTATIONS.size());
            FortifiedVillageStructure.Layout layout = structure.layout(context, variant, variant.maritime(), start,
                    Optional.of(rotation)).orElseThrow();
            time = System.nanoTime() - time;
            String name = "level " + start + " " + layout.size().tier() + " " + layout.style().name() + " "
                    + rotation;
            check(level, structure, chunk, layout, name, problems);
            checkLevel(level, generator, layout, name, problems);
            layout.village().forEach(piece -> VillageReference.templateOf(level, piece.getElement())
                    .ifPresent(id -> placed.add(id.getPath())));
            eligible = eligible(level, generator, layout);
            boolean top = levels.tiers().stream().anyMatch(tier -> tier.maxLevel() == start);
            if (top && !same(layout, structure.layout(context, variant, variant.maritime(), start,
                    Optional.of(rotation)).orElseThrow())) {
                problems.add(name + ": the same seed gives an other village");
            }
            VillagePlan plan = layout.plan();
            Map<String, Integer> signs = signs(level, layout);
            FolkholdWorld.LOGGER.info("Village plan {}: {} street pieces, {} plots, {} buildings, {} free plots, "
                    + "{} wall pieces, radius {}, {} ms, market {}, storehouses {}, barracks {}, posts {}, homes {}, "
                    + "forges {}",
                    name, layout.village().size() - 1 - layout.built(), plan.plots().size(), layout.built(),
                    layout.free(), layout.wall().size(), Math.round(plan.radius()), time / 1_000_000,
                    signs.getOrDefault(MARKET, 0), signs.getOrDefault(STOREHOUSE, 0),
                    signs.getOrDefault(BARRACKS, 0), layout.posts().size(),
                    signs.getOrDefault(HOME, 0) - layout.posts().size(), signs.getOrDefault(FORGE, 0));
        }
        checkVariety(level, variant, placed, eligible, problems);
        helper.assertTrue(problems.isEmpty(), "Problems of the villages: " + problems);
        helper.succeed();
    }

    private static void check(ServerLevel level, FortifiedVillageStructure structure, ChunkPos chunk,
            FortifiedVillageStructure.Layout layout, String name, List<String> problems) {
        VillagePlan plan = layout.plan();
        layout.problems().forEach(problem -> problems.add(name + ": " + problem));
        if (plan.missingPlots() > 0 || plan.missingHomes() > 0 || plan.plots().size() != plan.target()) {
            problems.add(name + ": the plan has " + plan.plots().size() + " plots of " + plan.target() + ", "
                    + plan.missingHomes() + " missing homes");
        }
        long buildings = layout.village().stream().skip(1)
                .filter(piece -> piece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID)
                .count();
        if (layout.built() + layout.free() != plan.plots().size() || buildings != layout.built()
                || layout.reserved().size() != layout.free() || layout.free() != expectedFree(layout)) {
            problems.add(name + ": " + layout.built() + " buildings (" + buildings + " pieces) and " + layout.free()
                    + " free plots (" + layout.reserved().size() + " spaces) for " + plan.plots().size() + " plots");
        }
        BlockPos center = layout.center();
        int distance = structure.maxDistanceFromCenter();
        for (PoolElementStructurePiece piece : layout.village()) {
            BoundingBox box = piece.getBoundingBox();
            if (Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                    Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ())) > distance) {
                problems.add(name + ": the piece " + box + " is more than " + distance + " blocks from " + center);
            }
        }
        for (StructurePiece piece : layout.pieces()) {
            BoundingBox box = piece.getBoundingBox();
            if ((box.minX() >> 4) < chunk.x - CHUNK_RANGE || (box.maxX() >> 4) > chunk.x + CHUNK_RANGE
                    || (box.minZ() >> 4) < chunk.z - CHUNK_RANGE || (box.maxZ() >> 4) > chunk.z + CHUNK_RANGE) {
                problems.add(name + ": the piece " + box + " is out of the range of the start chunk " + chunk);
            }
        }
        long gates = layout.wall().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast)
                .filter(piece -> VillageReference.templateOf(level, piece.getElement())
                        .map(id -> id.getPath().endsWith(GATE)).orElse(false))
                .count();
        long arms = layout.wall().stream().filter(VillagePathPiece.class::isInstance).count();
        if (gates != 4 || arms != 4) {
            problems.add(name + ": " + gates + " gates and " + arms + " arms");
        }
        List<BoundingBox> templates = layout.pieces().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(StructurePiece::getBoundingBox).toList();
        for (int i = 0; i < templates.size(); i++) {
            for (int j = i + 1; j < templates.size(); j++) {
                if (overlaps(templates.get(i), templates.get(j))) {
                    problems.add(name + ": the pieces " + templates.get(i) + " and " + templates.get(j) + " touch");
                }
            }
        }
        List<BoundingBox> solid = layout.pieces().stream()
                .filter(piece -> piece instanceof PalisadePiece || (piece instanceof PoolElementStructurePiece pool
                        && pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID))
                .map(StructurePiece::getBoundingBox).toList();
        for (BoundingBox space : layout.reserved()) {
            solid.stream().filter(box -> overlaps(box, space)).findFirst().ifPresent(box -> problems.add(name
                    + ": the space " + space + " of a free plot has the piece " + box));
        }
    }

    /** The free plots of a layout: the spare plots, and the wide plot of the market with no market. */
    private static long expectedFree(FortifiedVillageStructure.Layout layout) {
        boolean market = VillageBuildings.kinds(VillageBuildingTable.get(), layout.level()).contains(MARKET);
        return layout.plan().plots().stream().filter(plot -> plot.spare()
                || (!market && plot.wide() && plot.homes().get(1).name().equals(MARKET))).count();
    }

    /**
     * A village has the buildings and the job posts of its level, as the scan of the heart sees them (the signs of the
     * templates of its pieces):
     *
     * <ul>
     *   <li>the number of building signs of each kind of the requirements is the number of requirements of the level
     *       with this kind: the growth has no building step;</li>
     *   <li>the number of job posts (job signs) is the job slots of the level, or the number of jobs that the level and
     *       the biome allow when it is smaller;</li>
     *   <li>each post has a job that the level and the biome allow, a post has a food job and a post trades: the growth
     *       has no first post step;</li>
     *   <li>the job signs are the posts of the layout.</li>
     * </ul>
     */
    private static void checkLevel(ServerLevel level, ChunkGenerator generator, FortifiedVillageStructure.Layout layout,
            String name, List<String> problems) {
        List<VillageBuildings.Requirement> requirements = VillageBuildingTable.get();
        Map<String, Integer> signs = signs(level, layout);
        for (VillageBuildings.Requirement requirement : requirements) {
            long needed = VillageBuildings.kinds(requirements, layout.level()).stream()
                    .filter(requirement.kind()::equals).count();
            if (signs.getOrDefault(requirement.kind(), 0) != needed) {
                problems.add(name + ": " + signs.getOrDefault(requirement.kind(), 0) + " " + requirement.kind()
                        + " of " + needed);
            }
        }
        if (VillageBuildings.missing(requirements, layout.level(), signs).isPresent()) {
            problems.add(name + ": a building of the level is missing: " + signs);
        }
        List<String> eligible = eligible(level, generator, layout);
        int expected = Math.min(LevelTables.get().traits(layout.level()).jobSlots(), eligible.size());
        List<String> posts = jobSigns(level, layout);
        if (posts.size() != expected || !eligible.containsAll(posts)
                || VillageGrowth.needsFirstPost(posts)
                || !posts.stream().sorted().toList().equals(layout.posts().stream().sorted().toList())) {
            problems.add(name + ": the job posts " + posts + " (layout " + layout.posts() + ") are not " + expected
                    + " posts of " + eligible + " with food and trade");
        }
    }

    /** The jobs that the level of a village and the biome at the middle of its plaza allow. */
    private static List<String> eligible(ServerLevel level, ChunkGenerator generator,
            FortifiedVillageStructure.Layout layout) {
        BlockPos center = layout.center();
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(center.getX()),
                QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(center.getZ()),
                level.getChunkSource().randomState().sampler());
        return Economies.eligibleJobs(biome, layout.level(), null).stream().map(Job::id).toList();
    }

    /**
     * The villages of a style keep the variety of the pool {@code houses} of the variant: each piece with no building
     * sign of a level kind (for example the forge, the fish market) is in a village. A piece with a job sign (the
     * fishing hut) is in a village when its job is in the jobs that the top level allows.
     *
     * @param placed   the templates of the pieces of all the villages of the style
     * @param eligible the jobs of the top level in the biome of the villages
     */
    private static void checkVariety(ServerLevel level, FortifiedVillageStructure.Variant variant, Set<String> placed,
            List<String> eligible, List<String> problems) {
        Set<String> kinds = new HashSet<>(VillageBuildings.kinds(VillageBuildingTable.get(), Integer.MAX_VALUE));
        ResourceKey<StructureTemplatePool> houses = ResourceKey.create(Registries.TEMPLATE_POOL,
                VillagePlots.sibling(variant.homes().unwrapKey().orElseThrow().location(), "houses"));
        for (var element : level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getOrThrow(houses)
                .getShuffledTemplates(RandomSource.create(0L))) {
            VillageReference.templateOf(level, element).ifPresent(id -> {
                TemplateSigns signs = level.getStructureManager().get(id).map(TemplateSigns::of).orElseThrow();
                if (signs.kinds().keySet().stream().noneMatch(kinds::contains) && eligible.containsAll(signs.jobs())
                        && !placed.contains(id.getPath())) {
                    problems.add("the home " + id + " is in no village of the style");
                }
            });
        }
    }

    /** True if 2 layouts have the same pieces at the same positions and the same posts. */
    private static boolean same(FortifiedVillageStructure.Layout first, FortifiedVillageStructure.Layout second) {
        Function<FortifiedVillageStructure.Layout, List<String>> pieces = layout -> layout.pieces().stream()
                .map(piece -> piece.getBoundingBox() + " " + piece.getRotation()).toList();
        return pieces.apply(first).equals(pieces.apply(second)) && first.posts().equals(second.posts());
    }

    /** The number of building signs of each kind in the templates of the pieces of a village, as the scan counts. */
    private static Map<String, Integer> signs(ServerLevel level, FortifiedVillageStructure.Layout layout) {
        Map<String, Integer> counts = new HashMap<>();
        templates(level, layout).forEach(template -> TemplateSigns.of(template).kinds()
                .forEach((kind, count) -> counts.merge(kind, count, Integer::sum)));
        return counts;
    }

    /** The job ids of the job signs in the templates of the pieces of a village. */
    private static List<String> jobSigns(ServerLevel level, FortifiedVillageStructure.Layout layout) {
        return templates(level, layout).stream().flatMap(template -> TemplateSigns.of(template).jobs().stream())
                .toList();
    }

    /** The templates of the pieces of a village, one for each piece. */
    private static List<StructureTemplate> templates(ServerLevel level, FortifiedVillageStructure.Layout layout) {
        return layout.village().stream()
                .flatMap(piece -> VillageReference.templateOf(level, piece.getElement()).stream())
                .flatMap(id -> level.getStructureManager().get(id).stream())
                .toList();
    }

    /** True if 2 boxes overlap on the horizontal plane. */
    private static boolean overlaps(BoundingBox first, BoundingBox second) {
        return first.intersects(second.minX(), second.minZ(), second.maxX(), second.maxZ());
    }
}
