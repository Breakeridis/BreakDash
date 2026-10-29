package com.breakdash.launcher.core;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fail-closed projection of a sensitive dumpsys stream. Unknown lines and free-text values never survive. */
public final class BluetoothManagerSummary {
    public static final int INPUT_LIMIT=65536;
    private static final Pattern ENABLED=Pattern.compile("enabled:\\s*(true|false)",Pattern.CASE_INSENSITIVE);
    private static final Pattern STATE=Pattern.compile("state:\\s*((?:STATE_)?(?:OFF|ON|TURNING_ON|TURNING_OFF|BLE_ON|BLE_TURNING_ON|BLE_TURNING_OFF)|[0-9]{1,3})",Pattern.CASE_INSENSITIVE);
    private static final Pattern CRASHES=Pattern.compile("Bluetooth crashed ([0-9]{1,6}) times?",Pattern.CASE_INSENSITIVE);
    private static final Pattern BLE_APPS=Pattern.compile("([0-9]{1,6}) BLE apps?\\s*registered",Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE=Pattern.compile("Profile:\\s*(GattService|A2dpService|A2dpSinkService|HeadsetService|HeadsetClientService|AvrcpControllerService|AvrcpTargetService|PbapClientService|MapClientService|PanService|HidHostService|SapService)");
    private static final Pattern NUMBER=Pattern.compile("(mState|mScanMode|mNumHwTrackFiltersAvailable|mMaxScanFilters|mScannerId|mNumOfAdvertisementInstancesSupported|mOffloadedScanBatchingSupported|mOffloadedFilteringSupported)\\s*[:=]\\s*(-?[0-9]{1,7}|true|false)",Pattern.CASE_INSENSITIVE);
    private BluetoothManagerSummary() {}
    public static String parse(String raw) {
        if(raw==null || raw.isEmpty()) return "No Bluetooth-manager output returned.\n";
        boolean truncated=raw.length()>=INPUT_LIMIT;
        String input=raw.substring(0,Math.min(raw.length(),INPUT_LIMIT));
        String lower=input.toLowerCase(Locale.ROOT);
        if(lower.contains("permission denial") || lower.contains("permission denied") || lower.contains("requires android.permission.dump")) return "Bluetooth-manager capture denied: Android DUMP permission required. Optional authorized local ADB may provide access.\n";
        if(lower.contains("can't find service: bluetooth_manager") || lower.contains("service bluetooth_manager does not exist")) return "Android bluetooth_manager service not found.\n";
        if(lower.contains("dump timeout") || lower.contains("dump timed out")) return "Bluetooth-manager dump timed out.\n";
        Set<String> fields=new LinkedHashSet<>(); boolean registrations=false,managerSection=false;
        for(String original:input.split("\n",-1)) {
            String line=original.trim(); Matcher match;
            if(line.equals("Bluetooth Status")) { managerSection=true; fields.add("Android Bluetooth-manager status section present"); continue; }
            if(line.equals("Bluetooth Service not connected")) { fields.add("Android adapter service not connected"); continue; }
            if(managerSection && (match=ENABLED.matcher(line)).matches()) { fields.add("Manager enabled: "+match.group(1).toLowerCase(Locale.ROOT)); continue; }
            if(managerSection && (match=STATE.matcher(line)).matches()) { fields.add("Manager state: "+match.group(1).toUpperCase(Locale.ROOT)); continue; }
            if((match=CRASHES.matcher(line)).matches()) { fields.add("Bluetooth crash count: "+match.group(1)); continue; }
            if((match=BLE_APPS.matcher(line)).matches()) { fields.add("Registered BLE apps: "+match.group(1)); registrations=true; continue; }
            if(registrations) {
                if(line.isEmpty()) registrations=false;
                else if(line.equals("com.breakdash.launcher") || line.equals("com.breakdash.launcher.debug") || line.equals("com.jiawei.maxobd")) fields.add("Registered BLE app: "+line);
            }
            if((match=PROFILE.matcher(line)).matches()) { fields.add("Reported profile: "+match.group(1)); managerSection=false; continue; }
            if((match=NUMBER.matcher(line)).matches()) fields.add("Stack "+match.group(1)+": "+match.group(2));
        }
        String result=fields.isEmpty() ? "No recognized status fields; vendor dump format unavailable. Raw output omitted.\n" : String.join("\n",fields)+"\n";
        if(result.length()>1800) result=result.substring(0,1800)+"\n[status fields truncated]\n";
        return result+(truncated ? "Input reached capture limit; snapshot may be incomplete.\n" : "")+"Names, addresses, history and unrecognized output omitted.\n";
    }
}
