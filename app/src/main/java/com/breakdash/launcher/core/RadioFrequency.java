package com.breakdash.launcher.core;

import java.util.Locale;

public final class RadioFrequency {
    private RadioFrequency() {}
    public static boolean valid(int value) {
        return (value >= 8750 && value <= 10800) || (value >= 531 && value <= 1620);
    }
    public static boolean fm(int value) { return value >= 8750 && value <= 10800; }
    public static String number(int value) {
        if (!valid(value)) return "—";
        return fm(value) ? String.format(Locale.US, "%.2f", value / 100.0) : Integer.toString(value);
    }
    public static String unit(int value) { return fm(value) ? "MHz" : "kHz"; }
}
