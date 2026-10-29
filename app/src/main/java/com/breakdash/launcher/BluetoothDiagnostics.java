package com.breakdash.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.LocationManager;
import android.os.Build;
import com.breakdash.launcher.core.ReportPrivacy;
import com.breakdash.launcher.core.BluetoothSession.State;
import java.util.Date;

/** Snapshot only: collection never scans, connects, reads values or loads vendor code. */
final class BluetoothDiagnostics {
    static final String[] FACTORY={"com.nwd.android.phone","com.nwd.bt.music","com.nwd.kernel","com.nwd.setting.service"};
    private static final String[] PERMISSIONS={Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_ADMIN,
            "android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT",Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION};
    private BluetoothDiagnostics() {}
    private static SharedPreferences store(Context context) { return context.getSharedPreferences("bluetooth_diagnostics",Context.MODE_PRIVATE); }
    static void probe(Context context,State operation,String summary) {
        String value=captured(summary);
        SharedPreferences.Editor edit=store(context).edit().putString("probe",safe(value,4300));
        if(operation==State.CLASSIC_SCAN || operation==State.BLE_SCAN) edit.putString(operation==State.CLASSIC_SCAN ? "classic" : "ble",safe(value,400));
        edit.apply();
    }
    private static String captured(String summary) { return "Captured: "+new Date()+"\nCaptured by BreakDash: "+BuildConfig.VERSION_NAME+"\n"+summary; }
    static void factory(Context context,String summary) { store(context).edit().putString("factory",safe(captured(summary),28000)).apply(); }
    static void manager(Context context,boolean adb,String summary) { store(context).edit().putString(adb ? "manager_adb" : "manager_app",safe(captured(summary),1400)).apply(); }
    static void clear(Context context) { store(context).edit().clear().apply(); }
    static String safe(String text,int limit) {
        String value=ReportPrivacy.text(text).replaceAll("[^\\x09\\x0A\\x0D\\x20-\\x7E]","?");
        return value.length()<=limit ? value : value.substring(0,Math.max(0,limit-24))+"\n[section truncated]\n";
    }
    static BluetoothAdapter adapter(Context context) {
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager==null ? null : manager.getAdapter();
    }
    static boolean granted(Context context,String permission) { return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED; }
    static boolean connectAllowed(Context context) { return Build.VERSION.SDK_INT<31 ? granted(context,Manifest.permission.BLUETOOTH) : granted(context,"android.permission.BLUETOOTH_CONNECT"); }
    static boolean scanAllowed(Context context) { return connectAllowed(context) && (Build.VERSION.SDK_INT>=31 ? granted(context,"android.permission.BLUETOOTH_SCAN") : granted(context,Manifest.permission.ACCESS_FINE_LOCATION)); }
    static boolean locationEnabled(Context context) {
        try {
            LocationManager manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            return manager!=null && manager.isLocationEnabled();
        } catch(RuntimeException error) { return false; }
    }
    @SuppressLint("MissingPermission")
    static String platform(Context context) {
        StringBuilder out=new StringBuilder("SDK: "+Build.VERSION.SDK_INT+"\n"); PackageManager pm=context.getPackageManager();
        out.append("Classic feature: ").append(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).append("\nBLE feature: ").append(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)).append('\n');
        try {
            BluetoothAdapter adapter=adapter(context);
            out.append("Android adapter: ").append(adapter==null ? "unavailable" : !connectAllowed(context) ? "permission required" : adapter.isEnabled() ? "enabled" : "disabled").append('\n');
            out.append("Location service: ").append(locationEnabled(context)).append('\n');
            if(adapter!=null && connectAllowed(context) && adapter.isEnabled()) {
                out.append("Android BLE scanner object: ").append(adapter.getBluetoothLeScanner()==null ? "unavailable" : "available").append('\n');
                out.append("Offloaded scan filtering: ").append(adapter.isOffloadedFilteringSupported()).append("; batching: ").append(adapter.isOffloadedScanBatchingSupported()).append('\n');
            }
        } catch(RuntimeException error) { out.append("Adapter/location snapshot unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
        for(String name:new String[]{context.getPackageName(),"com.jiawei.maxobd"}) {
            try {
                PackageInfo info=pm.getPackageInfo(name,PackageManager.GET_PERMISSIONS);
                out.append("App: ").append(name).append(" version ").append(info.versionName).append(" target SDK ").append(info.applicationInfo==null ? -1 : info.applicationInfo.targetSdkVersion).append('\n');
                for(String permission:PERMISSIONS) {
                    if(Build.VERSION.SDK_INT<31 && (permission.equals("android.permission.BLUETOOTH_SCAN") || permission.equals("android.permission.BLUETOOTH_CONNECT"))) {
                        out.append("  ").append(permission.replace("android.permission.","")).append(" not applicable before SDK 31\n"); continue;
                    }
                    boolean declared=false,granted=false;
                    if(info.requestedPermissions!=null) for(int i=0;i<info.requestedPermissions.length;i++) if(permission.equals(info.requestedPermissions[i])) {
                        declared=true; granted=info.requestedPermissionsFlags!=null && i<info.requestedPermissionsFlags.length && (info.requestedPermissionsFlags[i]&PackageInfo.REQUESTED_PERMISSION_GRANTED)!=0;
                    }
                    out.append("  ").append(permission.replace("android.permission.","")).append(" declared=").append(declared).append(" granted=").append(granted).append('\n');
                }
            } catch(PackageManager.NameNotFoundException | RuntimeException error) { out.append("App unavailable: ").append(name).append('\n'); }
        }
        return safe(out.toString(),2200);
    }
    static String components(Context context) {
        StringBuilder out=new StringBuilder();
        for(String name:FACTORY) {
            try {
                PackageInfo info=context.getPackageManager().getPackageInfo(name,PackageManager.GET_SERVICES);
                out.append("Package: ").append(name).append(" version ").append(info.versionName).append('\n');
                if(info.services==null || info.services.length==0) out.append("  No declared services\n");
                if(info.services!=null) for(int i=0;i<Math.min(info.services.length,12);i++) {
                    ServiceInfo service=info.services[i]; out.append("  Service ").append(service.name).append(" exported=").append(service.exported).append(" enabled=").append(service.enabled).append(" permission=").append(service.permission==null ? "none" : service.permission).append('\n');
                }
            } catch(PackageManager.NameNotFoundException | RuntimeException error) { out.append("Package unavailable: ").append(name).append('\n'); }
        }
        return safe(out.toString(),1100);
    }
    static String collect(Context context) {
        SharedPreferences saved=store(context);
        String report="BLUETOOTH (snapshot; no scan or connection initiated)\nSaved captures survive updates. Check capture time/version; unversioned entries are from older releases.\n"+platform(context)
                +"\nLAST CLASSIC SCAN\n"+safe(saved.getString("classic","Not captured since this update."),400)
                +"\nLAST BLE SCAN\n"+safe(saved.getString("ble","Not captured since this update."),400)
                +"\nLAST BLUETOOTH OPERATION\n"+safe(saved.getString("probe","No probe captured."),4300)
                +"\nANDROID BLUETOOTH MANAGER: APP PROCESS\n"+safe(saved.getString("manager_app","Not captured. Use the explicit status capture button."),1400)
                +"\nANDROID BLUETOOTH MANAGER: LOCAL ADB\n"+safe(saved.getString("manager_adb","Not captured. Local ADB is optional."),1400)
                +(!saved.contains("manager_app") && !saved.contains("manager_adb") && saved.contains("manager") ? "\nLEGACY MANAGER CAPTURE (method not recorded)\n"+safe(saved.getString("manager",""),1400) : "")
                +"\nFACTORY INTERFACE INSPECTION\n"+safe(saved.getString("factory","Not inspected. Use Bluetooth > Diagnostics > Inspect vendor SDK entry points."),28000)+"\n";
        return safe(report,40960);
    }
}
