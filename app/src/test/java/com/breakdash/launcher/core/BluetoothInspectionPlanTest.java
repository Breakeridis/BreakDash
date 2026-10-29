package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothInspectionPlanTest {
    @Test public void unrelatedCanAndSettingsCannotCrowdOutBluetoothNamespaces() {
        BluetoothInspectionPlan plan=new BluetoothInspectionPlan();
        for(int i=0;i<100;i++) { plan.offer("com.nwd.can.ICanFeature"+i); plan.offer("com.nwd.setting.service.ISetting"+i); }
        plan.offer("com.visualframe.btphone.IBluetoothFeature");
        plan.offer("com.vendor.gocsdk.BluetoothManager");
        assertEquals(2,plan.names().size());
        assertTrue(plan.names().contains("com.visualframe.btphone.IBluetoothFeature"));
        assertTrue(plan.names().contains("com.vendor.gocsdk.BluetoothManager"));
    }
    @Test public void contractsDeduplicateStubsAndExcludeProxiesAndGeneratedUiClasses() {
        BluetoothInspectionPlan plan=new BluetoothInspectionPlan();
        plan.offer("com.demo.bluetooth.IBtFeature"); plan.offer("com.demo.bluetooth.IBtFeature$Stub");
        plan.offer("com.demo.bluetooth.IBtFeature$Stub$Proxy"); plan.offer("com.demo.bluetooth.R$id");
        plan.offer("com.demo.bluetooth.Screen$1"); plan.offer("android.bluetooth.BluetoothAdapter");
        assertEquals(1,plan.names().size()); assertEquals("com.demo.bluetooth.IBtFeature",plan.names().get(0));
    }
    @Test public void importantLateClassesReplaceLowerPriorityUiEntriesAtCapacity() {
        BluetoothInspectionPlan plan=new BluetoothInspectionPlan();
        for(int i=0;i<100;i++) plan.offer("com.demo.btphone.Screen"+i);
        plan.offer("com.demo.btphone.IBluetoothFeature"); plan.offer("com.demo.btphone.ConnectionManager");
        assertEquals(16,plan.names().size());
        assertEquals("com.demo.btphone.IBluetoothFeature",plan.names().get(0));
        assertEquals("com.demo.btphone.ConnectionManager",plan.names().get(1));
    }
    @Test public void invalidNamesAndUnrelatedPackagesAreNotInspectionTargets() {
        for(String name:new String[]{null,"","com.demo.bluetooth.Name\nsecret","com.demo.bluetooth.A/B","com.demo.other.Api","java.bluetooth.Example"}) assertNull(BluetoothInspectionPlan.contract(name));
        assertEquals(1,BluetoothInspectionPlan.methodPriority("startBleScan"));
        assertEquals(3,BluetoothInspectionPlan.methodPriority("getConnectionState"));
        assertEquals(4,BluetoothInspectionPlan.methodPriority("onCreate"));
    }
    @Test public void observedEntryPointsAndBleSurviveConstantsAndAlternateBackends() {
        BluetoothInspectionPlan plan=new BluetoothInspectionPlan();
        for(int i=0;i<100;i++) { plan.offer("com.bt.BTConstant$Feature"+i); plan.offer("com.bt.bc03.Manager"+i); }
        plan.offer("com.bt.hd.link.BluetoothAPI"); plan.offer("com.bt.hd.link.AbsServiceControllerHelper");
        plan.offer("com.bt.BTFeature"); plan.offer("com.bt.BTSourceFeature"); plan.offer("com.bt.vendor.BleManager");
        assertEquals("com.bt.hd.link.BluetoothAPI",plan.names().get(0));
        assertTrue(plan.names().contains("com.bt.BTSourceFeature"));
        assertTrue(plan.names().contains("com.bt.vendor.BleManager"));
        assertFalse(BluetoothInspectionPlan.hasBleMarker("enableBluetooth"));
        assertTrue(BluetoothInspectionPlan.methodPriority("getInstance")<BluetoothInspectionPlan.methodPriority("startBleScan"));
        assertTrue(BluetoothInspectionPlan.methodPriority("startBleScan")<BluetoothInspectionPlan.methodPriority("btReset"));
    }
}
