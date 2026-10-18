package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the archer. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcherGameTests {

    private ArcherGameTests() {
    }

    /** An archer kills a monster at a distance with its bow. */
    @GameTest(template = "arena", timeoutTicks = 800, batch = "archer_kill")
    public static void archerKillsMonsterAtDistance(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(2, 1, 2));
        helper.assertTrue(archer.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "The archer must hold a bow");
        // The husk does not move: the archer must kill it from a distance.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(13, 1, 13));
        husk.setNoAi(true);

        TestSupport.succeedWhenAndRemove(helper,
                () -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"), archer, husk);
    }

    /** An archer with a post goes to its post and stays there. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "archer_post")
    public static void archerGoesToItsPost(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos heartRel = new BlockPos(8, 1, 8);
        helper.setBlock(heartRel, ModBlocks.VILLAGE_HEART.get());
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(2, 1, 2));
        archer.setVillagePos(helper.absolutePos(heartRel));
        BlockPos post = helper.absolutePos(new BlockPos(13, 1, 13));
        archer.setPostPos(post);

        TestSupport.succeedWhenAndRemove(helper, () -> {
            double distance = archer.position().distanceTo(Vec3.atBottomCenterOf(post));
            helper.assertTrue(distance < 1.5, "The archer must be at its post, distance: " + distance);
        }, archer);
    }

    /**
     * An archer on its post does not go to a target outside its attack radius. When the target comes into the
     * radius, the archer shoots it from the post.
     */
    @GameTest(template = "arena", timeoutTicks = 800, batch = "archer_post_stay")
    public static void postedArcherStaysOnItsPost(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos post = helper.absolutePos(new BlockPos(1, 1, 1));
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(1, 1, 1));
        archer.setPostPos(post);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(14, 1, 14));
        husk.setNoAi(true);
        BlockPos near = helper.absolutePos(new BlockPos(9, 1, 9));
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    double distance = archer.position().distanceTo(Vec3.atBottomCenterOf(post));
                    helper.assertTrue(distance < 1.5, "The archer must stay on its post, distance: " + distance);
                    helper.assertTrue(husk.isAlive(), "The husk out of the attack radius must stay alive");
                    husk.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
                })
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "The archer must shoot the husk in range"))
                .thenExecute(() -> {
                    double distance = archer.position().distanceTo(Vec3.atBottomCenterOf(post));
                    archer.discard();
                    husk.discard();
                    helper.assertTrue(distance < 1.5, "The archer must shoot from its post, distance: " + distance);
                })
                .thenSucceed();
    }

    /**
     * A player in creative mode is not a target. A guard and an archer on its post drop the player when the player
     * goes to creative mode, and they do not accept the player as a target again.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "archer_creative")
    public static void unitsDropAPlayerInCreativeMode(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(2, 1, 2));
        archer.setPostPos(helper.absolutePos(new BlockPos(2, 1, 2)));
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(4, 1, 4));
        ServerPlayer player = TestSupport.mockPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(10, 1, 10));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        archer.setTarget(player);
        guard.setTarget(player);
        boolean targetInSurvival = archer.getTarget() == player && guard.getTarget() == player;

        player.setGameMode(GameType.CREATIVE);

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(targetInSurvival, "A player in survival mode must be a target");
                    helper.assertTrue(archer.getTarget() == null, "The archer must drop a player in creative mode");
                    helper.assertTrue(guard.getTarget() == null, "The guard must drop a player in creative mode");
                    archer.setTarget(player);
                    guard.setTarget(player);
                    helper.assertTrue(archer.getTarget() == null && guard.getTarget() == null,
                            "A player in creative mode must not be a target");
                })
                .thenExecute(() -> {
                    helper.getLevel().getServer().getPlayerList().remove(player);
                    archer.discard();
                    guard.discard();
                })
                .thenSucceed();
    }

    /** An archer at its post sees and kills a monster far from the post. */
    @GameTest(template = "arena", timeoutTicks = 800, batch = "archer_post_kill")
    public static void archerAtPostKillsDistantMonster(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(2, 1, 2));
        archer.setPostPos(helper.absolutePos(new BlockPos(2, 1, 2)));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(13, 1, 13));
        husk.setNoAi(true);

        TestSupport.succeedWhenAndRemove(helper,
                () -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"), archer, husk);
    }
}
