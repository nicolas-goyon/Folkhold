package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the chunks that are ready (see {@link ReadyChunks}). See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReadyChunksGameTests {
    /** The center of the arena: the chunks of its scan area are ready when the test starts. */
    private static final BlockPos CENTER = new BlockPos(8, 1, 8);
    /** The distance from the test to a chunk that is not loaded, in blocks. */
    private static final int FAR = 20_000;

    private ReadyChunksGameTests() {
    }

    /**
     * The layout scan around a position in a chunk that is not ready does not occur, and it does not load the
     * chunk. The scan around the center of the test, where the chunks are ready, occurs.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "ready_chunks")
    public static void scanOccursOnlyWhenTheChunksAreReady(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos near = helper.absolutePos(CENTER);
        BlockPos far = near.offset(FAR, 0, FAR);
        boolean farReadyBefore = ReadyChunks.isReady(level, far);
        VillageLayout farLayout = new VillageLayout();
        boolean farScanned = farLayout.scan(level, far);
        boolean farReadyAfter = ReadyChunks.isReady(level, far);
        VillageLayout nearLayout = new VillageLayout();
        boolean nearScanned = nearLayout.scan(level, near);

        helper.assertFalse(farReadyBefore, "A chunk far from the test must not be ready");
        helper.assertFalse(farScanned || farLayout.isScanned(), "The scan must not occur while a chunk is not ready");
        helper.assertFalse(farReadyAfter, "The scan must not load the chunk");
        helper.assertTrue(VillageLayout.isScanAreaReady(level, near), "The scan area of the test must be ready");
        helper.assertTrue(nearScanned && nearLayout.isScanned(), "The scan must occur when the chunks are ready");
        helper.succeed();
    }
}
