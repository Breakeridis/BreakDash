package com.breakdash.launcher;

import com.breakdash.launcher.core.BluetoothManagerSummary;
import dadb.Dadb;

/** Explicit, finite read of one fixed service; raw output lives only in the worker until projected. */
final class BluetoothManagerCapture implements AutoCloseable {
    private boolean closed;
    private Dadb connection;
    String read(SystemTools system,boolean useAdb) throws InterruptedException {
        try {
            if(Thread.currentThread().isInterrupted() || isClosed()) throw new InterruptedException();
            String raw;
            if(useAdb) {
                Dadb adb=system.bluetoothDiagnosticConnection();
                synchronized(this) { if(closed) { adb.close(); throw new InterruptedException(); } connection=adb; }
                // Fixed read-only command, server-side timeout and output limit. No user input is interpolated.
                raw=adb.shell("dumpsys -t 4 bluetooth_manager | head -c 65536").getAllOutput();
            } else raw=SystemTools.process(new String[]{"/system/bin/dumpsys","-t","4","bluetooth_manager"},6);
            if(Thread.currentThread().isInterrupted() || isClosed()) throw new InterruptedException();
            return "Capture method: "+(useAdb ? "authorized local ADB" : "app process")+"\n"+BluetoothManagerSummary.parse(raw);
        } catch(InterruptedException error) { throw error; }
        catch(Exception error) {
            if(isClosed()) throw new InterruptedException();
            String projected=BluetoothManagerSummary.parse(error.getMessage());
            if(!useAdb && (projected.startsWith("Bluetooth-manager capture denied") || projected.startsWith("Android bluetooth_manager service not found") || projected.startsWith("Bluetooth-manager dump timed out"))) return projected;
            return "Bluetooth-manager capture unavailable: "+error.getClass().getSimpleName()+". Use local ADB only after enabling TCP debugging and authorizing this app on the head unit.\n";
        } finally { close(); }
    }
    private synchronized boolean isClosed() { return closed; }
    @Override public synchronized void close() {
        closed=true;
        if(connection!=null) { try { connection.close(); } catch(Exception ignored) {} connection=null; }
    }
}
