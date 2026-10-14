package io.github.nicolasgoyon.folkhold.core.client.ui;

import net.minecraft.network.chat.Component;

/** The texts that the framework shows. The translations are in the language files of the core mod. */
public final class UiStrings {
    private static final String KEY = "ui.folkhold_core.";

    /** The label of a waiting button. */
    public static final Component WAITING = Component.translatable(KEY + "waiting");
    /** The tooltip of the close button. */
    public static final Component CLOSE = Component.translatable(KEY + "close");
    /** The tooltip of the back button. */
    public static final Component BACK = Component.translatable(KEY + "back");
    /** The cancel button of a dialog, with its key. */
    public static final Component CANCEL_KEY = Component.translatable(KEY + "cancel_key");
    /** The button that applies the value of a field. */
    public static final Component OK = Component.translatable(KEY + "ok");
    /** An empty list or section. */
    public static final Component EMPTY = Component.translatable(KEY + "empty");
    /** The hint of the footer: the keys of a screen. */
    public static final Component HINT_CLOSE = Component.translatable(KEY + "hint.close");
    /** The hint of the footer of a screen with sections. */
    public static final Component HINT_SECTIONS = Component.translatable(KEY + "hint.sections");
    /** The hint under a button that the player must hold. */
    public static final Component HOLD_HINT = Component.translatable(KEY + "hold_hint");
    /** The label above the field of a "type the name" dialog. */
    public static final Component TYPE_NAME = Component.translatable(KEY + "type_name");

    private UiStrings() {
    }
}
