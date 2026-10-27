package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RouteProgressTest {
    private final double[][] road={{0,0},{0,.01},{0,.02}};
    private final double length=RouteProgress.distance(0,0,0,.01);
    private final double[] remaining={length*2,length,0};
    @Test public void calculatesDistanceAlongRoadInsteadOfDistanceToDestination() {
        RouteProgress progress=RouteProgress.locate(road,remaining,0,.005,0);
        assertEquals(0,progress.segment);
        assertEquals(length*1.5,progress.remainingMeters,2);
        assertEquals(0,progress.offRouteMeters,.1);
    }
    @Test public void reacquiresAfterGpsGapAndDetectsOffRoute() {
        RouteProgress progress=RouteProgress.locate(road,remaining,.01,.015,0);
        assertEquals(1,progress.segment);
        assertTrue(progress.offRouteMeters>1000);
        assertEquals(length*.5,progress.remainingMeters,2);
    }
    @Test public void duplicateGeometryVerticesDoNotDivideByZero() {
        RouteProgress result=RouteProgress.locate(new double[][]{{0,0},{0,0}},new double[]{0,0},0,0,0);
        assertEquals(0,result.remainingMeters,0);
        assertFalse(Double.isNaN(result.offRouteMeters));
    }
}
