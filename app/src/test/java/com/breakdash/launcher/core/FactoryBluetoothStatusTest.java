package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FactoryBluetoothStatusTest {
    public interface StatusContract {
        boolean isBlueToothPowerOn(); boolean isConnectDevice(); boolean isConnectHFP();
        boolean isConnectA2DP(); boolean isConnectAVRCP(); boolean isSppConnect();
        int getBleRemoteState(); int getDiscoverMode(); int getAutoFeature(); int getLocalDeviceFeature();
        String getPinCode(); String getLocalDeviceName(); void btReset();
    }
    public static final class Feature implements StatusContract {
        int calls,privateReads,commands;
        public boolean isBlueToothPowerOn() { calls++; return true; }
        public boolean isConnectDevice() { calls++; return false; }
        public boolean isConnectHFP() { calls++; return false; }
        public boolean isConnectA2DP() { calls++; return true; }
        public boolean isConnectAVRCP() { calls++; return true; }
        public boolean isSppConnect() { calls++; return false; }
        public int getBleRemoteState() { calls++; return 7; }
        public int getDiscoverMode() { calls++; return -1; }
        public int getAutoFeature() { calls++; return 3; }
        public int getLocalDeviceFeature() { calls++; return 9; }
        public String getPinCode() { privateReads++; throw new AssertionError("No private data"); }
        public String getLocalDeviceName() { privateReads++; throw new AssertionError("No private data"); }
        public void btReset() { commands++; throw new AssertionError("No control calls"); }
        @Override public String toString() { throw new AssertionError("No object dumping"); }
    }
    @Test public void callsOnlyPrimitiveAllowlistWithoutPrivateGettersOrCommands() throws Exception {
        Feature feature=new Feature(); FactoryBluetoothStatus.Reading result=new FactoryBluetoothStatus(StatusContract.class).read(feature,()->false);
        assertTrue(result.complete()); assertEquals(10,result.values); assertEquals(10,feature.calls);
        assertEquals(0,feature.privateReads); assertEquals(0,feature.commands);
        assertTrue(result.text.contains("isConnectA2DP: true")); assertTrue(result.text.contains("getBleRemoteState: 7"));
        assertTrue(result.text.contains("getDiscoverMode: -1"));
        assertFalse(result.text.contains("getPinCode")); assertFalse(result.text.contains("getLocalDeviceName"));
        assertTrue(ReportPrivacy.text(result.text).contains("getBleRemoteState: 7"));
    }
    @Test public void cancellationStopsBeforeNextVendorRead() {
        Feature feature=new Feature();
        try { new FactoryBluetoothStatus(StatusContract.class).read(feature,()->feature.calls>=1); fail(); }
        catch(InterruptedException expected) { assertEquals(1,feature.calls); }
        assertEquals(0,feature.privateReads); assertEquals(0,feature.commands);
    }
    public static final class Unsupported {
        int invoked;
        public String isBlueToothPowerOn() { invoked++; return "SYNTHETIC-PRIVATE"; }
        public static boolean isConnectDevice() { throw new AssertionError("No static invocation"); }
        public Boolean isConnectHFP() { invoked++; return true; }
        public boolean isConnectA2DP() { throw new SecurityException("SYNTHETIC-PRIVATE 12:34:56:78:90:AB"); }
    }
    @Test public void wrongSignaturesMissingMethodsAndExceptionMessagesFailClosed() throws Exception {
        Unsupported feature=new Unsupported(); FactoryBluetoothStatus.Reading result=new FactoryBluetoothStatus(Unsupported.class).read(feature,()->false);
        assertEquals(0,feature.invoked); assertEquals(0,result.values); assertFalse(result.complete());
        assertTrue(result.text.contains("UNSUPPORTED_SIGNATURE")); assertTrue(result.text.contains("UNAVAILABLE"));
        assertFalse(result.text.contains("SYNTHETIC-PRIVATE")); assertFalse(result.text.contains("12:34"));
    }
    @Test(expected=IllegalArgumentException.class) public void mismatchedFeatureIsNotInvoked() throws Exception {
        new FactoryBluetoothStatus(StatusContract.class).read(new Object(),()->false);
    }
}
