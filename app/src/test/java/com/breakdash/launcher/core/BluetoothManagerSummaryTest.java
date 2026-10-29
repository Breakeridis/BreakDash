package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothManagerSummaryTest {
    @Test public void preservesStatusAndProfilesWithoutNamesAddressesOrRegistrationHistory() {
        String raw="Bluetooth Status\n  enabled: true\n  state: ON\n  address: 02:11:22:33:44:55\n  name: SYNTHETIC-PRIVATE-PHONE\nEnable log:\n  private-history and 021122334455\nBluetooth crashed 2 times\n2 BLE appsregistered\n  com.jiawei.maxobd\n  com.private.ownerapp\n\nProfile: GattService\nmScannerId: 4\nBonded devices:\n  PRIVATE-DEVICE 02:11:22:33:44:55\n";
        String result=BluetoothManagerSummary.parse(raw);
        assertTrue(result.contains("Manager enabled: true")); assertTrue(result.contains("Manager state: ON"));
        assertTrue(result.contains("Bluetooth crash count: 2")); assertTrue(result.contains("Registered BLE apps: 2"));
        assertTrue(result.contains("Registered BLE app: com.jiawei.maxobd")); assertTrue(result.contains("Reported profile: GattService"));
        assertTrue(result.contains("Stack mScannerId: 4"));
        for(String secret:new String[]{"PRIVATE","private-history","021122334455","02:11:22:33:44:55","com.private.ownerapp"}) assertFalse(secret,result.contains(secret));
        assertEquals(result,ReportPrivacy.text(result));
    }
    @Test public void unknownAndMalformedFieldsFailClosedInsteadOfPassingThroughRawText() {
        String result=BluetoothManagerSummary.parse("vendor private dump secret-owner\nBluetooth Status\nenabled: true private-name\nstate: ON private-name\nmState: 12 owner\nProfile: PrivatePhoneService\n");
        assertFalse(result.contains("private")); assertFalse(result.contains("owner")); assertFalse(result.contains("Manager state:"));
        assertTrue(BluetoothManagerSummary.parse("unknown private stuff").contains("No recognized status fields"));
    }
    @Test public void permissionAndUnavailableErrorsNeverIncludeSensitiveExceptionMessages() {
        String denied=BluetoothManagerSummary.parse("Permission Denial: can't dump from pid=123, uid=9999 PRIVATE-TOKEN");
        assertTrue(denied.contains("DUMP permission required")); assertFalse(denied.contains("PRIVATE")); assertFalse(denied.contains("9999"));
        assertTrue(BluetoothManagerSummary.parse("Can't find service: bluetooth_manager").contains("service not found"));
        assertTrue(BluetoothManagerSummary.parse("*** DUMP TIMEOUT *** private").contains("timed out"));
    }
    @Test public void captureBoundsAndMissingServiceAreExplicit() {
        String result=BluetoothManagerSummary.parse("Bluetooth Status\nstate: BLE_ON\nBluetooth Service not connected\n"+"X".repeat(70000));
        assertTrue(result.contains("Manager state: BLE_ON")); assertTrue(result.contains("adapter service not connected"));
        assertTrue(result.contains("capture limit")); assertTrue(result.length()<2200);
        assertTrue(BluetoothManagerSummary.parse(null).contains("No Bluetooth-manager output"));
    }
}
