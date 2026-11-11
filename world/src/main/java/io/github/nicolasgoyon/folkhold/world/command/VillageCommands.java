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
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * The command {@code /folkhold village generate <tier> [style]} (permission level 2): places a faction village of a
 * tier near the source, for the playtests.
 *
 * <ul>
 *   <li>The start level of the village is the top level of the tier: the village has the buildings and the job posts
 *       of this level.</li>
 *   <li>The command looks for a start chunk at {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} chunks from the source,
 *       the nearest first. The chunk has no settlement, the check of the site accepts a village there, and the
 *       variant of the chunk has the given style. The box of the village does not contain the source.</li>
 *   <li>The layout is the layout of the generation (see {@link FortifiedVillageStructure#layout(
 *       Structure.GenerationContext, int)}). The structure start goes into the start chunk, and each chunk in the box
 *       of the village gets a reference to it, as the world generation does: the village heart and the repairs find
 *       the structure. Then the pieces go into each chunk of the box. The command generates these chunks first.</li>
 *   <li>The settlement of the start chunk gets the start level. The heart of the plaza binds to it.</li>
 * </ul>
 *
 * <p>The chunks of the box exist before the village: the terrain adaptation of the world generation does not occur.
 * The foundations of the pieces fill the space under them.
 */
public final class VillageCommands {
    private static final String KEY = "commands.folkhold_world.village.generate.";
    /** The nearest start chunk of the village, in chunks from the chunk of the source. */
    static final int MIN_DISTANCE = 16;
    /** The farthest start chunk of the village, in chunks from the chunk of the source. */
    static final int MAX_DISTANCE = 48;
    /** The distance between 2 tried start chunks, in chunks. */
    private static final int STEP = 2;

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
        BlockPos origin = BlockPos.containing(source.getPosition());
        Optional<Generated> village = generate(level, origin, tier.get().maxLevel(), style);
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
     * @param level  the level
     * @param origin the position of the source: the box of the village does not contain it
     * @param start  the start level
     * @param style  the name of the style, or empty for the style of the site
     * @return the village, or empty if no start chunk in the distance accepts it
     */
    static Optional<Generated> generate(ServerLevel level, BlockPos origin, int start, Optional<String> style) {
        Structure found = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(Settlements.VILLAGE_STRUCTURE);
        if (!(found instanceof FortifiedVillageStructure structure)) {
            return Optional.empty();
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        SettlementData data = SettlementData.get(level.getServer());
        for (ChunkPos chunk : candidates(new ChunkPos(origin))) {
            if (!data.registry().byStart(chunk.x, chunk.z).isEmpty()) {
                continue;
            }
            Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                    generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                    level.getSeed(), chunk, level, biome -> true);
            Optional<FortifiedVillageStructure.Variant> variant = structure.variantAt(context);
            if (variant.isEmpty() || style.filter(name -> !FortifiedVillageStructure.styleOf(variant.get()).name()
                    .equals(name)).isPresent()) {
                continue;
            }
            Optional<FortifiedVillageStructure.Layout> layout = structure.layout(context, start);
            if (layout.isEmpty()) {
                continue;
            }
            StructureStart structureStart = new StructureStart(structure, chunk, 0,
                    new PiecesContainer(layout.get().pieces()));
            BoundingBox box = structureStart.getBoundingBox();
            if (box.isInside(origin.getX(), box.minY(), origin.getZ())) {
                continue;
            }
            place(level, structure, structureStart, chunk);
            BlockPos center = layout.get().center();
            if (data.registry().byStart(chunk.x, chunk.z).isEmpty()) {
                Settlement settlement = Settlements.create(level.getServer(), UUID.randomUUID(), chunk, center,
                        start);
                data.registry().add(settlement);
                data.setDirty();
            }
            FolkholdWorld.LOGGER.info("Generated a village of the level {} at {} (start chunk {}), box {}", start,
                    center, chunk, box);
            return Optional.of(new Generated(chunk, center, start, layout.get().style().name(), box));
        }
        return Optional.empty();
    }

    /** The start chunks in the distance, every {@link #STEP} chunks, the nearest first. */
    private static List<ChunkPos> candidates(ChunkPos origin) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx += STEP) {
            for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz += STEP) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                if (distance >= MIN_DISTANCE) {
                    chunks.add(new ChunkPos(origin.x + dx, origin.z + dz));
                }
            }
        }
        chunks.sort(Comparator.comparingLong(chunk -> (long) (chunk.x - origin.x) * (chunk.x - origin.x)
                + (long) (chunk.z - origin.z) * (chunk.z - origin.z)));
        return chunks;
    }

    /**
     * Puts a structure start into the world, as the world generation does: the start in its chunk, a reference in
     * each chunk of its box, then the pieces in each chunk. The chunks of the box are generated first.
     */
    private static void place(ServerLevel level, Structure structure, StructureStart start, ChunkPos startChunk) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(box.minX() >> 4, box.minZ() >> 4);
        ChunkPos max = new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);
        level.getChunk(startChunk.x, startChunk.z).setStartForStructure(structure, start);
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkAccess chunk = level.getChunk(x, z);
                chunk.addReferenceForStructure(structure, startChunk.toLong());
                chunk.setUnsaved(true);
            }
        }
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        level.getRandom(), new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(),
                                chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(),
                                chunk.getMaxBlockZ()), chunk);
            }
        }
    }
}
