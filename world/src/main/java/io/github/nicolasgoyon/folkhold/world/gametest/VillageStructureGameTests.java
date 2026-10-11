package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the village structure. The template is the center piece of the village
 * ({@code folkhold_world:village/center}), without the houses.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageStructureGameTests {
    /** Position of the heart in the center piece. */
    private static final BlockPos HEART = new BlockPos(19, 1, 19);
    private static final int TOWERS = 4;
    private static final int GATE_BLOCKS = 6;
    private static final int BRAZIERS = 4;

    private VillageStructureGameTests() {
    }

    /** The heart finds the posts, gates and braziers, and puts the archers on the posts. */
    @GameTest(template = "village/center", timeoutTicks = 100, batch = "village_structure")
    public static void heartFindsLayoutAndPostsArchers(GameTestHelper helper) {
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
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
