package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleHeadingTest {
    @Test public void stoppedOrSlowGpsCannotSpinTheMap() {
        VehicleHeading heading = new VehicleHeading();
        heading.update(90, 0, 5);
        assertFalse(heading.known());
        heading.update(90, 12, 5);
        heading.update(270, 0, 5);
        heading.update(180, 1.5, 5);
        assertTrue(heading.known());
        assertEquals(90, heading.degrees(), 0);
    }

    @Test public void unreliableOrMalformedCoursesKeepTheLastGoodDirection() {
        VehicleHeading heading = new VehicleHeading();
        heading.update(45, 10, 5);
        heading.update(180, 10, 80);
        heading.update(Double.NaN, 10, 5);
        heading.update(Double.POSITIVE_INFINITY, 10, 5);
        heading.update(180, Double.NaN, 5);
        heading.update(180, 10, -1);
        heading.update(180, 10, Double.POSITIVE_INFINITY);
        assertEquals(45, heading.degrees(), 0);
    }

    @Test public void olderReceiversWithoutBearingAccuracyCanStillNavigate() {
        VehicleHeading heading = new VehicleHeading();
        heading.update(350, 10, Double.NaN);
        assertTrue(heading.known());
        assertEquals(350, heading.degrees(), 0);
        heading.update(10, 10, Double.NaN);
        assertEquals(10, heading.degrees(), 0);
    }

    @Test public void courseWrapsIntoCompassRange() {
        VehicleHeading heading = new VehicleHeading();
        heading.update(-10, 10, 5);
        assertEquals(350, heading.degrees(), 0);
        heading.update(370, 10, 5);
        assertEquals(10, heading.degrees(), 0);
    }
}
