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
        assertTrue(result.text.indexOf("forContext")<result.text.indexOf("startBleScan"));
        assertTrue(result.text.indexOf("startBleScan")<result.text.indexOf("btReset"));
        assertTrue(result.related.contains(SdkFixture.ScanCallback.class));
        assertTrue(result.text.contains("Field type only: private static com.bt.fixture.SdkFixture instance"));
        assertTrue(result.text.contains("getFeature() [inherited: com.android.utils.utils.AbsServiceControllerHelper]"));
        assertTrue(result.related.contains(com.android.utils.utils.AbsServiceControllerHelper.OnServiceConnectSuccessListener.class));
        assertTrue(result.complete);
    }
    @Test public void cancellationAndSmallBudgetsAreBounded() throws Exception {
        Class<?> type=Class.forName("com.bt.fixture.SdkFixture",false,getClass().getClassLoader());
        assertTrue(BluetoothSdkSchema.inspect(type,500,()->true).text.contains("Cancelled/time budget"));
        for(int limit:new int[]{0,10,100,400}) assertTrue(BluetoothSdkSchema.inspect(type,limit,()->false).text.length()<=limit);
        assertFalse(SdkFixture.Tracker.initialized);
    }
    @Test public void onlyCompleteIdenticalCapturedSchemasDeduplicateAcrossPackages() throws Exception {
        Class<?> type=Class.forName("com.bt.fixture.SdkFixture",false,getClass().getClassLoader());
        BluetoothSdkSchema.Result schema=BluetoothSdkSchema.inspect(type,5000,()->false);
        BluetoothSdkSchema.Deduplicator cache=new BluetoothSdkSchema.Deduplicator();
        cache.display("factory.too-small",type.getName(),schema,20);
        assertEquals(schema.text,cache.display("factory.one",type.getName(),schema));
        assertTrue(cache.display("factory.two",type.getName(),schema).contains("Same complete captured schema as factory.one"));
        BluetoothSdkSchema.Result changed=new BluetoothSdkSchema.Result(schema.text+"different definition\n",schema.related,true);
        assertEquals(changed.text,cache.display("factory.three",type.getName(),changed));
        BluetoothSdkSchema.Result small=BluetoothSdkSchema.inspect(type,300,()->false);
        assertFalse(small.complete);
        assertEquals(small.text,cache.display("factory.four",type.getName(),small));
        assertEquals(small.text,cache.display("factory.five",type.getName(),small));
        assertFalse(SdkFixture.Tracker.initialized);
    }
    @Test public void omittedFieldDefinitionsPreventFalseDeduplication() {
        BluetoothSdkSchema.Result result=BluetoothSdkSchema.inspect(FieldRich.class,5000,()->false);
        assertFalse(result.complete); assertTrue(result.text.contains("Additional field definitions omitted"));
        BluetoothSdkSchema.Deduplicator cache=new BluetoothSdkSchema.Deduplicator();
        assertEquals(result.text,cache.display("factory.one",FieldRich.class.getName(),result));
        assertEquals(result.text,cache.display("factory.two",FieldRich.class.getName(),result));
    }
    public static class FieldRich { public Object listener1,listener2,listener3,listener4,listener5,listener6,listener7; }
}
