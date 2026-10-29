package com.breakdash.launcher.core;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.breakdash.launcher.core.BluetoothCaptureEvidence.Stage.*;

public class BluetoothCaptureEvidenceTest {
    @Test public void everyFailureRetainsMethodAndStageWithoutExceptionText() {
        String privateText="SYNTHETIC-PRIVATE-DEVICE 12:34:56:78:90:AB";
        String app=BluetoothCaptureEvidence.failure(false,READ,new IOException("Permission denial: requires android.permission.DUMP "+privateText));
        assertTrue(app.contains("method: app process")); assertTrue(app.contains("stage: READ")); assertTrue(app.contains("capture denied")); assertFalse(app.contains(privateText));
        String connect=BluetoothCaptureEvidence.failure(true,CONNECT,new IOException(privateText));
        assertTrue(connect.contains("method: local ADB")); assertTrue(connect.contains("stage: CONNECT")); assertTrue(connect.contains("connection/authorization failed")); assertFalse(connect.contains(privateText));
        String read=BluetoothCaptureEvidence.failure(true,READ,new IOException(privateText));
        assertTrue(read.contains("stage: READ")); assertFalse(read.contains("authorization failed")); assertFalse(read.contains(privateText));
    }
    @Test public void listenerConnectionAndReadTimeoutAreDistinct() {
        assertTrue(BluetoothCaptureEvidence.listenerUnavailable().contains("stage: LISTENER"));
        assertTrue(BluetoothCaptureEvidence.listenerUnavailable().contains("listener unavailable"));
        String connect=BluetoothCaptureEvidence.failure(true,CONNECT,new IOException(new SocketTimeoutException("SYNTHETIC-PRIVATE")));
        assertTrue(connect.contains("connection/authorization timed out")); assertFalse(connect.contains("SYNTHETIC-PRIVATE"));
        assertTrue(BluetoothCaptureEvidence.failure(true,READ,new SocketTimeoutException()).contains("status read timed out"));
    }
    @Test public void successfulAndRejectedDumpsUseSamePrivacyProjection() {
        String raw="Bluetooth Status\nenabled: true\nstate: ON\nname: SYNTHETIC-PRIVATE\n";
        for(boolean adb:new boolean[]{false,true}) {
            String summary=BluetoothCaptureEvidence.summary(adb,raw);
            assertTrue(summary.contains("Manager enabled: true")); assertFalse(summary.contains("SYNTHETIC-PRIVATE"));
            assertTrue(BluetoothCaptureEvidence.summary(adb,"Permission denial: android.permission.DUMP").contains("capture denied"));
        }
    }
}
