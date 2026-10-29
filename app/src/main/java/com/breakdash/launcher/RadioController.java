package com.breakdash.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Bundle;
import android.provider.Settings;
import com.breakdash.launcher.core.RadioFrequency;
import com.breakdash.launcher.core.RadioModel;
import java.util.function.Consumer;

/** Visible-only read connection. Factory AIDL decodes model objects; no frequency-request broadcasts or polling. */
final class RadioController {
    static final String DESCRIPTOR = "com.nwd.radio.service.RadioFeature";
    static final String PACKAGE = "com.nwd.radio.service";
    static final String NEW_FREQUENCY = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW";
    static final String FREQUENCY = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE";
    static final String RADIO_STATE = "com.nwd.action.ACTION_RADIO_STATE";
    static final String[] KEYS = {"nwd_radio_current_freq", "nwd_radio_current_band", "nwd_radio_state",
            "mcu_current_source", "nwd_radio_current_ps_data"};
    final State state = new State();
    static final class State {
        int frequency, source = -1;
        byte band;
        boolean playing, connected, connecting, observing, bandKnown;
        String ps = "", rt = "", error = "";
    }
    private final Context context;
    private final Tasks tasks;
    private final Consumer<State> changed;
    private final Consumer<String> errors;
    private Tasks.Scope scope;
    private boolean started, bound, registered, observed;
    private volatile IBinder binder;
    private String lastDescriptor="Not observed";
    private long lastConnected;
    private Runnable pendingCommand;
    private volatile VendorRadioApi vendorApi;
    private volatile String apiError="Not loaded";
    private volatile boolean apiAttempted;
    private boolean statusPending,statusScheduled,statusDirty,userConnection;
    private int broadcastsIgnored;
    private String broadcastSchema="No decoded radio broadcast";
    private volatile String lastFailure="";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable connectionTimeout=()->{ if(started && !state.connected) disconnected("Tuner connection timed out; try again"); };
    private final Runnable refreshStatus=this::readStatus;
    private final Runnable settleCommand=()->queueStatus();
    private static final class Status {
        RadioModel.Reading station=new RadioModel.Reading(0,null);
        String rt="",error="";
    }
    RadioController(Context context, Tasks tasks, Consumer<State> changed, Consumer<String> errors) {
        this.context = context; this.tasks = tasks; this.changed = changed; this.errors = errors;
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (!started || !bound) return;
            binder = service;
            scope.run(() -> {
                String actual = service.getInterfaceDescriptor();
                if (!DESCRIPTOR.equals(actual)) throw new IllegalStateException("Unexpected tuner: " + actual);
                loadFactoryApi();
                return readSnapshot(service);
            }, snapshot -> {
                if(!started || !bound || binder!=service) return;
                main.removeCallbacks(connectionTimeout); lastDescriptor=DESCRIPTOR; lastConnected=System.currentTimeMillis(); state.connected = true; state.connecting=false; state.error = "";
                DiagnosticHistory.event("radio read connection ready · no request broadcast"); readSettings(); apply(snapshot);
                Runnable command=pendingCommand; pendingCommand=null; if(command!=null) command.run();
            },error->{ if(started && bound && binder==service) { lastFailure=failure(error); disconnected("Radio connection unavailable"); } });
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected("Tuner service disconnected"); }
        @Override public void onBindingDied(ComponentName name) { disconnected("Tuner binding died; reopen dashboard to reconnect"); }
        @Override public void onNullBinding(ComponentName name) { disconnected("Tuner returned an empty binding"); }
    };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { if (started) { readSettings(); queueStatus(); } }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (!started) return;
            readSettings();
            try {
                VendorRadioApi api=vendorApi;
                if(api==null) { broadcastsIgnored++; queueStatus(); return; }
                intent.setExtrasClassLoader(api.loader);
                Bundle extras=intent.getExtras();
                if(extras!=null) {
                    StringBuilder schema=new StringBuilder(); int count=0;
                    for(String key:extras.keySet()) {
                        if(count++>=16) break;
                        Object value=extras.get(key);
                        if(key.matches("[A-Za-z0-9_.]{1,64}")) schema.append(key).append(": ").append(value==null ? "null" : value.getClass().getName()).append(';');
                        if(api.accepts(value)) applyStation(api.decode(value));
                    }
                    broadcastSchema=schema.toString();
                    Object incoming=extras.get("extra_frequence"); if(incoming==null) incoming=extras.get("extra_radio_frequence");
                    Integer number=RadioModel.integer(incoming); if(number!=null && RadioFrequency.valid(number)) state.frequency=number;
                    Integer band=RadioModel.integer(extras.get("extra_band"));
                    if(band!=null && band>=-128 && band<=255) { state.band=band.byteValue(); state.bandKnown=true; }
                }
            } catch(RuntimeException | LinkageError error) {
                broadcastsIgnored++; lastFailure=failure(error);
                if(broadcastsIgnored==1) DiagnosticHistory.event("radio broadcast rejected safely · "+error.getClass().getSimpleName());
            }
            changed.accept(state); queueStatus();
        }
    };
    void start() {
        if (started) return;
        started = true; state.observing=true; state.error=""; state.rt=""; state.ps=""; state.frequency=0; state.bandKnown=false; state.source=-1; state.playing=false; scope = tasks.scope();
        DiagnosticHistory.event("radio visible status start · no request broadcast");
        IntentFilter filter = new IntentFilter();
        filter.addAction(NEW_FREQUENCY); filter.addAction(FREQUENCY); filter.addAction(RADIO_STATE);
        try {
            androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
            registered = true;
            observed = true;
            for (String key : KEYS) context.getContentResolver().registerContentObserver(Settings.System.getUriFor(key), false, observer);
        } catch (RuntimeException error) { state.error = error.getMessage(); }
        readSettings();
        bindForStatus();
    }
    private void bindForStatus() {
        if(bound) return;
        state.connecting=true; changed.accept(state);
        DiagnosticHistory.event("radio read-only binding requested");
        try {
            bound = context.bindService(new Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE").setPackage(PACKAGE),
                    connection, Context.BIND_AUTO_CREATE);
            if (!bound) disconnected("NWD tuner service not installed");
            else main.postDelayed(connectionTimeout,8000);
        } catch (RuntimeException error) { lastFailure=failure(error); disconnected("Radio connection unavailable"); }
    }
    void stop() {
        if (!started) return;
        started = false; state.observing=false; state.connecting=false; pendingCommand=null; userConnection=false; main.removeCallbacks(connectionTimeout);
        main.removeCallbacks(refreshStatus); main.removeCallbacks(settleCommand); statusPending=false; statusScheduled=false; statusDirty=false;
        DiagnosticHistory.event("radio status stop · playback untouched");
        if (scope != null) scope.close();
        if (registered) { context.unregisterReceiver(receiver); registered = false; }
        if (observed) { context.getContentResolver().unregisterContentObserver(observer); observed = false; }
        if (bound) { context.unbindService(connection); bound = false; }
        binder = null; state.connected = false;
    }
    private void disconnected(String reason) {
        main.removeCallbacks(connectionTimeout); main.removeCallbacks(refreshStatus); main.removeCallbacks(settleCommand);
        statusScheduled=false; statusPending=false; statusDirty=false; pendingCommand=null;
        if(bound) { try { context.unbindService(connection); } catch(IllegalArgumentException ignored) {} bound=false; }
        binder = null; state.connected = false; state.connecting=false; state.error = reason; changed.accept(state);
        DiagnosticHistory.event("radio binding failed"); if(userConnection) errors.accept(reason); userConnection=false;
    }
    private synchronized void loadFactoryApi() {
        if(apiAttempted) return;
        apiAttempted=true;
        try { vendorApi=VendorRadioApi.load(context); apiError=""; }
        catch(Exception | LinkageError error) { apiError=failure(error)+(error instanceof VendorRadioApi.Unavailable ? " · "+error.getMessage() : ""); }
    }
    private Status readSnapshot(IBinder remote) {
        Status result=new Status();
        try { Http.checkInterrupted(); if(vendorApi!=null) result.station=vendorApi.current(remote); else result.error="Factory station reader unavailable"; }
        catch(Exception | LinkageError error) { lastFailure=failure(error); result.error="Station data unavailable"; }
        if(!RadioFrequency.valid(result.station.frequency) && result.error.isEmpty()) result.error="Station format unavailable";
        try { result.rt=transaction(remote,28,data->{},Parcel::readString); }
        catch(Exception error) { lastFailure=failure(error); }
        return result;
    }
    private void applyStation(RadioModel.Reading station) {
        if(RadioFrequency.valid(station.frequency)) state.frequency=station.frequency;
        if(station.band!=null) { state.band=station.band.byteValue(); state.bandKnown=true; }
    }
    private void apply(Status snapshot) {
        applyStation(snapshot.station); state.rt=snapshot.rt==null ? "" : snapshot.rt; state.error=snapshot.error; changed.accept(state);
    }
    void refresh() {
        if(!started) return;
        userConnection=true;
        if(vendorApi==null) apiAttempted=false;
        if(!state.connected) bindForStatus(); else readStatus();
    }
    private void queueStatus() {
        if(!started || !state.connected) return;
        if(statusPending) { statusDirty=true; return; }
        if(statusScheduled) return;
        statusScheduled=true; main.postDelayed(refreshStatus,100);
    }
    private void readStatus() {
        main.removeCallbacks(refreshStatus); statusScheduled=false;
        if(!started || !state.connected) return;
        if(statusPending) { statusDirty=true; return; }
        statusPending=true; IBinder remote=binder;
        scope.run(()->{ loadFactoryApi(); return readSnapshot(remote); },snapshot->{
            if(!started || binder!=remote) return;
            statusPending=false; apply(snapshot);
            if(statusDirty) { statusDirty=false; queueStatus(); }
        },error->{ if(started && binder==remote) { statusPending=false; lastFailure=failure(error); state.error="Radio status unavailable"; changed.accept(state); } });
    }
    private void commandFinished() {
        readSettings(); queueStatus();
        main.removeCallbacks(settleCommand); main.postDelayed(settleCommand,500);
    }
    private void readSettings() {
        try {
            Integer value=setting(KEYS[0]),band=setting(KEYS[1]),source=setting(KEYS[3]),playing=setting(KEYS[2]);
            if(value!=null && RadioFrequency.valid(value)) state.frequency=value;
            if(band!=null && band>=-128 && band<=255) { state.band=band.byteValue(); state.bandKnown=true; }
            if(source!=null) state.source=source;
            if(playing!=null) state.playing=playing!=0 && state.source==4;
            else if(source!=null && source!=4) state.playing=false;
            String ps = Settings.System.getString(context.getContentResolver(), KEYS[4]);
            if(ps!=null) state.ps=ps;
        } catch (RuntimeException error) { state.error="Radio settings unavailable"; lastFailure=failure(error); }
        changed.accept(state);
    }
    private Integer setting(String key) {
        String value=Settings.System.getString(context.getContentResolver(),key);
        try { return value==null ? null : Integer.valueOf(value); } catch(NumberFormatException ignored) { return null; }
    }
    private static String failure(Throwable error) {
        StringBuilder types=new StringBuilder();
        for(int i=0;error!=null && i<4;i++,error=error.getCause()) {
            if(i>0) types.append(" caused by "); types.append(error.getClass().getName());
        }
        return types.toString();
    }
    private interface Write { void accept(Parcel parcel); }
    private <T> T transaction(int code, Write write, java.util.function.Function<Parcel, T> read) throws Exception {
        return transaction(binder,code,write,read);
    }
    private <T> T transaction(IBinder remote,int code,Write write,java.util.function.Function<Parcel,T> read) throws Exception {
        Http.checkInterrupted();
        if (remote == null || !remote.isBinderAlive()) throw new IllegalStateException("Tuner disconnected. Open the stock Radio app or reopen BreakDash.");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); write.accept(data);
            if (!remote.transact(code, data, reply, 0)) throw new IllegalStateException("Unsupported radio transaction " + code);
            reply.readException(); return read.apply(reply);
        } finally { reply.recycle(); data.recycle(); }
    }
    void seek(boolean up) { execute(() -> transaction(3, data -> data.writeInt(up ? 1 : 0), reply -> null), ignored -> commandFinished()); }
    void power(boolean on) { execute(() -> transaction(13, data -> data.writeInt(on ? 1 : 0), reply -> null), ignored -> commandFinished()); }
    void changeBand() { execute(() -> transaction(5, data -> {}, reply -> null), ignored -> commandFinished()); }
    void tune(int frequency, byte band, int slot) {
        if (!RadioFrequency.valid(frequency)) { errors.accept("Hold a preset to save the current station"); return; }
        execute(() -> {
            transaction(13, data -> data.writeInt(1), reply -> null);
            return transaction(1, data -> { data.writeInt(frequency); data.writeByte(band); data.writeInt(slot); }, reply -> null);
        }, ignored -> commandFinished());
    }
    void save(int slot, Runnable success) {
        execute(() -> transaction(20, data -> data.writeByte((byte) slot), reply -> null), ignored -> success.run());
    }
    private <T> void execute(java.util.concurrent.Callable<T> work, Consumer<T> success) {
        if (scope == null || !started) { errors.accept("Radio is not connected"); return; }
        if(!state.connected) {
            if(pendingCommand!=null) { errors.accept("Connecting to the tuner…"); return; }
            userConnection=true; pendingCommand=()->execute(work,success); bindForStatus(); return;
        }
        scope.run(work, success, error -> { lastFailure=failure(error); state.error="Radio command failed"; errors.accept(state.error); changed.accept(state); });
    }
    String diagnostics() {
        return "Expected radio descriptor: " + DESCRIPTOR + "\nLast observed descriptor: " + lastDescriptor
                + "\nLast successful connection: " + (lastConnected==0 ? "Never" : new java.util.Date(lastConnected))
                + "\nDashboard binding currently connected: " + state.connected + " (released while dialogs are open)"
                + "\nVisible read connection: "+state.observing+" · no frequency-request broadcasts\nFrequency: " + state.frequency
                + "\nBand (raw): " + state.band + " · known: "+state.bandKnown+"\nPlaying: " + state.playing + "\nMCU source: " + state.source
                + "\nPS: " + state.ps + "\nRT: " + state.rt + "\nLast error: " + state.error
                + "\nFM: 8750..10800 (10 kHz units); AM: 531..1620 kHz\n"
                + "Factory API: "+(vendorApi==null ? apiError : vendorApi.provenance)+"\nModel schema (names/types only): "+(vendorApi==null ? "unavailable" : vendorApi.schema())
                + "\nBroadcast schema (names/types only): "+broadcastSchema+"\nBroadcasts safely ignored: "+broadcastsIgnored+"\nLast failure type: "+lastFailure
                + "\nModel decoding uses installed factory AIDL/Parcelable code; no guessed Parcel or callback layout. No polling.\n";
    }
}
