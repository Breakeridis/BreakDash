package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.breakdash.launcher.core.FactoryBluetoothProbeState.Stage.*;
import static com.breakdash.launcher.core.FactoryBluetoothProbeState.Outcome.*;

public class FactoryBluetoothProbeStateTest {
    @Test public void cancellationReleasesOnceAndRejectsLateSuccessAndStageChanges() {
        int[] released={0}; FactoryBluetoothProbeState state=new FactoryBluetoothProbeState(100,()->released[0]++);
        state.advance(BINDING,150);
        String result=state.finish(CANCELLED,200,"SYNTHETIC-PRIVATE");
        assertTrue(state.closed()); assertEquals(1,released[0]); assertTrue(result.contains("Result: CANCELLED"));
        assertFalse(result.contains("SYNTHETIC-PRIVATE"));
        state.advance(READING,210); assertNull(state.finish(COMPLETE,220,"late")); assertEquals(1,released[0]);
    }
    @Test public void lateVendorSuccessCannotOverrideTheHardSessionDeadline() {
        int[] released={0}; FactoryBluetoothProbeState state=new FactoryBluetoothProbeState(100,()->released[0]++);
        state.advance(READING,200);
        String result=state.finish(COMPLETE,8100,"late status must be discarded");
        assertTrue(result.contains("Result: TIMEOUT")); assertFalse(result.contains("late status must")); assertEquals(1,released[0]);
        assertNull(state.finish(TIMEOUT,8200,""));
    }
    @Test public void partialStatusKeepsEvidenceAndCleanupRunsForEveryFailure() {
        FactoryBluetoothProbeState partial=new FactoryBluetoothProbeState(0,()->{});
        partial.advance(READING,100); String result=partial.finish(PARTIAL,200,"isConnectA2DP: true\n");
        assertTrue(result.contains("isConnectA2DP: true"));
        for(FactoryBluetoothProbeState.Outcome outcome:FactoryBluetoothProbeState.Outcome.values()) {
            int[] released={0}; FactoryBluetoothProbeState state=new FactoryBluetoothProbeState(0,()->released[0]++);
            assertNotNull(state.finish(outcome,100,"")); assertEquals(1,released[0]);
            assertNull(state.finish(COMPLETE,200,"late")); assertEquals(1,released[0]);
        }
    }
    @Test public void cleanupFailureIsExplicitAndDoesNotReopenSession() {
        FactoryBluetoothProbeState state=new FactoryBluetoothProbeState(0,()->{ throw new IllegalArgumentException("SYNTHETIC-PRIVATE"); });
        String result=state.finish(BIND_REJECTED,100,"");
        assertTrue(result.contains("Cleanup could not be confirmed")); assertFalse(result.contains("SYNTHETIC-PRIVATE"));
        assertTrue(state.closed()); assertNull(state.finish(COMPLETE,200,""));
    }
    @Test public void discoveryHasItsOwnDeadlineWithoutChangingStatusDeadline() {
        FactoryBluetoothProbeState discovery=new FactoryBluetoothProbeState(0,()->{},20000);
        discovery.advance(SCANNING,100); assertFalse(discovery.expired(13000));
        discovery.advance(STOPPING,13000); assertTrue(discovery.finish(COMPLETE,13001,"").contains("Result: COMPLETE"));
        FactoryBluetoothProbeState status=new FactoryBluetoothProbeState(0,()->{});
        assertTrue(status.expired(8000));
    }
}
