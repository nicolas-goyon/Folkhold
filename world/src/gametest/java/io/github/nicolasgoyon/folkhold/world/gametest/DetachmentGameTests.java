package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Territories;
import io.github.nicolasgoyon.folkhold.world.village.VillageDetachments;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the detachments of the vanilla villages (see {@link VillageDetachments}). A test village is a
 * bell with two beds in the arena. The test settlements have no heart: their territory claims the chunk of the bell,
 * and the bell is outside their village radius.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DetachmentGameTests {
    private static final BlockPos BELL = new BlockPos(8, 1, 8);
    /**
     * The settlement that claims the chunk of the bell is this far to the east of the center of this chunk, in
     * blocks. The bell is at most 8 blocks from the center: the settlement is outside the village radius (48).
     */
    private static final int OWNER_DISTANCE = 66;
    /** The settlement of the other faction is this far to the west of the center: nearer, so its claim wins. */
    private static final int RIVAL_DISTANCE = 60;
    /** The radius of the scans of the tests, in blocks. */
    private static final int SCAN = 8;
    /** The start chunks of the test settlements are this far from their position, in chunks. */
    private static final int FAR_CHUNKS = 100_000;

    private DetachmentGameTests() {
    }

    /**
     * A vanilla village in a chunk that an active settlement claims gets a detachment of the faction of the
     * settlement: two guards near the bell. A second scan adds no guard. When a settlement of another faction claims
     * the chunk, the guards change their faction. When no settlement claims the chunk, the guards go away.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "detachment_claimed")
    public static void vanillaVillageInATerritoryGetsADetachment(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        buildVillage(helper, true);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos bell = helper.absolutePos(BELL);
        removeHeartsNear(level, bell);
        List<FactionSpec> factions = FactionData.get(server).factions();
        List<Settlement> settlements = new ArrayList<>();
        Settlement owner = settlement(server, chunkCenterX(bell) + OWNER_DISTANCE, bell.getZ(), factions.get(0));
        settlements.add(owner);

        // The point of interest of the bell comes on the next server task.
        helper.runAfterDelay(2, () -> {
            try {
                long homes = level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), bell,
                        VillageDetachments.HOME_RADIUS, PoiManager.Occupancy.ANY);
                helper.assertTrue(VillageDetachments.isVanillaVillage(level, bell),
                        "The bell with two beds must be a vanilla village: " + homes + " beds, heart "
                                + VillageHearts.findNearest(level, bell, WorldConfig.VILLAGE_RADIUS.get()));
                helper.assertTrue(Territories.settlementAt(server, bell).filter(owner::equals).isPresent(),
                        "The settlement must claim the chunk of the bell");
                List<BlockPos> withDetachment = VillageDetachments.scanAround(level, bell, SCAN);
                List<FactionUnit> guards = guards(level, bell);
                helper.assertTrue(withDetachment.contains(bell), "The bell must have a detachment");
                helper.assertTrue(guards.size() == 2, "The detachment must have 2 guards, not " + guards.size());
                helper.assertTrue(guards.stream().allMatch(guard -> owner.factionId().equals(guard.getFactionId())),
                        "The guards must be of the faction of the territory");

                VillageDetachments.scanAround(level, bell, SCAN);
                helper.assertTrue(guards(level, bell).size() == 2, "A second scan must add no guard");

                FactionSpec otherFaction = factions.stream()
                        .filter(faction -> !faction.id().equals(owner.factionId())).findFirst().orElseThrow();
                Settlement rival = settlement(server, chunkCenterX(bell) - RIVAL_DISTANCE, bell.getZ(),
                        otherFaction);
                settlements.add(rival);
                helper.assertTrue(Territories.settlementAt(server, bell).filter(rival::equals).isPresent(),
                        "The nearer settlement must claim the chunk of the bell");
                VillageDetachments.scanAround(level, bell, SCAN);
                helper.assertTrue(guards(level, bell).stream().allMatch(guard -> rival.factionId()
                        .equals(guard.getFactionId())), "The guards must change their faction with the territory");

                ruin(server, settlements);
                VillageDetachments.scanAround(level, bell, SCAN);
                helper.assertTrue(guards(level, bell).isEmpty(), "The detachment must leave an unclaimed village");
            } finally {
                cleanUp(helper, bell, settlements);
            }
            helper.succeed();
        });
    }

    /**
     * A vanilla village outside the territories gets no detachment. A bell with no beds in a claimed chunk is not a
     * vanilla village: it gets no detachment either.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "detachment_none")
    public static void noDetachmentOutsideATerritoryOrWithNoBeds(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        buildVillage(helper, true);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos bell = helper.absolutePos(BELL);
        removeHeartsNear(level, bell);
        List<Settlement> settlements = new ArrayList<>();

        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(Territories.settlementAt(server, bell).isEmpty(), "The arena must be unclaimed");
                helper.assertTrue(VillageDetachments.scanAround(level, bell, SCAN).isEmpty(),
                        "A village outside the territories must have no detachment");
                helper.assertTrue(guards(level, bell).isEmpty(), "No guard must spawn outside the territories");
                buildVillage(helper, false);
                settlements.add(settlement(server, chunkCenterX(bell) + OWNER_DISTANCE, bell.getZ(),
                        FactionData.get(server).factions().get(0)));
            } catch (RuntimeException e) {
                cleanUp(helper, bell, settlements);
                throw e;
            }
            // The beds leave the points of interest on the next server task.
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(VillageDetachments.scanAround(level, bell, SCAN).isEmpty(),
                            "A bell with no beds must have no detachment");
                    helper.assertTrue(guards(level, bell).isEmpty(), "No guard must spawn at a bell with no beds");
                } finally {
                    cleanUp(helper, bell, settlements);
                }
                helper.succeed();
            });
        });
    }

    /** The block X of the center of the chunk of a position. */
    private static int chunkCenterX(BlockPos pos) {
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(pos.getX()), 8);
    }

    /** Puts settlements in ruins: they claim no chunk. */
    private static void ruin(MinecraftServer server, List<Settlement> settlements) {
        settlements.forEach(settlement -> settlement.setState(SettlementState.RUINS));
        SettlementData.get(server).setDirty();
    }

    /**
     * Puts the test settlements in ruins, removes the guards, the bell and the beds. A later test in the same place
     * finds no vanilla village and no active settlement of this test.
     */
    private static void cleanUp(GameTestHelper helper, BlockPos bell, List<Settlement> settlements) {
        ServerLevel level = helper.getLevel();
        ruin(level.getServer(), settlements);
        VillageDetachments.scanAround(level, bell, SCAN);
        guards(level, bell).forEach(FactionUnit::discard);
        buildVillage(helper, false);
        helper.setBlock(BELL, Blocks.AIR);
    }

    /** A bell, and two beds near it when {@code beds} is true. With no beds, the method removes the beds. */
    private static void buildVillage(GameTestHelper helper, boolean beds) {
        helper.setBlock(BELL, Blocks.BELL);
        for (BlockPos head : List.of(new BlockPos(3, 1, 3), new BlockPos(13, 1, 3))) {
            if (beds) {
                BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                helper.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
                helper.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
            } else {
                helper.setBlock(head, Blocks.AIR);
                helper.setBlock(head.south(), Blocks.AIR);
            }
        }
    }

    /**
     * Removes the hearts of the earlier tests in the village radius around a position: the test positions of the
     * batches are the same, and a loaded heart near the bell makes it the bell of a faction village.
     */
    private static void removeHeartsNear(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 16; i++) {
            Optional<BlockPos> heart = VillageHearts.findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get());
            if (heart.isEmpty()) {
                return;
            }
            level.removeBlock(heart.get(), false);
            VillageHearts.unregister(level, heart.get());
        }
    }

    /**
     * A settlement with no heart at a block position, at level 1. Its start chunk is far away: a heart of a later test
     * in the same place does not bind to it (see {@code Settlements.bind}).
     */
    private static Settlement settlement(MinecraftServer server, int x, int z, FactionSpec faction) {
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), (x >> 4) + FAR_CHUNKS, z >> 4,
                faction.id(), "Outpost", x, z);
        settlement.setProgress(1, 0);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** The faction units near a bell. */
    private static List<FactionUnit> guards(ServerLevel level, BlockPos bell) {
        return level.getEntitiesOfClass(FactionUnit.class, new AABB(bell).inflate(16.0), FactionUnit::isAlive);
    }
}
