package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.QuestLogPayload;

/**
 * The last quest log that the server sent to this client, and the game time when it came. The quest HUD shows it.
 *
 * <p>The times of the log go down with the game time of the client level, so they stop while the game is paused.
 *
 * <p>This class does not use client classes: the network registration refers to it on both sides.
 */
public final class ClientQuestLog {
    private static final long TICKS_PER_SECOND = 20L;

    private static QuestLogPayload log = QuestLogPayload.EMPTY;
    private static long receivedAt;

    private ClientQuestLog() {
    }

    public static QuestLogPayload get() {
        return log;
    }

    /**
     * Keeps a new log.
     *
     * @param payload  the log
     * @param gameTime the game time of the client level when the log came
     */
    public static void set(QuestLogPayload payload, long gameTime) {
        log = payload;
        receivedAt = gameTime;
    }

    /**
     * The seconds that remain from a time of the log.
     *
     * @param seconds  a time of the log, in seconds
     * @param gameTime the game time of the client level now
     * @return the seconds now, at least 0
     */
    public static int secondsNow(int seconds, long gameTime) {
        long elapsed = Math.max(0L, gameTime - receivedAt) / TICKS_PER_SECOND;
        return (int) Math.max(0, seconds - elapsed);
    }

    /** True if an entity is the target of a quest of the log: the merchant of an escort. */
    public static boolean isTarget(int entityId) {
        if (entityId == QuestLogPayload.NO_ENTITY) {
            return false;
        }
        for (QuestLogPayload.Entry entry : log.quests()) {
            if (entry.targetEntity() == entityId) {
                return true;
            }
        }
        return false;
    }

    /** Clears the log, for example when the player leaves the server. */
    public static void clear() {
        log = QuestLogPayload.EMPTY;
    }
}
