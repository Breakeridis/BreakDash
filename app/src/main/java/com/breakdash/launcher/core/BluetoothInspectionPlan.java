package com.breakdash.launcher.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Select names only; the Android caller must separately verify the source APK before loading classes. */
public final class BluetoothInspectionPlan {
    public static final int PER_PACKAGE=16;
    private static final String CONTROLLER_HELPER="com.android.utils.utils.AbsServiceControllerHelper";
    private static final String[] ENTRY_POINTS={"com.bt.BTController",CONTROLLER_HELPER,CONTROLLER_HELPER+"$OnServiceConnectSuccessListener","com.bt.BTFeature","com.bt.BTSourceFeature","com.bt.hd.link.BluetoothAPI","com.bt.hd.link.AbsServiceControllerHelper","com.bt.hd.link.AbsServiceControllerHelper$OnServiceConnectSuccessListener","com.bt.hd.link.SppDataCallBack"};
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
        if(!(name.equals(CONTROLLER_HELPER) || name.startsWith(CONTROLLER_HELPER+"$") || lower.contains("bluetooth") || lower.contains("btphone") || lower.contains("btmusic") || lower.contains("gocsdk") || lower.contains(".bt."))) return null;
        String simple=name.substring(name.lastIndexOf('.')+1);
        if(simple.equals("R") || simple.startsWith("R$") || simple.equals("BuildConfig") || name.matches(".*\\.[A-Za-z0-9_]*Constants?(?:\\$.*)?")) return null;
        return name;
    }
    public static int priority(String name) {
        for(int i=0;i<ENTRY_POINTS.length;i++) if(ENTRY_POINTS[i].equals(name)) return i;
        String simple=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(hasBleMarker(name)) return 10;
        if(simple.matches("i(bluetooth|bt|goc|phone).*")) return 11;
        if(simple.matches(".*(callback|listener).*")) return 12;
        if(simple.matches(".*(feature|manager|client|api|sdk|service).*")) return 13;
        return 20;
    }
    public static int methodPriority(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.matches(".*(reset|reboot|delete|erase|factory).*")) return 9;
        if(lower.matches("(getinstance|createinstance|init|initialize|connectservice|disconnectservice|getfeature|getserviceintent|release|dispose).*")) return 0;
        if(hasBleMarker(name)) return 1;
        if(lower.matches(".*(callback|listener|register|regist|unregist).*")) return 2;
        if(lower.matches("(get|is|has|can|check).*")) return 3;
        return 4;
    }
    public static boolean hasBleMarker(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        return name.matches(".*(Ble|BLE|Gatt|GATT|LeScan).*" ) || lower.startsWith("ble") || lower.contains("_ble") || lower.contains("gatt") || lower.contains("scan") || lower.contains("discover") || lower.contains("advertis");
    }
    public static int schemaLimit(String name) {
        if(name.equals("com.bt.BTController")) return 4500;
        if(name.equals(CONTROLLER_HELPER)) return 3000;
        if(name.equals("com.bt.BTFeature")) return 6500;
        if(name.equals("com.bt.BTSourceFeature")) return 4000;
        return priority(name)<10 ? 2000 : 1200;
    }
}
