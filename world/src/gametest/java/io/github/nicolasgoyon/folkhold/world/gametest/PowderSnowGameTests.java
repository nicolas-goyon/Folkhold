package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.group.GroupEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the mobs of the mod on powder snow. See {@link TestSupport}. */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowderSnowGameTests {

    private PowderSnowGameTests() {
    }

    /** The faction units, the caravan merchant and the mules of a caravan walk on powder snow. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "powder_snow")
    public static void caravanAndUnitsWalkOnPowderSnow(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", "test_destination", new Route(List.of(new Route.Point(pos.getX(), pos.getZ()),
                        new Route.Point(pos.getX() + 10, pos.getZ()))));
        GroupMember mule = new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 20.0F);
        group.addMember(mule);

        Mob merchant = ModEntities.CARAVAN_MERCHANT.get().create(helper.getLevel());
        Mob packAnimal = GroupEntities.create(helper.getLevel(), group, mule, pos);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));

        boolean merchantWalks = merchant != null && PowderSnowBlock.canEntityWalkOnPowderSnow(merchant);
        boolean muleWalks = packAnimal != null && PowderSnowBlock.canEntityWalkOnPowderSnow(packAnimal);
        boolean guardWalks = PowderSnowBlock.canEntityWalkOnPowderSnow(guard);
        for (Entity entity : new Entity[] {merchant, packAnimal, guard}) {
            if (entity != null) {
                entity.discard();
            }
        }
        helper.assertTrue(merchantWalks, "The caravan merchant must walk on powder snow");
        helper.assertTrue(muleWalks, "The mule of a caravan must walk on powder snow");
        helper.assertTrue(guardWalks, "A guard must walk on powder snow");
        helper.succeed();
    }
}
