package com.breakdash.launcher.core;

import java.util.Locale;
import java.util.UUID;

/** Identity-free, finite operations. Tokens prevent an old Android callback owning a new operation. */
public final class BluetoothSession {
    public enum State { IDLE, CLASSIC_SCAN, BLE_SCAN, PAIRING, CONNECTING, CONNECTED, DISCOVERING, COMPLETE, FAILED, STOPPED }
    public enum Reason { START, FOUND, SCAN_FINISHED, CONNECTED, DISCOVERING, SERVICES, PAIRED, REJECTED, GATT_ERROR, SCAN_ERROR, EXCEPTION, TIMEOUT, USER_STOP, HIDDEN, BLUETOOTH_OFF, PERMISSION_DENIED, MISSING_HARDWARE, LOCATION_OFF }
    public static final int MAX_DEVICES=32;
    private long generation,started,deadline;
    private boolean running;
    private State state=State.IDLE;
    private final StringBuilder trace=new StringBuilder();
    private final Runnable release;
    public BluetoothSession() { this(()->{}); }
    public BluetoothSession(Runnable release) { this.release=release; }

    public long begin(State next,long now) {
        if(next!=State.CLASSIC_SCAN && next!=State.BLE_SCAN && next!=State.CONNECTING && next!=State.PAIRING) throw new IllegalArgumentException("Not an operation");
        if(running) stop(Reason.USER_STOP,now);
        ++generation; running=true; started=now;
        deadline=now+(next==State.CONNECTING ? 25000 : next==State.PAIRING ? 30000 : 12000);
        trace.setLength(0); state=next; record(now,Reason.START,0); return generation;
    }
    public boolean accepts(long token) { return running && token==generation; }
    public State state() { return state; }
    public boolean running() { return running; }
    public long remaining(long now) { return Math.max(0,deadline-now); }
    public boolean connected(long token,int status,boolean connected,long now) {
        if(timeout(token,now)) return false;
        if(!accepts(token) || state!=State.CONNECTING) return false;
        if(status!=0 || !connected) { finish(token,State.FAILED,Reason.GATT_ERROR,status,now); return false; }
        state=State.CONNECTED; record(now,Reason.CONNECTED,status); return true;
    }
    public boolean discovering(long token,long now) {
        if(timeout(token,now)) return false;
        if(!accepts(token) || state!=State.CONNECTED) return false;
        state=State.DISCOVERING; record(now,Reason.DISCOVERING,0); return true;
    }
    public boolean services(long token,int status,long now) {
        if(timeout(token,now)) return false;
        if(!accepts(token) || state!=State.DISCOVERING) return false;
        return finish(token,status==0 ? State.COMPLETE : State.FAILED,status==0 ? Reason.SERVICES : Reason.GATT_ERROR,status,now);
    }
    public boolean finish(long token,State next,Reason reason,int code,long now) {
        if(!accepts(token)) return false;
        if(next!=State.COMPLETE && next!=State.FAILED && next!=State.STOPPED) throw new IllegalArgumentException("Not terminal");
        state=next; record(now,reason,code); running=false; release.run(); return true;
    }
    public boolean timeout(long token,long now) {
        return accepts(token) && now>=deadline && finish(token,State.FAILED,Reason.TIMEOUT,0,now);
    }
    public void stop(Reason reason,long now) {
        if(running) finish(generation,State.STOPPED,reason,0,now);
        ++generation;
    }
    public String evidence() { return trace.length()==0 ? "No Bluetooth operation captured.\n" : trace.toString(); }
    private void record(long now,Reason reason,int code) {
        if(trace.length()<3000) trace.append(Math.max(0,now-started)).append(" ms | ").append(state).append(" | ").append(reason).append(" | code ").append(code).append('\n');
    }
    /** Space-separated bytes survive the upload privacy filter without exposing device identity. */
    public static String serviceId(UUID value) {
        String hex=value.toString().replace("-",""); StringBuilder result=new StringBuilder();
        for(int i=0;i<hex.length();i+=2) { if(i>0) result.append(' '); result.append(hex.substring(i,i+2).toUpperCase(Locale.ROOT)); }
        return result.toString();
    }
}
