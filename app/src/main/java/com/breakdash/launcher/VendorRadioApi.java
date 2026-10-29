package com.breakdash.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import com.breakdash.launcher.core.RadioModel;
import com.breakdash.launcher.core.FactoryApkTrust;

/** Reuses the factory radio's generated AIDL/model code. Never loads a downloaded/user APK or guesses wire fields. */
final class VendorRadioApi {
    static final class Unavailable extends IOException { Unavailable(String code) { super(code); } }
    final ClassLoader loader;
    final String provenance;
    private final Class<?> contract;
    private final Method asInterface,getCurrentFrequency;
    private RadioModel model;
    private Class<?> modelType;
    private volatile String schema="No model read yet";
    private VendorRadioApi(ClassLoader loader,String provenance) throws Exception {
        this.loader=loader; this.provenance=provenance;
        contract=Class.forName(RadioController.DESCRIPTOR,false,loader);
        Class<?> stub=Class.forName(RadioController.DESCRIPTOR+"$Stub",false,loader);
        asInterface=stub.getMethod("asInterface",IBinder.class);
        getCurrentFrequency=contract.getMethod("getCurrentFrequency");
        schema="Model return type: "+getCurrentFrequency.getReturnType().getName();
    }
    static VendorRadioApi load(Context context) throws Exception {
        ApplicationInfo info=context.getPackageManager().getApplicationInfo(RadioController.PACKAGE,0);
        boolean system=(info.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
        List<String> apks=new ArrayList<>(); apks.add(factoryPath(system,info.sourceDir));
        if(info.splitSourceDirs!=null) for(String split:info.splitSourceDirs) apks.add(factoryPath(system,split));
        // Boot/system parent avoids importing this app's unrelated Parcelable classes into broadcast decoding.
        ClassLoader loader=new PathClassLoader(String.join(File.pathSeparator,apks),ClassLoader.getSystemClassLoader());
        return new VendorRadioApi(loader,"Read-only factory APK · "+info.packageName+" · "+apks.get(0));
    }
    private static String factoryPath(boolean system,String path) throws IOException {
        if(path==null) throw new Unavailable("FACTORY_APK_PATH_MISSING");
        File file=new File(path); String canonical=file.getCanonicalPath();
        if(!FactoryApkTrust.accepts(system,canonical,file.isFile(),file.canRead(),file.canWrite())) throw new Unavailable("FACTORY_APK_POLICY_REJECTED");
        return canonical;
    }
    RadioModel.Reading current(IBinder service) throws Exception {
        Object proxy=asInterface.invoke(null,service);
        if(proxy==null || !contract.isInstance(proxy)) throw new IOException("Factory radio proxy unavailable");
        return decode(getCurrentFrequency.invoke(proxy));
    }
    boolean accepts(Object value) { return value!=null && getCurrentFrequency.getReturnType().isInstance(value); }
    synchronized RadioModel.Reading decode(Object value) {
        if(value==null) return new RadioModel.Reading(0,null);
        if(!accepts(value)) return new RadioModel.Reading(0,null);
        if(model==null || modelType!=value.getClass()) { modelType=value.getClass(); model=new RadioModel(modelType); schema=model.schema(); }
        return model.read(value);
    }
    String schema() { return schema; }
}
