package com.setsuna.ui.hud;

/** Projects Minecraft's east/south axes into screen right/down coordinates. */
record RadarProjection(double cosine, double sine) {

    static RadarProjection of(double yawDegrees, boolean rotateWithFacing) {
        double angle = rotateWithFacing ? Math.toRadians(180.0 - yawDegrees) : 0.0;
        return new RadarProjection(Math.cos(angle), Math.sin(angle));
    }

    double screenX(double east, double south) {
        return east * cosine - south * sine;
    }

    double screenY(double east, double south) {
        return east * sine + south * cosine;
    }
}
