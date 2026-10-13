package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;

/**
 * The last quest log that the server sent to this client, and the time when it came. The quest HUD shows it.
 *
 * <p>This class does not use client classes: the network registration refers to it on both sides.
 */
public final class ClientQuestLog {
    private static QuestLogPayload log = QuestLogPayload.EMPTY;
    private static long receivedAt;

    private ClientQuestLog() {
    }

    public static QuestLogPayload get() {
        return log;
    }

    public static void set(QuestLogPayload payload) {
        log = payload;
        receivedAt = System.currentTimeMillis();
    }

    /**
     * The seconds that remain from a time of the log, counted down from the moment the log came.
     *
     * @param seconds a time of the log, in seconds
     * @return the seconds now, at least 0
     */
    public static int secondsNow(int seconds) {
        long elapsed = (System.currentTimeMillis() - receivedAt) / 1000L;
        return (int) Math.max(0, seconds - elapsed);
    }

    /** Clears the log, for example when the player leaves the server. */
    public static void clear() {
        log = QuestLogPayload.EMPTY;
    }
}
