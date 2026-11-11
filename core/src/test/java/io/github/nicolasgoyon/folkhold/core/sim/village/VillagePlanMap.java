package io.github.nicolasgoyon.folkhold.core.sim.village;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Piece;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;

/**
 * A text map of a village plan, for a review. North is up. One character shows a square of blocks: the character of
 * the first item in this list that has a block in the square.
 *
 * <ul>
 *   <li>{@code P} the plaza, {@code =} a segment, {@code +} a crossroad, {@code s} a side piece, {@code e} a street
 *       end;</li>
 *   <li>{@code W} a plot with a wide home, {@code h} a home plot, {@code o} a spare plot, {@code x} a spare plot with
 *       bad terrain;</li>
 *   <li>{@code .} inside the maximum radius, a space outside it.</li>
 * </ul>
 */
final class VillagePlanMap {
    private VillagePlanMap() {
    }

    static String map(VillagePlan plan, int plazaSize, int maxRadius, int scale) {
        int half = plazaSize / 2;
        Box plaza = new Box(-half, -half, half, half);
        StringBuilder text = new StringBuilder();
        for (int row = -maxRadius; row <= maxRadius; row += scale) {
            for (int column = -maxRadius; column <= maxRadius; column += scale) {
                Box cell = new Box(column, row, column + scale - 1, row + scale - 1);
                text.append(symbol(plan, plaza, cell, maxRadius));
            }
            text.append('\n');
        }
        return text.toString().replaceAll(" +\n", "\n");
    }

    private static char symbol(VillagePlan plan, Box plaza, Box cell, int maxRadius) {
        if (plaza.overlaps(cell)) {
            return 'P';
        }
        for (Piece piece : plan.pieces()) {
            if (piece.box().overlaps(cell)) {
                return switch (piece.kind()) {
                    case SEGMENT -> '=';
                    case CROSS -> '+';
                    case SIDE -> 's';
                    case END -> 'e';
                };
            }
        }
        for (Plot plot : plan.plots()) {
            if (plot.area().overlaps(cell)) {
                return plot.spare() ? plot.good() ? 'o' : 'x' : plot.wide() ? 'W' : 'h';
            }
        }
        double x = cell.minX() + (cell.maxX() - cell.minX()) / 2.0;
        double z = cell.minZ() + (cell.maxZ() - cell.minZ()) / 2.0;
        return x * x + z * z <= (double) maxRadius * maxRadius ? '.' : ' ';
    }
}
