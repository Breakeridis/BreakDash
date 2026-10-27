package com.breakdash.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Package changes require an explicit in-app control. No persistent ADB connection or startup shell. */
final class SystemTools {
    static final String[] GOOGLE={"com.google.android.gsf","com.google.android.gms","com.android.vending"};
    private static final Set<String> ALLOWED=new HashSet<>(Arrays.asList(GOOGLE));
    private final Context context;
    private long previousTotal,previousIdle;
    SystemTools(Context context) { this.context=context.getApplicationContext(); }
    void resetMonitor() { previousTotal=0; previousIdle=0; }
    boolean portOpen() {
        try(Socket socket=new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1",5555),500); return true; }
        catch(IOException error) { return false; }
    }
    boolean rootBinaryPresent() {
        for(String path:new String[]{"/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su","/data/adb/magisk/su"}) if(new File(path).canExecute()) return true;
        return false;
    }
    private Dadb connect() throws Exception {
        File directory=new File(context.getNoBackupFilesDir(),"adb");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private ADB key directory");
        File privateKey=new File(directory,"adbkey"),publicKey=new File(directory,"adbkey.pub");
        if(!privateKey.isFile() || !publicKey.isFile()) AdbKeyPair.generate(privateKey,publicKey);
        return Dadb.create("127.0.0.1",5555,AdbKeyPair.read(privateKey,publicKey),2000,20000);
    }
    synchronized String testAdb() throws Exception {
        try(Dadb adb=connect()) {
            AdbShellResponse response=adb.shell("id");
            if(response.getExitCode()!=0) throw new IOException(response.getAllOutput());
            return "ADB authorized · " + response.getAllOutput().trim();
        }
    }
    static String command(String packageName,boolean enabled) {
        if(!ALLOWED.contains(packageName)) throw new IllegalArgumentException("Package is not managed by BreakDash");
        return enabled ? "(pm unhide --user 0 " + packageName + " || true); (cmd package install-existing --user 0 " + packageName + " || true); pm enable --user 0 " + packageName
                : "pm disable-user --user 0 " + packageName;
    }
    synchronized String toggle(String packageName,boolean enabled) throws Exception {
        String command=command(packageName,enabled),adbFailure="";
        if(portOpen()) {
            try(Dadb adb=connect()) {
                AdbShellResponse response=adb.shell(command);
                if(response.getExitCode()!=0 || response.getAllOutput().contains("SecurityException")) throw new IOException(response.getAllOutput());
                return "Local ADB: " + response.getAllOutput().trim();
            } catch(Exception error) { adbFailure=error.getMessage(); }
        }
        if(rootBinaryPresent()) return "Root: " + process(new String[]{"su","-c",command},12);
        try {
            context.getPackageManager().setApplicationEnabledSetting(packageName,enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,0);
            return "System API: " + packageName + (enabled ? " enabled" : " disabled");
        } catch(SecurityException error) {
            throw new IOException("System permission denied. Enable TCP ADB on port 5555 and tap Connect ADB." + (adbFailure.isEmpty() ? "" : " ADB: " + adbFailure));
        }
    }
    String packageStatus(String packageName) {
        try {
            ApplicationInfo info=context.getPackageManager().getApplicationInfo(packageName,PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES);
            if((info.flags & ApplicationInfo.FLAG_INSTALLED)==0) return "Hidden / not installed for user 0";
            return info.enabled ? "Enabled" : "Disabled";
        } catch(PackageManager.NameNotFoundException error) { return "Not installed"; }
    }
    boolean packageEnabled(String packageName) { return "Enabled".equals(packageStatus(packageName)); }
    boolean packageInstalled(String packageName) {
        try { return (context.getPackageManager().getApplicationInfo(packageName,PackageManager.MATCH_DISABLED_COMPONENTS).flags & ApplicationInfo.FLAG_INSTALLED)!=0; }
        catch(PackageManager.NameNotFoundException error) { return false; }
    }
    String auroraStatus() {
        try { return "Aurora Store " + context.getPackageManager().getPackageInfo("com.aurora.store",0).versionName; }
        catch(PackageManager.NameNotFoundException error) { return "Aurora Store not installed"; }
    }
    String monitor() {
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        double gb=1024.0*1024*1024,mb=1024.0*1024;
        String cpu="CPU unavailable (/proc/stat restricted by firmware)";
        try(BufferedReader reader=new BufferedReader(new FileReader("/proc/stat"))) {
            String first=reader.readLine();
            if(first!=null && first.startsWith("cpu ")) {
                String[] values=first.trim().split("\\s+"); long total=0;
                for(int i=1;i<Math.min(values.length,9);i++) total+=Long.parseLong(values[i]);
                long idle=Long.parseLong(values[4])+(values.length>5 ? Long.parseLong(values[5]) : 0);
                long delta=total-previousTotal,idleDelta=idle-previousIdle;
                cpu=previousTotal==0 || delta<=0 ? "CPU · sampling…" : String.format(Locale.US,"CPU %.0f%% · %d active cores",Math.max(0,Math.min(100,100.0*(delta-idleDelta)/delta)),Runtime.getRuntime().availableProcessors());
                previousTotal=total; previousIdle=idle;
            }
        } catch(Exception ignored) { /* Android 10 may deny /proc/stat; never fabricate readings. */ }
        return cpu + String.format(Locale.US,"\nRAM %.2f / %.2f GB (%.0f%%) · %.0f MB available\nBreakDash PSS %.1f MB\nRAM totals are firmware-reported and may be inflated on this unit.",
                (memory.totalMem-memory.availMem)/gb,memory.totalMem/gb,100.0*(memory.totalMem-memory.availMem)/Math.max(1,memory.totalMem),memory.availMem/mb,android.os.Debug.getPss()/1024.0);
    }
    static String process(String[] command,int seconds) throws Exception {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();
        Thread reader=new Thread(() -> {
            try(java.io.InputStream input=process.getInputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while((n=input.read(buffer))!=-1) if(result.size()<256*1024) result.write(buffer,0,Math.min(n,256*1024-result.size()));
            } catch(IOException ignored) {}
        },"BreakDash-command-output");
        reader.setDaemon(true); reader.start();
        try {
            if(!process.waitFor(seconds,TimeUnit.SECONDS)) throw new IOException("Command timed out");
            reader.join(1000); String output=result.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            if(process.exitValue()!=0) throw new IOException(output.isEmpty() ? "Command failed" : output);
            return output;
        } finally { process.destroyForcibly(); process.getInputStream().close(); reader.interrupt(); reader.join(1000); }
    }
}
