package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.journal.MapWaypoints;
import io.github.nicolasgoyon.folkhold.world.registry.ModMapDecorations;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.List;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the waypoints of the villages on the vanilla maps (see {@link MapWaypoints}). See
 * {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapWaypointGameTests {
    private static final BlockPos HEART = new BlockPos(4, 1, 4);

    private MapWaypointGameTests() {
    }

    /**
     * A filled map used on a village heart gets the waypoint of the village, with its name. A second use removes the
     * waypoint.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "map_waypoints")
    public static void mapOnTheHeartTogglesTheWaypoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        BlockPos heartPos = helper.absolutePos(HEART);
        ItemStack map = MapItem.create(level, heartPos.getX(), heartPos.getZ(), (byte) 0, false, false);
        MapItemSavedData data = MapItem.getSavedData(map, level);
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack before = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, map);

        use(player, heartPos);
        List<MapDecoration> added = waypoints(data);
        use(player, heartPos);
        List<MapDecoration> removed = waypoints(data);

        helper.assertTrue(added.size() == 1, "The map must have the waypoint of the village: " + added);
        helper.assertTrue(added.get(0).name().map(name -> name.getString().equals(settlement.name())).orElse(false),
                "The waypoint must have the name of the village: " + added.get(0).name());
        helper.assertTrue(removed.isEmpty(), "A second use must remove the waypoint: " + removed);
        helper.assertTrue(map.get(DataComponents.MAP_ID) != null, "The map keeps its id");
        player.setItemInHand(InteractionHand.MAIN_HAND, before);
        helper.succeed();
    }

    private static void use(ServerPlayer player, BlockPos pos) {
        MapWaypoints.onRightClickBlock(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static List<MapDecoration> waypoints(MapItemSavedData data) {
        return StreamSupport.stream(data.getDecorations().spliterator(), false)
                .filter(decoration -> decoration.type().equals(ModMapDecorations.VILLAGE)).toList();
    }
}
