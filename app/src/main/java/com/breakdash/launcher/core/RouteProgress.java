package com.breakdash.launcher.core;

/** Projects a GPS fix onto a route segment; independent of Android for CI verification. */
public final class RouteProgress {
    private static final double EARTH_RADIUS = 6371000;
    public final int segment;
    public final double remainingMeters, offRouteMeters;
    private RouteProgress(int segment, double remaining, double offRoute) {
        this.segment = segment; remainingMeters = remaining; offRouteMeters = offRoute;
    }
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
    public static RouteProgress locate(double[][] points, double[] remaining, double lat, double lon, int previous) {
        if (points.length < 2 || points.length != remaining.length) throw new IllegalArgumentException("Invalid route");
        double best = Double.MAX_VALUE, left = 0;
        int chosen = 0;
        double scaleY = Math.PI * EARTH_RADIUS / 180, scaleX = scaleY * Math.cos(Math.toRadians(lat));
        // Prefer nearby progress on crossing roads, but allow reacquisition after a GPS gap.
        int start = Math.max(0, previous - 3), end = Math.min(points.length - 1, previous + 160);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = start; i < end; i++) {
                double ax = (points[i][1] - lon) * scaleX, ay = (points[i][0] - lat) * scaleY;
                double bx = (points[i+1][1] - lon) * scaleX, by = (points[i+1][0] - lat) * scaleY;
                double dx = bx - ax, dy = by - ay, norm = dx*dx + dy*dy;
                double t = norm == 0 ? 0 : Math.max(0, Math.min(1, -(ax*dx + ay*dy) / norm));
                double gap = Math.hypot(ax + t*dx, ay + t*dy);
                if (gap < best) {
                    best = gap; chosen = i;
                    left = remaining[i+1] + (1-t) * (remaining[i] - remaining[i+1]);
                }
            }
            if (best < 100) break;
            start = 0; end = points.length - 1;
        }
        return new RouteProgress(chosen, left, best);
    }
}
