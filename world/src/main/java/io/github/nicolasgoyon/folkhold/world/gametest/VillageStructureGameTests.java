package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.VillageCat;
import io.github.nicolasgoyon.folkhold.world.faction.Culture;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village structure. The templates are the pieces of the village: the center piece
 * ({@code folkhold_world:village/center}) and the houses ({@code folkhold_world:village/house_*}).
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageStructureGameTests {
    private static final int TOWERS = 4;
    private static final int GATE_BLOCKS = 6;
    private static final int BRAZIERS = 4;
    /** 4 banners on the north and south walls, 3 banners on the west and east walls. */
    private static final int BANNERS = 14;
    /** Each house has 2 windows in each side wall and 2 windows in the back wall. */
    private static final int HOUSE_WINDOWS = 6;

    private VillageStructureGameTests() {
    }

    /**
     * Finds the heart in the test area. The test does not use a fixed relative position: the position of a
     * template block and the relative positions of {@link GameTestHelper} do not always agree.
     */
    private static VillageHeartBlockEntity findHeart(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (helper.getLevel().getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
                return heart;
            }
        }
        throw new GameTestAssertException("No village heart in the test area");
    }

    /** The heart finds the posts, gates and braziers, and puts the archers on the posts. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_structure")
    public static void heartFindsLayoutAndPostsArchers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        VillageLayout layout = heart.getLayout();
        List<FactionUnit> units = heart.getUnits();
        List<ArcherEntity> archers = units.stream()
                .filter(ArcherEntity.class::isInstance).map(ArcherEntity.class::cast).toList();
        boolean archersOnPosts = !archers.isEmpty() && archers.stream()
                .allMatch(archer -> archer.getPostPos() != null && layout.posts().contains(archer.getPostPos())
                        && archer.blockPosition().equals(archer.getPostPos()));
        int heartY = heart.getBlockPos().getY();
        boolean othersOnGround = units.stream()
                .filter(unit -> unit.getPostPos() == null)
                .allMatch(unit -> Math.abs(unit.blockPosition().getY() - heartY) <= 2
                        && helper.getLevel().canSeeSky(unit.blockPosition()));
        units.forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);

        helper.assertTrue(layout.posts().size() == TOWERS, "Expected " + TOWERS + " posts, found " + layout.posts().size());
        helper.assertTrue(layout.gates().size() == GATE_BLOCKS,
                "Expected " + GATE_BLOCKS + " gate blocks, found " + layout.gates().size());
        helper.assertTrue(layout.braziers().size() == BRAZIERS,
                "Expected " + BRAZIERS + " braziers, found " + layout.braziers().size());
        helper.assertTrue(archersOnPosts, "Each archer must spawn on a post of the village");
        helper.assertTrue(othersOnGround, "Each unit without a post must spawn on the ground near the heart");
        helper.succeed();
    }

    /** The heart gives the banner color of the faction culture to the banners of the village. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_banners")
    public static void heartPaintsBanners(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        List<BlockPos> banners = heart.getLayout().banners();
        DyeColor expected = FactionVisuals.faction(helper.getLevel().getServer(), heart.getFactionId())
                .flatMap(faction -> Cultures.byId(faction.cultureId()))
                .map(Culture::bannerBase)
                .orElseThrow(() -> new GameTestAssertException("The village has no faction culture"));
        boolean painted = banners.stream().allMatch(pos -> helper.getLevel().getBlockState(pos).getBlock()
                instanceof AbstractBannerBlock banner && banner.getColor() == expected);

        helper.assertTrue(banners.size() == BANNERS, "Expected " + BANNERS + " banners, found " + banners.size());
        helper.assertTrue(painted, "Each banner must have the color " + expected + " of the faction culture");
        helper.succeed();
    }

    /** At night, the gates close, the braziers burn and the guards watch the gates. By day, the opposite. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_night")
    public static void villageAppliesDayAndNight(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        VillageLayout layout = heart.getLayout();
        List<GuardEntity> guards = heart.getUnits().stream()
                .filter(unit -> unit instanceof GuardEntity && !(unit instanceof CaptainEntity))
                .map(GuardEntity.class::cast).toList();

        heart.applyDayNight(true);
        boolean gatesClosed = layout.gates().stream()
                .allMatch(pos -> !helper.getLevel().getBlockState(pos).getValue(FenceGateBlock.OPEN));
        boolean braziersLit = layout.braziers().stream()
                .allMatch(pos -> helper.getLevel().getBlockState(pos).getValue(CampfireBlock.LIT));
        boolean guardsOnWatch = !guards.isEmpty() && guards.stream().allMatch(guard -> guard.getNightPos() != null);

        heart.applyDayNight(false);
        boolean gatesOpen = layout.gates().stream()
                .allMatch(pos -> helper.getLevel().getBlockState(pos).getValue(FenceGateBlock.OPEN));
        boolean braziersOut = layout.braziers().stream()
                .noneMatch(pos -> helper.getLevel().getBlockState(pos).getValue(CampfireBlock.LIT));
        boolean guardsOffWatch = guards.stream().allMatch(guard -> guard.getNightPos() == null);
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);

        helper.assertTrue(gatesClosed, "At night, all gates must be closed");
        helper.assertTrue(braziersLit, "At night, all braziers must burn");
        helper.assertTrue(guardsOnWatch, "At night, each guard must have a night watch position");
        helper.assertTrue(gatesOpen, "By day, all gates must be open");
        helper.assertTrue(braziersOut, "By day, the braziers must not burn");
        helper.assertTrue(guardsOffWatch, "By day, the guards must have no night watch position");
        helper.succeed();
    }

    /** Each window of a house is a full pane: it connects to the wall on its two sides. */
    @GameTest(template = "village/house_cottage", timeoutTicks = 20, batch = "village_house")
    public static void houseWindowsConnectToWalls(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int panes = 0;
        int posts = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!(state.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            panes++;
            boolean northSouth = state.getValue(CrossCollisionBlock.NORTH) && state.getValue(CrossCollisionBlock.SOUTH);
            boolean eastWest = state.getValue(CrossCollisionBlock.EAST) && state.getValue(CrossCollisionBlock.WEST);
            if (!northSouth && !eastWest) {
                posts++;
            }
        }

        helper.assertTrue(panes == HOUSE_WINDOWS, "Expected " + HOUSE_WINDOWS + " window panes, found " + panes);
        helper.assertTrue(posts == 0, posts + " window panes do not connect to the wall on their two sides");
        helper.succeed();
    }

    /** In an alert, two archers on the same tower go to two different towers. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_archer_alert")
    public static void alertSendsArchersToDifferentTowers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        List<ArcherEntity> archers = heart.getUnits().stream()
                .filter(ArcherEntity.class::isInstance).map(ArcherEntity.class::cast).toList();
        BlockPos first = heart.getLayout().validPosts().get(0);
        for (ArcherEntity archer : archers) {
            archer.setPostPos(first);
            archer.moveTo(first.getX() + 0.5, first.getY(), first.getZ() + 0.5);
        }

        heart.assignAlertPosts();

        long distinct = archers.stream().map(FactionUnit::getPostPos).distinct().count();
        List<BlockPos> validPosts = heart.getLayout().validPosts();
        boolean valid = archers.stream().allMatch(archer -> validPosts.contains(archer.getPostPos()));
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        helper.assertTrue(archers.size() >= 2, "The garrison must have 2 archers or more");
        helper.assertTrue(distinct == archers.size(), "Each archer must have its own tower");
        helper.assertTrue(valid, "Each archer must have a valid tower");
        helper.succeed();
    }

    /** In the calm rotation, each archer moves to another free tower. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_archer_rotation")
    public static void calmRotationMovesTheArchers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        List<ArcherEntity> archers = heart.getUnits().stream()
                .filter(ArcherEntity.class::isInstance).map(ArcherEntity.class::cast).toList();
        List<BlockPos> before = archers.stream().map(FactionUnit::getPostPos).toList();

        heart.rotateArchers(helper.getLevel().random, 1.0);

        List<BlockPos> after = archers.stream().map(FactionUnit::getPostPos).toList();
        boolean moved = true;
        for (int i = 0; i < archers.size(); i++) {
            moved &= !after.get(i).equals(before.get(i));
        }
        long distinct = after.stream().distinct().count();
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        helper.assertTrue(moved, "Each archer must move to another tower: " + before + " -> " + after);
        helper.assertTrue(distinct == archers.size(), "Two archers must not share a tower");
        helper.succeed();
    }

    /** When the village goes to the alert state, the alarm bell rings. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_bell")
    public static void alertRingsTheBell(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        List<BlockPos> bells = heart.getLayout().bells();

        heart.reportThreat(null);

        boolean rang = !bells.isEmpty() && bells.stream().allMatch(pos ->
                helper.getLevel().getBlockEntity(pos) instanceof BellBlockEntity bell && bell.shaking);
        helper.assertTrue(bells.size() == 1, "Expected 1 bell, found " + bells.size());
        helper.assertTrue(rang, "The bell must ring");
        helper.succeed();
    }

    /** The village has a cat for each gate, near the gate. The player cannot tame a village cat with fish. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_cats")
    public static void villageHasCatsAtTheGates(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        List<VillageCat> cats = heart.getCats();
        List<BlockPos> anchors = heart.catAnchors();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD, 64));
        boolean tamed = false;
        for (VillageCat cat : cats) {
            cat.mobInteract(player, InteractionHand.MAIN_HAND);
            tamed |= cat.isTame();
        }
        boolean anchored = cats.stream().allMatch(cat -> anchors.contains(cat.getAnchorPos()));
        int expected = heart.catCount();
        cats.forEach(Entity::discard);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(anchors.size() == 3, "Expected 2 gate anchors and 1 center anchor, found " + anchors.size());
        helper.assertTrue(cats.size() == expected && expected >= 2,
                "Expected " + expected + " cats (2 or more), found " + cats.size());
        helper.assertTrue(anchored, "Each cat must have an anchor of the village");
        helper.assertTrue(!tamed, "The player must not tame a village cat");
        helper.succeed();
    }
}
