package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the commands of the abstract simulation.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandGameTests {
    private static final int CELL = TerritoryClaims.CELL_BLOCKS;

    private CommandGameTests() {
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** The commands set the level and a stock, and run a cycle. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "commands_settlement")
    public static void commandsChangeTheSettlement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String faction = FactionData.get(server).factions().get(0).id();
        int x = 800 * CELL + CELL / 2;
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, 0, faction, "Commandton", x,
                100);
        settlement.setBiome("minecraft:plains");
        SettlementData.get(server).registry().add(settlement);

        run(server, "folkhold settlement level " + settlement.id() + " 7");
        run(server, "folkhold settlement stock " + settlement.id() + " minecraft:emerald_block 33");
        int stock = settlement.economy().stock().count("minecraft:emerald_block");
        run(server, "folkhold sim cycle 1");

        helper.assertTrue(settlement.level() >= 6, "The level must be 7, then lose at most one level, not "
                + settlement.level());
        helper.assertTrue(stock == 33, "The stock must be 33, not " + stock);
        helper.assertTrue(settlement.economy().isInitialized(), "The cycle must start the economy of the settlement");
        helper.succeed();
    }
}
