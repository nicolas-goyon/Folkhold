package io.github.nicolasgoyon.folkhold.balance.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TrainPushTest {
    @Test
    void pushedMinecartDrives() {
        assertEquals(2, TrainPush.driver(new double[] {0.0, 0.0, 0.05}, -1));
        assertEquals(1, TrainPush.driver(new double[] {-0.01, 0.06, -0.01}, 0));
    }

    @Test
    void slowdownIsNotAPush() {
        assertEquals(2, TrainPush.driver(new double[] {-0.016, -0.02, -0.01}, 2));
        assertEquals(0, TrainPush.driver(new double[] {-0.016, -0.02, TrainPush.MIN_GAIN}, -1));
    }

    @Test
    void largerOfTwoPushesDrives() {
        // Two pushes in opposite directions: the gain does not have a direction, and the larger push drives.
        assertEquals(0, TrainPush.driver(new double[] {0.06, 0.0, 0.05}, 2));
        assertEquals(2, TrainPush.driver(new double[] {0.05, 0.0, 0.06}, 0));
    }

    @Test
    void lastDriverWinsATie() {
        assertEquals(2, TrainPush.driver(new double[] {0.05, 0.0, 0.05}, 2));
        assertEquals(0, TrainPush.driver(new double[] {0.05, 0.0, 0.05}, 1));
        assertEquals(0, TrainPush.driver(new double[] {0.05, 0.0, 0.05}, -1));
    }

    @Test
    void lastDriverOutsideTheTrainIsIgnored() {
        assertEquals(0, TrainPush.driver(new double[] {0.0, 0.0}, 5));
    }

    @Test
    void emptyTrainIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TrainPush.driver(new double[0], -1));
    }
}
