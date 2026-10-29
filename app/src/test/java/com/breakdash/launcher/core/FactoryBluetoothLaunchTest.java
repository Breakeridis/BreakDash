package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.breakdash.launcher.core.FactoryBluetoothLaunch.Decision.*;

public class FactoryBluetoothLaunchTest {
    @Test public void waitsForAllDismissCallbacksAndStartsExactlyOnce() {
        FactoryBluetoothLaunch launch=new FactoryBluetoothLaunch();
        assertTrue(launch.request(100));
        assertEquals(WAIT,launch.update(false,101)); // dismiss() returned, OnDismiss has not run
        assertEquals(WAIT,launch.update(false,102)); // first panel removed, another is still closing
        assertEquals(START,launch.update(true,103)); // resumed dashboard, stack now empty
        assertFalse(launch.pending()); assertEquals(NONE,launch.update(true,104));
        assertEquals(NONE,launch.update(true,3200)); // old timeout cannot start or expire a completed handoff
    }
    @Test public void backgroundOrNewPanelCancellationNeverStartsOnLaterResume() {
        FactoryBluetoothLaunch launch=new FactoryBluetoothLaunch(); launch.request(0);
        assertEquals(WAIT,launch.update(false,10)); assertTrue(launch.cancel());
        assertEquals(NONE,launch.update(true,100)); assertFalse(launch.cancel());
    }
    @Test public void unavailableDashboardExpiresWithoutLaterAutomaticRetry() {
        FactoryBluetoothLaunch launch=new FactoryBluetoothLaunch(); launch.request(0);
        assertEquals(WAIT,launch.update(false,2999)); assertEquals(EXPIRED,launch.update(false,3000));
        assertEquals(NONE,launch.update(true,3001));
        assertTrue(launch.request(4000)); assertEquals(START,launch.update(true,4001));
    }
    @Test public void duplicateTapDoesNotExtendTheHandoffDeadline() {
        FactoryBluetoothLaunch launch=new FactoryBluetoothLaunch(); launch.request(0);
        assertFalse(launch.request(2000)); assertEquals(EXPIRED,launch.update(true,3000));
    }
}
