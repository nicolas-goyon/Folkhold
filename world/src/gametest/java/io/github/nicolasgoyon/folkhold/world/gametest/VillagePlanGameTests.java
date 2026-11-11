package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageSizes;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the villages from the plan of their tier: for each tier and style, the layout of a village (see
 * {@link FortifiedVillageStructure#layout(Structure.GenerationContext, FortifiedVillageStructure.Variant, boolean,
 * VillageSize)}) is valid. The tests place no block: they read the heights of the terrain from the chunk generator.
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
     * A village of each tier of a style is valid:
     *
     * <ul>
     *   <li>the plan has all its plots and homes, and each piece and plot of the plan has an element of its pool;</li>
     *   <li>each plot of the plan has a building or is free, the free plots are the spare plots and the plot of the
 *       market, and each free plot keeps its space;</li>
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
        List<VillageSize> sizes = List.copyOf(VillageSizes.get().sizes());
        for (int i = 0; i < sizes.size(); i++) {
            VillageSize size = sizes.get(i);
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * STEP, FIRST_CHUNK + row * STEP);
            Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                    generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                    level.getSeed(), chunk, level, biome -> true);
            long time = System.nanoTime();
            // Each rotation of the plaza: 5 tiers turn the plaza 4 times at least.
            Rotation rotation = ROTATIONS.get(i % ROTATIONS.size());
            FortifiedVillageStructure.Layout layout = structure.layout(context, variant, variant.maritime(), size,
                    Optional.of(rotation)).orElseThrow();
            time = System.nanoTime() - time;
            String name = size.tier() + " " + layout.style().name() + " " + rotation;
            check(level, structure, chunk, layout, name, problems);
            VillagePlan plan = layout.plan();
            FolkholdWorld.LOGGER.info("Village plan {}: {} street pieces, {} plots, {} buildings, {} free plots, "
                    + "{} wall pieces, radius {}, {} ms", name, layout.village().size() - 1 - layout.built(),
                    plan.plots().size(), layout.built(), layout.free(), layout.wall().size(),
                    Math.round(plan.radius()), time / 1_000_000);
        }
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
                || layout.reserved().size() != layout.free() || layout.free() != expectedFree(plan)) {
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

    /** The free plots of a plan: the spare plots and the wide plot of the market. */
    private static long expectedFree(VillagePlan plan) {
        return plan.plots().stream().filter(plot -> plot.spare()
                || (plot.wide() && plot.homes().get(1).name().equals("market"))).count();
    }

    /** True if 2 boxes overlap on the horizontal plane. */
    private static boolean overlaps(BoundingBox first, BoundingBox second) {
        return first.intersects(second.minX(), second.minZ(), second.maxX(), second.maxZ());
    }
}
