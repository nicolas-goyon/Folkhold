package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
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

    /** During an alert, an archer goes to its post. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "archer_post")
    public static void archerGoesToPostDuringAlert(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos heartRel = new BlockPos(8, 1, 8);
        helper.setBlock(heartRel, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(heartRel);
        ArcherEntity archer = TestSupport.spawnUnit(helper, ModEntities.ARCHER.get(), new BlockPos(2, 1, 2));
        archer.setVillagePos(helper.absolutePos(heartRel));
        BlockPos post = helper.absolutePos(new BlockPos(13, 1, 13));
        archer.setPostPos(post);

        heart.reportThreat(null);

        TestSupport.succeedWhenAndRemove(helper, () -> {
            heart.reportThreat(null);
            double distance = archer.position().distanceTo(Vec3.atBottomCenterOf(post));
            helper.assertTrue(distance < 1.5, "The archer must be at its post, distance: " + distance);
        }, archer);
    }
}
