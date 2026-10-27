package com.breakdash.launcher.core;

/** GPS course, held steady at stops instead of using the device's physical orientation. */
public final class VehicleHeading {
    private double degrees;
    private boolean known;

    public void update(double course, double speedMetersPerSecond, double accuracyDegrees) {
        if (!Double.isFinite(course) || !Double.isFinite(speedMetersPerSecond) || speedMetersPerSecond <= 1.5) return;
        // NaN denotes a receiver that does not report bearing accuracy.
        if (!Double.isNaN(accuracyDegrees) && (!Double.isFinite(accuracyDegrees) || accuracyDegrees < 0 || accuracyDegrees > 35)) return;
        degrees = (course % 360 + 360) % 360;
        known = true;
    }

    public double degrees() { return degrees; }
    public boolean known() { return known; }
}
