package com.breakdash.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.provider.Settings;
import com.breakdash.launcher.core.RadioFrequency;
import java.util.function.Consumer;

/** Only documented primitive Binder calls. Frequency/state arrive through events, never a poll. */
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
        boolean playing, connected;
        String ps = "", rt = "", error = "Tuner disconnected";
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
    private final Handler main = new Handler(Looper.getMainLooper());
    RadioController(Context context, Tasks tasks, Consumer<State> changed, Consumer<String> errors) {
        this.context = context; this.tasks = tasks; this.changed = changed; this.errors = errors;
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (!started) return;
            binder = service;
            execute(() -> {
                String actual = service.getInterfaceDescriptor();
                if (!DESCRIPTOR.equals(actual)) throw new IllegalStateException("Unexpected tuner: " + actual);
                return actual;
            }, actual -> { lastDescriptor=actual; lastConnected=System.currentTimeMillis(); state.connected = true; state.error = ""; readSettings(); requestState(); readRt(); });
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected("Tuner service disconnected"); }
        @Override public void onBindingDied(ComponentName name) { disconnected("Tuner binding died; reopen dashboard to reconnect"); }
        @Override public void onNullBinding(ComponentName name) { disconnected("Tuner returned an empty binding"); }
    };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { if (started) { readSettings(); readRt(); } }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (!started) return;
            readSettings();
            int incoming = intent.getIntExtra("extra_frequence", intent.getIntExtra("extra_radio_frequence", 0));
            if (RadioFrequency.valid(incoming)) state.frequency = incoming;
            if (intent.hasExtra("extra_band")) {
                Object raw = intent.getExtras() == null ? null : intent.getExtras().get("extra_band");
                if (raw instanceof Number) state.band = ((Number) raw).byteValue();
            }
            if (intent.hasExtra("nwd_radio_state")) {
                Object raw = intent.getExtras() == null ? null : intent.getExtras().get("nwd_radio_state");
                if (raw instanceof Number) state.playing = ((Number) raw).intValue() != 0 && state.source == 4;
            }
            changed.accept(state); readRt();
        }
    };
    void start() {
        if (started) return;
        started = true; scope = tasks.scope();
        IntentFilter filter = new IntentFilter();
        filter.addAction(NEW_FREQUENCY); filter.addAction(FREQUENCY); filter.addAction(RADIO_STATE);
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
            registered = true;
            observed = true;
            for (String key : KEYS) context.getContentResolver().registerContentObserver(Settings.System.getUriFor(key), false, observer);
        } catch (RuntimeException error) { state.error = error.getMessage(); }
        readSettings();
        try {
            bound = context.bindService(new Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE").setPackage(PACKAGE),
                    connection, Context.BIND_AUTO_CREATE);
            if (!bound) disconnected("NWD tuner service not installed");
        } catch (RuntimeException error) { disconnected("Tuner unavailable: " + error.getMessage()); }
        requestState();
    }
    void stop() {
        if (!started) return;
        started = false;
        if (scope != null) scope.close();
        rtPending = false;
        if (registered) { context.unregisterReceiver(receiver); registered = false; }
        if (observed) { context.getContentResolver().unregisterContentObserver(observer); observed = false; }
        if (bound) { context.unbindService(connection); bound = false; }
        binder = null; state.connected = false;
    }
    private void disconnected(String reason) {
        binder = null; state.connected = false; state.error = reason; changed.accept(state);
    }
    private void requestState() {
        context.sendBroadcast(new Intent("com.nwd.action.radiowidget_request_frequence").setPackage(PACKAGE));
    }
    private void readSettings() {
        try {
            int value = Settings.System.getInt(context.getContentResolver(), KEYS[0], 0);
            if (RadioFrequency.valid(value)) state.frequency = value;
            state.band = (byte) Settings.System.getInt(context.getContentResolver(), KEYS[1], 0);
            state.source = Settings.System.getInt(context.getContentResolver(), KEYS[3], -1);
            state.playing = Settings.System.getInt(context.getContentResolver(), KEYS[2], 0) != 0 && state.source == 4;
            String ps = Settings.System.getString(context.getContentResolver(), KEYS[4]);
            state.ps = ps == null ? "" : ps;
        } catch (RuntimeException error) { state.error = "Settings access: " + error.getMessage(); }
        changed.accept(state);
    }
    private interface Write { void accept(Parcel parcel); }
    private <T> T transaction(int code, Write write, java.util.function.Function<Parcel, T> read) throws Exception {
        IBinder remote = binder;
        if (remote == null || !remote.isBinderAlive()) throw new IllegalStateException("Tuner disconnected. Open the stock Radio app or reopen BreakDash.");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); write.accept(data);
            if (!remote.transact(code, data, reply, 0)) throw new IllegalStateException("Unsupported radio transaction " + code);
            reply.readException(); return read.apply(reply);
        } finally { reply.recycle(); data.recycle(); }
    }
    void seek(boolean up) { execute(() -> transaction(3, data -> data.writeInt(up ? 1 : 0), reply -> null), ignored -> requestState()); }
    void power(boolean on) { execute(() -> transaction(13, data -> data.writeInt(on ? 1 : 0), reply -> null), ignored -> requestState()); }
    void changeBand() { execute(() -> transaction(5, data -> {}, reply -> null), ignored -> requestState()); }
    void tune(int frequency, byte band, int slot) {
        if (!RadioFrequency.valid(frequency)) { errors.accept("Hold a preset to save the current station"); return; }
        execute(() -> {
            transaction(13, data -> data.writeInt(1), reply -> null);
            return transaction(1, data -> { data.writeInt(frequency); data.writeByte(band); data.writeInt(slot); }, reply -> null);
        }, ignored -> requestState());
    }
    void save(int slot, Runnable success) {
        execute(() -> transaction(20, data -> data.writeByte((byte) slot), reply -> null), ignored -> success.run());
    }
    private boolean rtPending;
    private void readRt() {
        if (!state.connected || rtPending) return;
        rtPending = true;
        scope.run(() -> transaction(28, data -> {}, Parcel::readString), value -> { rtPending = false; state.rt = value == null ? "" : value; changed.accept(state); },error -> { rtPending = false; state.error = "RT unavailable: " + error.getMessage(); });
    }
    private <T> void execute(java.util.concurrent.Callable<T> work, Consumer<T> success) {
        if (scope == null || !started) { errors.accept("Radio is not connected"); return; }
        scope.run(work, success, error -> { rtPending = false; state.error = error.getMessage(); errors.accept(state.error); changed.accept(state); });
    }
    String diagnostics() {
        return "Expected radio descriptor: " + DESCRIPTOR + "\nLast observed descriptor: " + lastDescriptor
                + "\nLast successful connection: " + (lastConnected==0 ? "Never" : new java.util.Date(lastConnected))
                + "\nDashboard binding currently connected: " + state.connected + " (released while dialogs are open)\nFrequency: " + state.frequency
                + "\nBand (raw): " + state.band + "\nPlaying: " + state.playing + "\nMCU source: " + state.source
                + "\nPS: " + state.ps + "\nRT: " + state.rt + "\nLast error: " + state.error
                + "\nFM: 8750..10800 (10 kHz units); AM: 531..1620 kHz\n"
                + "Parcelable/callback wire layouts: not supplied; state uses broadcasts + ContentObserver\n";
    }
}
