package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationGuidanceTest {
    @Test public void turnDistanceInstructionAndDestinationRemainIndependent() {
        String road="Turn right onto A · B Road\nwith a long street name";
        NavigationGuidance guidance=NavigationGuidance.active(250,12500,road,"Destination · East",false);
        assertEquals("250 m",guidance.distance); assertEquals(road,guidance.instruction);
        assertEquals("12.5 km to Destination · East",guidance.detail); assertEquals(NavigationGuidance.State.ACTIVE,guidance.state);
    }
    @Test public void arrivalOffRouteAndGpsLossHaveExplicitStatesWithoutFakeTurnDistances() {
        assertEquals(NavigationGuidance.State.ARRIVED,NavigationGuidance.arrived("Home").state);
        assertEquals("Arrived",NavigationGuidance.arrived("Home").distance);
        assertEquals(NavigationGuidance.State.OFF_ROUTE,NavigationGuidance.offRoute("Home").state);
        assertEquals("Off route",NavigationGuidance.offRoute("Home").distance);
        assertEquals(NavigationGuidance.State.WAITING_FOR_GPS,NavigationGuidance.waiting("Home").state);
        assertEquals("Home",NavigationGuidance.waiting("Home").detail);
    }
    @Test public void unitsAndNonnegativeDistancesKeepExistingFormatting() {
        assertEquals("0 m",NavigationGuidance.active(-5,-20,"Continue","Home",false).distance);
        assertEquals("1.0 mi",NavigationGuidance.distance(1609.344,true));
        assertEquals("33 ft",NavigationGuidance.distance(10,true));
    }
}
