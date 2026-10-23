package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.VillageCat;
import io.github.nicolasgoyon.folkhold.world.faction.Culture;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    /** The heart finds the posts, gates and braziers, and puts the archers on the posts. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_structure")
    public static void heartFindsLayoutAndPostsArchers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
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
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);

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
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
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

    /**
     * At night, the gates close, the braziers burn and the guards watch the gates. By day, the opposite. The test
     * removes the villagers first: in a village with no handymen, the heart puts out the braziers at the start of the
     * day.
     */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_night")
    public static void villageAppliesDayAndNight(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        VillageLayout layout = heart.getLayout();
        List<GuardEntity> guards = heart.getUnits().stream()
                .filter(unit -> unit instanceof GuardEntity && !(unit instanceof CaptainEntity))
                .map(GuardEntity.class::cast).toList();
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);

        heart.applyDayNight(true);
        boolean gatesClosed = layout.gates().stream()
                .allMatch(pos -> !helper.getLevel().getBlockState(pos).getValue(FenceGateBlock.OPEN));
        boolean braziersLit = layout.braziers().stream()
                .map(pos -> helper.getLevel().getBlockState(pos))
                .allMatch(state -> state.is(ModBlocks.BRAZIER.get()) && state.getValue(BrazierBlock.LIT));
        boolean guardsOnWatch = !guards.isEmpty() && guards.stream().allMatch(guard -> guard.getNightPos() != null);

        heart.applyDayNight(false);
        boolean gatesOpen = layout.gates().stream()
                .allMatch(pos -> helper.getLevel().getBlockState(pos).getValue(FenceGateBlock.OPEN));
        boolean braziersOut = layout.braziers().stream()
                .map(pos -> helper.getLevel().getBlockState(pos))
                .allMatch(state -> state.is(ModBlocks.BRAZIER.get()) && !state.getValue(BrazierBlock.LIT));
        boolean guardsOffWatch = guards.stream().allMatch(guard -> guard.getNightPos() == null);
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);

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
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
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
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        helper.assertTrue(archers.size() >= 2, "The garrison must have 2 archers or more");
        helper.assertTrue(distinct == archers.size(), "Each archer must have its own tower");
        helper.assertTrue(valid, "Each archer must have a valid tower");
        helper.succeed();
    }

    /** In the calm rotation, each archer moves to another free tower. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_archer_rotation")
    public static void calmRotationMovesTheArchers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        // At the level 1, the garrison has 2 archers for the 4 towers: each archer has a free tower to go to.
        MinecraftServer server = helper.getLevel().getServer();
        heart.ensureSettlement();
        SettlementLevels.setProgress(server, Settlements.of(server, heart).orElseThrow(), 1, 0);
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
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        helper.assertTrue(moved, "Each archer must move to another tower: " + before + " -> " + after);
        helper.assertTrue(distinct == archers.size(), "Two archers must not share a tower");
        helper.succeed();
    }

    /** When the village goes to the alert state, the alarm bell rings. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_bell")
    public static void alertRingsTheBell(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
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
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
        List<VillageCat> cats = heart.getCats();
        List<BlockPos> anchors = heart.catAnchors();
        ServerPlayer player = TestSupport.mockPlayer(helper);
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
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(anchors.size() == 3, "Expected 2 gate anchors and 1 center anchor, found " + anchors.size());
        helper.assertTrue(cats.size() == expected && expected >= 2,
                "Expected " + expected + " cats (2 or more), found " + cats.size());
        helper.assertTrue(anchored, "Each cat must have an anchor of the village");
        helper.assertTrue(!tamed, "The player must not tame a village cat");
        helper.succeed();
    }

    /**
     * An archer with a new tower walks to it: down the stair of its tower, across the compound and up the stair of
     * the new tower. The archer does not move directly to the post. The heart does not rotate the archers during
     * the test: a second tower on the way would make the travel longer than the limit of the direct move.
     */
    @GameTest(template = "village/center", timeoutTicks = 1200, batch = "village_archer_walk")
    public static void archerWalksToANewTower(GameTestHelper helper) {
        int rotationSeconds = WorldConfig.ARCHER_ROTATION_SECONDS.get();
        WorldConfig.ARCHER_ROTATION_SECONDS.set(0);
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        List<ArcherEntity> archers = heart.getUnits().stream()
                .filter(ArcherEntity.class::isInstance).map(ArcherEntity.class::cast).toList();
        helper.assertTrue(!archers.isEmpty(), "The garrison must have an archer");
        ArcherEntity archer = archers.get(0);
        heart.getUnits().stream().filter(unit -> unit != archer).forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        BlockPos start = archer.getPostPos();
        // The rotation occurs when the archer stands on its post (after the first ticks).
        helper.runAfterDelay(10, () -> {
            heart.rotateArchers(helper.getLevel().random, 1.0);
            if (archer.getPostPos() == null || archer.getPostPos().equals(start)) {
                helper.fail("The archer must get another tower");
            }
        });

        int groundY = heart.getBlockPos().getY();
        double[] last = {archer.getX(), archer.getY(), archer.getZ()};
        // seen[0]: the archer was on the ground of the compound. seen[1]: the archer moved more than 2 blocks in 1 tick.
        boolean[] seen = {false, false};
        // The last states of the archer, for the message of a failure.
        ArrayDeque<String> history = new ArrayDeque<>();
        String[] jump = {""};
        helper.onEachTick(() -> {
            double dx = archer.getX() - last[0];
            double dy = archer.getY() - last[1];
            double dz = archer.getZ() - last[2];
            if (dx * dx + dy * dy + dz * dz > 4.0 && !seen[1]) {
                seen[1] = true;
                jump[0] = String.format(Locale.ROOT, " (%.1f %.1f %.1f -> %s, post %s, start %s; %s)",
                        last[0], last[1], last[2], archer.blockPosition().toShortString(),
                        archer.getPostPos() == null ? "none" : archer.getPostPos().toShortString(),
                        start.toShortString(), String.join(" | ", history));
            }
            last[0] = archer.getX();
            last[1] = archer.getY();
            last[2] = archer.getZ();
            if (archer.getBlockY() <= groundY + 1) {
                seen[0] = true;
            }
            Path path = archer.getNavigation().getPath();
            history.addLast(archer.blockPosition().toShortString() + " ground=" + archer.onGround() + " path="
                    + (path == null ? "null" : path.getEndNode() + "/" + path.canReach() + "/" + path.isDone())
                    + " target=" + archer.getTarget());
            if (history.size() > 6) {
                history.removeFirst();
            }
        });
        helper.succeedWhen(() -> {
            if (seen[1]) {
                WorldConfig.ARCHER_ROTATION_SECONDS.set(rotationSeconds);
                helper.fail("The archer must walk, not move directly to the post" + jump[0]);
            }
            BlockPos post = archer.getPostPos();
            helper.assertTrue(post != null && !post.equals(start), "The archer must have another tower");
            helper.assertTrue(archer.distanceToSqr(Vec3.atBottomCenterOf(post)) <= 4.0,
                    "The archer must arrive at the new post " + post.toShortString() + " ("
                            + String.join(" | ", history) + ")");
            helper.assertTrue(seen[0], "The archer must walk on the ground of the compound");
            WorldConfig.ARCHER_ROTATION_SECONDS.set(rotationSeconds);
            archer.discard();
        });
    }

    /** The kinds of the building signs in the test area, one entry for each sign. */
    private static List<BuildingSignKind> signsIn(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        List<BuildingSignKind> kinds = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            for (BuildingSignKind kind : BuildingSignKind.values()) {
                if (state.is(ModBlocks.buildingSign(kind))) {
                    kinds.add(kind);
                }
            }
        }
        return kinds;
    }

    /** The center piece has the village hall sign near the heart. */
    @GameTest(template = "village/center", timeoutTicks = 20, batch = "village_hall_sign")
    public static void centerHasTheHallSign(GameTestHelper helper) {
        List<BuildingSignKind> signs = signsIn(helper);
        helper.assertTrue(signs.equals(List.of(BuildingSignKind.HALL)), "Expected 1 hall sign, found " + signs);
        helper.succeed();
    }

    /** A house has the sign of its kind on its porch. */
    @GameTest(template = "village/house_cottage", timeoutTicks = 20, batch = "village_house_sign")
    public static void houseHasItsSign(GameTestHelper helper) {
        List<BuildingSignKind> signs = signsIn(helper);
        helper.assertTrue(signs.equals(List.of(BuildingSignKind.HOUSE)), "Expected 1 house sign, found " + signs);
        helper.succeed();
    }

    /** The bank has its sign, the 4 bank chests, and its walls are bank walls. */
    @GameTest(template = "village/bank", timeoutTicks = 20, batch = "village_bank_building")
    public static void bankHasItsSignAndItsVault(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        List<BlockPos> chests = new ArrayList<>();
        int walls = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(ModBlocks.BANK_CHEST.get())) {
                chests.add(pos.immutable());
            } else if (state.is(ModBlocks.BANK_WALL.get())) {
                walls++;
            }
        }
        List<BuildingSignKind> signs = signsIn(helper);
        // The bank chests stay in the world after the test. A village heart of a later test near this place
        // would count them as chests of its bank.
        chests.forEach(pos -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        helper.assertTrue(signs.equals(List.of(BuildingSignKind.BANK)), "Expected 1 bank sign, found " + signs);
        helper.assertTrue(chests.size() == 4, "Expected 4 bank chests, found " + chests.size());
        helper.assertTrue(walls > 100, "The walls and the roof must be bank walls, found " + walls);
        helper.succeed();
    }
}
