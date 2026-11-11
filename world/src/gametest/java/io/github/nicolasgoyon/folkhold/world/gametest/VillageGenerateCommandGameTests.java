package io.github.nicolasgoyon.folkhold.world.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the command {@code /folkhold village generate <tier> [style]}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGenerateCommandGameTests {
    /** The position of the source: far from the areas of the other tests. */
    private static final BlockPos ORIGIN = new BlockPos(-24000, 0, 24000);
    private static final String HAMLET = "hamlet";

    private VillageGenerateCommandGameTests() {
    }

    /**
     * The command places a hamlet of the top level of the tier near the source, and not on the source. The village
     * has its structure start: the structure manager finds it at the middle of the plaza. The settlement of the start
     * chunk has the top level of the tier, and the heart of the plaza binds to it. A source with no permission level
     * 2 does not have the command.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_generate_command")
    public static void generateCommandPlacesAVillageOfTheTier(GameTestHelper helper)
            throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        Set<String> before = data.registry().all().stream().map(Settlement::id).collect(Collectors.toSet());
        int top = LevelTables.get().tiers().stream().filter(tier -> tier.id().equals(HAMLET)).findFirst()
                .map(Tier::maxLevel).orElseThrow();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(ORIGIN)).withPermission(2).withSuppressedOutput();
        CommandSourceStack player = source.withPermission(0);

        int result = server.getCommands().getDispatcher().execute("folkhold village generate " + HAMLET, source);
        boolean hidden = server.getCommands().getDispatcher().parse("folkhold village generate " + HAMLET, player)
                .getReader().canRead();

        helper.assertTrue(result == 1, "The command must place a village");
        helper.assertTrue(hidden, "A source with no permission level 2 must not have the command");
        List<Settlement> added = data.registry().all().stream().filter(s -> !before.contains(s.id())).toList();
        helper.assertTrue(added.size() == 1, "The command must add one settlement, not " + added.size());
        Settlement settlement = added.get(0);
        helper.assertTrue(settlement.level() == top, "The settlement must have the level " + top + ", not "
                + settlement.level());
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(Settlements.VILLAGE_STRUCTURE);
        BlockPos center = new BlockPos(settlement.x(), level.getHeight(
                Heightmap.Types.WORLD_SURFACE, settlement.x(), settlement.z()),
                settlement.z());
        StructureStart start = level.structureManager().getStructureAt(center, structure);
        helper.assertTrue(start.isValid(), "The structure manager must find the village at " + center);
        helper.assertTrue(start.getChunkPos().x == settlement.startChunkX()
                && start.getChunkPos().z == settlement.startChunkZ(), "The start chunk of the settlement must be "
                + start.getChunkPos());
        BoundingBox box = start.getBoundingBox();
        helper.assertFalse(box.isInside(ORIGIN.getX(), box.minY(), ORIGIN.getZ()),
                "The village must not contain the source: " + box);
        Optional<VillageHeartBlockEntity> heart = BlockPos.betweenClosedStream(start.getPieces().get(0)
                .getBoundingBox()).map(pos -> level.getBlockEntity(pos))
                .filter(VillageHeartBlockEntity.class::isInstance).map(VillageHeartBlockEntity.class::cast)
                .findFirst();
        helper.assertTrue(heart.isPresent(), "The plaza must have a village heart");
        heart.get().ensureSettlement();
        helper.assertTrue(settlement.hasHeart() && settlement.heartY() == heart.get().getBlockPos().getY(),
                "The heart must bind to the settlement of the start chunk");
        helper.succeed();
    }
}
