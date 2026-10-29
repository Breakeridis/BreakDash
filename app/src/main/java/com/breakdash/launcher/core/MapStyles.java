package com.breakdash.launcher.core;

/** Recommended styles are local; their tiles, fonts and sprites still require consent. */
public final class MapStyles {
    public static final String DAY = "asset://maps/driving-day.json";
    public static final String NIGHT = "asset://maps/driving-night.json";
    private MapStyles() {}

    public static String override(boolean light, String saved) {
        String value = saved == null ? "" : saved.trim();
        String legacy = "https://tiles.openfreemap.org/styles/" + (light ? "positron" : "dark");
        return value.equals(legacy) || value.equals(legacy + "/") ? "" : value;
    }

    public static String uri(boolean light, String saved) {
        String value = override(light, saved);
        return value.isEmpty() ? (light ? DAY : NIGHT) : value;
    }
}
