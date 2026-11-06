package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.level.Experience;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.RaidEffects;
import io.github.nicolasgoyon.folkhold.world.village.RaidHelpers;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the effects of the end of a raid at a faction village ({@link RaidEffects}). Each test makes a
 * village of level {@link #LEVEL} with {@link #EXPERIENCE} experience and known stocks, and a raid at its last wave
 * (see {@link RaidRewardGameTests}). A helper kills the raider, an idle player in the raid area does nothing. The test
 * ticks the raid until its end, then waits for the effects at the next server tick. The simulation cycle, the
 * reputation for the kill of a monster and the loss for the death of the captain are off during the tests: only the
 * raid changes the values.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaidEffectGameTests {
    private static final int LEVEL = 5;
    private static final int EXPERIENCE = 30;
    private static final int DIAMONDS = 40;
    private static final int BREAD = 3;

    private RaidEffectGameTests() {
    }

    /** The heart, the settlement and the players of a test. */
    private record Scene(VillageHeartBlockEntity heart, Settlement settlement, FactionSpec faction,
            ServerPlayer helper, ServerPlayer idle, int helperStart, int idleStart) {
    }

    /** A raid that ends in a loss: the settlement loses experience and stocks, the helper gets the fall change. */
    @GameTest(template = "arena_large", timeoutTicks = 100, batch = "raid_effect_loss")
    public static void fallenVillageLosesExperienceAndStocks(GameTestHelper helper) {
        // The center of the raid is far over the beds of the area: the raid ends in a loss.
        TestSupport.setConfigForTest(helper, WorldConfig.RAID_HEART_RADIUS, 96);
        Scene scene = scene(helper, RaidRewardGameTests.CENTER);
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(RaidRewardGameTests.CENTER).above(RaidRewardGameTests.SKY);
        Raid[] raid = new Raid[1];
        waitForHeart(helper, scene)
                .thenExecute(() -> {
                    raid[0] = RaidRewardGameTests.raidAt(level, center);
                    killAndEnd(helper, scene, raid[0]);
                    helper.assertTrue(raid[0].isLoss(), "The raid must end in a loss");
                })
                .thenWaitUntil(() -> helper.assertTrue(RaidHelpers.isSettled(level, raid[0]),
                        "Waiting for the effects of the raid"))
                .thenExecute(() -> {
                    try {
                        int loss = RaidEffects.experienceLoss(LevelTables.get().traits(LEVEL).experience(),
                                WorldConfig.RAID_FALL_EXPERIENCE_PERCENT.get());
                        Experience.Progress expected = Experience.apply(LEVEL, EXPERIENCE, -loss, LevelTables.get());
                        assertProgress(helper, scene, expected.level(), expected.experience());
                        int percent = WorldConfig.RAID_FALL_STOCK_PERCENT.get();
                        assertStock(helper, scene, DIAMONDS - DIAMONDS * percent / 100, BREAD - BREAD * percent / 100);
                        assertReputation(helper, scene, WorldConfig.RAID_FALL_REPUTATION.get());
                    } finally {
                        end(level, raid[0], scene);
                    }
                })
                .thenSucceed();
    }

    /**
     * A raid that ends in a victory with a living garrison: the village holds, the settlement keeps its experience
     * and its stocks, the helper gets the hold change. A heart that is loaded but not in the registry (a chunk that
     * loads again) is not a broken heart.
     */
    @GameTest(template = "arena_large", timeoutTicks = 200, batch = "raid_effect_hold")
    public static void heldVillageKeepsItsProgress(GameTestHelper helper) {
        Scene scene = scene(helper, RaidRewardGameTests.HEART);
        ServerLevel level = helper.getLevel();
        BlockPos center = RaidRewardGameTests.placeVillageBed(helper);
        BlockPos heartPos = helper.absolutePos(RaidRewardGameTests.HEART);
        Raid[] raid = new Raid[1];
        waitForGarrison(helper, scene, center)
                .thenExecute(() -> {
                    raid[0] = RaidRewardGameTests.raidAt(level, center);
                    Raider pillager = RaidRewardGameTests.join(helper, raid[0], EntityType.PILLAGER);
                    helper.assertFalse(RaidHelpers.updateFall(level, raid[0]), "The village must stand at the start");
                    VillageHearts.unregister(level, heartPos);
                    boolean fallen = RaidHelpers.updateFall(level, raid[0]);
                    VillageHearts.register(level, heartPos);
                    helper.assertFalse(fallen, "A heart that is not in the registry must not be a broken heart");
                    pillager.hurt(level.damageSources().playerAttack(scene.helper()), 1000.0F);
                    RaidRewardGameTests.tickToEnd(helper, raid[0]);
                    helper.assertTrue(raid[0].isVictory(), "The raid must end in a victory");
                })
                .thenWaitUntil(() -> helper.assertTrue(RaidHelpers.isSettled(level, raid[0]),
                        "Waiting for the effects of the raid"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(VillageRaids.holds(level, raid[0]), "The village must hold");
                        assertProgress(helper, scene, LEVEL, EXPERIENCE);
                        assertStock(helper, scene, DIAMONDS, BREAD);
                        assertReputation(helper, scene, WorldConfig.RAID_HOLD_REPUTATION.get());
                    } finally {
                        end(level, raid[0], scene);
                    }
                })
                .thenSucceed();
    }

    /**
     * The garrison has no living unit at the start of the raid, and the helper kills the raiders: the raid ends in a
     * victory, but the village falls. The heart stands, thus the settlement keeps its experience and its stocks. The
     * helper gets the fall change and no reward.
     */
    @GameTest(template = "arena_large", timeoutTicks = 200, batch = "raid_effect_saved")
    public static void victoryWithNoGarrisonKeepsTheProgress(GameTestHelper helper) {
        Scene scene = scene(helper, RaidRewardGameTests.HEART);
        ServerLevel level = helper.getLevel();
        BlockPos center = RaidRewardGameTests.placeVillageBed(helper);
        Raid[] raid = new Raid[1];
        waitForGarrison(helper, scene, center)
                .thenExecute(() -> scene.heart().getUnits().forEach(LivingEntity::kill))
                .thenWaitUntil(() -> helper.assertTrue(scene.heart().getGarrison().aliveCount() == 0,
                        "Waiting for the death of the garrison"))
                .thenExecute(() -> {
                    raid[0] = RaidRewardGameTests.raidAt(level, center);
                    Raider pillager = RaidRewardGameTests.join(helper, raid[0], EntityType.PILLAGER);
                    helper.assertTrue(RaidHelpers.updateFall(level, raid[0]), "The village must fall at the start");
                    pillager.hurt(level.damageSources().playerAttack(scene.helper()), 1000.0F);
                    RaidRewardGameTests.tickToEnd(helper, raid[0]);
                    helper.assertTrue(raid[0].isVictory(), "The raid must end in a victory");
                })
                .thenWaitUntil(() -> helper.assertTrue(RaidHelpers.isSettled(level, raid[0]),
                        "Waiting for the effects of the raid"))
                .thenExecute(() -> {
                    try {
                        helper.assertFalse(VillageRaids.holds(level, raid[0]), "The village must fall");
                        assertProgress(helper, scene, LEVEL, EXPERIENCE);
                        assertStock(helper, scene, DIAMONDS, BREAD);
                        assertReputation(helper, scene, WorldConfig.RAID_FALL_REPUTATION.get());
                        helper.assertFalse(scene.helper().hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
                                "The helper must not get Hero of the Village after the fall");
                    } finally {
                        end(level, raid[0], scene);
                    }
                })
                .thenSucceed();
    }

    /** Makes the village and the players of a test, with the heart at a relative position. */
    private static Scene scene(GameTestHelper helper, BlockPos heartPos) {
        TestSupport.sweep(helper);
        TestSupport.setConfigForTest(helper, WorldConfig.SIMULATION_INTERVAL_TICKS, 72000);
        TestSupport.setConfigForTest(helper, WorldConfig.REPUTATION_KILL_MONSTER, 0);
        TestSupport.setConfigForTest(helper, WorldConfig.LEVEL_CAPTAIN_DEATH_LOSS, 0);
        MinecraftServer server = helper.getLevel().getServer();
        helper.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(heartPos);
        TestSupport.setLevel(helper, heart, LEVEL);
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        SettlementLevels.setProgress(server, settlement, LEVEL, EXPERIENCE);
        TestSupport.emptyStock(settlement);
        settlement.economy().stock().set("minecraft:diamond", DIAMONDS);
        settlement.economy().stock().set("minecraft:bread", BREAD);
        FactionSpec faction = FactionData.get(server).byId(settlement.factionId()).orElseThrow();
        ServerPlayer helperPlayer = RaidRewardGameTests.playerAt(helper, new BlockPos(12, 1, 20));
        ServerPlayer idle = RaidRewardGameTests.playerAt(helper, new BlockPos(20, 1, 20));
        return new Scene(heart, settlement, faction, helperPlayer, idle, reputation(server, faction, helperPlayer),
                reputation(server, faction, idle));
    }

    /** Waits for the registration of the heart: a raid finds its village after the first tick of the heart. */
    private static GameTestSequence waitForHeart(GameTestHelper helper, Scene scene) {
        return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                VillageHearts.findNearest(helper.getLevel(), scene.heart().getBlockPos(), 1.0).isPresent(),
                "Waiting for the registration of the heart"));
    }

    /** Waits for the village of the bed, the registration of the heart and its full garrison. */
    private static GameTestSequence waitForGarrison(GameTestHelper helper, Scene scene, BlockPos center) {
        return waitForHeart(helper, scene).thenWaitUntil(() -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(level.isVillage(center), "Waiting for the village of the bed");
            scene.heart().ensureGarrison(level.getGameTime());
            int alive = scene.heart().getGarrison().aliveCount();
            helper.assertTrue(alive > 0 && scene.heart().getUnits().size() == alive, "Waiting for the garrison");
        });
    }

    /** A raider joins the raid, the helper kills it, and the raid ticks until its end. */
    private static void killAndEnd(GameTestHelper helper, Scene scene, Raid raid) {
        Raider pillager = RaidRewardGameTests.join(helper, raid, EntityType.PILLAGER);
        pillager.hurt(helper.getLevel().damageSources().playerAttack(scene.helper()), 1000.0F);
        helper.assertTrue(RaidHelpers.helps(helper.getLevel(), raid, scene.helper().getUUID()), "The helper must help");
        RaidRewardGameTests.tickToEnd(helper, raid);
    }

    private static void assertProgress(GameTestHelper helper, Scene scene, int level, int experience) {
        helper.assertTrue(scene.settlement().level() == level && scene.settlement().experience() == experience,
                "The settlement must have level " + level + " and experience " + experience + ": level "
                        + scene.settlement().level() + ", experience " + scene.settlement().experience());
    }

    private static void assertStock(GameTestHelper helper, Scene scene, int diamonds, int bread) {
        int diamondCount = scene.settlement().economy().stock().count("minecraft:diamond");
        int breadCount = scene.settlement().economy().stock().count("minecraft:bread");
        helper.assertTrue(diamondCount == diamonds && breadCount == bread, "The stock must have " + diamonds
                + " diamonds and " + bread + " bread: " + diamondCount + " and " + breadCount);
    }

    /** The helper gets the change with the faction, the idle player gets no change. */
    private static void assertReputation(GameTestHelper helper, Scene scene, int change) {
        MinecraftServer server = helper.getLevel().getServer();
        int helperChange = reputation(server, scene.faction(), scene.helper()) - scene.helperStart();
        int idleChange = reputation(server, scene.faction(), scene.idle()) - scene.idleStart();
        helper.assertTrue(helperChange == change, "The helper must get " + change + ": " + helperChange);
        helper.assertTrue(idleChange == 0, "The idle player must get no change: " + idleChange);
    }

    private static int reputation(MinecraftServer server, FactionSpec faction, ServerPlayer player) {
        return ReputationData.get(server).get(player.getUUID()).faction(faction);
    }

    private static void end(ServerLevel level, Raid raid, Scene scene) {
        RaidRewardGameTests.end(level, raid, raid.getCenter());
        scene.heart().getUnits().forEach(LivingEntity::discard);
    }
}
