package com.breakdash.launcher;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.breakdash.launcher.core.BluetoothSession;
import com.breakdash.launcher.core.BluetoothSession.Reason;
import com.breakdash.launcher.core.BluetoothSession.State;
import com.breakdash.launcher.core.BluetoothDiscovery;
import java.util.List;

/** Only the visible Bluetooth page owns this controller. No service, reconnect, reads or writes. */
@SuppressLint("MissingPermission") // Actions check runtime grants; vendor/revocation failures are caught at each boundary.
final class BluetoothController {
    static final class Device {
        final BluetoothDevice handle;
        String name="Unnamed device"; int type,bond; Integer rssi;
        boolean classic,ble;
        Device(BluetoothDevice handle) { this.handle=handle; }
        String state() { return bond==BluetoothDevice.BOND_BONDED ? "Paired" : bond==BluetoothDevice.BOND_BONDING ? "Pairing" : "Discovered"; }
        String transport() { return classic && ble || type==BluetoothDevice.DEVICE_TYPE_DUAL ? "Classic + BLE" : ble || type==BluetoothDevice.DEVICE_TYPE_LE ? "BLE" : classic || type==BluetoothDevice.DEVICE_TYPE_CLASSIC ? "Classic" : "Unknown transport"; }
    }
    private final BluetoothDiscovery<BluetoothDevice,Device> inventory=new BluetoothDiscovery<>();
    final List<Device> devices=inventory.rows();
    final BluetoothSession session=new BluetoothSession(this::cleanup);
    String status="Ready · choose a scan or refresh paired devices";
    private final Context context;
    private final Runnable changed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean visible,registered,ownClassic,classicStarted,uiPending;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BroadcastReceiver classicReceiver;
    private BluetoothGatt gatt;
    private Device pairing;
    private long token;
    private State operation=State.IDLE;
    private String schema="";
    private final Runnable update=this::renderChanged;
    private final Runnable deadline=this::deadline;
    BluetoothController(Context context,Runnable changed) { this.context=context.getApplicationContext(); this.changed=changed; }
    private long now() { return SystemClock.elapsedRealtime(); }
    void visible(boolean value) {
        if(visible==value) return;
        visible=value;
        if(!value) {
            stop(Reason.HIDDEN);
            if(registered) { try { context.unregisterReceiver(stateReceiver); } catch(RuntimeException ignored) {} registered=false; }
            handler.removeCallbacks(update); uiPending=false; return;
        }
        try {
            adapter=BluetoothDiagnostics.adapter(context);
            IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED); filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            androidx.core.content.ContextCompat.registerReceiver(context,stateReceiver,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED); registered=true;
            refreshPaired();
        } catch(RuntimeException error) { status="Bluetooth unavailable on this firmware"; }
        notifyUi();
    }
    private final BroadcastReceiver stateReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context ignored,Intent intent) {
            if(!visible) return;
            try {
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
                    if(state==BluetoothAdapter.STATE_OFF || state==BluetoothAdapter.STATE_TURNING_OFF) { stop(Reason.BLUETOOTH_OFF); status="Bluetooth is off"; }
                    else if(state==BluetoothAdapter.STATE_ON) status="Bluetooth is on · choose an operation";
                } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction()) && BluetoothDiagnostics.connectAllowed(context)) {
                    BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device!=null) {
                        Device row=add(device,null,false,false);
                        int bond=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.BOND_NONE);
                        if(row!=null) row.bond=bond;
                        if(pairing!=null && pairing.handle.equals(device) && session.state()==State.PAIRING && session.accepts(token)) {
                            if(bond==BluetoothDevice.BOND_BONDED) complete(Reason.PAIRED,"Paired · no profile connection has been tested");
                            else if(bond==BluetoothDevice.BOND_NONE) fail(Reason.REJECTED,0,"Pairing did not complete");
                        }
                    }
                }
            } catch(RuntimeException error) { if(session.running()) fail(Reason.EXCEPTION,0,"Bluetooth state unavailable"); }
            notifyUi();
        }
    };
    void refreshPaired() {
        if(!visible) return;
        try {
            if(adapter==null) { status="Android Bluetooth adapter unavailable"; notifyUi(); return; }
            if(!BluetoothDiagnostics.connectAllowed(context)) { status="Allow Bluetooth access to list paired devices"; notifyUi(); return; }
            // Refresh existing rows too: a forgotten device must not continue to appear paired.
            for(Device row:devices) row.bond=row.handle.getBondState();
            for(BluetoothDevice device:adapter.getBondedDevices()) add(device,null,false,false);
        } catch(RuntimeException error) { status="Paired devices unavailable on this firmware"; }
        notifyUi();
    }
    private Device add(BluetoothDevice device,Integer rssi,boolean classic,boolean ble) {
        return add(device,rssi,classic,ble,null);
    }
    private Device add(BluetoothDevice device,Integer rssi,boolean classic,boolean ble,String advertisedName) {
        Device row=inventory.getOrAdd(device,()->new Device(device));
        if(row==null) return null;
        String name=device.getName();
        if(name==null || name.trim().isEmpty()) name=advertisedName;
        if(name!=null && !name.trim().isEmpty()) row.name=name.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(name.length(),80));
        row.type=device.getType(); row.bond=device.getBondState();
        if(rssi!=null) row.rssi=rssi;
        row.classic|=classic; row.ble|=ble; return row;
    }
    private void renderChanged() { uiPending=false; if(visible) changed.run(); }
    private void notifyUi() { if(visible && !uiPending) { uiPending=true; handler.postDelayed(update,200); } }
    private void save() { BluetoothDiagnostics.probe(context,operation,session.evidence()+"Devices listed (cap 32): "+devices.size()+"\n"+schema); }
    private boolean prepare(State operation,boolean scan) {
        if(!visible) return false;
        stop(Reason.USER_STOP); token=session.begin(operation,now()); schema="";
        this.operation=operation;
        try {
            adapter=BluetoothDiagnostics.adapter(context);
            if(adapter==null) { fail(Reason.MISSING_HARDWARE,0,"Android Bluetooth adapter unavailable"); return false; }
            if(!BluetoothDiagnostics.connectAllowed(context) || scan && !BluetoothDiagnostics.scanAllowed(context)) {
                fail(Reason.PERMISSION_DENIED,0,"Allow Bluetooth access before continuing"); return false;
            }
            if(!adapter.isEnabled()) { fail(Reason.BLUETOOTH_OFF,0,"Bluetooth is off · open Bluetooth settings"); return false; }
            if(scan && Build.VERSION.SDK_INT<31 && !BluetoothDiagnostics.locationEnabled(context)) { fail(Reason.LOCATION_OFF,0,"Turn on Location to discover devices on Android 10/11"); return false; }
            if((Build.VERSION.SDK_INT<31 || BluetoothDiagnostics.granted(context,"android.permission.BLUETOOTH_SCAN")) && adapter.isDiscovering()) { fail(Reason.REJECTED,0,"Classic discovery is still active · stop the other scan and retry"); return false; }
            save(); handler.postDelayed(deadline,session.remaining(now())); return true;
        } catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Bluetooth operation unavailable on this firmware"); return false; }
    }
    void scan(boolean ble) {
        if(!prepare(ble ? State.BLE_SCAN : State.CLASSIC_SCAN,true)) return;
        final long revision=token;
        inventory.clear(); refreshPaired();
        try {
            if(ble) {
                if(!context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)) { fail(Reason.MISSING_HARDWARE,0,"Android reports BLE as unavailable"); return; }
                scanner=adapter.getBluetoothLeScanner();
                if(scanner==null) { fail(Reason.MISSING_HARDWARE,0,"Android BLE scanner unavailable"); return; }
                scanCallback=new ScanCallback() {
                    @Override public void onScanResult(int callbackType,ScanResult result) {
                        handler.post(()->{ if(visible && session.accepts(revision)) try { add(result.getDevice(),result.getRssi(),false,true,result.getScanRecord()==null ? null : result.getScanRecord().getDeviceName()); notifyUi(); } catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"BLE result unavailable"); } });
                    }
                    @Override public void onScanFailed(int code) { handler.post(()->{ if(visible && session.accepts(revision)) fail(Reason.SCAN_ERROR,code,code==ScanCallback.SCAN_FAILED_INTERNAL_ERROR ? "Android BLE scan could not start · internal error (3). Capture Bluetooth-manager status in Diagnostics." : "BLE scan failed · status "+code); }); }
                };
                scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
                status="Scanning BLE · up to 12 seconds";
            } else {
                classicStarted=false;
                classicReceiver=new BroadcastReceiver() {
                    @Override public void onReceive(Context ignored,Intent intent) {
                        if(!visible || !session.accepts(revision)) return;
                        try {
                            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) classicStarted=true;
                            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()) && classicStarted) complete(Reason.SCAN_FINISHED,"Classic scan finished");
                            else if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction()) && classicStarted) {
                                BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                if(device!=null) { add(device,(int)intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE),true,false); notifyUi(); }
                            }
                        } catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Classic scan result unavailable"); }
                    }
                };
                IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND); filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                androidx.core.content.ContextCompat.registerReceiver(context,classicReceiver,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
                ownClassic=true;
                if(!adapter.startDiscovery()) { fail(Reason.REJECTED,0,"Classic scan was rejected by Android"); return; }
                status="Scanning Classic · up to 12 seconds";
            }
        } catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Bluetooth scan unavailable"); }
        notifyUi();
    }
    void pair(Device device) {
        if(device.bond!=BluetoothDevice.BOND_NONE) { status="Already paired or pairing · refresh paired devices"; notifyUi(); return; }
        if(!prepare(State.PAIRING,false)) return;
        pairing=device;
        try { if(!device.handle.createBond()) { fail(Reason.REJECTED,0,"Android rejected the pairing request"); return; } status="Pairing · respond to Android's prompt"; }
        catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Pairing unavailable on this firmware"); }
        notifyUi();
    }
    void test(Device device) {
        if(!prepare(State.CONNECTING,false)) return;
        final long revision=token;
        status="Connecting over BLE · up to 25 seconds";
        try {
            gatt=device.handle.connectGatt(context,false,new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt connection,int code,int state) {
                    handler.post(()->{
                        if(!visible || !session.accepts(revision) || gatt!=connection) return;
                        if(code!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) { fail(Reason.GATT_ERROR,code,"BLE connection failed · status "+code); return; }
                        if(state==BluetoothProfile.STATE_CONNECTED && session.state()==State.CONNECTING) {
                            if(!session.connected(revision,code,true,now()) || !session.discovering(revision,now())) { finishExpired(); return; }
                            save(); status="Connected · discovering services";
                            try { if(!connection.discoverServices()) fail(Reason.REJECTED,0,"Service discovery was rejected"); }
                            catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Service discovery unavailable"); }
                            notifyUi();
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt connection,int code) {
                    handler.post(()->{
                        if(!visible || !session.accepts(revision) || gatt!=connection) return;
                        if(session.state()!=State.DISCOVERING) return;
                        if(session.remaining(now())==0) { session.timeout(revision,now()); finishExpired(); return; }
                        if(code!=BluetoothGatt.GATT_SUCCESS) { fail(Reason.GATT_ERROR,code,"Service discovery failed · status "+code); return; }
                        try { schema=services(connection); }
                        catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"Service definitions unavailable"); return; }
                        if(!session.services(revision,code,now()) || session.state()!=State.COMPLETE) { finishExpired(); return; }
                        save(); cleanup(); status=schema.contains("No services returned") ? "Connected, but no services returned · test connection released" : "Services discovered · test connection released"; notifyUi();
                    });
                }
            },BluetoothDevice.TRANSPORT_LE);
            if(gatt==null) fail(Reason.REJECTED,0,"Android did not create a BLE connection");
        } catch(RuntimeException error) { fail(Reason.EXCEPTION,0,"BLE connection unavailable"); }
        notifyUi();
    }
    private String services(BluetoothGatt connection) {
        StringBuilder out=new StringBuilder("Service identifiers (16 hex octets; no values read):\n"); int count=0;
        for(BluetoothGattService service:connection.getServices()) {
            if(count++>=16 || out.length()>4200) { out.append("Additional services omitted\n"); break; }
            out.append("Service: ").append(BluetoothSession.serviceId(service.getUuid())).append('\n'); int chars=0;
            for(BluetoothGattCharacteristic characteristic:service.getCharacteristics()) {
                if(chars++>=32 || out.length()>4200) { out.append("Additional characteristics omitted\n"); break; }
                out.append("  Characteristic: ").append(BluetoothSession.serviceId(characteristic.getUuid())).append(" flags=").append(characteristic.getProperties()).append('\n');
            }
        }
        if(count==0) out.append("No services returned\n");
        return out.toString();
    }
    private void deadline() {
        if(!visible || !session.accepts(token)) return;
        if(session.remaining(now())>0) { handler.postDelayed(deadline,session.remaining(now())); return; }
        if(session.state()==State.BLE_SCAN || session.state()==State.CLASSIC_SCAN) complete(Reason.SCAN_FINISHED,"Scan finished · "+devices.size()+" devices listed (maximum 32)");
        else if(session.timeout(token,now())) finishExpired();
    }
    private void finishExpired() { save(); cleanup(); status="Bluetooth operation timed out"; notifyUi(); }
    private void complete(Reason reason,String text) { session.finish(token,State.COMPLETE,reason,0,now()); save(); cleanup(); status=text; notifyUi(); }
    private void fail(Reason reason,int code,String text) { session.finish(token,State.FAILED,reason,code,now()); save(); cleanup(); status=text; notifyUi(); }
    void stop(Reason reason) {
        boolean running=session.running(); session.stop(reason,now());
        if(running) { save(); status=reason==Reason.HIDDEN ? "Paused · choose an operation to resume" : "Stopped · connection released"; }
        cleanup(); notifyUi();
    }
    private void cleanup() {
        handler.removeCallbacks(deadline);
        if(scanner!=null && scanCallback!=null) try { scanner.stopScan(scanCallback); } catch(RuntimeException ignored) {}
        scanner=null; scanCallback=null;
        if(classicReceiver!=null) { try { context.unregisterReceiver(classicReceiver); } catch(RuntimeException ignored) {} classicReceiver=null; }
        if(ownClassic && adapter!=null) try { adapter.cancelDiscovery(); } catch(RuntimeException ignored) {}
        ownClassic=false; pairing=null;
        BluetoothGatt current=gatt; gatt=null;
        if(current!=null) { try { current.disconnect(); } catch(RuntimeException ignored) {} try { current.close(); } catch(RuntimeException ignored) {} }
    }
}
