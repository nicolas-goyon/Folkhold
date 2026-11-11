package io.github.nicolasgoyon.folkhold.balance.gametest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Tools that the GameTests of the balance mod share.
 *
 * @see "TestSupport.setConfigForTest in the GameTests of the world module: the same restore of a config value"
 */
final class BalanceTestSupport {
    /** The config values that each running test changed. The GameTests run on the server thread only. */
    private static final Map<GameTestInfo, Set<Object>> CHANGED_SETTINGS = new WeakHashMap<>();

    private BalanceTestSupport() {
    }

    /**
     * Sets a config value for one test. When the test ends (pass, failure or timeout), a listener sets the value that
     * the config had before the first change of the test: the next tests see this value also after a failure. Only
     * the first change of a value in a test adds a listener, so a test can change the same value more than one time.
     * The listeners of a test run in their order: a second listener for the same value would set the value of the
     * first change again.
     */
    static <T> void setConfigForTest(GameTestHelper helper, ModConfigSpec.ConfigValue<T> value, T newValue) {
        if (CHANGED_SETTINGS.computeIfAbsent(helper.testInfo, info -> new HashSet<>()).add(value)) {
            T old = value.get();
            helper.testInfo.addListener(new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) {
                }

                @Override
                public void testPassed(GameTestInfo info, GameTestRunner runner) {
                    restore();
                }

                @Override
                public void testFailed(GameTestInfo info, GameTestRunner runner) {
                    restore();
                }

                @Override
                public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
                }

                private void restore() {
                    value.set(old);
                    CHANGED_SETTINGS.remove(helper.testInfo);
                }
            });
        }
        value.set(newValue);
    }
}
