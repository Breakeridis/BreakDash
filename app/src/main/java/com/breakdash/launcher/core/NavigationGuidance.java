package com.breakdash.launcher.core;

import java.util.Locale;

/** Presentation fields stay separate from route calculations and from each other. */
public final class NavigationGuidance {
    public enum State { ACTIVE, ARRIVED, OFF_ROUTE, WAITING_FOR_GPS }
    public final State state;
    public final String distance,instruction,detail;
    private NavigationGuidance(State state,String distance,String instruction,String detail) {
        this.state=state; this.distance=distance; this.instruction=instruction; this.detail=detail;
    }
    public static NavigationGuidance active(double nextMeters,double remainingMeters,String instruction,String destination,boolean mph) {
        return new NavigationGuidance(State.ACTIVE,distance(Math.max(0,nextMeters),mph),instruction,distance(Math.max(0,remainingMeters),mph)+" to "+destination);
    }
    public static NavigationGuidance waiting(String destination) { return new NavigationGuidance(State.WAITING_FOR_GPS,"GPS","Waiting for GPS",destination); }
    public static NavigationGuidance arrived(String destination) { return new NavigationGuidance(State.ARRIVED,"Arrived","You have arrived",destination); }
    public static NavigationGuidance offRoute(String destination) { return new NavigationGuidance(State.OFF_ROUTE,"Off route","Search again to recalculate",destination); }
    public static String distance(double meters,boolean mph) {
        if(mph) return meters<160.9 ? String.format(Locale.US,"%.0f ft",meters*3.28084) : String.format(Locale.US,"%.1f mi",meters/1609.344);
        return meters<1000 ? String.format(Locale.US,"%.0f m",meters) : String.format(Locale.US,"%.1f km",meters/1000);
    }
}
