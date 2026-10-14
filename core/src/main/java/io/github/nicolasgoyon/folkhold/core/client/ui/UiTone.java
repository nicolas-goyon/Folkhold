package io.github.nicolasgoyon.folkhold.core.client.ui;

/**
 * The meaning of a badge, a status line or a toast, and its colors. A tone never gives the meaning alone: the
 * badge also has a word, and if possible a glyph.
 */
public enum UiTone {
    /** A level, a tier: gold border. */
    GOLD(UiTheme.WOOD_RAISED, UiTheme.GILT, UiTheme.TEXT, UiTheme.GILT),
    /** A good state: calm, active, completed. */
    GOOD(0xFF2F4A2A, 0xFF6E9C4A, UiTheme.TEXT, 0xFF2F8F4E),
    /** A warning: alert, stopped caravan. */
    WARN(0xFF5A4114, 0xFFE0A635, UiTheme.TEXT, 0xFFE0A635),
    /** A danger: assault, failed. */
    BAD(0xFF5A1E18, 0xFFE05A47, UiTheme.TEXT, UiTheme.WAX),
    /** An information: accepted. */
    INFO(0xFF2C3E52, 0xFF5F95C4, UiTheme.TEXT, 0xFF5F95C4),
    /** A state that does not count now: ruins. */
    MUTED(0xFF2A231B, 0xFF6E6455, UiTheme.TEXT_MUTED, 0xFF6E6455);

    private final int background;
    private final int border;
    private final int text;
    private final int mark;

    UiTone(int background, int border, int text, int mark) {
        this.background = background;
        this.border = border;
        this.text = text;
        this.mark = mark;
    }

    /** The background of a badge. */
    public int background() {
        return background;
    }

    /** The border of a badge. */
    public int border() {
        return border;
    }

    /** The text of a badge. */
    public int text() {
        return text;
    }

    /** The color of the square mark of a toast or a status line. */
    public int mark() {
        return mark;
    }
}
