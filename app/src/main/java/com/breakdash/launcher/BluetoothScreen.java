package com.breakdash.launcher;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.breakdash.launcher.core.BluetoothSession.Reason;

/** Native page; the selected device stays in this page so details never hide its connection owner. */
final class BluetoothScreen {
    private final MainActivity main;
    private final Ui ui;
    private final Panels.Panel panel;
    private final BluetoothController controller;
    private final LinearLayout page;
    private final TextView status;
    private final Button[] tabs=new Button[3];
    private int tab;
    private BluetoothController.Device selected;
    private Tasks.Scope inspection;
    private String inspectionStatus="";
    BluetoothScreen(MainActivity main) {
        this.main=main; ui=main.ui;
        controller=new BluetoothController(main,this::render);
        panel=main.panels.new Panel("Bluetooth",false,true);
        LinearLayout rail=ui.row();
        String[] names={"Devices","Phone & Music","Diagnostics"};
        for(int i=0;i<names.length;i++) {
            final int index=i; tabs[i]=ui.button(names[i],()->{ controller.stop(Reason.USER_STOP); tab=index; render(); });
            ui.weighted(rail,tabs[i],72);
        }
        panel.body.addView(rail); ui.gap(panel.body,10);
        status=ui.text(controller.status,20,ui.text); status.setMinHeight(ui.dp(48)); panel.body.addView(status); ui.gap(panel.body,8);
        ScrollView scroll=new ScrollView(main); page=ui.column(); scroll.addView(page); panel.body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panel.visibility=value->{
            controller.visible(value);
            if(!value && inspection!=null) { inspection.close(); inspection=null; inspectionStatus="Inspection cancelled · tap to run again"; }
            if(value) render();
        };
        panel.dismissed=()->main.panels.bluetoothClosed(this);
        // The Panel constructor already marked itself visible before our callback existed.
        panel.visibility.accept(panel.top()); render();
    }
    void permissionsChanged() { controller.refreshPaired(); render(); }
    private Button action(String label,Runnable run) {
        Button button=ui.button(label,run); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,ui.dp(72)); params.bottomMargin=ui.dp(8); page.addView(button,params); return button;
    }
    private void text(String value) { TextView label=ui.text(value,18,ui.muted); label.setPadding(0,ui.dp(8),0,ui.dp(12)); page.addView(label); }
    private void render() {
        if(!panel.scope.active()) return;
        for(int i=0;i<tabs.length;i++) ui.selected(tabs[i],i==tab);
        status.setText(controller.status); page.removeAllViews();
        if(tab==0) devices(); else if(tab==1) phoneMusic(); else diagnostics();
    }
    private void permissions() {
        controller.stop(Reason.USER_STOP);
        if(Build.VERSION.SDK_INT>=31) main.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},43);
        else main.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},43);
    }
    private void devices() {
        if(!BluetoothDiagnostics.scanAllowed(main)) {
            text(Build.VERSION.SDK_INT<31 ? "Location permission is required for Bluetooth discovery on this Android version." : "Allow Bluetooth access to discover and inspect devices.");
            action("Allow Bluetooth access",this::permissions);
        }
        action("Bluetooth settings / forget a device",()->settings(Settings.ACTION_BLUETOOTH_SETTINGS));
        if(Build.VERSION.SDK_INT<31 && !BluetoothDiagnostics.locationEnabled(main)) action("Turn on Location for scanning",()->settings(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        LinearLayout scans=ui.row();
        ui.weighted(scans,ui.button("Scan Classic",()->{ selected=null; controller.scan(false); render(); }),72);
        ui.weighted(scans,ui.button("Scan BLE",()->{ selected=null; controller.scan(true); render(); }),72);
        ui.weighted(scans,ui.button("Stop / Disconnect",()->controller.stop(Reason.USER_STOP)),72);
        page.addView(scans); ui.gap(page,8);
        action("Refresh paired devices",()->{ controller.refreshPaired(); render(); });
        text("Scans stop after 12 seconds. Select a device below. Paired does not mean connected. Names and addresses stay on this device.");
        if(selected!=null) selection();
        if(controller.devices.isEmpty()) text("No devices listed. Try BLE for the KW906. Stop other scans and disconnect the scanner from your phone before testing.");
        for(BluetoothController.Device device:controller.devices) {
            String label=device.name+"\n"+device.transport()+" · "+device.state()+(device.rssi==null || device.rssi==Short.MIN_VALUE ? "" : " · "+device.rssi+" dBm");
            Button row=action(label,()->{ controller.stop(Reason.USER_STOP); selected=device; render(); }); row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); row.setMaxLines(3);
            ui.selected(row,selected==device);
        }
    }
    private void selection() {
        ui.heading(page,"SELECTED DEVICE"); text(selected.name+" · "+selected.transport()+" · "+selected.state());
        text(localAddress(selected));
        boolean classic=selected.classic || selected.type==BluetoothDevice.DEVICE_TYPE_CLASSIC || selected.type==BluetoothDevice.DEVICE_TYPE_DUAL;
        boolean ble=selected.ble || selected.type==BluetoothDevice.DEVICE_TYPE_LE || selected.type==BluetoothDevice.DEVICE_TYPE_DUAL;
        final BluetoothController.Device device=selected;
        Button pair=action("Pair using Android",()->controller.pair(device)); pair.setEnabled(classic && device.bond==BluetoothDevice.BOND_NONE);
        Button test=action("Test BLE connection",()->controller.test(device)); test.setEnabled(ble);
        text("The BLE test connects and lists available services, then disconnects. It does not read car data or send vehicle commands. Open MAXOBD after the test to use the scanner.");
    }
    @android.annotation.SuppressLint("MissingPermission")
    private String localAddress(BluetoothController.Device device) {
        if(BluetoothDiagnostics.connectAllowed(main)) try { return "Address (local only): "+device.handle.getAddress(); } catch(RuntimeException ignored) {}
        return "Address unavailable";
    }
    private void phoneMusic() {
        text("Use the working factory apps for calls and Bluetooth audio during this diagnostic stage. Their verified control interfaces will guide the integrated replacement.");
        launchButton("Open factory phone","com.nwd.android.phone");
        launchButton("Open Bluetooth music","com.nwd.bt.music");
        launchButton("Open MAXOBD","com.jiawei.maxobd");
    }
    private void launchButton(String title,String name) {
        Intent intent=main.getPackageManager().getLaunchIntentForPackage(name);
        Button button=action(intent==null ? title+" · unavailable" : title,()->{ controller.stop(Reason.USER_STOP); main.launchPackage(name); }); button.setEnabled(intent!=null);
    }
    private void diagnostics() {
        text("Run a BLE connection test for the KW906, then send feedback. Factory inspection reads package/interface definitions without calling unknown vendor controls.");
        Button inspect=action(inspection==null ? "Inspect factory interfaces" : "Inspecting…",this::inspect); inspect.setEnabled(inspection==null);
        if(!inspectionStatus.isEmpty()) text(inspectionStatus);
        action("Refresh snapshot",this::render);
        action("Send feedback",()->{ controller.stop(Reason.USER_STOP); main.panels.report(); });
        action("Clear Bluetooth diagnostic history",()->{ controller.stop(Reason.USER_STOP); BluetoothDiagnostics.clear(main); inspectionStatus=""; render(); });
        TextView report=ui.text(BluetoothDiagnostics.collect(main),14,ui.muted); report.setTextIsSelectable(true); page.addView(report);
    }
    private void inspect() {
        if(inspection!=null || !panel.top()) return;
        controller.stop(Reason.USER_STOP); inspectionStatus="Inspecting read-only factory metadata…"; inspection=main.tasks.scope();
        inspection.run(()->BluetoothFactoryInspector.inspect(main.getApplicationContext()),result->{
            if(inspection!=null) { inspection.close(); inspection=null; }
            if(!panel.top()) return;
            BluetoothDiagnostics.factory(main,result); inspectionStatus="Inspection saved for feedback"; render();
        },error->{ if(inspection!=null) { inspection.close(); inspection=null; } inspectionStatus="Inspection unavailable on this firmware"; render(); });
        render();
    }
    private void settings(String action) {
        controller.stop(Reason.USER_STOP);
        try { main.openExternal(new Intent(action)); } catch(RuntimeException error) { main.toast("Settings unavailable on this firmware"); }
    }
}
