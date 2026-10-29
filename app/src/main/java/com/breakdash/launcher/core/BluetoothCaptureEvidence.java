package com.breakdash.launcher.core;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/** Fixed failure vocabulary; exception messages and raw dumps never become report text. */
public final class BluetoothCaptureEvidence {
    public enum Stage { LISTENER, CONNECT, READ }
    private BluetoothCaptureEvidence() {}
    private static String header(boolean adb,Stage stage) {
        return "Capture method: "+(adb ? "local ADB" : "app process")+"\nCapture stage: "+stage+"\n";
    }
    public static String summary(boolean adb,String raw) {
        return header(adb,Stage.READ)+BluetoothManagerSummary.parse(raw);
    }
    public static String listenerUnavailable() {
        return header(true,Stage.LISTENER)+"Capture unavailable: local ADB listener unavailable on 127.0.0.1:5555. A configured port does not mean the daemon is running or reachable.\n";
    }
    public static String failure(boolean adb,Stage stage,Exception error) {
        String prefix=header(adb,stage);
        if(adb && stage==Stage.LISTENER) return prefix+"Capture unavailable: local ADB listener check failed. Exception details omitted.\n";
        if(!adb) {
            String projected=BluetoothManagerSummary.parse(error.getMessage());
            if(projected.startsWith("Bluetooth-manager capture denied:") || projected.equals("Android bluetooth_manager service not found.\n") || projected.equals("Bluetooth-manager dump timed out.\n")) return prefix+projected;
        }
        boolean timeout=false; Throwable cause=error;
        for(int i=0;i<8 && cause!=null;i++,cause=cause.getCause()) if(cause instanceof SocketTimeoutException || cause instanceof TimeoutException) timeout=true;
        if(adb && stage==Stage.CONNECT) return prefix+"Capture unavailable: local ADB connection/authorization "+(timeout ? "timed out" : "failed")+". Listener access alone does not confirm Android authorization. Open Local ADB setup, authorize if prompted, then retry.\n";
        return prefix+"Capture unavailable: Bluetooth status read "+(timeout ? "timed out" : "failed")+". Exception details omitted.\n";
    }
}
