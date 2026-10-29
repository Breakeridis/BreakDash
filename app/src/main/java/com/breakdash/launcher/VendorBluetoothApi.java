package com.breakdash.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import com.breakdash.launcher.core.FactoryApkTrust;
import com.breakdash.launcher.core.FactoryBluetoothStatus;
import com.breakdash.launcher.core.FactoryDiscoveryContract;
import com.breakdash.launcher.core.BluetoothInspectionPlan;
import com.breakdash.launcher.core.BluetoothSdkSchema;
import com.breakdash.launcher.core.FactoryBluetoothProbeState.Outcome;
import dalvik.system.PathClassLoader;
import dalvik.system.DexFile;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Observed SDK supplies the intent and generated proxy; BreakDash owns the binding, with no vendor callbacks. */
final class VendorBluetoothApi {
    static final String PACKAGE="com.nwd.android.phone",DESCRIPTOR="com.bt.BTFeature";
    static final class Unavailable extends Exception {
        final Outcome outcome;
        Unavailable(Outcome outcome) { this.outcome=outcome; }
    }
    static final class SideEffectBlocked extends SecurityException {}
    private final Class<?> controller,contract;
    final ClassLoader loader;
    private List<String> targetPaths;
    private final Method getInstance,getServiceIntent,asInterface;
    private final FactoryBluetoothStatus status;
    private VendorBluetoothApi(ClassLoader loader) throws Exception {
        this.loader=loader;
        controller=Class.forName("com.bt.BTController",false,loader);
        contract=Class.forName(DESCRIPTOR,false,loader);
        getInstance=controller.getMethod("getInstance",Context.class);
        getServiceIntent=controller.getDeclaredMethod("getServiceIntent");
        asInterface=Class.forName(DESCRIPTOR+"$Stub",false,loader).getMethod("asInterface",IBinder.class);
        if(getInstance.getReturnType()!=controller || !Modifier.isStatic(getInstance.getModifiers())
                || getServiceIntent.getReturnType()!=Intent.class || Modifier.isStatic(getServiceIntent.getModifiers())
                || !contract.isInterface() || !IInterface.class.isAssignableFrom(contract)
                || !Modifier.isStatic(asInterface.getModifiers()) || asInterface.getReturnType()!=contract) throw new Unavailable(Outcome.UNSUPPORTED_SDK);
        getServiceIntent.setAccessible(true); status=new FactoryBluetoothStatus(contract);
    }
    static VendorBluetoothApi load(Context context) throws Exception {
        ApplicationInfo info=context.getPackageManager().getApplicationInfo(PACKAGE,0);
        List<String> paths=trustedPaths(info);
        return new VendorBluetoothApi(new PathClassLoader(String.join(File.pathSeparator,paths),ClassLoader.getSystemClassLoader()));
    }
    private static List<String> trustedPaths(ApplicationInfo info) throws Exception {
        List<String> raw=new ArrayList<>(); raw.add(info.sourceDir);
        if(info.splitSourceDirs!=null) java.util.Collections.addAll(raw,info.splitSourceDirs);
        List<String> verified=new ArrayList<>();
        for(String path:raw) {
            if(path==null) throw new Unavailable(Outcome.TRUST_REJECTED);
            File file=new File(path); String canonical=file.getCanonicalPath();
            if(!FactoryApkTrust.accepts((info.flags&ApplicationInfo.FLAG_SYSTEM)!=0,canonical,file.isFile(),file.canRead(),file.canWrite())) throw new Unavailable(Outcome.TRUST_REJECTED);
            verified.add(canonical);
        }
        return verified;
    }
    Intent serviceIntent(Context context,BooleanSupplier stopped) throws Exception {
        if(stopped.getAsBoolean()) throw new InterruptedException();
        Object instance=getInstance.invoke(null,new DefinitionContext(context));
        if(instance==null || !controller.isInstance(instance)) throw new Unavailable(Outcome.UNSUPPORTED_SDK);
        if(stopped.getAsBoolean()) throw new InterruptedException();
        Object value=getServiceIntent.invoke(instance);
        if(!(value instanceof Intent)) throw new Unavailable(Outcome.INTENT_UNSUPPORTED);
        Intent original=(Intent)value;
        if(original.getData()!=null || original.getType()!=null || original.getSelector()!=null || original.getClipData()!=null
                || original.getComponent()==null && original.getAction()==null) throw new Unavailable(Outcome.INTENT_UNSUPPORTED);
        // Rebuild routing fields only. Never decode or forward opaque extras/Parcelables or URI grants.
        Intent clean=new Intent().setAction(original.getAction()).setComponent(original.getComponent()).setPackage(original.getPackage());
        if(original.getCategories()!=null) {
            if(original.getCategories().size()>8) throw new Unavailable(Outcome.INTENT_UNSUPPORTED);
            for(String category:original.getCategories()) clean.addCategory(category);
        }
        return clean;
    }
    Intent resolve(Context context,Intent intent) throws Exception {
        List<ResolveInfo> matches=context.getPackageManager().queryIntentServices(intent,0);
        if(matches.isEmpty()) throw new Unavailable(Outcome.NO_FACTORY_SERVICE);
        if(matches.size()!=1) throw new Unavailable(Outcome.AMBIGUOUS_SERVICE);
        ServiceInfo service=matches.get(0).serviceInfo;
        if(service==null || !service.exported || !service.enabled || !service.applicationInfo.enabled) throw new Unavailable(Outcome.BIND_REJECTED);
        if(service.permission!=null && context.checkSelfPermission(service.permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw new Unavailable(Outcome.BIND_REJECTED);
        targetPaths=trustedPaths(service.applicationInfo);
        return new Intent(intent).setComponent(new ComponentName(service.packageName,service.name)).setPackage(service.packageName);
    }
    static String routeSummary(Intent intent) {
        String action=intent.getAction(); ComponentName component=intent.getComponent();
        return "SDK action: "+definition(action)+"\nSDK component: "+(component==null ? "implicit" : definition(component.getPackageName())+"/"+definition(component.getClassName()))+"\n";
    }
    private static String definition(String value) { return value!=null && value.matches("[A-Za-z_$][A-Za-z0-9_.$]{0,199}") ? value : "not recorded"; }
    FactoryBluetoothStatus.Reading read(IBinder binder,BooleanSupplier stopped,Runnable validated) throws Exception {
        Object feature=feature(binder,stopped); validated.run();
        return status.read(feature,stopped);
    }
    Object feature(IBinder binder,BooleanSupplier stopped) throws Exception {
        if(stopped.getAsBoolean()) throw new InterruptedException();
        if(binder==null) throw new Unavailable(Outcome.NULL_BINDER);
        if(!DESCRIPTOR.equals(binder.getInterfaceDescriptor())) throw new Unavailable(Outcome.DESCRIPTOR_MISMATCH);
        if(stopped.getAsBoolean()) throw new InterruptedException();
        Object feature=asInterface.invoke(null,binder);
        if(feature==null || !contract.isInstance(feature)) throw new Unavailable(Outcome.INVALID_FEATURE);
        return feature;
    }
    FactoryDiscoveryContract discovery() throws Exception {
        return new FactoryDiscoveryContract(contract,Class.forName("com.bt.BTDefine",false,loader),Class.forName("com.bt.BTDevice",false,loader));
    }
    /** Metadata only: discovers possible vendor BLE paths without invoking an unverified BLE API. */
    @SuppressWarnings("deprecation")
    String bleDefinitions() {
        StringBuilder out=new StringBuilder("Resolved factory BLE/GATT definitions (metadata only; support unverified)\n");
        if(targetPaths==null) return out+"No resolved target APK\n";
        long deadline=android.os.SystemClock.elapsedRealtime()+500;
        List<String> names=new ArrayList<>(); int entries=0;
        try {
            for(String path:targetPaths) {
                if(android.os.SystemClock.elapsedRealtime()>=deadline || Thread.currentThread().isInterrupted() || entries>=20000) break;
                DexFile dex=new DexFile(path);
                try {
                    java.util.Enumeration<String> inventory=dex.entries();
                    while(inventory.hasMoreElements() && entries++<20000 && android.os.SystemClock.elapsedRealtime()<deadline && !Thread.currentThread().isInterrupted()) {
                        String name=BluetoothInspectionPlan.contract(inventory.nextElement());
                        if(name!=null && BluetoothInspectionPlan.hasBleMarker(name) && names.size()<6 && !names.contains(name)) names.add(name);
                    }
                } finally { dex.close(); }
            }
            ClassLoader target=new PathClassLoader(String.join(File.pathSeparator,targetPaths),ClassLoader.getSystemClassLoader());
            for(String name:names) {
                if(android.os.SystemClock.elapsedRealtime()>=deadline || Thread.currentThread().isInterrupted()) break;
                try { out.append(BluetoothSdkSchema.inspect(Class.forName(name,false,target),700,()->android.os.SystemClock.elapsedRealtime()>=deadline || Thread.currentThread().isInterrupted()).text); }
                catch(ReflectiveOperationException | LinkageError | RuntimeException error) { out.append("BLE type metadata unavailable\n"); }
                if(out.length()>2200) break;
            }
            out.append("Matching candidate names: ").append(names.size()).append("; metadata budget 500 ms. Missing definitions do not prove absence of BLE/GATT support.\n");
        } catch(Exception | LinkageError error) { out.append("Target BLE metadata unavailable\n"); }
        return BluetoothDiagnostics.safe(out.toString(),2500);
    }
    /** Instance/intent construction is not allowed to create a hidden SDK-owned binding or receiver. */
    private static final class DefinitionContext extends ContextWrapper {
        DefinitionContext(Context context) { super(context.getApplicationContext()); }
        @Override public Context getApplicationContext() { return this; }
        private SecurityException blocked() { return new SideEffectBlocked(); }
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags) { throw blocked(); }
        @Override public boolean bindService(Intent intent,int flags,Executor executor,ServiceConnection connection) { throw blocked(); }
        @Override public ComponentName startService(Intent intent) { throw blocked(); }
        @Override public ComponentName startForegroundService(Intent intent) { throw blocked(); }
        @Override public boolean stopService(Intent intent) { throw blocked(); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter) { throw blocked(); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags) { throw blocked(); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,String permission,Handler scheduler) { throw blocked(); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,String permission,Handler scheduler,int flags) { throw blocked(); }
        @Override public void sendBroadcast(Intent intent) { throw blocked(); }
        @Override public void sendBroadcast(Intent intent,String permission) { throw blocked(); }
    }
}
