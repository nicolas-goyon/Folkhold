package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the reputation. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReputationGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private ReputationGameTests() {
    }

    /** A player who kills a unit loses reputation with its faction and its village. */
    @GameTest(template = "arena", batch = "reputation_kill")
    public static void killingAUnitLowersTheReputation(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureFaction();
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(3, 1, 3));
        guard.setVillagePos(helper.absolutePos(HEART));
        guard.setFactionId(heart.getFactionId());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        FactionSpec faction = FactionData.get(helper.getLevel().getServer()).byId(heart.getFactionId()).orElseThrow();
        PlayerReputation reputation = ReputationData.get(helper.getLevel().getServer()).get(player.getUUID());
        int before = reputation.faction(faction);
        String villageId = heart.getVillageId().toString();

        guard.hurt(helper.getLevel().damageSources().playerAttack(player), 10000.0F);

        int expected = before + WorldConfig.REPUTATION_KILL_UNIT.get();
        int factionAfter = reputation.faction(faction);
        int villageAfter = reputation.village(faction, villageId);
        helper.getLevel().getServer().getPlayerList().remove(player);
        guard.discard();

        helper.assertTrue(factionAfter == Math.max(PlayerReputation.MIN, expected),
                "Faction reputation must be " + expected + ", not " + factionAfter);
        helper.assertTrue(villageAfter == Math.max(PlayerReputation.MIN, expected),
                "Village reputation must be " + expected + ", not " + villageAfter);
        helper.succeed();
    }
}
