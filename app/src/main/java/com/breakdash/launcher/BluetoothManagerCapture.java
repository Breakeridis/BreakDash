package com.breakdash.launcher;

import com.breakdash.launcher.core.BluetoothCaptureEvidence;
import com.breakdash.launcher.core.BluetoothCaptureEvidence.Stage;
import dadb.Dadb;

/** Explicit, finite read of one fixed service; raw output lives only in the worker until projected. */
final class BluetoothManagerCapture implements AutoCloseable {
    private boolean closed;
    private Dadb connection;
    String read(SystemTools system,boolean useAdb) throws InterruptedException {
        Stage stage=useAdb ? Stage.LISTENER : Stage.READ;
        try {
            if(Thread.currentThread().isInterrupted() || isClosed()) throw new InterruptedException();
            String raw;
            if(useAdb) {
                if(!system.portOpen()) return BluetoothCaptureEvidence.listenerUnavailable();
                if(Thread.currentThread().isInterrupted() || isClosed()) throw new InterruptedException();
                stage=Stage.CONNECT;
                Dadb adb=system.bluetoothDiagnosticConnection();
                synchronized(this) { if(closed) { adb.close(); throw new InterruptedException(); } connection=adb; }
                stage=Stage.READ;
                // Fixed read-only command, server-side timeout and output limit. No user input is interpolated.
                raw=adb.shell("dumpsys -t 4 bluetooth_manager | head -c 65536").getAllOutput();
            } else raw=SystemTools.process(new String[]{"/system/bin/dumpsys","-t","4","bluetooth_manager"},6);
            if(Thread.currentThread().isInterrupted() || isClosed()) throw new InterruptedException();
            return BluetoothCaptureEvidence.summary(useAdb,raw);
        } catch(InterruptedException error) { throw error; }
        catch(Exception error) {
            if(isClosed() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            return BluetoothCaptureEvidence.failure(useAdb,stage,error);
        } finally { close(); }
    }
    private synchronized boolean isClosed() { return closed; }
    @Override public synchronized void close() {
        closed=true;
        if(connection!=null) { try { connection.close(); } catch(Exception ignored) {} connection=null; }
    }
}
