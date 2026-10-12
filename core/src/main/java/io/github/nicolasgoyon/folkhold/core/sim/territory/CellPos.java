package io.github.nicolasgoyon.folkhold.core.sim.territory;

/**
 * Position of a territory cell. A territory cell is a square of {@link TerritoryClaims#CELL_BLOCKS} blocks
 * (16 × 16 chunks).
 *
 * @param x cell coordinate on the X axis
 * @param z cell coordinate on the Z axis
 */
public record CellPos(int x, int z) {

    /** The cell that contains a block position. Uses floor division: block -1 is in cell -1. */
    public static CellPos ofBlock(int blockX, int blockZ) {
        return new CellPos(Math.floorDiv(blockX, TerritoryClaims.CELL_BLOCKS),
                Math.floorDiv(blockZ, TerritoryClaims.CELL_BLOCKS));
    }

    /** Block X of the center of the cell. */
    public double centerX() {
        return (x + 0.5) * TerritoryClaims.CELL_BLOCKS;
    }

    /** Block Z of the center of the cell. */
    public double centerZ() {
        return (z + 0.5) * TerritoryClaims.CELL_BLOCKS;
    }

    /** Square of the distance from a block position to the center of the cell. */
    public double distanceSquared(int blockX, int blockZ) {
        double dx = centerX() - blockX;
        double dz = centerZ() - blockZ;
        return dx * dx + dz * dz;
    }
}
