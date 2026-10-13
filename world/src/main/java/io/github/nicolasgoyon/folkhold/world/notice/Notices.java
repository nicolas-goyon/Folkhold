package io.github.nicolasgoyon.folkhold.world.notice;

import io.github.nicolasgoyon.folkhold.world.network.NoticePayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Notices: the important events for a player, in a toast at the top right of the screen in place of a chat
 * message. For example the warning of a guard, the result of a quest, or a pillage.
 *
 * <ul>
 *   <li>A client with no channel for the notices gets a chat message with the two texts.</li>
 *   <li>A warning that the player can cause again and again (a click on a bank chest) has a cooldown: the same
 *       warning shows at most once in {@link #WARNING_COOLDOWN_TICKS}.</li>
 * </ul>
 */
public final class Notices {
    /** The time between two warnings of the same kind for a player, in ticks. */
    public static final long WARNING_COOLDOWN_TICKS = 200L;

    /** Game time of the last warning of each kind, by player. Not saved. */
    private static final Map<UUID, Map<String, Long>> LAST_WARNINGS = new HashMap<>();

    /** The tone of a notice: the color of its mark. */
    public enum Tone {
        GOOD, WARN, BAD, INFO;

        /** Lower-case name, for the network. */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Notices() {
    }

    /**
     * Sends a notice to a player.
     *
     * @param player  the player
     * @param title   the main text
     * @param details the second text, or null
     * @param tone    the tone
     */
    public static void send(ServerPlayer player, Component title, @Nullable Component details, Tone tone) {
        if (player.connection.hasChannel(NoticePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new NoticePayload(title, Optional.ofNullable(details),
                    tone.serializedName()));
        } else {
            player.sendSystemMessage(asText(title, details));
        }
    }

    /**
     * Sends a warning to a player, at most once in the cooldown for the same kind.
     *
     * @param player  the player
     * @param kind    the kind of the warning, for the cooldown
     * @param title   the main text
     * @param details the second text, or null
     * @return true if the player got the warning
     */
    public static boolean warn(ServerPlayer player, String kind, Component title, @Nullable Component details) {
        long now = player.serverLevel().getGameTime();
        Map<String, Long> last = LAST_WARNINGS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Long previous = last.get(kind);
        if (previous != null && now - previous < WARNING_COOLDOWN_TICKS && now >= previous) {
            return false;
        }
        last.put(kind, now);
        send(player, title, details, Tone.WARN);
        return true;
    }

    /** The two texts of a notice in one line, for the chat or a status line. */
    public static Component asText(Component title, @Nullable Component details) {
        if (details == null) {
            return title;
        }
        MutableComponent text = title.copy();
        return text.append(" ").append(details);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_WARNINGS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_WARNINGS.clear();
    }
}
