package com.breakdash.launcher.core;

/** Finite session gate: closes resources once and rejects all late completions. */
public final class FactoryBluetoothProbeState {
    public static final long DEADLINE_MS=8000;
    public enum Stage { LOADING, SDK_INTENT, RESOLVING, BINDING, VALIDATING, READING, SCANNING, STOPPING }
    public enum Outcome { COMPLETE, PARTIAL, RESOURCE_BUSY, UNSUPPORTED_SDK, INTENT_UNSUPPORTED, TRUST_REJECTED,
        NO_FACTORY_SERVICE, AMBIGUOUS_SERVICE, INIT_FAILED, SDK_INIT_SIDE_EFFECT_BLOCKED, SDK_ACCESS_DENIED, BIND_REJECTED, NULL_BINDER, DESCRIPTOR_MISMATCH,
        INVALID_FEATURE, READ_FAILED, SERVICE_LOST, TIMEOUT, CANCELLED, DISCOVERY_EVENTS_UNAVAILABLE, SEARCH_REJECTED, SEARCH_STOP_UNCONFIRMED }
    private final long started;
    private final long deadlineMs;
    private final Runnable cleanup;
    private final StringBuilder trace=new StringBuilder();
    private volatile boolean closed;
    private Stage stage=Stage.LOADING;
    public FactoryBluetoothProbeState(long started,Runnable cleanup) { this(started,cleanup,DEADLINE_MS); }
    public FactoryBluetoothProbeState(long started,Runnable cleanup,long deadlineMs) { this.started=started; this.cleanup=cleanup; this.deadlineMs=deadlineMs; advance(Stage.LOADING,started); }
    public boolean closed() { return closed; }
    public boolean expired(long now) { return now-started>=deadlineMs; }
    public synchronized void advance(Stage next,long now) {
        if(closed || next.ordinal()<stage.ordinal()) return;
        stage=next;
        if(trace.length()<500) trace.append(Math.max(0,now-started)).append(" ms | ").append(next).append('\n');
    }
    public String finish(Outcome outcome,long now,String evidence) {
        String result;
        synchronized(this) {
            if(closed) return null;
            closed=true;
            if(outcome!=Outcome.CANCELLED && expired(now)) outcome=Outcome.TIMEOUT;
            result="Factory SDK status check\n"+trace+"Result: "+outcome+"\nLast stage: "+stage+"\nElapsed ms: "+Math.max(0,now-started)+"\n"
                    +(outcome==Outcome.COMPLETE || outcome==Outcome.PARTIAL ? evidence : "")
                    +"Vendor numeric state meanings are unverified. No scanner or vehicle connection tested.\n";
        }
        try { cleanup.run(); } catch(RuntimeException error) { result+="Cleanup could not be confirmed.\n"; }
        return result;
    }
}
