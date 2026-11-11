package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The templates of the Folkhold village structure ({@link Settlements#VILLAGE_STRUCTURE}), read from its data: the
 * base variant and each variant of the structure.
 *
 * <ul>
 *   <li>The pools of a variant: the start pool, the pool of the homes and the pools of the growth (see
 *       {@link VillageGrowth#pools}), and each pool that a jigsaw connector of a piece of these pools targets, or that
 *       one of these pools has as its fallback.</li>
 *   <li>The pools of the planned streets of a variant ({@link #PLANNED_STREETS}), in the folder of the pool of its
 *       homes. No connector of the other pools targets them.</li>
 *   <li>The pieces of the wall of a variant: the template of the tower and the template of the gate. The wall places
 *       them as rigid pieces.</li>
 * </ul>
 */
final class VillagePieces {
    /** The id of the empty pool of the game: it has no pieces. */
    private static final ResourceLocation EMPTY_POOL = ResourceLocation.withDefaultNamespace("empty");
    /**
     * The names of the pools of the planned streets of a variant: the street segment that continues, the crossroad
     * and the start of a side street.
     */
    static final List<String> PLANNED_STREETS = List.of("streets_next", "streets_side");

    private VillagePieces() {
    }

    /**
     * A template of the village structure.
     *
     * @param template   the id of the template
     * @param pool       the id of the first pool that has the template (sorted by id), or empty for a piece of the
     *                   wall
     * @param projection the projection of the piece: a street follows the terrain ({@code TERRAIN_MATCHING}), the
     *                   other pieces are rigid
     */
    record Piece(ResourceLocation template, Optional<ResourceLocation> pool,
            StructureTemplatePool.Projection projection) {
        /** The name of the template: the last part of its path, for example {@code house_cottage}. */
        String name() {
            String path = template.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }

        /** True if the pool of the piece has this name: the last part of the path of the pool. */
        boolean inPool(String name) {
            return pool.map(id -> id.getPath().endsWith("/" + name)).orElse(false);
        }
    }

    /**
     * The templates of all the variants of the village structure, sorted by id. A template id with 2 projections, or
     * a missing pool, adds a problem.
     *
     * @param level    the level, for the registries and the templates
     * @param problems the list that gets the problems
     * @return the pieces by template id
     */
    static Map<ResourceLocation, Piece> all(ServerLevel level, List<String> problems) {
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        List<FortifiedVillageStructure.Variant> variants = new ArrayList<>();
        variants.add(new FortifiedVillageStructure.Variant(HolderSet.direct(),
                structure.startPool(), structure.wall(), structure.homes(), Optional.empty()));
        variants.addAll(structure.variants());
        Map<ResourceLocation, Piece> pieces = new TreeMap<>();
        Set<ResourceLocation> visited = new HashSet<>();
        Deque<ResourceLocation> open = new ArrayDeque<>();
        for (FortifiedVillageStructure.Variant variant : variants) {
            ResourceLocation homes = id(variant.homes());
            open.add(id(variant.startPool()));
            open.add(homes);
            open.addAll(VillageGrowth.pools(homes));
            PLANNED_STREETS.forEach(name -> open.add(VillagePlots.sibling(homes, name)));
            add(pieces, new Piece(variant.wall().tower(), Optional.empty(),
                    StructureTemplatePool.Projection.RIGID), problems);
            add(pieces, new Piece(variant.wall().gate(), Optional.empty(),
                    StructureTemplatePool.Projection.RIGID), problems);
        }
        // The pools in sorted order: the pool of a piece is the same in each run.
        List<ResourceLocation> found = new ArrayList<>();
        while (!open.isEmpty()) {
            ResourceLocation id = open.poll();
            if (id.equals(EMPTY_POOL) || !visited.add(id)) {
                continue;
            }
            Optional<StructureTemplatePool> pool = pools.getOptional(id);
            if (pool.isEmpty()) {
                problems.add("no pool " + id);
                continue;
            }
            found.add(id);
            open.add(id(pool.get().getFallback()));
            for (StructurePoolElement element : pool.get().getShuffledTemplates(RandomSource.create(0L))) {
                VillageReference.templateOf(level, element).flatMap(level.getStructureManager()::get)
                        .ifPresent(template -> open.addAll(targets(template)));
            }
        }
        found.sort(null);
        for (ResourceLocation id : found) {
            for (StructurePoolElement element : pools.getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, id))
                    .getShuffledTemplates(RandomSource.create(0L))) {
                VillageReference.templateOf(level, element).ifPresent(template -> add(pieces,
                        new Piece(template, Optional.of(id), element.getProjection()), problems));
            }
        }
        return pieces;
    }

    /** Adds a piece. The first pool of a template stays. A template with 2 projections adds a problem. */
    private static void add(Map<ResourceLocation, Piece> pieces, Piece piece, List<String> problems) {
        Piece old = pieces.putIfAbsent(piece.template(), piece);
        if (old != null && old.projection() != piece.projection()) {
            problems.add(piece.template() + " has the projections " + old.projection() + " and "
                    + piece.projection());
        }
    }

    /** The pools that the jigsaw connectors of a template target. */
    private static List<ResourceLocation> targets(StructureTemplate template) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW).stream()
                .filter(info -> info.nbt() != null)
                .map(info -> ResourceLocation.tryParse(info.nbt().getString(JigsawBlockEntity.POOL)))
                .filter(id -> id != null)
                .toList();
    }

    private static ResourceLocation id(Holder<StructureTemplatePool> pool) {
        return pool.unwrapKey().orElseThrow().location();
    }
}
