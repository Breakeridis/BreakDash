package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FactoryDiscoveryLeaseTest {
    private static final class Commands implements FactoryDiscoveryLease.Commands {
        int starts,stops; boolean accept=true,confirm=true;
        public boolean start() { starts++; return accept; }
        public boolean stop() { stops++; return confirm; }
    }
    @Test public void acceptedSearchStopsOnceAtEnd() throws Exception {
        Commands commands=new Commands(); int[] waited={0};
        FactoryDiscoveryLease.Result result=FactoryDiscoveryLease.run(commands,()->false,()->waited[0]++,()->{});
        assertTrue(result.accepted); assertTrue(result.stopConfirmed); assertEquals(1,commands.starts); assertEquals(1,commands.stops); assertEquals(1,waited[0]);
    }
    @Test public void rejectedSearchDoesNotStopSomebodyElsesDiscovery() throws Exception {
        Commands commands=new Commands(); commands.accept=false;
        FactoryDiscoveryLease.Result result=FactoryDiscoveryLease.run(commands,()->false,()->{ throw new AssertionError(); },()->{ throw new AssertionError(); });
        assertFalse(result.accepted); assertEquals(0,commands.stops);
    }
    @Test public void cancellationDuringWaitStillStopsAcceptedSearch() throws Exception {
        Commands commands=new Commands();
        try { FactoryDiscoveryLease.run(commands,()->false,()->{ throw new InterruptedException(); },()->{}); fail(); }
        catch(InterruptedException expected) { assertEquals(1,commands.stops); }
    }
    @Test public void cancellationWhileStartReturnsDoesNotWaitButStillStops() throws Exception {
        Commands commands=new Commands();
        FactoryDiscoveryLease.Result result=FactoryDiscoveryLease.run(commands,()->commands.starts>0,()->{ throw new AssertionError(); },()->{});
        assertTrue(result.accepted); assertEquals(1,commands.stops);
    }
    @Test public void stopRejectionRemainsExplicit() throws Exception {
        Commands commands=new Commands(); commands.confirm=false;
        assertFalse(FactoryDiscoveryLease.run(commands,()->false,()->{},()->{}).stopConfirmed);
    }
}
