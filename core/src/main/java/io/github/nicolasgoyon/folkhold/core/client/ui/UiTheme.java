package io.github.nicolasgoyon.folkhold.core.client.ui;

/**
 * The visual language of the Folkhold screens: "wood and parchment". A dark wood frame for the navigation, a light
 * parchment for what the player reads.
 *
 * <p>The colors are ARGB values. The sizes are in GUI pixels. The vanilla font has a line of 9 pixels.
 */
public final class UiTheme {
    // ------------------------------------------------------------------ wood (frames, navigation)

    /** The panel of a screen. */
    public static final int WOOD = 0xFF241C13;
    /** The header, the tab bar and the footer. */
    public static final int WOOD_DARK = 0xFF1A140D;
    /** One row of two in a list, and the cards on wood. */
    public static final int WOOD_ROW = 0xFF2B2218;
    /** A button, a chip, a selected row. */
    public static final int WOOD_RAISED = 0xFF3A2D1E;
    /** A button under the mouse. */
    public static final int WOOD_HOVER = 0xFF4A3A26;
    /** The active tab. */
    public static final int WOOD_TAB = 0xFF2F2519;
    /** The border of a button, a line between two parts. */
    public static final int WOOD_EDGE = 0xFF5C4730;
    /** A text field and the track of a progress bar. */
    public static final int WOOD_INPUT = 0xFF120D08;
    /** The background of a screen behind its panel. The game stays visible: the screens do not pause it. */
    public static final int SCREEN_DIM = 0x90100C08;
    /** A panel of the HUD. */
    public static final int HUD = 0x9E0A0805;

    // ------------------------------------------------------------------ gold

    /** The frame of a panel, the primary buttons, the progress. */
    public static final int GILT = 0xFFB8913F;
    /** The border of a primary button on parchment. */
    public static final int GILT_DARK = 0xFF7A5C1E;
    /** The active tab, the focus, a section title. */
    public static final int GOLD = 0xFFFFD37F;

    // ------------------------------------------------------------------ parchment (what the player reads)

    public static final int PARCHMENT = 0xFFE9DCBF;
    /** A card on parchment. */
    public static final int PARCHMENT_LIGHT = 0xFFF4EAD3;
    /** The border of a card on parchment, and the track of a progress bar on parchment. */
    public static final int PARCHMENT_EDGE = 0xFFCDBB94;
    /** A card that is not available. */
    public static final int PARCHMENT_OFF = 0xFFE2D5B6;
    /** The dashed border of a card that is not available. */
    public static final int PARCHMENT_OFF_EDGE = 0xFFB9A67C;
    /** The ink: the text on parchment. */
    public static final int INK = 0xFF2B2117;
    /** The second text on parchment. */
    public static final int INK_MUTED = 0xFF6B5A43;
    /** A text on parchment that is not available. */
    public static final int INK_FAINT = 0xFF7A6A50;
    /** The wax: red on parchment, the pins of the quest board. */
    public static final int WAX = 0xFF8E2F22;

    // ------------------------------------------------------------------ text on wood

    public static final int TEXT = 0xFFF2E6CC;
    /** A label, a second text. */
    public static final int TEXT_MUTED = 0xFFA89F8C;
    /** A text that is not available. */
    public static final int TEXT_FAINT = 0xFF7E7363;
    /** A good value, on wood. */
    public static final int TEXT_GOOD = 0xFF9AD07E;
    /** A bad value or a danger, on wood. */
    public static final int TEXT_BAD = 0xFFE8846F;
    /** A time that is almost over, on wood. */
    public static final int TEXT_URGENT = 0xFFFF9A86;
    /** The text on a primary button. */
    public static final int TEXT_ON_GILT = 0xFF1F1810;

    // ------------------------------------------------------------------ dangerous actions

    public static final int DANGER = 0xFF5A1E18;
    public static final int DANGER_EDGE = 0xFFC0503F;
    public static final int DANGER_TEXT = 0xFFFFE2DA;
    /** A strong danger background (the header of a pillage dialog). */
    public static final int DANGER_STRONG = 0xFF8E2F22;

    // ------------------------------------------------------------------ disabled

    public static final int DISABLED = 0xFF2A231B;
    public static final int DISABLED_EDGE = 0xFF4A3E30;

    // ------------------------------------------------------------------ status line

    public static final int STATUS_GOOD = 0xFF1F2A1A;
    public static final int STATUS_GOOD_EDGE = 0xFF4E7D34;
    public static final int STATUS_GOOD_TEXT = 0xFFC9E8B8;
    public static final int STATUS_BAD = 0xFF3A1814;
    public static final int STATUS_BAD_EDGE = WAX;
    public static final int STATUS_BAD_TEXT = 0xFFFFC8BE;

    // ------------------------------------------------------------------ sizes

    /** The height of one line of text. */
    public static final int LINE = 9;
    /** The space between the edge of a panel and its content. */
    public static final int PADDING = 6;
    /** The space between two elements. */
    public static final int GAP = 3;
    /** The height of a button, a tab or a text field. */
    public static final int CONTROL = 16;
    /** The height of a row of a key-value list. */
    public static final int ROW = 12;
    /** The height of a badge. */
    public static final int BADGE = 12;
    /** The margin between a panel and the edge of the screen. */
    public static final int SCREEN_MARGIN = 6;
    /** The width of a scroll bar. */
    public static final int SCROLL_BAR = 3;

    private UiTheme() {
    }
}
