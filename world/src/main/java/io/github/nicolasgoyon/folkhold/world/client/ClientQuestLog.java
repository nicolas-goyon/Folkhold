package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;

/**
 * The last quest log that the server sent to this client. The quest HUD shows it.
 *
 * <p>This class does not use client classes: the network registration refers to it on both sides.
 */
public final class ClientQuestLog {
    private static QuestLogPayload log = QuestLogPayload.EMPTY;

    private ClientQuestLog() {
    }

    public static QuestLogPayload get() {
        return log;
    }

    public static void set(QuestLogPayload payload) {
        log = payload;
    }

    /** Clears the log, for example when the player leaves the server. */
    public static void clear() {
        log = QuestLogPayload.EMPTY;
    }
}
