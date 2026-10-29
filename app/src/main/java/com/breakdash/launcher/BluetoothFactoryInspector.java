package com.breakdash.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IInterface;
import android.os.SystemClock;
import com.breakdash.launcher.core.FactoryApkTrust;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Metadata and signatures only: no class initialization, fields, construction, binding or method invocation. */
final class BluetoothFactoryInspector {
    private BluetoothFactoryInspector() {}
    @SuppressWarnings("deprecation")
    static String inspect(Context context) {
        long deadline=SystemClock.elapsedRealtime()+5000; int candidates=0;
        StringBuilder out=new StringBuilder(BluetoothDiagnostics.components(context));
        for(String name:BluetoothDiagnostics.FACTORY) {
            if(expired(deadline) || candidates>=64) { out.append("Inspection incomplete: time/class budget\n"); break; }
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
                ClassLoader loader=new PathClassLoader(String.join(File.pathSeparator,verified),ClassLoader.getSystemClassLoader());
                out.append("Signatures from read-only factory APK: ").append(name).append('\n');
                for(String path:verified) {
                    if(expired(deadline) || candidates>=64) break;
                    DexFile dex=new DexFile(path);
                    try {
                        Enumeration<String> entries=dex.entries();
                        while(entries.hasMoreElements() && candidates<64 && !expired(deadline) && out.length()<7000) {
                            String typeName=entries.nextElement();
                            String simple=typeName.substring(typeName.lastIndexOf('.')+1);
                            if(typeName.length()>180 || !typeName.startsWith("com.nwd.") || !(typeName.endsWith("$Stub") || simple.matches("I[A-Z][A-Za-z0-9_]*"))) continue;
                            candidates++;
                            try {
                                Class<?> type=Class.forName(typeName.endsWith("$Stub") ? typeName.substring(0,typeName.length()-5) : typeName,false,loader);
                                if(!type.isInterface() || !IInterface.class.isAssignableFrom(type)) continue;
                                out.append("  Interface ").append(type.getName()).append('\n');
                                int methods=0;
                                for(Method method:type.getDeclaredMethods()) {
                                    if(methods++>=24 || expired(deadline) || out.length()>7000) break;
                                    if(Modifier.isStatic(method.getModifiers())) continue;
                                    out.append("    ").append(method.getReturnType().getName()).append(' ').append(method.getName()).append('(');
                                    Class<?>[] arguments=method.getParameterTypes();
                                    for(int i=0;i<Math.min(arguments.length,12);i++) { if(i>0) out.append(", "); out.append(arguments[i].getName()); }
                                    out.append(")\n");
                                }
                            } catch(ClassNotFoundException | LinkageError | RuntimeException error) { out.append("  Signature unavailable: ").append(error.getClass().getSimpleName()).append('\n'); }
                        }
                    } finally { dex.close(); }
                }
            } catch(Exception | LinkageError error) { out.append("Inspection unavailable for ").append(name).append(": ").append(error.getClass().getSimpleName()).append('\n'); }
        }
        if(expired(deadline) || candidates>=64 || out.length()>=7000) out.append("Inspection incomplete: time/class/text budget\n");
        out.append("No vendor calls, field values or APK data collected.\n");
        return BluetoothDiagnostics.safe(out.toString(),7500);
    }
    private static boolean expired(long deadline) { return Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime()>=deadline; }
}
