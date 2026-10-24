package io.github.nicolasgoyon.folkhold.core.sim.construction;

/**
 * The position of a block in a construction.
 *
 * @param x block X
 * @param y block Y
 * @param z block Z
 */
public record BuildPos(int x, int y, int z) {

    /** The position next to this position, on a side. */
    public BuildPos offset(Side side) {
        return offset(side.dx(), side.dy(), side.dz());
    }

    public BuildPos offset(int dx, int dy, int dz) {
        return new BuildPos(x + dx, y + dy, z + dz);
    }

    public BuildPos above() {
        return offset(Side.UP);
    }

    public BuildPos below() {
        return offset(Side.DOWN);
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
