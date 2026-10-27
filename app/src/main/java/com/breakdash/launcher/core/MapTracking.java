package com.breakdash.launcher.core;

/** Camera ownership: GPS follows only until the driver chooses to explore. */
public final class MapTracking {
    public static final long ROUTE_PREVIEW_MS = 6500;
    private boolean following = true;
    private long previewUntil;

    public void explore() { following = false; previewUntil = 0; }
    public void recenter() { following = true; previewUntil = 0; }
    public void previewRoute(long now) { following = true; previewUntil = now + ROUTE_PREVIEW_MS; }
    public boolean following() { return following; }
    public boolean shouldFollow(long now) { return following && now >= previewUntil; }
    public long previewRemaining(long now) { return following ? Math.max(0, previewUntil-now) : 0; }
}
