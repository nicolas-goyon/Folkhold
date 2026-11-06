package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.village.RaidHelpers;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the rewards of the players who help a faction village during a raid ({@link RaidHelpers}). Each
 * test makes a raid at its last wave, with no raider. A raider joins the raid, a mock player kills it, and the test
 * ticks the raid until its end, with a limit of {@link #MAX_RAID_TICKS} ticks. The vanilla raid ends in a victory
 * when an occupied village point of interest (a bed) is near its center, else in a loss. The loss test puts the center
 * {@link #SKY} blocks over the test, far from the beds.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaidRewardGameTests {
    /** The level of the raids of the tests. The effect Hero of the Village gets the amplifier level - 1. */
    private static final int RAID_LEVEL = 2;
    /** The limit of the wait for the end of a raid. Vanilla sets the victory 40 ticks after the last raider dies. */
    private static final int MAX_RAID_TICKS = 200;
    /** The ids of the raids of the tests: each run takes new ids, far from the ids of the raids of the world. */
    private static final AtomicInteger NEXT_RAID_ID = new AtomicInteger(314_159);
    private static final BlockPos CENTER = new BlockPos(16, 1, 16);
    /** The heart of the fall test, away from the bed at the center. */
    private static final BlockPos HEART = new BlockPos(6, 1, 6);
    /** The distance of a player outside the raid area, on the x axis: more than {@link RaidHelpers#RAID_AREA_RADIUS}. */
    private static final int FAR = 100;
    /** The height of the center of a raid with no village, over the test: no bed of the area is near it. */
    private static final int SKY = 64;

    private RaidRewardGameTests() {
    }

    /**
     * A player who kills a raider and a player who gives the minimum damage get the effect Hero of the Village when
     * the village wins. A player in the raid area who does nothing gets no effect. A player who kills a raider from
     * outside the raid area gets no effect: vanilla adds this player to the heroes, and {@link RaidHelpers} removes it.
     */
    @GameTest(template = "arena_large", timeoutTicks = 100, batch = "raid_reward_victory")
    public static void helpersGetHeroOfTheVillageAfterAVictory(GameTestHelper helper) {
        TestSupport.sweep(helper);
        ServerLevel level = helper.getLevel();
        BlockPos center = placeVillageBed(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isVillage(center), "Waiting for the village of the bed"))
                .thenExecute(() -> raidAtVillage(helper, level, center))
                .thenSucceed();
    }

    private static void raidAtVillage(GameTestHelper helper, ServerLevel level, BlockPos center) {
        ServerPlayer killer = playerAt(helper, new BlockPos(12, 1, 20));
        ServerPlayer hitter = playerAt(helper, new BlockPos(16, 1, 20));
        ServerPlayer idle = playerAt(helper, new BlockPos(20, 1, 20));
        ServerPlayer far = playerAt(helper, CENTER.east(FAR));
        Raid raid = raidAt(level, center);
        VillageHearts.register(level, center);
        try {
            Raider pillager = join(helper, raid, EntityType.PILLAGER);
            pillager.hurt(level.damageSources().playerAttack(killer), 1000.0F);
            Raider second = join(helper, raid, EntityType.PILLAGER);
            second.hurt(level.damageSources().playerAttack(far), 1000.0F);
            helper.assertTrue(second.isDeadOrDying(), "The far player must kill the second pillager");
            Raider ravager = join(helper, raid, EntityType.RAVAGER);
            double minDamage = WorldConfig.RAID_REWARD_MIN_DAMAGE.get();
            helper.assertTrue(minDamage > 0 && minDamage < ravager.getMaxHealth(),
                    "The default minimum damage must be less than the health of a ravager: " + minDamage);
            ravager.hurt(level.damageSources().playerAttack(hitter), (float) minDamage + 1.0F);
            ravager.hurt(level.damageSources().generic(), 1000.0F);

            helper.assertTrue(RaidHelpers.helps(level, raid, killer.getUUID()), "The killer must help");
            helper.assertTrue(RaidHelpers.helps(level, raid, hitter.getUUID()), "The hitter must help");
            helper.assertFalse(RaidHelpers.helps(level, raid, idle.getUUID()), "The idle player must not help");
            helper.assertFalse(RaidHelpers.helps(level, raid, far.getUUID()), "The far player must not help");
            helper.assertFalse(raid.heroesOfTheVillage.contains(far.getUUID()),
                    "The far player must not be a hero of the raid");
            tickToEnd(helper, raid);
            helper.assertTrue(raid.isVictory(), "The raid must end in a victory");
            assertHero(helper, killer, "The killer");
            assertHero(helper, hitter, "The hitter");
            helper.assertFalse(idle.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
                    "The idle player must not get Hero of the Village");
            helper.assertFalse(far.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
                    "The far player must not get Hero of the Village");
            helper.assertTrue(VillageRaids.holds(level, raid), "The village must hold");
        } finally {
            end(level, raid, center);
        }
    }

    /**
     * The garrison dies during the raid, then a player kills the last raider. The raid ends in a victory, but the
     * village falls: the player gets no effect.
     */
    @GameTest(template = "arena_large", timeoutTicks = 200, batch = "raid_reward_garrison")
    public static void noRewardWhenTheGarrisonDies(GameTestHelper helper) {
        TestSupport.sweep(helper);
        ServerLevel level = helper.getLevel();
        BlockPos center = placeVillageBed(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        TestSupport.setLevel(helper, heart, 1);
        ServerPlayer killer = playerAt(helper, new BlockPos(12, 1, 20));
        Raid[] raid = new Raid[1];
        Raider[] pillager = new Raider[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.isVillage(center), "Waiting for the village of the bed");
                    // The heart registers itself in its first tick: a raid finds its village after this tick.
                    helper.assertTrue(VillageHearts.findNearest(level, helper.absolutePos(HEART), 1.0).isPresent(),
                            "Waiting for the registration of the heart");
                    heart.ensureGarrison(level.getGameTime());
                    int alive = heart.getGarrison().aliveCount();
                    helper.assertTrue(alive > 0 && heart.getUnits().size() == alive, "Waiting for the garrison");
                })
                .thenExecute(() -> {
                    raid[0] = raidAt(level, center);
                    pillager[0] = join(helper, raid[0], EntityType.PILLAGER);
                    helper.assertFalse(RaidHelpers.isFallen(level, raid[0]), "The village must stand at the start");
                    heart.getUnits().forEach(LivingEntity::kill);
                })
                .thenWaitUntil(() -> helper.assertTrue(RaidHelpers.isFallen(level, raid[0]),
                        "Waiting for the fall of the village: heart " + VillageRaids.heartOf(raid[0], level)
                                + ", living units " + heart.getGarrison().aliveCount() + ", loaded units "
                                + heart.getUnits().size() + ", started " + raid[0].isStarted()))
                .thenExecute(() -> {
                    try {
                        pillager[0].hurt(level.damageSources().playerAttack(killer), 1000.0F);
                        helper.assertTrue(RaidHelpers.helps(level, raid[0], killer.getUUID()), "The killer must help");
                        tickToEnd(helper, raid[0]);
                        helper.assertTrue(raid[0].isVictory(), "The raid must end in a victory");
                        helper.assertFalse(VillageRaids.holds(level, raid[0]), "The village must fall");
                        helper.assertFalse(killer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
                                "The killer must not get Hero of the Village after the fall");
                    } finally {
                        end(level, raid[0], center);
                        heart.getUnits().forEach(LivingEntity::discard);
                    }
                })
                .thenSucceed();
    }

    /** A player who kills a raider gets no effect when the village falls: the raid ends in a loss. */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "raid_reward_loss")
    public static void noRewardWhenTheVillageFalls(GameTestHelper helper) {
        TestSupport.sweep(helper);
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CENTER).above(SKY);
        ServerPlayer killer = playerAt(helper, new BlockPos(12, 1, 20));
        Raid raid = raidAt(level, center);
        VillageHearts.register(level, center);
        try {
            Raider pillager = join(helper, raid, EntityType.PILLAGER);
            pillager.hurt(level.damageSources().playerAttack(killer), 1000.0F);
            helper.assertTrue(RaidHelpers.helps(level, raid, killer.getUUID()), "The killer must help");
            tickToEnd(helper, raid);
            helper.assertTrue(raid.isLoss(), "The raid must end in a loss");
            helper.assertFalse(killer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
                    "The killer must not get Hero of the Village after a loss");
        } finally {
            end(level, raid, center);
        }
        helper.succeed();
    }

    /**
     * Places a bed at the center and claims it: vanilla counts only the occupied points of interest of a village, and
     * a villager claims its bed. The test waits for {@code isVillage} before the raid, with its timeout as the limit.
     *
     * @return the absolute position of the center
     */
    private static BlockPos placeVillageBed(GameTestHelper helper) {
        TestSupport.placeBed(helper, CENTER, Direction.NORTH);
        BlockPos center = helper.absolutePos(CENTER);
        helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> true, center, 2);
        return center;
    }

    /** A mock player at a position of the test. The listener of {@link TestSupport#mockPlayer} removes it. */
    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = TestSupport.mockPlayer(helper);
        BlockPos pos = helper.absolutePos(relative);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** A raid that started, at its last wave, with no raider: it ends when its raiders are dead. */
    private static Raid raidAt(ServerLevel level, BlockPos center) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", NEXT_RAID_ID.getAndIncrement());
        tag.putBoolean("Started", true);
        tag.putBoolean("Active", true);
        tag.putInt("BadOmenLevel", RAID_LEVEL);
        // A raid of level 2 has a bonus wave: 2 waves spawned of 1 wave is after the bonus wave.
        tag.putInt("GroupsSpawned", 2);
        tag.putInt("NumGroups", 1);
        tag.putString("Status", "ongoing");
        tag.putInt("CX", center.getX());
        tag.putInt("CY", center.getY());
        tag.putInt("CZ", center.getZ());
        return new Raid(level, tag);
    }

    private static Raider join(GameTestHelper helper, Raid raid, EntityType<? extends Raider> type) {
        Raider raider = type.create(helper.getLevel());
        helper.assertTrue(raider != null, "The raider must be created");
        raid.joinRaid(2, raider, helper.absolutePos(new BlockPos(16, 1, 8)), false);
        return raider;
    }

    /** Ticks the raid until it ends, with the limit {@link #MAX_RAID_TICKS}. */
    private static void tickToEnd(GameTestHelper helper, Raid raid) {
        for (int i = 0; i < MAX_RAID_TICKS && !raid.isOver(); i++) {
            raid.tick();
        }
        helper.assertTrue(raid.isOver(), "The raid must end in " + MAX_RAID_TICKS + " ticks");
    }

    private static void assertHero(GameTestHelper helper, ServerPlayer player, String name) {
        MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        helper.assertTrue(effect != null, name + " must get Hero of the Village");
        helper.assertTrue(effect.getAmplifier() == RAID_LEVEL - 1,
                name + " must get the level of the raid: amplifier " + effect.getAmplifier());
    }

    private static void end(ServerLevel level, Raid raid, BlockPos center) {
        raid.getAllRaiders().forEach(Raider::discard);
        raid.stop();
        VillageHearts.unregister(level, center);
    }
}
