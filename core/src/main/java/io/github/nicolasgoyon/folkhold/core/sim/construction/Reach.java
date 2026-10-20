package io.github.nicolasgoyon.folkhold.core.sim.construction;

/**
 * The zone where a builder places and removes blocks: a box around the feet of the builder.
 *
 * @param horizontal the distance in blocks from the feet to the block, on the X axis and on the Z axis
 * @param below      the number of layers of the zone below the feet. 1 is the layer of the floor of the builder.
 * @param above      the number of layers of the zone above the feet. 1 is the layer of the head, 2 is one block
 *                   above the head.
 */
public record Reach(int horizontal, int below, int above) {
    /** The default zone: 2 blocks around the builder, from the layer of its floor to one block above its head. */
    public static final Reach DEFAULT = new Reach(2, 1, 2);

    public Reach {
        if (horizontal < 1) {
            throw new IllegalArgumentException("horizontal must be 1 or more: " + horizontal);
        }
        if (below < 0) {
            throw new IllegalArgumentException("below must be 0 or more: " + below);
        }
        // On a scaffolding column, the block of the step is at the height of the head.
        if (above < 1) {
            throw new IllegalArgumentException("above must be 1 or more: " + above);
        }
    }

    /**
     * True if a builder reaches a block from a position.
     *
     * @param feet  the position of the feet of the builder
     * @param block the position of the block
     * @return true if the block is in the zone of the builder
     */
    public boolean contains(BuildPos feet, BuildPos block) {
        int dy = block.y() - feet.y();
        return Math.abs(block.x() - feet.x()) <= horizontal && Math.abs(block.z() - feet.z()) <= horizontal
                && dy >= -below && dy <= above;
    }
}
