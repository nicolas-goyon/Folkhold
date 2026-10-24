package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.RegionPos;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the placement of the faction villages. The structure set of the faction villages has an
 * exclusion zone: a faction village does not start when a possible vanilla village start is in the square of
 * {@link #EXCLUSION_CHUNKS} chunks around its candidate chunk.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillagePlacementGameTests {
    /** The chunk count of the exclusion zone in {@code worldgen/structure_set/villages.json}. */
    private static final int EXCLUSION_CHUNKS = 8;
    /** The test checks the placement regions in a square of this size, in regions. */
    private static final int REGIONS = 24;
    private static final long[] SEEDS = {0L, 12345L, -4172144997902289642L, 2026101701L};

    private VillagePlacementGameTests() {
    }

    /**
     * For 4 seeds, a faction village starts in its candidate chunk only when no possible vanilla village start is in
     * the exclusion zone. The exclusion zone removes a part of the candidates only.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_placement_exclusion")
    public static void factionVillagesStayAwayFromVanillaVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RandomSpreadStructurePlacement faction = placement(level, SettlementDiscovery.VILLAGE_SET);
        RandomSpreadStructurePlacement vanilla = placement(level, BuiltinStructureSets.VILLAGES);
        int starts = 0;
        int removed = 0;
        for (long seed : SEEDS) {
            ChunkGeneratorStructureState state = state(level, seed);
            for (int x = -REGIONS / 2; x < REGIONS / 2; x++) {
                for (int z = -REGIONS / 2; z < REGIONS / 2; z++) {
                    ChunkPos candidate = faction.getPotentialStructureChunk(seed, x * faction.spacing(),
                            z * faction.spacing());
                    int distance = nearestStart(vanilla, seed, candidate);
                    boolean start = faction.isStructureChunk(state, candidate.x, candidate.z);
                    helper.assertTrue(start == distance > EXCLUSION_CHUNKS, "Seed " + seed + ", chunk " + candidate
                            + ": start " + start + ", nearest vanilla village start at " + distance + " chunks");
                    if (start) {
                        starts++;
                    } else {
                        removed++;
                    }
                }
            }
        }
        int share = 100 * removed / (starts + removed);
        helper.assertTrue(share >= 10 && share <= 40,
                "The exclusion zone must remove a part of the candidates only: " + removed + " of "
                        + (starts + removed));
        helper.succeed();
    }

    /** The discovery adds no settlement for a candidate chunk that the exclusion zone removes. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_placement_discovery")
    public static void discoveryIgnoresARemovedCandidate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RandomSpreadStructurePlacement faction = placement(level, SettlementDiscovery.VILLAGE_SET);
        ChunkGeneratorStructureState state = TestSupport.villageStructures(level);
        int spacing = faction.spacing();
        // Regions far from the spawn and from the areas of the other tests.
        for (int x = 400; x < 440; x++) {
            RegionPos region = new RegionPos(x, 400);
            ChunkPos candidate = faction.getPotentialStructureChunk(state.getLevelSeed(), region.minChunkX(spacing),
                    region.minChunkZ(spacing));
            if (!faction.isStructureChunk(state, candidate.x, candidate.z)) {
                helper.assertTrue(SettlementDiscovery.checkRegion(level, state, region).isEmpty(),
                        "The discovery must find no settlement in the region " + region);
                helper.assertTrue(SettlementData.get(level.getServer()).registry()
                        .byStart(candidate.x, candidate.z).isEmpty(), "No settlement must start in " + candidate);
                helper.succeed();
                return;
            }
        }
        helper.fail("40 regions must contain a candidate that the exclusion zone removes");
    }

    /**
     * The GameTest world is a vanilla Superflat world: its generator generates the vanilla villages, not the faction
     * villages. The discovery with the generator of the level adds no settlement and checks no region.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_placement_flat")
    public static void discoveryFindsNoVillageWhenTheGeneratorHasNone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState generator = level.getChunkSource().getGeneratorState();
        boolean factionVillages = generator.possibleStructureSets().stream()
                .anyMatch(set -> set.is(SettlementDiscovery.VILLAGE_SET));
        SettlementData data = SettlementData.get(level.getServer());
        int before = data.registry().all().size();
        // A position far from the spawn and from the areas of the other tests.
        BlockPos center = new BlockPos(-300_000, 64, -300_000);
        RandomSpreadStructurePlacement faction = placement(level, SettlementDiscovery.VILLAGE_SET);
        RegionPos region = RegionPos.ofChunk(center.getX() >> 4, center.getZ() >> 4, faction.spacing());

        SettlementDiscovery.Discovery discovery = SettlementDiscovery.discoverAround(level, center, 2048);

        helper.assertFalse(factionVillages, "The generator of the GameTest world must not generate faction villages");
        helper.assertTrue(discovery.found() == 0, "The discovery must add no settlement: " + discovery.found());
        helper.assertTrue(data.registry().all().size() == before, "The registry must not change");
        helper.assertFalse(data.isRegionChecked(region.x(), region.z()), "The discovery must check no region");
        helper.succeed();
    }

    private static RandomSpreadStructurePlacement placement(ServerLevel level, ResourceKey<StructureSet> key) {
        return (RandomSpreadStructurePlacement) level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(key).placement();
    }

    /** The structure state of a level with another seed. The placement checks use the seed only. */
    private static ChunkGeneratorStructureState state(ServerLevel level, long seed) {
        ServerChunkCache chunks = level.getChunkSource();
        return ChunkGeneratorStructureState.createForFlat(chunks.randomState(), seed,
                chunks.getGenerator().getBiomeSource(), Stream.empty());
    }

    /**
     * The distance in chunks (the larger of the two axis distances) from a chunk to the nearest possible start of a
     * random spread placement, for the distances up to {@code EXCLUSION_CHUNKS + 1}.
     */
    private static int nearestStart(RandomSpreadStructurePlacement placement, long seed, ChunkPos chunk) {
        int range = EXCLUSION_CHUNKS + 1;
        int spacing = placement.spacing();
        int nearest = Integer.MAX_VALUE;
        for (int x = Math.floorDiv(chunk.x - range, spacing); x <= Math.floorDiv(chunk.x + range, spacing); x++) {
            for (int z = Math.floorDiv(chunk.z - range, spacing); z <= Math.floorDiv(chunk.z + range, spacing); z++) {
                ChunkPos start = placement.getPotentialStructureChunk(seed, x * spacing, z * spacing);
                nearest = Math.min(nearest, Math.max(Math.abs(start.x - chunk.x), Math.abs(start.z - chunk.z)));
            }
        }
        return nearest;
    }
}
