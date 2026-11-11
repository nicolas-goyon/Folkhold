package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.QuestBoardFeature;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the center of a new village: the builders do not break the generated blocks of the plaza (see
 * {@link VillageRepairs} and {@link VillageGrowth}). Each test generates a village of a style, places it in the level
 * and activates its heart. The village has offered quests: the quest board of the plaza shows papers. Then the test
 * does the checks of the repairs and of the growth, and logs each site and each block of the plaza that a site
 * changes.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageCenterGameTests {
    /** The first candidate chunk: far from the spawn and from the areas of the other tests. */
    private static final int FIRST_CHUNK = 15000;
    /**
     * The distance between 2 candidate chunks, in chunks. A city of the start levels is about 15 chunks wide with its
     * wall: the villages of 2 styles do not overlap.
     */
    private static final int CANDIDATE_STEP = 16;
    private static final int CANDIDATES = 8;
    /** The number of offered quests of the village. */
    private static final int OFFERS = 2;
    /** The time for the first update of the quest board, in ticks. */
    private static final int BOARD_UPDATE = QuestBoardFeature.UPDATE_INTERVAL + 2;
    /** The time for the forced chunks of the village to tick, in ticks. */
    private static final int CHUNK_LOAD = 5;
    /** The time between two calls of the garrison: each call adds a group of villagers, in ticks. */
    private static final int GARRISON_STEP = 10;

    private VillageCenterGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "village_center_base")
    public static void baseVillageKeepsItsCenter(GameTestHelper helper) {
        checkStyle(helper, "", 0);
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "village_center_valeholm")
    public static void valeholmVillageKeepsItsCenter(GameTestHelper helper) {
        checkStyle(helper, "valeholm/", 1);
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "village_center_sunreach")
    public static void sunreachVillageKeepsItsCenter(GameTestHelper helper) {
        checkStyle(helper, "sunreach/", 2);
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "village_center_maritime")
    public static void maritimeVillageKeepsItsCenter(GameTestHelper helper) {
        checkStyle(helper, "maritime/", 3);
    }

    /**
     * Generates and activates a village of a style, then checks that no site of the builders changes a block of its
     * plaza.
     *
     * @param folder the folder of the pieces of the style
     * @param row    the row of the candidate chunks of the style
     */
    private static void checkStyle(GameTestHelper helper, String folder, int row) {
        ServerLevel level = helper.getLevel();
        // The growth of the villages is off by default: the tests of the growth turn it on.
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_GROWTH, true);
        MinecraftServer server = level.getServer();
        Village village = generate(level, folder, row);
        VillageHeartBlockEntity heart = (VillageHeartBlockEntity) level.getBlockEntity(village.heart());
        helper.assertTrue(heart != null, "The heart must be on the plaza at " + village.heart());
        heart.setRepairReference(village.reference());
        setForced(level, village.box(), true);
        List<Quest> offers = new ArrayList<>();
        Settlement[] settlement = new Settlement[1];
        helper.startSequence()
                .thenIdle(CHUNK_LOAD)
                // The forced chunks of a large village are ready after some ticks: the first garrison waits for them.
                .thenWaitUntil(() -> helper.assertTrue(VillageLayout.isAreaReady(level, heart.scanArea(level)),
                        "The scan area of the village must be ready"))
                .thenExecute(() -> {
                    heart.ensureSettlement();
                    settlement[0] = Settlements.of(server, heart).orElseThrow();
                    // The first activation: the clearing of the trees, of the powder snow, then the first garrison
                    // with the villagers. The heart starts the updates of the quest board.
                    heart.clearTrees(level);
                    heart.clearTrees(level);
                    heart.ensureGarrison(level.getGameTime());
                    for (int i = 0; i < OFFERS; i++) {
                        offers.add(offer(server, settlement[0]));
                    }
                })
                .thenIdle(GARRISON_STEP)
                .thenExecute(() -> heart.ensureGarrison(level.getGameTime()))
                .thenIdle(GARRISON_STEP)
                .thenExecute(() -> heart.ensureGarrison(level.getGameTime()))
                .thenIdle(GARRISON_STEP)
                .thenExecute(() -> heart.ensureGarrison(level.getGameTime()))
                .thenIdle(BOARD_UPDATE)
                // The papers of the board: else the checks of the test find nothing. The board of a large village can
                // need more updates.
                .thenWaitUntil(() -> helper.assertTrue(papers(level, heart) == OFFERS,
                        "The quest board must show the offers: " + papers(level, heart)))
                .thenExecute(() -> {
                    List<String> broken = new ArrayList<>();
                    int builders = VillageFolk.of(level, village.heart(), ModProfessions.BUILDER.get()).size();
                    try {
                        BuilderSites.of(level, village.heart()).forEach(site -> broken.addAll(log(level, folder,
                                village, site)));
                        Optional<BuilderSite> repair = VillageRepairs.check(level, heart);
                        repair.ifPresent(site -> broken.addAll(log(level, folder, village, site)));
                        if (repair.isEmpty()) {
                            VillageGrowth.check(level, heart).ifPresent(site -> broken.addAll(log(level, folder,
                                    village, site)));
                        }
                    } finally {
                        BuilderSites.stop(level);
                        heart.getUnits().forEach(Entity::discard);
                        heart.getCats().forEach(Entity::discard);
                        VillageFolk.inhabitants(level, village.heart()).forEach(Entity::discard);
                        clear(server, offers);
                        setForced(level, village.box(), false);
                    }
                    helper.assertTrue(builders > 0, "The village " + folder + " must have builders");
                    helper.assertTrue(broken.isEmpty(), "The builders must keep the plaza of the village "
                            + folder + ": " + broken.stream().limit(10).toList());
                })
                .thenSucceed();
    }

    /** The largest number of papers of the quest boards of the scan of a heart. */
    private static int papers(ServerLevel level, VillageHeartBlockEntity heart) {
        return heart.getLayout().questBoards().stream().map(level::getBlockState)
                .filter(state -> state.hasProperty(QuestBoardBlock.PAPERS))
                .mapToInt(state -> state.getValue(QuestBoardBlock.PAPERS)).max().orElse(0);
    }

    /**
     * Logs a site of the builders and the blocks of the plaza that it changes.
     *
     * @return the descriptions of the blocks of the plaza that the site changes
     */
    private static List<String> log(ServerLevel level, String folder, Village village, BuilderSite site) {
        List<String> changed = new ArrayList<>();
        BoundingBox plaza = village.plaza();
        if (site.isRepair()) {
            Map<BlockPos, BlockState> expected = VillageRepairs.expectedBlocks(level, village.reference());
            FolkholdWorld.LOGGER.info("Village {} {}: repair site {} with {} blocks", folder, village.heart(),
                    site.id(), site.repairPositions().size());
            for (BlockPos pos : site.repairPositions()) {
                String block = pos + " " + level.getBlockState(pos) + " -> " + expected.get(pos) + " ("
                        + pieceAt(level, village.reference(), pos) + ")";
                FolkholdWorld.LOGGER.info("Village {} {}: repair of {}", folder, village.heart(), block);
                if (plaza.isInside(pos)) {
                    changed.add(block);
                }
            }
            return changed;
        }
        BoundingBox box = level.getStructureManager().get(site.template())
                .map(template -> GhostPlacement.box(template.getSize(), site.rotation(), Mirror.NONE, site.origin()))
                .orElseThrow();
        FolkholdWorld.LOGGER.info("Village {} {}: site {} of {} at {} in {}", folder, village.heart(), site.id(),
                site.template(), site.origin(), box);
        if (box.intersects(plaza)) {
            changed.add(site.template() + " in " + box + " on the plaza " + plaza);
        }
        return changed;
    }

    /** The template of the first piece of the reference that holds a position. */
    private static String pieceAt(ServerLevel level, VillageReference reference, BlockPos pos) {
        for (VillageReference.Piece piece : reference.pieces()) {
            boolean holds = level.getStructureManager().get(piece.template())
                    .map(found -> GhostPlacement.box(found.getSize(), piece.rotation(), Mirror.NONE, piece.origin()))
                    .filter(box -> box.isInside(pos))
                    .isPresent();
            if (holds) {
                return piece.template() + " " + piece.rotation();
            }
        }
        return "palisade or street";
    }

    /** A generated village: its reference, the box of its plaza and the position of its heart. */
    private record Village(VillageReference reference, BoundingBox box, BoundingBox plaza, BlockPos heart) {
    }

    /**
     * Generates a village of a style and places it in the level. The village goes to the first candidate chunk where no
     * other structure is: the GameTest world generates the vanilla villages.
     */
    private static Village generate(ServerLevel level, String folder, int row) {
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOrThrow(Settlements.VILLAGE_STRUCTURE);
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        ResourceLocation homes = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                "village/" + folder + "homes");
        // The style with no variants: the biome and the culture of the GameTest world do not select another style.
        FortifiedVillageStructure style = new FortifiedVillageStructure(new Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation()),
                pool(pools, "village/" + folder + "plaza"), structure.maxDistanceFromCenter(),
                structure.wall(), pools.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, homes)),
                List.of());
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < CANDIDATES; i++) {
            ChunkPos chunk = new ChunkPos(FIRST_CHUNK + i * CANDIDATE_STEP, FIRST_CHUNK + row * CANDIDATE_STEP);
            StructureStart start = style.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(),
                    chunk, 0, level, biome -> true);
            if (!start.isValid()) {
                continue;
            }
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
            VillageReference reference = VillageReference.of(level, start.getPieces()).orElseThrow().withHomes(homes);
            for (BlockPos pos : BlockPos.betweenClosed(plaza.minX(), plaza.minY(), plaza.minZ(), plaza.maxX(),
                    plaza.maxY(), plaza.maxZ())) {
                if (level.getBlockState(pos).is(ModBlocks.VILLAGE_HEART.get())) {
                    return new Village(reference, box, plaza, pos.immutable());
                }
            }
            throw new IllegalStateException("No village heart on the plaza " + plaza);
        }
        throw new IllegalStateException("No candidate chunk has a village with no other structure");
    }

    /** Forces the chunks of a box to stay loaded and to tick, or stops this. */
    private static void setForced(ServerLevel level, BoundingBox box, boolean forced) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

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

    private static Holder<StructureTemplatePool> pool(Registry<StructureTemplatePool> pools, String path) {
        return pools.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL,
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path)));
    }

    private static Quest offer(MinecraftServer server, Settlement village) {
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, village.id(), village.factionId(),
                "minecraft:bread", 8, 3, 20, ReputationTier.NEUTRAL, Long.MAX_VALUE / 2, 24_000);
        QuestData.get(server).quests().add(quest);
        return quest;
    }

    private static void clear(MinecraftServer server, List<Quest> quests) {
        Quests registry = QuestData.get(server).quests();
        for (Quest quest : quests) {
            registry.withdraw(quest.id());
            registry.finish(quest.id(), QuestState.FAILED);
        }
        registry.removeFinished();
    }
}
