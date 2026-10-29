package com.breakdash.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import com.breakdash.launcher.core.FactoryBluetoothProbeState;
import com.breakdash.launcher.core.FactoryBluetoothProbeState.Outcome;
import com.breakdash.launcher.core.FactoryBluetoothProbeState.Stage;
import com.breakdash.launcher.core.FactoryBluetoothStatus;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One explicit dashboard-visible status read. No vendor listener, scan, pairing or transport commands. */
final class FactoryBluetoothProbe {
    // A synchronous vendor call may ignore interrupts. Never accumulate another worker behind one that has stalled.
    private static final AtomicBoolean IN_FLIGHT=new AtomicBoolean();
    private final Context context;
    private final Consumer<String> completed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FactoryBluetoothProbeState state=new FactoryBluetoothProbeState(SystemClock.elapsedRealtime(),this::cleanup);
    private HandlerThread worker;
    private volatile Handler work;
    private VendorBluetoothApi api;
    private volatile String routing="";
    private boolean bindRequested,reading;
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            if(state.closed() || reading) return;
            if(stopped()) { finish(Outcome.TIMEOUT,""); return; }
            reading=true; state.advance(Stage.VALIDATING,SystemClock.elapsedRealtime());
            Handler handler=work;
            if(handler==null || !handler.post(()->read(binder))) finish(Outcome.INIT_FAILED,"");
        }
        @Override public void onServiceDisconnected(ComponentName name) { finish(Outcome.SERVICE_LOST,""); }
        @Override public void onBindingDied(ComponentName name) { finish(Outcome.SERVICE_LOST,""); }
        @Override public void onNullBinding(ComponentName name) { finish(Outcome.NULL_BINDER,""); }
    };
    FactoryBluetoothProbe(Context context,Consumer<String> completed) { this.context=context.getApplicationContext(); this.completed=completed; }
    void start() {
        if(!IN_FLIGHT.compareAndSet(false,true)) { finish(Outcome.RESOURCE_BUSY,""); return; }
        worker=new HandlerThread("BreakDash-Factory-BT") {
            @Override protected void onLooperPrepared() {
                work=new Handler(getLooper());
                if(state.closed()) quitSafely(); else work.post(FactoryBluetoothProbe.this::prepare);
            }
            @Override public void run() { try { super.run(); } finally { IN_FLIGHT.set(false); } }
        };
        main.postDelayed(()->finish(Outcome.TIMEOUT,""),FactoryBluetoothProbeState.DEADLINE_MS);
        try { worker.start(); }
        catch(RuntimeException error) { IN_FLIGHT.set(false); finish(Outcome.INIT_FAILED,""); }
    }
    private boolean stopped() { return state.closed() || state.expired(SystemClock.elapsedRealtime()) || Thread.currentThread().isInterrupted(); }
    private void prepare() {
        try {
            if(stopped()) throw new InterruptedException();
            api=VendorBluetoothApi.load(context);
            state.advance(Stage.SDK_INTENT,SystemClock.elapsedRealtime());
            Intent intent=api.serviceIntent(context,this::stopped); routing=VendorBluetoothApi.routeSummary(intent);
            if(stopped()) throw new InterruptedException();
            state.advance(Stage.RESOLVING,SystemClock.elapsedRealtime());
            Intent resolved=api.resolve(context,intent); routing+=VendorBluetoothApi.routeSummary(resolved).replace("SDK ","Resolved ");
            main.post(()->bind(resolved));
        } catch(Exception | LinkageError error) { failed(error,Outcome.INIT_FAILED); }
    }
    private void bind(Intent intent) {
        if(state.closed()) return;
        if(stopped()) { finish(Outcome.TIMEOUT,""); return; }
        state.advance(Stage.BINDING,SystemClock.elapsedRealtime());
        bindRequested=true;
        try {
            if(!context.bindService(intent,connection,Context.BIND_AUTO_CREATE|Context.BIND_NOT_FOREGROUND)) finish(Outcome.BIND_REJECTED,"");
        } catch(RuntimeException error) { finish(Outcome.BIND_REJECTED,""); }
    }
    private void read(IBinder binder) {
        try {
            FactoryBluetoothStatus.Reading result=api.read(binder,this::stopped,()->state.advance(Stage.READING,SystemClock.elapsedRealtime()));
            Outcome outcome=result.values==0 ? Outcome.READ_FAILED : result.complete() ? Outcome.COMPLETE : Outcome.PARTIAL;
            main.post(()->finish(outcome,result.text));
        } catch(Exception | LinkageError error) { failed(error,Outcome.READ_FAILED); }
    }
    private void failed(Throwable error,Outcome fallback) {
        for(int depth=0;depth<4 && (error instanceof java.lang.reflect.InvocationTargetException || error instanceof ExceptionInInitializerError) && error.getCause()!=null;depth++) error=error.getCause();
        Outcome outcome=error instanceof VendorBluetoothApi.Unavailable ? ((VendorBluetoothApi.Unavailable)error).outcome
                : error instanceof VendorBluetoothApi.SideEffectBlocked ? Outcome.SDK_INIT_SIDE_EFFECT_BLOCKED
                : error instanceof SecurityException ? Outcome.SDK_ACCESS_DENIED
                : error instanceof ClassNotFoundException || error instanceof NoSuchMethodException ? Outcome.UNSUPPORTED_SDK
                : error instanceof InterruptedException ? Outcome.TIMEOUT : fallback;
        // Fixed stage/outcome only. SDK/Binder exception messages may contain private values.
        main.post(()->finish(outcome,""));
    }
    void cancel() { finish(Outcome.CANCELLED,""); }
    private void finish(Outcome outcome,String values) {
        String result=state.finish(outcome,SystemClock.elapsedRealtime(),values);
        if(result!=null) completed.accept(result+"SDK source: "+VendorBluetoothApi.PACKAGE+"\n"+routing);
    }
    private void cleanup() {
        main.removeCallbacksAndMessages(null);
        RuntimeException failure=null;
        if(bindRequested) {
            bindRequested=false;
            try { context.unbindService(connection); } catch(RuntimeException error) { failure=error; }
        }
        Handler handler=work; if(handler!=null) handler.removeCallbacksAndMessages(null);
        if(worker!=null) { worker.quitSafely(); worker.interrupt(); }
        if(failure!=null) throw failure;
    }
}
