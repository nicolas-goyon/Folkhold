package io.github.nicolasgoyon.folkhold.core.sim.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerritoryClaimsTest {
    private static final int CELL = TerritoryClaims.CELL_BLOCKS;

    @Test
    void cellOfNegativeBlocksUsesFloorDivision() {
        assertEquals(new CellPos(-1, 0), CellPos.ofBlock(-1, 0));
        assertEquals(new CellPos(0, -1), CellPos.ofBlock(CELL - 1, -CELL));
        assertEquals(new CellPos(1, -2), CellPos.ofBlock(CELL, -CELL - 1));
    }

    @Test
    void hamletClaimsOnlyItsCell() {
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", 10, 10, 1, 1)));

        assertEquals(Optional.of("a"), claims.ownerAt(200, 5));
        assertEquals(Optional.empty(), claims.ownerAt(CELL + 1, 10));
        assertEquals(1, claims.cellCount("a"));
    }

    @Test
    void cityInTheCenterOfACellClaimsTheThreeByThreeSquare() {
        int center = CELL / 2;
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", center, center, 13, 9)));

        assertEquals(9, claims.cellCount("a"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertEquals(Optional.of("a"), claims.owner(new CellPos(dx, dz)));
            }
        }
    }

    @Test
    void settlementNearACellEdgeClaimsTheCellsOnThatSide() {
        // The settlement is near the east edge of cell 0. Its second cell is cell 1, not cell -1.
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", CELL - 10, CELL / 2, 5, 2)));

        assertEquals(Optional.of("a"), claims.owner(new CellPos(1, 0)));
        assertEquals(Optional.empty(), claims.owner(new CellPos(-1, 0)));
    }

    @Test
    void contestedCellGoesToTheNearestSettlement() {
        int z = CELL / 2;
        // Cell 1 is between the two settlements, and both claim it. "b" is nearer to its center.
        Claimant a = new Claimant("a", CELL - 20, z, 20, 3);
        Claimant b = new Claimant("b", 2 * CELL + 10, z, 1, 3);

        TerritoryClaims claims = TerritoryClaims.compute(List.of(a, b));

        assertEquals(Optional.of("b"), claims.owner(new CellPos(1, 0)));
        assertEquals(Optional.of("a"), claims.owner(new CellPos(0, 0)));
        assertEquals(Optional.of("b"), claims.owner(new CellPos(2, 0)));
    }

    @Test
    void equalDistanceGivesTheCellToTheHigherLevel() {
        int z = CELL / 2;
        // Both settlements are at 10 blocks from cell 1, and both claim it.
        Claimant low = new Claimant("a", CELL - 10, z, 2, 2);
        Claimant high = new Claimant("b", 2 * CELL + 10, z, 9, 2);

        TerritoryClaims claims = TerritoryClaims.compute(List.of(low, high));

        assertEquals(Optional.of("b"), claims.owner(new CellPos(1, 0)));
    }

    @Test
    void cellOfTheSettlementIsAlwaysFirst() {
        Claimant claimant = new Claimant("a", 3, 3, 1, 5);
        List<CellPos> cells = TerritoryClaims.nearestCells(claimant);

        assertEquals(new CellPos(0, 0), cells.get(0));
        assertEquals(5, new HashSet<>(cells).size());
        assertTrue(TerritoryClaims.nearestCells(new Claimant("b", 0, 0, 1, 0)).isEmpty());
    }

    @Test
    void sameInputGivesTheSameClaims() {
        List<Claimant> claimants = List.of(new Claimant("a", 100, -40, 5, 4), new Claimant("b", 600, 300, 9, 6),
                new Claimant("c", -900, 50, 14, 9));
        assertEquals(TerritoryClaims.compute(claimants).asMap(), TerritoryClaims.compute(claimants).asMap());
    }
}
