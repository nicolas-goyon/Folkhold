package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiToast;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTone;
import io.github.nicolasgoyon.folkhold.world.network.NoticePayload;

/** Shows the notices of the server in toasts (see {@link io.github.nicolasgoyon.folkhold.world.notice.Notices}). */
public final class NoticeToasts {
    private NoticeToasts() {
    }

    /** Shows a notice. Called by the network handler on the client thread. */
    public static void show(NoticePayload payload) {
        UiTone tone = switch (payload.tone()) {
            case "good" -> UiTone.GOOD;
            case "warn" -> UiTone.WARN;
            case "bad" -> UiTone.BAD;
            default -> UiTone.INFO;
        };
        UiToast.show(payload.title(), payload.details().orElse(null), tone);
    }
}
