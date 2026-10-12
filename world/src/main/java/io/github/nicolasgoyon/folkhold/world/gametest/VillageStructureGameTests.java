package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.faction.Culture;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village structure. The template is the center piece of the village
 * ({@code folkhold_world:village/center}), without the houses.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageStructureGameTests {
    private static final int TOWERS = 4;
    private static final int GATE_BLOCKS = 6;
    private static final int BRAZIERS = 4;
    /** 4 banners on the north and south walls, 3 banners on the west and east walls. */
    private static final int BANNERS = 14;

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
        units.forEach(Entity::discard);

        helper.assertTrue(layout.posts().size() == TOWERS, "Expected " + TOWERS + " posts, found " + layout.posts().size());
        helper.assertTrue(layout.gates().size() == GATE_BLOCKS,
                "Expected " + GATE_BLOCKS + " gate blocks, found " + layout.gates().size());
        helper.assertTrue(layout.braziers().size() == BRAZIERS,
                "Expected " + BRAZIERS + " braziers, found " + layout.braziers().size());
        helper.assertTrue(archersOnPosts, "Each archer must spawn on a post of the village");
        helper.succeed();
    }

    /** The heart gives the banner color of the faction culture to the banners of the village. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_banners")
    public static void heartPaintsBanners(GameTestHelper helper) {
        VillageHeartBlockEntity heart = findHeart(helper);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.getUnits().forEach(Entity::discard);
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

        helper.assertTrue(gatesClosed, "At night, all gates must be closed");
        helper.assertTrue(braziersLit, "At night, all braziers must burn");
        helper.assertTrue(guardsOnWatch, "At night, each guard must have a night watch position");
        helper.assertTrue(gatesOpen, "By day, all gates must be open");
        helper.assertTrue(braziersOut, "By day, the braziers must not burn");
        helper.assertTrue(guardsOffWatch, "By day, the guards must have no night watch position");
        helper.succeed();
    }
}
