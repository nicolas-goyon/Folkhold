package io.github.nicolasgoyon.folkhold.world.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import java.util.ArrayList;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
    private static final String COMMAND = "folkhold village generate ";

    private VillageGenerateCommandGameTests() {
    }

    /**
     * The command places a hamlet, then a village, then a hamlet of the style of the first hamlet, from one source.
     * Each run adds one settlement with the top level of its tier. The structure manager finds each structure start
     * at the middle of its plaza and at a corner of its box, with the start chunk of the settlement. No box contains
     * the source, and no 2 boxes overlap. The heart of the plaza binds to the settlement.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_generate_command")
    public static void generateCommandPlacesVillagesApart(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommandSourceStack source = source(level);
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).get(Settlements.VILLAGE_STRUCTURE);
        List<StructureStart> starts = new ArrayList<>();
        StructureStart first = run(helper, source, "hamlet", starts);
        run(helper, source, "village", starts);
        String style = FortifiedVillageStructure.styleOf(structure.variantOf(level, first.getPieces())).name();
        StructureStart third = run(helper, source, "hamlet " + style, starts);
        String thirdStyle = FortifiedVillageStructure.styleOf(structure.variantOf(level, third.getPieces())).name();
        helper.assertTrue(thirdStyle.equals(style), "The third village must have the style " + style + ", not "
                + thirdStyle);
        for (int i = 0; i < starts.size(); i++) {
            BoundingBox box = starts.get(i).getBoundingBox();
            helper.assertFalse(box.isInside(ORIGIN.getX(), box.minY(), ORIGIN.getZ()),
                    "A village must not contain the source: " + box);
            for (int j = i + 1; j < starts.size(); j++) {
                helper.assertFalse(box.intersects(starts.get(j).getBoundingBox()),
                        "The villages " + box + " and " + starts.get(j).getBoundingBox() + " must not overlap");
            }
        }
        helper.assertTrue(server.getCommands().getDispatcher().parse(COMMAND + "hamlet", source.withPermission(0))
                .getContext().getNodes().stream().noneMatch(node -> node.getNode().getName().equals("village")),
                "A source with no permission level 2 must not have the command");
        helper.succeed();
    }

    /** The command fails with no village for an unknown tier, an unknown style and a level that is not the Overworld. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "village_generate_command_failures")
    public static void generateCommandRefusesBadArguments(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = source(level);
        int settlements = SettlementData.get(server).registry().size();

        int tier = dispatcher.execute(COMMAND + "metropolis", source);
        int style = dispatcher.execute(COMMAND + "hamlet gothic", source);
        int nether = dispatcher.execute(COMMAND + "hamlet", source.withLevel(server.getLevel(Level.NETHER)));

        helper.assertTrue(tier == 0 && style == 0 && nether == 0, "The command must fail: tier " + tier + ", style "
                + style + ", Nether " + nether);
        helper.assertTrue(SettlementData.get(server).registry().size() == settlements,
                "A failed command must add no settlement");
        helper.succeed();
    }

    private static CommandSourceStack source(ServerLevel level) {
        return level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(ORIGIN)).withPermission(2).withSuppressedOutput();
    }

    /** Runs the command for a tier, checks the new village, and adds its structure start to a list. */
    private static StructureStart run(GameTestHelper helper, CommandSourceStack source, String arguments,
            List<StructureStart> starts) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        Set<String> before = data.registry().all().stream().map(Settlement::id).collect(Collectors.toSet());
        String tierId = arguments.contains(" ") ? arguments.substring(0, arguments.indexOf(' ')) : arguments;
        int top = LevelTables.get().tiers().stream().filter(tier -> tier.id().equals(tierId)).findFirst()
                .map(Tier::maxLevel).orElseThrow();

        int result = server.getCommands().getDispatcher().execute(COMMAND + arguments, source);

        helper.assertTrue(result == 1, "The command must place a village: " + arguments);
        List<Settlement> added = data.registry().all().stream().filter(s -> !before.contains(s.id())).toList();
        helper.assertTrue(added.size() == 1, "The command must add one settlement, not " + added.size());
        Settlement settlement = added.get(0);
        helper.assertTrue(settlement.level() == top, "The settlement must have the level " + top + ", not "
                + settlement.level());
        FortifiedVillageStructure structure = (FortifiedVillageStructure) level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).get(Settlements.VILLAGE_STRUCTURE);
        BlockPos center = new BlockPos(settlement.x(), level.getHeight(Heightmap.Types.WORLD_SURFACE,
                settlement.x(), settlement.z()), settlement.z());
        StructureStart start = level.structureManager().getStructureAt(center, structure);
        helper.assertTrue(start.isValid() && start.getChunkPos().x == settlement.startChunkX()
                && start.getChunkPos().z == settlement.startChunkZ(),
                "The structure manager must find the village of the settlement at " + center);
        BoundingBox box = start.getBoundingBox();
        BlockPos corner = new BlockPos(box.minX(), (box.minY() + box.maxY()) / 2, box.minZ());
        helper.assertTrue(level.structureManager().getStructureAt(corner, structure).isValid(),
                "The structure manager must find the village at the corner " + corner);
        Optional<VillageHeartBlockEntity> heart = BlockPos.betweenClosedStream(start.getPieces().get(0)
                .getBoundingBox()).map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance).map(VillageHeartBlockEntity.class::cast)
                .findFirst();
        helper.assertTrue(heart.isPresent(), "The plaza must have a village heart");
        heart.get().ensureSettlement();
        helper.assertTrue(settlement.hasHeart() && settlement.heartY() == heart.get().getBlockPos().getY(),
                "The heart must bind to the settlement of the start chunk");
        starts.add(start);
        return start;
    }
}
