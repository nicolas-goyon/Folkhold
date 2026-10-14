package io.github.nicolasgoyon.folkhold.world.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class NoticesTest {
    @Test
    void textJoinsTheTwoTexts() {
        assertEquals("Title Details", Notices.asText(Component.literal("Title"), Component.literal("Details"))
                .getString());
    }

    @Test
    void textWithNoDetailsIsTheTitle() {
        assertEquals("Title", Notices.asText(Component.literal("Title"), null).getString());
    }
}
