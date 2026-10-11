package com.setsuna.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarProjectionTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void northUpKeepsWorldCardinalsIndependentOfPlayerYaw() {
        for (double yaw : new double[]{0.0, 90.0, 180.0, 270.0, -45.0}) {
            RadarProjection projection = RadarProjection.of(yaw, false);
            assertPosition(projection, 0.0, -8.0, 0.0, -8.0);
            assertPosition(projection, 8.0, 0.0, 8.0, 0.0);
            assertPosition(projection, 0.0, 8.0, 0.0, 8.0);
            assertPosition(projection, -8.0, 0.0, -8.0, 0.0);
        }
    }

    @Test
    void facingUpPlacesForwardAboveAndPlayerRightToTheRightAtEveryHeading() {
        for (double yaw = -180.0; yaw <= 180.0; yaw += 22.5) {
            RadarProjection projection = RadarProjection.of(yaw, true);
            double angle = Math.toRadians(yaw);
            assertPosition(projection, -Math.sin(angle) * 8.0, Math.cos(angle) * 8.0, 0.0, -8.0);
            assertPosition(projection, -Math.cos(angle) * 8.0, -Math.sin(angle) * 8.0, 8.0, 0.0);
        }
    }

    @Test
    void diagonalContactsKeepTheirDistanceAndFullTurnsKeepTheirDirection() {
        for (double yaw : new double[]{-720.0, -179.5, 0.0, 37.5, 179.5, 720.0}) {
            RadarProjection projection = RadarProjection.of(yaw, true);
            RadarProjection nextTurn = RadarProjection.of(yaw + 360.0, true);
            double x = projection.screenX(48.0, -64.0);
            double y = projection.screenY(48.0, -64.0);
            assertEquals(80.0, Math.hypot(x, y), EPSILON);
            assertEquals(x, nextTurn.screenX(48.0, -64.0), EPSILON);
            assertEquals(y, nextTurn.screenY(48.0, -64.0), EPSILON);
        }
    }

    private static void assertPosition(RadarProjection projection, double east, double south,
                                       double expectedX, double expectedY) {
        assertEquals(expectedX, projection.screenX(east, south), EPSILON);
        assertEquals(expectedY, projection.screenY(east, south), EPSILON);
    }
}
