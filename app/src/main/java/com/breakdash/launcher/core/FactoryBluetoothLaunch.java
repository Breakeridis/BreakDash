package com.breakdash.launcher.core;

/** One user request waits for asynchronous panel dismissal; it cannot restart on a later dashboard visit. */
public final class FactoryBluetoothLaunch {
    public static final long WAIT_MS=3000;
    public enum Decision { NONE, WAIT, START, EXPIRED }
    private boolean pending;
    private long requested;
    public boolean request(long now) {
        if(pending) return false;
        requested=now; pending=true; return true;
    }
    public boolean pending() { return pending; }
    public Decision update(boolean dashboardVisible,long now) {
        if(!pending) return Decision.NONE;
        if(now-requested>=WAIT_MS) { pending=false; return Decision.EXPIRED; }
        if(!dashboardVisible) return Decision.WAIT;
        pending=false; return Decision.START;
    }
    public boolean cancel() { boolean wasPending=pending; pending=false; return wasPending; }
}
