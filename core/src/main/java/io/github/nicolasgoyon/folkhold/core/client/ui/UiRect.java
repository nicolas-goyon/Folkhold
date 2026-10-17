package io.github.nicolasgoyon.folkhold.core.client.ui;

/**
 * A rectangle in GUI pixels, for the layout of the screens. The methods make new rectangles: a rectangle does not
 * change.
 *
 * <p>The size is never negative: a cut that is larger than the rectangle gives a size of 0.
 *
 * @param x      the left X
 * @param y      the top Y
 * @param width  the width (0 or more)
 * @param height the height (0 or more)
 */
public record UiRect(int x, int y, int width, int height) {

    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    /** The right X (exclusive). */
    public int right() {
        return x + width;
    }

    /** The bottom Y (exclusive). */
    public int bottom() {
        return y + height;
    }

    /** The X of the center. */
    public int centerX() {
        return x + width / 2;
    }

    /** The Y of the center. */
    public int centerY() {
        return y + height / 2;
    }

    /**
     * True if a point is in the rectangle.
     *
     * @param px the X of the point
     * @param py the Y of the point
     * @return true if the point is in the rectangle
     */
    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    /**
     * The rectangle with a margin on all sides.
     *
     * @param margin the margin in pixels (a negative margin makes the rectangle larger)
     * @return the inner rectangle
     */
    public UiRect inset(int margin) {
        return inset(margin, margin);
    }

    /**
     * The rectangle with a horizontal and a vertical margin.
     *
     * @param horizontal the margin on the left and on the right
     * @param vertical   the margin on the top and on the bottom
     * @return the inner rectangle
     */
    public UiRect inset(int horizontal, int vertical) {
        return new UiRect(x + horizontal, y + vertical, width - 2 * horizontal, height - 2 * vertical);
    }

    /** The top part, with a height. The height is clamped to the rectangle. */
    public UiRect top(int size) {
        return new UiRect(x, y, width, Math.min(size, height));
    }

    /** The bottom part, with a height. The height is clamped to the rectangle. */
    public UiRect bottomPart(int size) {
        int clamped = Math.min(size, height);
        return new UiRect(x, bottom() - clamped, width, clamped);
    }

    /** The left part, with a width. The width is clamped to the rectangle. */
    public UiRect left(int size) {
        return new UiRect(x, y, Math.min(size, width), height);
    }

    /** The right part, with a width. The width is clamped to the rectangle. */
    public UiRect rightPart(int size) {
        int clamped = Math.min(size, width);
        return new UiRect(right() - clamped, y, clamped, height);
    }

    /**
     * The rectangle below a top part and a gap.
     *
     * @param size the height of the top part
     * @param gap  the gap after the top part
     * @return the rest of the rectangle
     */
    public UiRect belowTop(int size, int gap) {
        int cut = Math.min(size + gap, height);
        return new UiRect(x, y + cut, width, height - cut);
    }

    /**
     * The rectangle on the right of a left part and a gap.
     *
     * @param size the width of the left part
     * @param gap  the gap after the left part
     * @return the rest of the rectangle
     */
    public UiRect afterLeft(int size, int gap) {
        int cut = Math.min(size + gap, width);
        return new UiRect(x + cut, y, width - cut, height);
    }

    /**
     * The rectangle on the left of a right part and a gap.
     *
     * @param size the width of the right part
     * @param gap  the gap before the right part
     * @return the rest of the rectangle
     */
    public UiRect beforeRight(int size, int gap) {
        return new UiRect(x, y, width - Math.min(size + gap, width), height);
    }

    /**
     * One column of a grid of equal columns. The last column gets the pixels that the division leaves.
     *
     * @param index the index of the column, from 0
     * @param count the number of columns (1 or more)
     * @param gap   the gap between two columns
     * @return the column
     */
    public UiRect column(int index, int count, int gap) {
        int cell = (width - (count - 1) * gap) / count;
        int left = x + index * (cell + gap);
        int cellWidth = index == count - 1 ? right() - left : cell;
        return new UiRect(left, y, cellWidth, height);
    }

    /**
     * One row of a grid of equal rows. The last row gets the pixels that the division leaves.
     *
     * @param index the index of the row, from 0
     * @param count the number of rows (1 or more)
     * @param gap   the gap between two rows
     * @return the row
     */
    public UiRect row(int index, int count, int gap) {
        int cell = (height - (count - 1) * gap) / count;
        int top = y + index * (cell + gap);
        int cellHeight = index == count - 1 ? bottom() - top : cell;
        return new UiRect(x, top, width, cellHeight);
    }

    /**
     * A rectangle with a size, at the center of this rectangle. The size is clamped to this rectangle.
     *
     * @param w the width
     * @param h the height
     * @return the centered rectangle
     */
    public UiRect centered(int w, int h) {
        int cw = Math.min(w, width);
        int ch = Math.min(h, height);
        return new UiRect(x + (width - cw) / 2, y + (height - ch) / 2, cw, ch);
    }

    /**
     * A panel at the center of a screen: the preferred size, clamped to the screen less a margin on each side.
     *
     * @param screenWidth     the width of the screen
     * @param screenHeight    the height of the screen
     * @param preferredWidth  the preferred width of the panel
     * @param preferredHeight the preferred height of the panel
     * @param margin          the margin on each side of the screen
     * @return the panel, at the center of the screen
     */
    public static UiRect panel(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight,
            int margin) {
        int w = Math.min(preferredWidth, screenWidth - 2 * margin);
        int h = Math.min(preferredHeight, screenHeight - 2 * margin);
        return new UiRect((screenWidth - w) / 2, (screenHeight - h) / 2, w, h);
    }
}
