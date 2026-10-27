package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleMotionTest {
    @Test public void firstFixIsImmediateAndLaterFixesMoveContinuously() {
        VehicleMotion motion=new VehicleMotion();
        motion.target(38,23,90,1000,false);
        assertFalse(motion.moving(1000)); assertEquals(23,motion.sample(1000).longitude,1e-9);
        motion.target(38,23.001,90,2000,false);
        assertEquals(23,motion.sample(2000).longitude,1e-9);
        assertEquals(23.0005,motion.sample(2350).longitude,1e-9);
        assertEquals(23.001,motion.sample(2700).longitude,1e-9);
        assertFalse(motion.moving(2700));
    }
    @Test public void anEarlyFixContinuesFromTheDisplayedPosition() {
        VehicleMotion motion=new VehicleMotion(); motion.target(38,23,0,0,false);
        motion.target(38,23.001,0,1000,false);
        double before=motion.sample(1350).longitude;
        motion.target(38,23.002,0,1350,false);
        assertEquals(before,motion.sample(1350).longitude,1e-9);
        assertEquals((before+23.002)/2,motion.sample(1700).longitude,1e-9);
    }
    @Test public void turnsAndDatelineCrossingsTakeTheShortPath() {
        VehicleMotion.Pose halfway=VehicleMotion.interpolate(new VehicleMotion.Pose(0,179.9,359),new VehicleMotion.Pose(0,-179.9,1),.5);
        assertEquals(180,Math.abs(halfway.longitude),1e-9); assertEquals(0,halfway.bearing,1e-9);
        halfway=VehicleMotion.interpolate(new VehicleMotion.Pose(0,-179.9,1),new VehicleMotion.Pose(0,179.9,359),.5);
        assertEquals(180,Math.abs(halfway.longitude),1e-9); assertEquals(0,halfway.bearing,1e-9);
    }
    @Test public void stoppingOrSnappingNeverExtrapolatesBeyondTheLatestFix() {
        VehicleMotion motion=new VehicleMotion(); motion.target(38,23,0,0,false); motion.target(38,23.001,5,1000,false);
        assertEquals(23.001,motion.sample(99999).longitude,1e-9);
        motion.finish(); assertFalse(motion.moving(1100)); assertEquals(23.001,motion.sample(1100).longitude,1e-9);
        motion.target(39,24,10,2000,true); assertFalse(motion.moving(2000)); assertEquals(24,motion.sample(2000).longitude,1e-9);
    }
    @Test public void stationaryFixesNeedNoFramesAndClockBoundsCannotOvershoot() {
        VehicleMotion motion=new VehicleMotion(); motion.target(38,23,90,1000,false); motion.target(38,23,90,2000,false);
        assertFalse(motion.moving(2000));
        assertEquals(0,VehicleMotion.fraction(-1),0); assertEquals(1,VehicleMotion.fraction(10000),0);
        VehicleMotion.Pose polar=new VehicleMotion.Pose(90,540,-1);
        assertEquals(85.05112878,polar.latitude,1e-9); assertEquals(-180,polar.longitude,1e-9); assertEquals(359,polar.bearing,1e-9);
    }
}
