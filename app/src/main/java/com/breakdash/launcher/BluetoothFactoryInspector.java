package com.breakdash.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import com.breakdash.launcher.core.FactoryApkTrust;
import com.breakdash.launcher.core.BluetoothInspectionPlan;
import com.breakdash.launcher.core.BluetoothSdkSchema;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Metadata and signatures only: no class initialization, fields, construction, binding or method invocation. */
final class BluetoothFactoryInspector {
    private static final int[] TIME_END={2800,4000,4500,5000};
    private static final int[] TEXT_LIMIT={20000,4500,700,700};
    private BluetoothFactoryInspector() {}
    @SuppressWarnings("deprecation")
    static String inspect(Context context) {
        long started=SystemClock.elapsedRealtime();
        StringBuilder out=new StringBuilder(BluetoothDiagnostics.components(context));
        BluetoothSdkSchema.Deduplicator definitions=new BluetoothSdkSchema.Deduplicator();
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
                BluetoothInspectionPlan plan=new BluetoothInspectionPlan(); int entriesRead=0; boolean incomplete=false;
                long inventoryDeadline=deadline-400; // Reserve part of every package's share for signatures.
                for(String path:verified) {
                    if(expired(inventoryDeadline) || entriesRead>=20000) { incomplete=true; break; }
                    DexFile dex=new DexFile(path);
                    try {
                        Enumeration<String> entries=dex.entries();
                        while(entries.hasMoreElements() && entriesRead<20000 && !expired(inventoryDeadline)) { entriesRead++; plan.offer(entries.nextElement()); }
                        if(entries.hasMoreElements()) incomplete=true;
                    } finally { dex.close(); }
                }
                List<String> candidates=plan.names(); Set<String> inspected=new HashSet<>();
                section.append("Matching entries: ").append(plan.matches()).append("; selected: ").append(candidates.size()).append(" (cap 16)\n");
                if(candidates.isEmpty()) section.append("No focused Bluetooth classes found\n");
                ClassLoader loader=new PathClassLoader(String.join(File.pathSeparator,verified),ClassLoader.getSystemClassLoader());
                for(int candidate=0;candidate<candidates.size() && inspected.size()<24;candidate++) {
                    String contract=candidates.get(candidate); if(!inspected.add(contract)) continue;
                    if(expired(deadline) || section.length()>TEXT_LIMIT[index]-240) { section.append("Additional classes omitted: time/text budget\n"); break; }
                    try {
                        Class<?> type=Class.forName(contract,false,loader);
                        BluetoothSdkSchema.Result result=BluetoothSdkSchema.inspect(type,BluetoothInspectionPlan.schemaLimit(contract),()->expired(deadline));
                        int available=TEXT_LIMIT[index]-section.length();
                        section.append(BluetoothDiagnostics.safe(definitions.display(name,contract,result,available),available));
                        for(Class<?> related:result.related) {
                            if(inspected.contains(related.getName()) || candidates.contains(related.getName()) || candidates.size()>=32) continue;
                            candidates.add(related.getName());
                        }
                        candidates.subList(candidate+1,candidates.size()).sort(Comparator.comparingInt(BluetoothInspectionPlan::priority).thenComparing(Comparator.naturalOrder()));
                    } catch(ClassNotFoundException | LinkageError | RuntimeException error) { section.append("  Type ").append(contract).append(" metadata unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
                }
                if(incomplete) section.append("Class inventory may be incomplete: time/entry budget\n");
            } catch(Exception | LinkageError error) { section.append("Inspection unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
            out.append(BluetoothDiagnostics.safe(section.toString(),TEXT_LIMIT[index]));
        }
        out.append("No vendor calls, field values or APK data collected. Bluetooth-only class selection; per-package budgets.\n");
        return BluetoothDiagnostics.safe(out.toString(),28000);
    }
    private static boolean expired(long deadline) { return Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime()>=deadline; }
}
