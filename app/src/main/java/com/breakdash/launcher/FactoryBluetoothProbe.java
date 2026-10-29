package com.breakdash.launcher;

import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.breakdash.launcher.core.FactoryDiscoveryContract;
import com.breakdash.launcher.core.FactoryDiscoveryLease;
import com.breakdash.launcher.core.FactoryDiscoveryEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit dashboard-visible status or finite factory discovery. No guessed Binder callbacks or vehicle commands. */
final class FactoryBluetoothProbe {
    // A synchronous vendor call may ignore interrupts. Never accumulate another worker behind one that has stalled.
    private static final AtomicBoolean IN_FLIGHT=new AtomicBoolean();
    static boolean busy() { return IN_FLIGHT.get(); }
    private final Context context;
    private final Consumer<String> completed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FactoryBluetoothProbeState state;
    private final boolean discovery;
    private final Consumer<List<FactoryDiscoveryContract.Device>> devicesChanged;
    private final Consumer<String> capabilities;
    private final CountDownLatch releaseSearch=new CountDownLatch(1);
    private FactoryDiscoveryContract discoveryContract;
    private FactoryDiscoveryEvents discoveryEvents;
    private BroadcastReceiver discoveryReceiver;
    private volatile String discoveryEvidence="";
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
    FactoryBluetoothProbe(Context context,Consumer<String> completed) { this(context,false,ignored->{},ignored->{},completed); }
    FactoryBluetoothProbe(Context context,boolean discovery,Consumer<List<FactoryDiscoveryContract.Device>> devicesChanged,Consumer<String> capabilities,Consumer<String> completed) {
        this.context=context.getApplicationContext(); this.discovery=discovery; this.devicesChanged=devicesChanged; this.capabilities=capabilities; this.completed=completed;
        state=new FactoryBluetoothProbeState(SystemClock.elapsedRealtime(),this::cleanup,discovery ? 20000 : FactoryBluetoothProbeState.DEADLINE_MS);
    }
    void start() {
        if(!IN_FLIGHT.compareAndSet(false,true)) { finish(Outcome.RESOURCE_BUSY,""); return; }
        worker=new HandlerThread("BreakDash-Factory-BT") {
            @Override protected void onLooperPrepared() {
                work=new Handler(getLooper());
                if(state.closed()) quit(); else work.post(FactoryBluetoothProbe.this::prepare);
            }
            @Override public void run() {
                try { super.run(); }
                catch(RuntimeException | LinkageError | AssertionError error) { failed(error,Outcome.INIT_FAILED); }
                finally { IN_FLIGHT.set(false); }
            }
        };
        main.postDelayed(()->finish(Outcome.TIMEOUT,""),discovery ? 20000 : FactoryBluetoothProbeState.DEADLINE_MS);
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
            if(discovery) {
                Object feature=api.feature(binder,this::stopped);
                state.advance(Stage.READING,SystemClock.elapsedRealtime());
                discoveryContract=api.discovery();
                discoveryEvents=new FactoryDiscoveryEvents(discoveryContract);
                String ble=api.bleDefinitions();
                main.post(()->{
                    if(state.closed()) return;
                    capabilities.accept(ble);
                    if(discoveryContract.actions.isEmpty()) { finish(Outcome.DISCOVERY_EVENTS_UNAVAILABLE,""); return; }
                    receiveDiscovery(feature);
                });
                return;
            }
            FactoryBluetoothStatus.Reading result=api.read(binder,this::stopped,()->state.advance(Stage.READING,SystemClock.elapsedRealtime()));
            Outcome outcome=result.values==0 ? Outcome.READ_FAILED : result.complete() ? Outcome.COMPLETE : Outcome.PARTIAL;
            main.post(()->finish(outcome,result.text));
        } catch(Exception | LinkageError error) { failed(error,Outcome.READ_FAILED); }
    }
    private void receiveDiscovery(Object feature) {
        if(stopped()) { finish(Outcome.TIMEOUT,""); return; }
        IntentFilter filter=new IntentFilter(); for(String action:discoveryContract.actions) filter.addAction(action);
        BroadcastReceiver receiver=new BroadcastReceiver() {
            @Override public void onReceive(Context ignored,Intent intent) {
                if(state.closed() || !discoveryContract.actions.contains(intent.getAction())) return;
                int previous=discoveryEvents.devices().size();
                discoveryEvents.observe(intent.getAction(),()->{
                    intent.setExtrasClassLoader(api.loader);
                    android.os.Bundle extras=intent.getExtras();
                    return new FactoryDiscoveryEvents.Payload() {
                        public Object get(String key) { return extras==null ? null : extras.get(key); }
                        public Iterable<?> values() {
                            List<Object> values=new ArrayList<>();
                            if(extras!=null) for(String key:extras.keySet()) {
                                if(values.size()>=16) break;
                                values.add(extras.get(key));
                            }
                            return values;
                        }
                    };
                });
                if(discoveryEvents.devices().size()!=previous) devicesChanged.accept(new ArrayList<>(discoveryEvents.devices()));
            }
        };
        discoveryReceiver=receiver;
        try { androidx.core.content.ContextCompat.registerReceiver(context,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED); }
        catch(RuntimeException error) { discoveryReceiver=null; finish(Outcome.DISCOVERY_EVENTS_UNAVAILABLE,""); return; }
        Handler handler=work;
        if(handler==null || !handler.post(()->search(feature))) finish(Outcome.INIT_FAILED,"");
    }
    private void search(Object feature) {
        try {
            FactoryDiscoveryLease.Result result=FactoryDiscoveryLease.run(new FactoryDiscoveryLease.Commands() {
                public boolean start() throws Exception { return discoveryContract.start(feature); }
                public boolean stop() throws Exception { state.advance(Stage.STOPPING,SystemClock.elapsedRealtime()); return discoveryContract.stop(feature); }
            },this::stopped,()->releaseSearch.await(12000,TimeUnit.MILLISECONDS),()->state.advance(Stage.SCANNING,SystemClock.elapsedRealtime()));
            discoveryEvidence="Search accepted: "+result.accepted+"\nSearch stop confirmed: "+result.stopConfirmed+"\n";
            main.post(()->finish(!result.accepted ? Outcome.SEARCH_REJECTED : !result.stopConfirmed ? Outcome.SEARCH_STOP_UNCONFIRMED
                    : discoveryEvents.partial() ? Outcome.PARTIAL : Outcome.COMPLETE,""));
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
        if(result!=null) {
            if(discovery) result=result.replace("Factory SDK status check","Factory discovery")+discoveryEvidence
                    +(discoveryEvents==null ? "No discovery observations\n" : discoveryEvents.evidence())+(discoveryContract==null ? "" : discoveryContract.schema)
                    +"Factory device transport is unverified. Zero parsed models do not prove zero nearby devices. No generic vendor BLE API was invoked.\n";
            completed.accept(result+"SDK source: "+VendorBluetoothApi.PACKAGE+"\n"+routing);
        }
    }
    private void cleanup() {
        main.removeCallbacksAndMessages(null);
        releaseSearch.countDown();
        if(discoveryReceiver!=null) { try { context.unregisterReceiver(discoveryReceiver); } catch(RuntimeException ignored) {} discoveryReceiver=null; }
        RuntimeException failure=null;
        if(bindRequested) {
            bindRequested=false;
            try { context.unbindService(connection); } catch(RuntimeException error) { failure=error; }
        }
        Handler handler=work; if(handler!=null) handler.removeCallbacksAndMessages(null);
        if(worker!=null) { worker.quit(); worker.interrupt(); }
        if(failure!=null) throw failure;
    }
}
