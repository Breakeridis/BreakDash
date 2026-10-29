package com.breakdash.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import com.breakdash.launcher.core.FactoryApkTrust;
import com.breakdash.launcher.core.BluetoothInspectionPlan;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/** Metadata and signatures only: no class initialization, fields, construction, binding or method invocation. */
final class BluetoothFactoryInspector {
    private static final int[] TIME_END={1500,3000,4000,5000};
    private static final int[] TEXT_LIMIT={1800,1800,500,500};
    private BluetoothFactoryInspector() {}
    @SuppressWarnings("deprecation")
    static String inspect(Context context) {
        long started=SystemClock.elapsedRealtime();
        StringBuilder out=new StringBuilder(BluetoothDiagnostics.components(context));
        for(int index=0;index<BluetoothDiagnostics.FACTORY.length;index++) {
            String name=BluetoothDiagnostics.FACTORY[index]; long deadline=started+TIME_END[index];
            StringBuilder section=new StringBuilder("Focused Bluetooth signatures: "+name+"\n");
            if(expired(deadline)) { out.append(section).append("Not inspected: time budget\n"); continue; }
            try {
                ApplicationInfo info=context.getPackageManager().getApplicationInfo(name,0);
                List<String> paths=new ArrayList<>(); paths.add(info.sourceDir);
                if(info.splitSourceDirs!=null) Collections.addAll(paths,info.splitSourceDirs);
                List<String> verified=new ArrayList<>();
                for(String path:paths) {
                    File file=new File(path); String canonical=file.getCanonicalPath();
                    if(!FactoryApkTrust.accepts((info.flags&ApplicationInfo.FLAG_SYSTEM)!=0,canonical,file.isFile(),file.canRead(),file.canWrite())) throw new SecurityException();
                    verified.add(canonical);
                }
                BluetoothInspectionPlan plan=new BluetoothInspectionPlan(); int entriesRead=0;
                long inventoryDeadline=deadline-400; // Reserve part of every package's share for signatures.
                for(String path:verified) {
                    if(expired(inventoryDeadline) || entriesRead>=20000) break;
                    DexFile dex=new DexFile(path);
                    try {
                        Enumeration<String> entries=dex.entries();
                        while(entries.hasMoreElements() && entriesRead<20000 && !expired(inventoryDeadline)) { entriesRead++; plan.offer(entries.nextElement()); }
                    } finally { dex.close(); }
                }
                List<String> candidates=plan.names();
                section.append("Matching entries: ").append(plan.matches()).append("; selected: ").append(candidates.size()).append(" (cap 16)\n");
                if(candidates.isEmpty()) section.append("No focused Bluetooth classes found\n");
                ClassLoader loader=new PathClassLoader(String.join(File.pathSeparator,verified),ClassLoader.getSystemClassLoader());
                for(String contract:candidates) {
                    if(expired(deadline) || section.length()>TEXT_LIMIT[index]-120) { section.append("Additional classes omitted: time/text budget\n"); break; }
                    int classStart=section.length(); section.append("  Type ").append(contract).append('\n');
                    try {
                        Class<?> type=Class.forName(contract,false,loader);
                        Method[] methods=type.getDeclaredMethods();
                        Arrays.sort(methods,Comparator.comparingInt((Method method)->BluetoothInspectionPlan.methodPriority(method.getName())).thenComparing(Method::getName).thenComparingInt(Method::getParameterCount));
                        int count=0;
                        for(Method method:methods) {
                            if(!Modifier.isPublic(method.getModifiers())) continue;
                            if(count++>=8 || expired(deadline) || section.length()-classStart>500 || section.length()>TEXT_LIMIT[index]-150) { section.append("    More public methods omitted\n"); break; }
                            section.append("    "); if(Modifier.isStatic(method.getModifiers())) section.append("static ");
                            section.append(method.getReturnType().getName()).append(' ').append(method.getName()).append('(');
                            Class<?>[] arguments=method.getParameterTypes();
                            for(int i=0;i<Math.min(arguments.length,8);i++) { if(i>0) section.append(", "); section.append(arguments[i].getName()); }
                            if(arguments.length>8) section.append(", ..."); section.append(")\n");
                        }
                    } catch(ClassNotFoundException | LinkageError | RuntimeException error) { section.append("    Signature unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
                }
                if(expired(inventoryDeadline) || entriesRead>=20000) section.append("Class inventory may be incomplete: time/entry budget\n");
            } catch(Exception | LinkageError error) { section.append("Inspection unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
            out.append(BluetoothDiagnostics.safe(section.toString(),TEXT_LIMIT[index]));
        }
        out.append("No vendor calls, field values or APK data collected. Bluetooth-only class selection; per-package budgets.\n");
        return BluetoothDiagnostics.safe(out.toString(),6000);
    }
    private static boolean expired(long deadline) { return Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime()>=deadline; }
}
