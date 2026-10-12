package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the behavior of the units from the player reputation. See {@link TestSupport}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BehaviorGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private BehaviorGameTests() {
    }

    /** A village with a faction, one guard of the village and a survival player with the given reputation. */
    private record Scene(VillageHeartBlockEntity heart, GuardEntity guard, ServerPlayer player) {
        void remove(GameTestHelper helper) {
            guard.discard();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static Scene scene(GameTestHelper helper, int reputation) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureFaction();
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        guard.setVillagePos(helper.absolutePos(HEART));
        guard.setFactionId(heart.getFactionId());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(7.5, 1.0, 3.5));
        player.moveTo(pos.x, pos.y, pos.z);
        ReputationData data = ReputationData.get(helper.getLevel().getServer());
        data.get(player.getUUID()).setFaction(heart.getFactionId(), reputation);
        return new Scene(heart, guard, player);
    }

    /** Units attack a hostile player on sight. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "behavior_hostile")
    public static void unitsAttackHostilePlayer(GameTestHelper helper) {
        Scene scene = scene(helper, -500);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.guard().getTarget() == scene.player(),
                        "The guard must target the hostile player"))
                .thenExecute(() -> scene.remove(helper))
                .thenSucceed();
    }

    /** Units do not attack a friendly player, also after an accidental hit. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "behavior_friend")
    public static void unitsIgnoreFriendlyPlayer(GameTestHelper helper) {
        Scene scene = scene(helper, 500);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> scene.guard().hurt(
                        helper.getLevel().damageSources().playerAttack(scene.player()), 1.0F))
                .thenIdle(60)
                .thenExecute(() -> {
                    boolean targeted = scene.guard().getTarget() == scene.player();
                    scene.remove(helper);
                    helper.assertTrue(!targeted, "The guard must not target a friendly player");
                })
                .thenSucceed();
    }

    /** A neutral player gets a warning for the first offense. The units attack after the second offense. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "behavior_neutral")
    public static void neutralPlayerIsWarnedThenAttacked(GameTestHelper helper) {
        Scene scene = scene(helper, 0);
        scene.heart().recordOffense(scene.player());
        boolean warnedOnly = scene.heart().offenseCount(scene.player()) == 1 && scene.guard().getTarget() == null;
        scene.heart().recordOffense(scene.player());
        boolean attacked = scene.guard().getTarget() == scene.player();
        scene.remove(helper);

        helper.assertTrue(warnedOnly, "The first offense must give only a warning");
        helper.assertTrue(attacked, "The second offense must make the guard attack");
        helper.succeed();
    }

    /** After an attack on the village, a unit that did not see the attack also attacks the player on sight. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "behavior_village_hostility")
    public static void villageAttacksOffenderOnSight(GameTestHelper helper) {
        Scene scene = scene(helper, 0);
        scene.heart().recordOffense(scene.player());
        scene.heart().recordOffense(scene.player());
        GuardEntity other = TestSupport.spawnGuard(helper, new BlockPos(12, 1, 12));
        other.setVillagePos(helper.absolutePos(HEART));
        other.setFactionId(scene.heart().getFactionId());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(other.getTarget() == scene.player(),
                        "A unit of the village must attack the player that attacked the village"))
                .thenExecute(() -> {
                    other.discard();
                    scene.remove(helper);
                })
                .thenSucceed();
    }

    /** The chase area is around the village heart, with the chase radius. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "behavior_chase_area")
    public static void chaseAreaIsAroundTheVillage(GameTestHelper helper) {
        Scene scene = scene(helper, 0);
        BlockPos heart = helper.absolutePos(HEART);
        int radius = WorldConfig.CHASE_RADIUS.get();
        boolean near = scene.guard().isInChaseArea(heart.offset(radius / 2, 0, 0));
        boolean far = scene.guard().isInChaseArea(heart.offset(radius + 8, 0, 0));
        scene.remove(helper);

        helper.assertTrue(near, "A position in the chase radius must be in the chase area");
        helper.assertTrue(!far, "A position outside the chase radius must not be in the chase area");
        helper.succeed();
    }
}
