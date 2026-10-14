package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the notices. See {@link TestSupport}. */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoticeGameTests {

    private NoticeGameTests() {
    }

    /** The same warning shows at most once in the cooldown. Another kind of warning still shows. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "notice_cooldown")
    public static void warningsHaveACooldown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Component text = Component.literal("test");

        boolean first = Notices.warn(player, "test_kind", text, null);
        boolean second = Notices.warn(player, "test_kind", text, null);
        boolean other = Notices.warn(player, "test_other_kind", text, text);

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(first, "The first warning must show");
        helper.assertTrue(!second, "The same warning must not show again in the cooldown");
        helper.assertTrue(other, "Another kind of warning must show");
        helper.succeed();
    }

    /** The two texts of a notice make one line for the chat. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "notice_text")
    public static void noticeTextJoinsTheTwoTexts(GameTestHelper helper) {
        String joined = Notices.asText(Component.literal("Title"), Component.literal("Details")).getString();
        String alone = Notices.asText(Component.literal("Title"), null).getString();
        helper.assertTrue(joined.equals("Title Details"), "Expected the two texts: " + joined);
        helper.assertTrue(alone.equals("Title"), "Expected the title alone: " + alone);
        helper.succeed();
    }
}
