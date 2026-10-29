package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothDiscoveryTest {
    @Test public void repeatedAdvertisementsUpdateOneExistingRowWithoutRecreatingIt() {
        BluetoothDiscovery<String,int[]> devices=new BluetoothDiscovery<>();
        int[] row=devices.getOrAdd("synthetic-device",()->new int[]{-80});
        int[] duplicate=devices.getOrAdd(new String("synthetic-device"),()->{ throw new AssertionError("Duplicate allocation"); });
        duplicate[0]=-55;
        assertSame(row,duplicate); assertEquals(1,devices.rows().size()); assertEquals(-55,devices.rows().get(0)[0]);
    }
    @Test public void capacityRejectsNewDevicesButRetainsUpdatesAndOrder() {
        BluetoothDiscovery<Integer,String> devices=new BluetoothDiscovery<>();
        for(int i=0;i<32;i++) { final int id=i; assertEquals("row-"+i,devices.getOrAdd(i,()->"row-"+id)); }
        assertNull(devices.getOrAdd(32,()->{ throw new AssertionError("Over-cap allocation"); }));
        assertEquals("row-0",devices.getOrAdd(0,()->"replacement"));
        assertEquals("row-31",devices.rows().get(31)); assertEquals(32,devices.rows().size());
        devices.clear(); assertTrue(devices.rows().isEmpty()); assertEquals("fresh",devices.getOrAdd(32,()->"fresh"));
    }
    @Test public void failedRowCreationDoesNotConsumeAnInventorySlot() {
        BluetoothDiscovery<Integer,String> devices=new BluetoothDiscovery<>();
        assertThrows(IllegalStateException.class,()->devices.getOrAdd(1,()->{ throw new IllegalStateException(); }));
        assertTrue(devices.rows().isEmpty()); assertEquals("retry",devices.getOrAdd(1,()->"retry"));
        assertThrows(UnsupportedOperationException.class,()->devices.rows().clear());
    }
}
