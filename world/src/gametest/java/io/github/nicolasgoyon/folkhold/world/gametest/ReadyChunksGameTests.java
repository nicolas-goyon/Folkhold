package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the chunks that are ready (see {@link ReadyChunks}). See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReadyChunksGameTests {
    /** The distance from the test to the chunk of the test, in blocks: this chunk is not loaded. */
    private static final int FAR = 20_000;

    private ReadyChunksGameTests() {
    }

    /**
     * The layout scan around a position in a chunk that is not ready does not occur, and it does not load the
     * chunk. When all the chunks of the scan area are ready, the scan occurs.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "ready_chunks")
    public static void scanOccursOnlyWhenTheChunksAreReady(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos heart = helper.absolutePos(BlockPos.ZERO).offset(FAR, 0, FAR);
        ChunkPos chunk = new ChunkPos(heart);
        boolean readyBefore = ReadyChunks.isReady(level, heart);
        VillageLayout layout = new VillageLayout();
        boolean scanned = layout.scan(level, heart);
        boolean readyAfter = ReadyChunks.isReady(level, heart);

        helper.assertFalse(readyBefore, "A chunk far from the test must not be ready");
        helper.assertFalse(scanned || layout.isScanned(), "The scan must not occur while a chunk is not ready");
        helper.assertFalse(readyAfter, "The scan must not load the chunk");
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillageLayout.isScanAreaReady(level, heart),
                        "The chunks of the scan area must become ready"))
                .thenExecute(() -> {
                    boolean done = layout.scan(level, heart);
                    level.setChunkForced(chunk.x, chunk.z, false);
                    helper.assertTrue(done && layout.isScanned(), "The scan must occur when the chunks are ready");
                })
                .thenSucceed();
    }
}
