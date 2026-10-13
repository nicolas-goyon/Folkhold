package io.github.nicolasgoyon.folkhold.core.client.ui;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * One row of a key-value list: a label on the left, a value on the right, and an optional meter below. A heading row
 * has only a label: the title of a group of rows.
 *
 * @param label   the label
 * @param value   the value (empty for a heading)
 * @param meter   the meter below the text, or null
 * @param heading true for a heading row
 */
public record UiRow(Component label, Component value, @Nullable UiMeter meter, boolean heading) {

    /** A row with a label and a value. */
    public static UiRow of(Component label, Component value) {
        return new UiRow(label, value, null, false);
    }

    /** A row with a label, a value and a meter. */
    public static UiRow of(Component label, Component value, @Nullable UiMeter meter) {
        return new UiRow(label, value, meter, false);
    }

    /** A heading row. */
    public static UiRow heading(Component title) {
        return new UiRow(title, Component.empty(), null, true);
    }
}
