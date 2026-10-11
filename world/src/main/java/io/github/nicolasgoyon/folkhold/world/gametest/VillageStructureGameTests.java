package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
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
}
