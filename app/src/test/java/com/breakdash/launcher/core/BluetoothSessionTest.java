package com.breakdash.launcher.core;

import org.junit.Test;
import java.util.UUID;
import static com.breakdash.launcher.core.BluetoothSession.State.*;
import static com.breakdash.launcher.core.BluetoothSession.Reason.HIDDEN;
import static com.breakdash.launcher.core.BluetoothSession.Reason.PERMISSION_DENIED;
import static com.breakdash.launcher.core.BluetoothSession.Reason.MISSING_HARDWARE;
import static com.breakdash.launcher.core.BluetoothSession.Reason.LOCATION_OFF;
import static com.breakdash.launcher.core.BluetoothSession.Reason.BLUETOOTH_OFF;
import static com.breakdash.launcher.core.BluetoothSession.Reason.GATT_ERROR;
import static com.breakdash.launcher.core.BluetoothSession.Reason.SCAN_ERROR;
import static com.breakdash.launcher.core.BluetoothSession.Reason.EXCEPTION;
import static com.breakdash.launcher.core.BluetoothSession.Reason.REJECTED;
import static com.breakdash.launcher.core.BluetoothSession.Reason.SERVICES;
import static com.breakdash.launcher.core.BluetoothSession.Reason.SCAN_FINISHED;
import static org.junit.Assert.*;

public class BluetoothSessionTest {
    @Test public void hiddenScreenReleasesAnOperationAndRejectsEveryOldCallback() {
        int[] releases={0}; BluetoothSession session=new BluetoothSession(()->releases[0]++);
        long old=session.begin(CONNECTING,100);
        session.stop(HIDDEN,200);
        assertEquals(1,releases[0]); assertEquals(STOPPED,session.state());
        long current=session.begin(CONNECTING,300);
        assertFalse(session.connected(old,0,true,400));
        assertFalse(session.services(old,0,400));
        assertTrue(session.accepts(current)); assertEquals(CONNECTING,session.state());
        assertEquals(1,releases[0]);
    }
    @Test public void connectionAndServiceDiscoveryShareOneTwentyFiveSecondBudget() {
        int[] releases={0}; BluetoothSession session=new BluetoothSession(()->releases[0]++);
        long token=session.begin(CONNECTING,1000);
        assertTrue(session.connected(token,0,true,24000));
        assertTrue(session.discovering(token,24000));
        assertEquals(2000,session.remaining(24000));
        assertFalse(session.services(token,0,26000));
        assertEquals(FAILED,session.state()); assertEquals(1,releases[0]);
        assertTrue(session.evidence().contains("TIMEOUT"));
    }
    @Test public void lateSuccessfulConnectionCannotBeatAnExpiredTimer() {
        BluetoothSession session=new BluetoothSession(); long token=session.begin(CONNECTING,0);
        assertFalse(session.connected(token,0,true,25001)); assertEquals(FAILED,session.state());
    }
    @Test public void servicesCannotSucceedBeforeConnectionAndDiscovery() {
        BluetoothSession session=new BluetoothSession(); long token=session.begin(CONNECTING,0);
        assertFalse(session.services(token,0,10)); assertEquals(CONNECTING,session.state());
        assertTrue(session.connected(token,0,true,20)); assertEquals(CONNECTED,session.state());
        assertFalse(session.services(token,0,25));
        assertTrue(session.discovering(token,30)); assertTrue(session.services(token,0,40));
        assertEquals(COMPLETE,session.state()); assertFalse(session.accepts(token));
    }
    @Test public void terminalPathsReleaseResourcesExactlyOnce() {
        for(BluetoothSession.Reason reason:new BluetoothSession.Reason[]{PERMISSION_DENIED,MISSING_HARDWARE,LOCATION_OFF,BLUETOOTH_OFF,GATT_ERROR,SCAN_ERROR,EXCEPTION,REJECTED}) {
            int[] releases={0}; BluetoothSession session=new BluetoothSession(()->releases[0]++);
            long token=session.begin(CONNECTING,0);
            assertTrue(session.finish(token,FAILED,reason,133,10));
            assertFalse(session.finish(token,COMPLETE,SERVICES,0,11)); session.stop(HIDDEN,12);
            assertEquals(1,releases[0]); assertTrue(session.evidence().contains(reason.name()));
        }
    }
    @Test public void replacingScanInvalidatesItsResultsAndReleasesIt() {
        int[] releases={0}; BluetoothSession session=new BluetoothSession(()->releases[0]++);
        long classic=session.begin(CLASSIC_SCAN,0);
        assertEquals(12000,session.remaining(0));
        long ble=session.begin(BLE_SCAN,500);
        assertEquals(1,releases[0]); assertFalse(session.accepts(classic)); assertTrue(session.accepts(ble));
        assertTrue(session.finish(ble,COMPLETE,SCAN_FINISHED,0,12500)); assertEquals(2,releases[0]);
    }
    @Test public void pairingIsSeparateFromGattAndHasABoundedWait() {
        BluetoothSession session=new BluetoothSession(); long token=session.begin(PAIRING,0);
        assertFalse(session.connected(token,0,true,5)); assertFalse(session.discovering(token,5));
        assertTrue(session.timeout(token,30000)); assertEquals(FAILED,session.state());
    }
    @Test public void serviceDefinitionsSurviveTheExistingUploadFilter() {
        UUID id=UUID.fromString("12345678-1234-1234-1234-123456789abc");
        String bytes=BluetoothSession.serviceId(id);
        assertEquals("12 34 56 78 12 34 12 34 12 34 12 34 56 78 9A BC",bytes);
        assertEquals(bytes,ReportPrivacy.text(bytes));
        assertEquals(id.toString().replace("-",""),ReportPrivacy.text(bytes).replace(" ","").toLowerCase(java.util.Locale.ROOT));
        String report=ReportPrivacy.text("Service: "+bytes+"\nDevice: DEMO 021122334455 at 02:11:22:33:44:55\n");
        assertTrue(report.contains(bytes)); assertFalse(report.contains("021122334455")); assertFalse(report.contains("02:11:22:33:44:55"));
    }
    @Test public void numericFailureCodesAndStageTimingsRemainAvailable() {
        BluetoothSession session=new BluetoothSession(); long token=session.begin(CONNECTING,500);
        session.connected(token,0,true,700); session.discovering(token,710); session.services(token,133,900);
        String report=ReportPrivacy.text(session.evidence());
        assertTrue(report.contains("200 ms | CONNECTED")); assertTrue(report.contains("210 ms | DISCOVERING"));
        assertTrue(report.contains("400 ms | FAILED | GATT_ERROR | code 133"));
    }
}
