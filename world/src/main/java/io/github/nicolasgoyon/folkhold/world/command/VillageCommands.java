package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.StreetStyle;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageSizes;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * The command {@code /folkhold village generate <tier> [style]} (permission level 2): places a faction village of a
 * tier near the source, in the Overworld, for the playtests.
 *
 * <ul>
 *   <li>The start level of the village is the top level of the tier: the village has the buildings and the job posts
 *       of this level.</li>
 *   <li>The command looks for a start chunk at {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} chunks from the source,
 *       the nearest first (see {@link #farFromVillages}). The chunk is far from the settlements, from the vanilla
 *       villages and from the natural faction villages. The check of the site accepts a village there, the biome at
 *       the middle of the plaza is a biome of the structure, and the variant of the site has the given style. The box
 *       of the village does not contain the source.</li>
 *   <li>The layout is the layout of the generation (see {@link FortifiedVillageStructure#layout(
 *       Structure.GenerationContext, int, java.util.function.Predicate)}). The structure start goes into the start
 *       chunk at the status {@code STRUCTURE_STARTS} (see {@link #place}): the world generation of the chunks of the
 *       box then adds the references, the terrain adaptation and the pieces, as for a natural village.</li>
 *   <li>The settlement of the start chunk gets the start level. The heart of the plaza binds to it.</li>
 * </ul>
 *
 * <p>The command runs in one server tick: a capital can stop the server for 10 seconds or more.
 */
public final class VillageCommands {
    private static final String KEY = "commands.folkhold_world.village.generate.";
    /** The nearest start chunk of the village, in chunks from the chunk of the source. */
    static final int MIN_DISTANCE = 16;
    /** The farthest start chunk of the village, in chunks from the chunk of the source. */
    static final int MAX_DISTANCE = 48;
    /** The distance between 2 tried start chunks, in chunks. */
    private static final int STEP = 2;
    /** The exclusion zone of the faction villages around the vanilla villages, in chunks, as in the structure set. */
    static final int VANILLA_EXCLUSION = 12;
    /** The free blocks between the walls of 2 villages. */
    static final int GAP = 16;
    /** The palisade and the outer row of the gates go this number of blocks past the margin of the wall. */
    private static final int WALL_LINE = 2;

    private VillageCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("village")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(Commands.literal("generate")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                LevelTables.get().tiers().stream().map(Tier::id), builder))
                                        .executes(context -> generate(context, Optional.empty()))
                                        .then(Commands.argument("style", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        StreetStyle.ALL.stream().map(StreetStyle::name), builder))
                                                .executes(context -> generate(context, Optional.of(
                                                        StringArgumentType.getString(context, "style")))))))));
    }

    private static int generate(CommandContext<CommandSourceStack> context, Optional<String> style) {
        CommandSourceStack source = context.getSource();
        String tierId = StringArgumentType.getString(context, "tier");
        Optional<Tier> tier = LevelTables.get().tiers().stream().filter(t -> t.id().equals(tierId)).findFirst();
        if (tier.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_tier", tierId));
            return 0;
        }
        if (style.isPresent() && StreetStyle.ALL.stream().noneMatch(s -> s.name().equals(style.get()))) {
            source.sendFailure(Component.translatable(KEY + "unknown_style", style.get()));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable(KEY + "wrong_dimension"));
            return 0;
        }
        if (!(level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Settlements.VILLAGE_STRUCTURE)
                instanceof FortifiedVillageStructure structure)) {
            FolkholdWorld.LOGGER.warn("The structure {} is not a faction village: no village to generate",
                    Settlements.VILLAGE_STRUCTURE.location());
            source.sendFailure(Component.translatable(KEY + "no_structure"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "started", tierName(tierId)), false);
        BlockPos origin = BlockPos.containing(source.getPosition());
        Optional<Generated> village;
        try {
            village = generate(level, structure, origin, tier.get().maxLevel(), style);
        } catch (RuntimeException e) {
            FolkholdWorld.LOGGER.error("The command could not generate a village near {}", origin, e);
            source.sendFailure(Component.translatable(KEY + "failed"));
            return 0;
        }
        if (village.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_site", tierName(tierId), MAX_DISTANCE * 16));
            return 0;
        }
        BlockPos center = village.get().center();
        int distance = (int) Math.round(Math.sqrt(center.distSqr(origin.atY(center.getY()))));
        source.sendSuccess(() -> Component.translatable(KEY + "success", tierName(tierId), village.get().style(),
                village.get().level(), center.getX(), center.getY(), center.getZ(), distance), true);
        return 1;
    }

    private static Component tierName(String tier) {
        return Component.translatable("folkhold_world.settlement_tier." + tier);
    }

    /**
     * A placed village.
     *
     * @param start  the start chunk
     * @param center the middle of the plaza
     * @param level  the start level
     * @param style  the name of the style
     * @param box    the box of the structure
     */
    record Generated(ChunkPos start, BlockPos center, int level, String style, BoundingBox box) {
    }

    /**
     * Places a village of a start level near a position (see {@link VillageCommands}).
     *
     * @param level     the Overworld
     * @param structure the village structure
     * @param origin    the position of the source: the box of the village does not contain it
     * @param start     the start level
     * @param style     the name of the style, or empty for the style of the site
     * @return the village, or empty if no start chunk in the distance accepts it
     */
    static Optional<Generated> generate(ServerLevel level, FortifiedVillageStructure structure, BlockPos origin,
            int start, Optional<String> style) {
        SettlementData data = SettlementData.get(level.getServer());
        for (ChunkPos chunk : candidates(new ChunkPos(origin))) {
            if (!farFromVillages(level, structure, chunk, start)) {
                continue;
            }
            Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(),
                    level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level,
                    structure.biomes()::contains);
            Optional<FortifiedVillageStructure.Layout> layout = structure.layout(context, start, variant -> style
                    .map(name -> FortifiedVillageStructure.styleOf(variant).name().equals(name)).orElse(true));
            if (layout.isEmpty()) {
                continue;
            }
            StructureStart structureStart = new StructureStart(structure, chunk, 0,
                    new PiecesContainer(layout.get().pieces()));
            BoundingBox box = structureStart.getBoundingBox();
            if (box.isInside(origin.getX(), box.minY(), origin.getZ())) {
                continue;
            }
            try {
                place(level, structure, structureStart, chunk);
            } catch (RuntimeException e) {
                FolkholdWorld.LOGGER.error("The village of the start chunk {} could not be placed", chunk);
                throw e;
            }
            BlockPos center = layout.get().center();
            data.registry().add(Settlements.create(level.getServer(), UUID.randomUUID(), chunk, center, start));
            data.setDirty();
            FolkholdWorld.LOGGER.info("Generated a village of the level {} at {} (start chunk {}), box {}", start,
                    center, chunk, box);
            return Optional.of(new Generated(chunk, center, start, layout.get().style().name(), box));
        }
        return Optional.empty();
    }

    /**
     * True if a start chunk is far from the other villages, with the rules of the world generation:
     *
     * <ul>
     *   <li>the walls of the new village and of each settlement (with the maximum radius of its level) are
     *       {@link #GAP} blocks apart at least;</li>
     *   <li>no possible vanilla village start is in {@link #VANILLA_EXCLUSION} chunks, as the exclusion zone of the
     *       structure set of the faction villages;</li>
     *   <li>no possible natural faction village start is near enough for its wall to come near the new wall: the
     *       natural village has the largest radius of the structure.</li>
     * </ul>
     */
    static boolean farFromVillages(ServerLevel level, FortifiedVillageStructure structure, ChunkPos chunk, int start) {
        int radius = radius(structure, start);
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        for (Settlement settlement : SettlementData.get(level.getServer()).registry().all()) {
            long dx = (long) settlement.x() - x;
            long dz = (long) settlement.z() - z;
            long distance = (long) radius + radius(structure, settlement.level()) + GAP;
            if (dx * dx + dz * dz < distance * distance) {
                return false;
            }
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        int natural = structure.maxDistanceFromCenter() + structure.wall().margin() + WALL_LINE;
        int naturalRange = Math.ceilDiv(radius + natural + GAP, 16);
        return !inRange(level, state, BuiltinStructureSets.VILLAGES, chunk, VANILLA_EXCLUSION)
                && !inRange(level, state, SettlementDiscovery.VILLAGE_SET, chunk, naturalRange);
    }

    /** The largest distance of the wall of a village of a level from its middle, in blocks. */
    private static int radius(FortifiedVillageStructure structure, int level) {
        return Math.min(VillageSizes.forLevel(level).maxRadius(), structure.maxDistanceFromCenter())
                + structure.wall().margin() + WALL_LINE;
    }

    /** True if a structure set has a possible start chunk in a range of chunks. */
    private static boolean inRange(ServerLevel level, ChunkGeneratorStructureState state, ResourceKey<StructureSet> key,
            ChunkPos chunk, int range) {
        Optional<Holder.Reference<StructureSet>> set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getHolder(key);
        return set.isPresent() && state.hasStructureChunkInRange(set.get(), chunk.x, chunk.z, range);
    }

    /** The start chunks in the distance, every {@link #STEP} chunks, the nearest first. */
    private static List<ChunkPos> candidates(ChunkPos origin) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx += STEP) {
            for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz += STEP) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) >= MIN_DISTANCE) {
                    chunks.add(new ChunkPos(origin.x + dx, origin.z + dz));
                }
            }
        }
        chunks.sort(Comparator.comparingLong(chunk -> (long) (chunk.x - origin.x) * (chunk.x - origin.x)
                + (long) (chunk.z - origin.z) * (chunk.z - origin.z)));
        return chunks;
    }

    /**
     * Puts a structure start into the world, as the world generation does.
     *
     * <ol>
     *   <li>The start goes into the start chunk at the status {@code STRUCTURE_STARTS}, before the references of the
     *       other chunks.</li>
     *   <li>Each chunk of the box goes to the status {@code STRUCTURE_STARTS} at least. A new chunk gets its reference,
     *       its terrain adaptation and its pieces from the world generation. A chunk that is already past
     *       {@code STRUCTURE_REFERENCES} gets its reference from this method, and a chunk that is already past
     *       {@code FEATURES} also gets its pieces from this method, with no terrain adaptation. With no structures in
     *       the world settings, the world generation places no pieces: this method places them in each chunk.</li>
     *   <li>Then each chunk of the box goes to the status {@code FULL}.</li>
     * </ol>
     */
    private static void place(ServerLevel level, Structure structure, StructureStart start, ChunkPos startChunk) {
        BoundingBox box = start.getBoundingBox();
        ChunkAccess origin = chunk(level, startChunk.x, startChunk.z);
        origin.setStartForStructure(structure, start);
        origin.setUnsaved(true);
        // With no structures in the world settings, the world generation places no pieces: the command does it.
        boolean structures = level.structureManager().shouldGenerateStructures();
        List<ChunkPos> generated = new ArrayList<>();
        List<ChunkPos> all = new ArrayList<>();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                ChunkAccess chunk = chunk(level, x, z);
                all.add(chunk.getPos());
                if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
                    chunk.addReferenceForStructure(structure, startChunk.toLong());
                    chunk.setUnsaved(true);
                }
                if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES) || !structures) {
                    generated.add(chunk.getPos());
                }
            }
        }
        all.forEach(pos -> level.getChunk(pos.x, pos.z));
        for (ChunkPos pos : generated) {
            start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                    level.getRandom(), new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(),
                            pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()),
                    pos);
        }
    }

    /** A chunk at the status {@code STRUCTURE_STARTS} at least: the full chunk if it is full. */
    private static ChunkAccess chunk(ServerLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true);
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
    }
}
