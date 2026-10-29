package com.breakdash.launcher.core;

import com.bt.fixture.SdkFixture;
import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothSdkSchemaTest {
    @Test public void definitionsIncludeInitializationInheritanceAndCallbackWithoutExecutingSdk() throws Exception {
        Class<?> type=Class.forName("com.bt.fixture.SdkFixture",false,getClass().getClassLoader());
        assertFalse(SdkFixture.Tracker.initialized);
        BluetoothSdkSchema.Result result=BluetoothSdkSchema.inspect(type,5000,()->false);
        assertFalse(SdkFixture.Tracker.initialized);
        assertTrue(result.text.contains("Constructor private(java.lang.Object)"));
        assertTrue(result.text.contains("static com.bt.fixture.SdkFixture getInstance(java.lang.Object)"));
        assertTrue(result.text.contains("Extends com.bt.fixture.FixtureParent"));
        assertTrue(result.text.contains("connectService(com.bt.fixture.SdkFixture$ScanCallback) [inherited:"));
        assertTrue(result.text.contains("startBleScan(com.bt.fixture.SdkFixture$ScanCallback)"));
        assertTrue(result.text.indexOf("getInstance")<result.text.indexOf("startBleScan"));
        assertTrue(result.text.indexOf("startBleScan")<result.text.indexOf("btReset"));
        assertTrue(result.related.contains(SdkFixture.ScanCallback.class));
        assertTrue(result.text.contains("Field type only: private static com.bt.fixture.SdkFixture instance"));
    }
    @Test public void cancellationAndSmallBudgetsAreBounded() throws Exception {
        Class<?> type=Class.forName("com.bt.fixture.SdkFixture",false,getClass().getClassLoader());
        assertTrue(BluetoothSdkSchema.inspect(type,500,()->true).text.contains("Cancelled/time budget"));
        for(int limit:new int[]{0,10,100,400}) assertTrue(BluetoothSdkSchema.inspect(type,limit,()->false).text.length()<=limit);
        assertFalse(SdkFixture.Tracker.initialized);
    }
}
