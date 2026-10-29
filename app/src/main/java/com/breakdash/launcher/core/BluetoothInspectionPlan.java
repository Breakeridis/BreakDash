package com.breakdash.launcher.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Select names only; the Android caller must separately verify the source APK before loading classes. */
public final class BluetoothInspectionPlan {
    public static final int PER_PACKAGE=16;
    private final TreeSet<String> selected=new TreeSet<>(Comparator.comparingInt(BluetoothInspectionPlan::priority).thenComparing(Comparator.naturalOrder()));
    private int matches;
    public void offer(String entry) {
        String name=contract(entry); if(name==null) return;
        matches++; selected.add(name); if(selected.size()>PER_PACKAGE) selected.pollLast();
    }
    public List<String> names() { return new ArrayList<>(selected); }
    public int matches() { return matches; }
    public static String contract(String entry) {
        if(entry==null || !entry.matches("[A-Za-z_$][A-Za-z0-9_.$]{0,179}") || entry.matches(".*\\$[0-9].*") || entry.contains("$Proxy") || entry.contains("$Stub$")) return null;
        String name=entry.endsWith("$Stub") ? entry.substring(0,entry.length()-5) : entry;
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.startsWith("android.") || lower.startsWith("androidx.") || lower.startsWith("java.") || lower.startsWith("kotlin.")) return null;
        if(!(lower.contains("bluetooth") || lower.contains("btphone") || lower.contains("btmusic") || lower.contains("gocsdk") || lower.contains(".bt."))) return null;
        String simple=name.substring(name.lastIndexOf('.')+1);
        if(simple.equals("R") || simple.startsWith("R$") || simple.equals("BuildConfig")) return null;
        return name;
    }
    public static int priority(String name) {
        String simple=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(simple.matches("i(bluetooth|bt|goc|phone).*")) return 0;
        if(simple.matches(".*(feature|manager|client|api|sdk|service).*")) return 1;
        if(simple.matches(".*(callback|listener).*")) return 2;
        return 3;
    }
    public static int methodPriority(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*(bluetooth|scan|discover|gatt|connect|pair|bond|dial|answer|hang|call|music|play|pause|bt).*" ) ? 0 : 1;
    }
}
