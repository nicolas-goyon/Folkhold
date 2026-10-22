package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The route of the raiders ({@link RaidMarch}). The village of the tests has a heart at (0, 64, 0) and a palisade 19
 * blocks from the heart, with a north gate and a south gate of 3 fence gates.
 */
class RaidMarchTest {
    private static final BlockPos HEART = new BlockPos(0, 64, 0);
    private static final VillageExits.Approach NORTH = approach(-19, -1);
    private static final VillageExits.Approach SOUTH = approach(19, 1);
    private static final List<VillageExits.Approach> APPROACHES = List.of(NORTH, SOUTH);

    /** The approach of a gate at a Z position, with a direction (+1 or -1) out of the village. */
    private static VillageExits.Approach approach(int gateZ, int out) {
        BlockPos point = new BlockPos(0, 64, gateZ + out * VillageExits.APPROACH_DISTANCE);
        return new VillageExits.Approach(point, List.of(new BlockPos(-1, 64, gateZ), new BlockPos(0, 64, gateZ),
                new BlockPos(1, 64, gateZ)));
    }

    private static boolean open(VillageExits.Approach approach) {
        return true;
    }

    private static boolean closed(VillageExits.Approach approach) {
        return false;
    }

    private static RaidMarch.Leg leg(int x, int z, Predicate<VillageExits.Approach> isOpen,
            @Nullable BlockPos entering) {
        return RaidMarch.next(HEART, APPROACHES, isOpen, new BlockPos(x, 64, z), entering);
    }

    @Test
    void outsideTheRaiderWalksToTheNearestApproach() {
        RaidMarch.Leg north = leg(5, -45, RaidMarchTest::open, null);
        RaidMarch.Leg south = leg(-3, 40, RaidMarchTest::open, null);

        assertEquals(Optional.of(NORTH.point()), north.target());
        assertNull(north.entering());
        assertEquals(Optional.of(SOUTH.point()), south.target());
    }

    @Test
    void atAClosedGateTheRaiderWaits() {
        RaidMarch.Leg leg = leg(1, -26, RaidMarchTest::closed, null);

        assertEquals(Optional.empty(), leg.target());
        assertNull(leg.entering());
    }

    @Test
    void atAnOpenGateTheRaiderGoesThroughTheGate() {
        RaidMarch.Leg leg = leg(1, -26, RaidMarchTest::open, null);

        assertEquals(Optional.of(HEART), leg.target());
        assertEquals(NORTH.point(), leg.entering());
    }

    /**
     * Between the approach and the gate, a raider on its way through an open gate keeps its way to the heart. A
     * raider with no way walks to the approach first.
     */
    @Test
    void theRaiderKeepsItsWayThroughAnOpenGate() {
        RaidMarch.Leg entering = leg(0, -21, RaidMarchTest::open, NORTH.point());
        RaidMarch.Leg other = leg(0, -21, RaidMarchTest::open, null);

        assertEquals(Optional.of(HEART), entering.target());
        assertEquals(NORTH.point(), entering.entering());
        assertEquals(Optional.of(NORTH.point()), other.target());
    }

    @Test
    void whenTheGateClosesTheRaiderGoesBackToTheApproach() {
        RaidMarch.Leg leg = leg(0, -21, RaidMarchTest::closed, NORTH.point());

        assertEquals(Optional.of(NORTH.point()), leg.target());
        assertNull(leg.entering());
    }

    /** A corner of the palisade is far from the heart, but inside the square of the palisade. */
    @Test
    void insideThePalisadeTheRaiderWalksToTheHeart() {
        RaidMarch.Leg corner = leg(17, 17, RaidMarchTest::closed, null);
        RaidMarch.Leg gate = leg(0, -18, RaidMarchTest::closed, NORTH.point());

        assertEquals(Optional.of(HEART), corner.target());
        assertNull(corner.entering());
        assertEquals(Optional.of(HEART), gate.target());
        assertNull(gate.entering());
    }

    @Test
    void withNoKnownGatesTheRaiderWalksToTheHeart() {
        RaidMarch.Leg leg = RaidMarch.next(HEART, List.of(), RaidMarchTest::closed, new BlockPos(30, 64, 30), null);

        assertEquals(Optional.of(HEART), leg.target());
    }

    @Test
    void insideAndOutsideOfThePalisade() {
        assertTrue(RaidMarch.isInside(HEART, APPROACHES, new BlockPos(18, 64, -18)));
        assertFalse(RaidMarch.isInside(HEART, APPROACHES, new BlockPos(0, 64, -19)));
        assertFalse(RaidMarch.isOutside(HEART, APPROACHES, new BlockPos(0, 64, -19)));
        assertTrue(RaidMarch.isOutside(HEART, APPROACHES, new BlockPos(20, 64, 3)));
        assertFalse(RaidMarch.isInside(HEART, List.of(), HEART));
        assertFalse(RaidMarch.isOutside(HEART, List.of(), new BlockPos(100, 64, 100)));
        assertEquals(19, RaidMarch.squareDistance(HEART, new BlockPos(-7, 70, 19)));
    }
}
