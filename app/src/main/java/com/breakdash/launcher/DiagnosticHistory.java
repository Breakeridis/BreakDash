package com.breakdash.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded app-only breadcrumbs; no logcat, external task history, URLs or exception messages. */
final class DiagnosticHistory {
    private static SharedPreferences store;
    private static final Deque<String> events=new ArrayDeque<>();
    private static final AtomicBoolean handlingCrash=new AtomicBoolean();
    private DiagnosticHistory() {}
    static synchronized void install(Context context) {
        if(store!=null) return;
        store=context.getSharedPreferences("diagnostic_history",Context.MODE_PRIVATE);
        String saved=store.getString("events","");
        for(String line:saved.split("\n")) if(!line.isEmpty()) events.addLast(line);
        event("process start · version "+BuildConfig.VERSION_NAME+" · pid "+Process.myPid());
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try {
                if(handlingCrash.compareAndSet(false,true)) {
                    StringBuilder crash=new StringBuilder("Version ").append(BuildConfig.VERSION_NAME).append(" · ").append(new java.util.Date()).append('\n');
                    Throwable cause=error;
                    for(int depth=0;cause!=null && depth<4;depth++,cause=cause.getCause()) {
                        crash.append(depth==0 ? "Exception: " : "Caused by: ").append(cause.getClass().getName()).append('\n');
                        StackTraceElement[] frames=cause.getStackTrace();
                        for(int i=0;i<Math.min(32,frames.length);i++) crash.append("  at ").append(frames[i]).append('\n');
                    }
                    synchronized(DiagnosticHistory.class) {
                        store.edit().putString("crash",bounded(crash.toString(),12000)).putString("events",history()).commit();
                    }
                }
            } catch(Throwable ignored) { /* Never replace the platform's fatal-error handling. */ }
            finally {
                if(previous!=null) previous.uncaughtException(thread,error);
                else { Process.killProcess(Process.myPid()); System.exit(10); }
            }
        });
    }
    static synchronized void event(String value) {
        if(store==null) return;
        events.addLast(String.format(Locale.ROOT,"%tF %tT · %s",System.currentTimeMillis(),System.currentTimeMillis(),bounded(value,360)));
        while(events.size()>32 || history().length()>10000) events.removeFirst();
        store.edit().putString("events",history()).apply();
    }
    static synchronized String history() { return String.join("\n",events); }
    static synchronized void update(String value) {
        if(store!=null) store.edit().putString("update",bounded(value,6000)).apply();
    }
    static synchronized String collect() {
        if(store==null) return "Persistent diagnostics unavailable\n";
        return "PERSISTED APP EVENTS (up to 32, including previous processes)\n"+history()
                +"\nLAST JAVA CRASH (exception types/frames only)\n"+store.getString("crash","None captured. Native crashes, process kills and firmware routing are not Java exceptions.")
                +"\nLAST UPDATE VALIDATION\n"+store.getString("update","No update downloaded since diagnostic history was enabled.")+"\n";
    }
    private static String bounded(String value,int max) { return value.length()<=max ? value : value.substring(0,max); }
}
