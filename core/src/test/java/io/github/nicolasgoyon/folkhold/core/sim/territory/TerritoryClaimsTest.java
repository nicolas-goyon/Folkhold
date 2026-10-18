package io.github.nicolasgoyon.folkhold.core.sim.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerritoryClaimsTest {
    /** The 8 directions around a position, as unit vectors. */
    private static final double[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7071, 0.7071},
        {-0.7071, 0.7071}, {0.7071, -0.7071}, {-0.7071, -0.7071}};

    private static Optional<String> ownerAtDistance(TerritoryClaims claims, Claimant claimant, double[] direction,
            double distance) {
        return claims.ownerAt((int) Math.floor(claimant.x() + 0.5 + direction[0] * distance),
                (int) Math.floor(claimant.z() + 0.5 + direction[1] * distance));
    }

    /** The territory is centered on the settlement: the same reach in the 8 directions, also off a chunk center. */
    @Test
    void discIsCenteredOnTheSettlement() {
        Claimant claimant = new Claimant("a", 100, -37, 1, 9);
        TerritoryClaims claims = TerritoryClaims.compute(List.of(claimant));

        assertEquals(Optional.of("a"), claims.ownerAt(100, -37));
        for (double[] direction : DIRECTIONS) {
            assertEquals(Optional.of("a"), ownerAtDistance(claims, claimant, direction, 9 * 16 - 12));
            assertEquals(Optional.empty(), ownerAtDistance(claims, claimant, direction, 9 * 16 + 12));
        }
    }

    /** The number of chunks is near the area of the disc: the territory is near a circle, not a square. */
    @Test
    void discIsNearACircle() {
        for (int radius : new int[] {9, 20, 33}) {
            for (int[] position : new int[][] {{8, 8}, {0, 0}, {-3, 13}}) {
                TerritoryClaims claims = TerritoryClaims.compute(
                        List.of(new Claimant("a", position[0], position[1], 1, radius)));
                double area = Math.PI * radius * radius;
                int count = claims.chunkCount("a");
                assertTrue(Math.abs(count - area) < 0.1 * area, "Radius " + radius + ": " + count + " chunks");
            }
        }
    }

    /** A chunk belongs to one settlement or to none: all the blocks of a chunk have the same owner. */
    @Test
    void ownerAtUsesTheChunkOfTheBlock() {
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", -20, -20, 1, 1)));

        assertEquals(Optional.of("a"), claims.ownerAt(-20, -20));
        assertEquals(claims.ownerOfChunk(-1, -1), claims.ownerAt(-1, -1));
        assertEquals(claims.ownerOfChunk(-2, -2), claims.ownerAt(-32, -17));
        assertEquals(claims.ownerOfChunk(0, 0), claims.ownerAt(15, 0));
    }

    /** With the minimum radius, the territory contains all the blocks at 1.5 times the village radius or nearer. */
    @Test
    void minimumRadiusContainsTheVillageAndItsMargin() {
        assertEquals(6, TerritoryClaims.minimumRadius(48));
        for (int villageRadius : new int[] {8, 30, 48, 100}) {
            Claimant claimant = new Claimant("a", 7, -9, 1, TerritoryClaims.minimumRadius(villageRadius));
            TerritoryClaims claims = TerritoryClaims.compute(List.of(claimant));
            double reach = TerritoryClaims.VILLAGE_MARGIN * villageRadius;
            int square = (int) Math.ceil(reach) + 1;
            for (int dx = -square; dx <= square; dx++) {
                for (int dz = -square; dz <= square; dz++) {
                    if (Math.hypot(dx, dz) <= reach) {
                        assertEquals(Optional.of("a"), claims.ownerAt(claimant.x() + dx, claimant.z() + dz),
                                "Village radius " + villageRadius + ", block " + dx + " " + dz);
                    }
                }
            }
        }
    }

    /** A chunk that two settlements claim belongs to the nearest settlement. */
    @Test
    void sharedChunkGoesToTheNearestSettlement() {
        // The center of chunk 6 is at x = 104, at the same distance from the two settlements.
        Claimant a = new Claimant("a", 50, 7, 1, 6);
        Claimant b = new Claimant("b", 157, 7, 1, 6);
        TerritoryClaims claims = TerritoryClaims.compute(List.of(a, b));

        assertEquals(Optional.of("a"), claims.ownerOfChunk(5, 0));
        assertEquals(Optional.of("b"), claims.ownerOfChunk(7, 0));
        assertEquals(Optional.of("a"), claims.ownerOfChunk(6, 0), "At equal distance and level, the lower id wins");
        assertTrue(claims.chunkCount("a") > claims.chunkCount("b"));
    }

    /** At equal distance, the higher level gets the chunk, then the lower id. */
    @Test
    void equalDistanceGivesTheChunkToTheHigherLevel() {
        Claimant low = new Claimant("a", 50, 7, 2, 6);
        Claimant high = new Claimant("b", 157, 7, 9, 6);

        assertEquals(Optional.of("b"), TerritoryClaims.compute(List.of(low, high)).ownerOfChunk(6, 0));
        assertEquals(Optional.of("a"), TerritoryClaims.compute(List.of(new Claimant("a", 50, 7, 9, 6), high))
                .ownerOfChunk(6, 0));
    }

    /** The index of the settlements by area finds a disc that crosses the border of an area. */
    @Test
    void discCrossesTheBorderOfAnArea() {
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", 1020, -1030, 1, 9)));

        assertEquals(Optional.of("a"), claims.ownerAt(1100, -1030));
        assertEquals(Optional.of("a"), claims.ownerAt(950, -1030));
        assertEquals(Optional.of("a"), claims.ownerAt(1020, -960));
        assertEquals(Optional.of("a"), claims.ownerAt(1020, -1100));
    }

    /** A settlement with a radius of 0 claims nothing. */
    @Test
    void zeroRadiusClaimsNothing() {
        TerritoryClaims claims = TerritoryClaims.compute(List.of(new Claimant("a", 0, 0, 1, 0)));

        assertEquals(Optional.empty(), claims.ownerAt(0, 0));
        assertEquals(0, claims.chunkCount("a"));
        assertEquals(0, TerritoryClaims.EMPTY.chunkCount("a"));
    }

    /** The same settlements give the same claims. */
    @Test
    void sameInputGivesTheSameClaims() {
        List<Claimant> claimants = List.of(new Claimant("a", 100, -40, 5, 13), new Claimant("b", 300, 200, 9, 20),
                new Claimant("c", -200, 50, 14, 27));
        TerritoryClaims first = TerritoryClaims.compute(claimants);
        TerritoryClaims second = TerritoryClaims.compute(List.of(claimants.get(2), claimants.get(0),
                claimants.get(1)));
        for (int chunkX = -40; chunkX <= 40; chunkX++) {
            for (int chunkZ = -40; chunkZ <= 40; chunkZ++) {
                assertEquals(first.ownerOfChunk(chunkX, chunkZ), second.ownerOfChunk(chunkX, chunkZ));
            }
        }
    }
}
