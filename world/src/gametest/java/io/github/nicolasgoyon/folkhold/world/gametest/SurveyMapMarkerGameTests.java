package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import io.github.nicolasgoyon.cartofold.paper.SurveyMarker;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModMapDecorations;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageMapMarkers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the markers of the villages on the survey maps of Cartofold (see {@link VillageMapMarkers}). See
 * {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurveyMapMarkerGameTests {
    private static final BlockPos HEART = new BlockPos(4, 1, 4);

    private SurveyMapMarkerGameTests() {
    }

    /**
     * A survey map used on a village heart gets the marker of the village, with its name. A second use removes the
     * marker.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "survey_map_markers")
    public static void surveyMapOnTheHeartTogglesTheMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Settlement settlement = Settlements.of(level.getServer(), heart(helper)).orElseThrow();
        BlockPos pos = helper.absolutePos(HEART);
        ItemStack map = surveyMap(level, pos);
        ServerPlayer player = player(level, map);

        use(player, pos);
        List<SurveyMarker> added = markers(level, map);
        use(player, pos);
        List<SurveyMarker> removed = markers(level, map);

        helper.assertTrue(added.size() == 1, "The map must have the marker of the village: " + added);
        helper.assertTrue(added.get(0).name().equals(settlement.name()),
                "The marker must have the name of the village: " + added.get(0));
        helper.assertTrue(added.get(0).type().equals(ModMapDecorations.VILLAGE.getId()),
                "The marker must have the icon of a village: " + added.get(0));
        helper.assertTrue(added.get(0).pos().equals(pos), "The marker must be at the heart: " + added.get(0));
        helper.assertTrue(removed.isEmpty(), "A second use must remove the marker: " + removed);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    /** A survey map used on the heart of a ruin gets no marker. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "survey_map_markers")
    public static void ruinGivesNoMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Settlements.of(level.getServer(), heart(helper)).orElseThrow().setState(SettlementState.RUINS);
        BlockPos pos = helper.absolutePos(HEART);
        ItemStack map = surveyMap(level, pos);
        ServerPlayer player = player(level, map);

        use(player, pos);

        helper.assertTrue(markers(level, map).isEmpty(), "A ruin must give no marker: " + markers(level, map));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    /**
     * A click on the heart with an empty hand opens the screen of the village: the click links the heart to its
     * settlement. A survey map skips the action of the block, so that Cartofold gets the use of the map.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "survey_map_markers")
    public static void emptyHandOpensTheScreen(GameTestHelper helper) {
        clickOpensTheScreen(helper, ItemStack.EMPTY);
    }

    /** A click on the heart with an item that is not a survey map opens the screen of the village. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "survey_map_markers")
    public static void otherItemOpensTheScreen(GameTestHelper helper) {
        clickOpensTheScreen(helper, new ItemStack(Items.STICK));
    }

    private static void clickOpensTheScreen(GameTestHelper helper, ItemStack stack) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        BlockPos pos = helper.absolutePos(HEART);
        // The screen goes through the network: a mock player has a connection with a channel.
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = hit(pos);
        ItemInteractionResult withMap = level.getBlockState(pos).useItemOn(surveyMap(level, pos), level, player,
                InteractionHand.MAIN_HAND, hit);
        ItemInteractionResult withStack = level.getBlockState(pos).useItemOn(stack, level, player,
                InteractionHand.MAIN_HAND, hit);
        boolean linkedBefore = Settlements.of(level.getServer(), heart).isPresent();

        InteractionResult result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);

        helper.assertTrue(withMap == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                "A survey map must skip the action of the heart: " + withMap);
        helper.assertTrue(withStack == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "The item must go to the action of the heart: " + withStack);
        helper.assertTrue(!linkedBefore, "The heart must have no settlement before the click");
        helper.assertTrue(result.consumesAction(), "The click must succeed: " + result);
        helper.assertTrue(Settlements.of(level.getServer(), heart).isPresent(),
                "The click must open the screen of the village and link the heart");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    /** A village heart with its settlement, on a floor. */
    private static VillageHeartBlockEntity heart(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        return heart;
    }

    /** A survey map of scale 0 around a position. */
    private static ItemStack surveyMap(ServerLevel level, BlockPos pos) {
        return SurveyMapItem.create(level.getServer(),
                SurveyMapData.around(level.dimension(), pos.getX(), pos.getZ(), 0));
    }

    private static ServerPlayer player(ServerLevel level, ItemStack stack) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** A use of the item in the main hand on a block, as a right click of the player. */
    private static void use(ServerPlayer player, BlockPos pos) {
        player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                hit(pos));
    }

    /** The markers of the village hearts on a survey map. */
    private static List<SurveyMarker> markers(ServerLevel level, ItemStack map) {
        return SurveyMapData.get(level.getServer(), SurveyMapItem.id(map)).markers().stream()
                .filter(marker -> marker.source().equals(VillageMapMarkers.SOURCE)).toList();
    }
}
