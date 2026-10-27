package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapTrackingTest {
    @Test public void gpsCannotTakeTheCameraBackAfterAGesture() {
        MapTracking camera = new MapTracking();
        assertTrue(camera.shouldFollow(0));
        camera.explore();
        assertFalse(camera.shouldFollow(1000));
        assertFalse(camera.shouldFollow(86400000));
    }

    @Test public void recenterRestoresGpsImmediately() {
        MapTracking camera = new MapTracking();
        camera.explore(); camera.recenter();
        assertTrue(camera.following());
        assertTrue(camera.shouldFollow(100));
    }

    @Test public void routeOverviewHoldsBeforeFollowingTheVehicle() {
        MapTracking camera = new MapTracking();
        camera.previewRoute(1000);
        assertFalse(camera.shouldFollow(7499));
        assertTrue(camera.shouldFollow(7500));
    }

    @Test public void browsingDuringARoutePreviewRemainsUnderUserControl() {
        MapTracking camera = new MapTracking();
        camera.previewRoute(1000); camera.explore();
        assertFalse(camera.shouldFollow(7500));
        camera.recenter();
        assertTrue(camera.shouldFollow(7500));
    }

    @Test public void recenterDoesNotWaitForAPreviewTimer() {
        MapTracking camera = new MapTracking();
        camera.previewRoute(1000); camera.recenter();
        assertTrue(camera.shouldFollow(1001));
    }
}
