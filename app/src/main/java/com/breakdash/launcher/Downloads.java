package com.breakdash.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.breakdash.launcher.core.Versions;
import com.breakdash.launcher.core.ReleaseAssets;
import com.breakdash.launcher.core.CertificateTrust;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

final class Downloads {
    static final class Release {
        final String tag, notes, url, digest;
        final boolean newer;
        Release(String tag,String notes,String url,String digest,boolean newer) {
            this.tag=tag; this.notes=notes; this.url=url; this.digest=digest; this.newer=newer;
        }
    }
    private Downloads() {}
    static Release latest(String repository) throws Exception {
        if(!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("Set the GitHub owner/repository in Connections");
        JSONObject release=new JSONObject(Http.json("https://api.github.com/repos/" + repository + "/releases/latest"));
        if(release.optBoolean("draft") || release.optBoolean("prerelease")) throw new IOException("No stable release found");
        String tag=release.getString("tag_name"),url="",digest="";
        JSONArray assets=release.getJSONArray("assets");
        int bestPriority=Integer.MAX_VALUE;
        for(int i=0;i<assets.length();i++) {
            JSONObject asset=assets.getJSONObject(i); String name=asset.getString("name");
            int priority=ReleaseAssets.priority(name,android.os.Build.SUPPORTED_ABIS);
            if(priority<bestPriority) {
                bestPriority=priority; url=asset.getString("browser_download_url"); digest=asset.optString("digest","");
            }
        }
        return new Release(tag,release.optString("body","No release notes"),url,digest,Versions.compare(tag,BuildConfig.VERSION_NAME)>0);
    }
    static String auroraUrl() throws Exception {
        try {
            JSONObject metadata=new JSONObject(Http.json("https://f-droid.org/api/v1/packages/com.aurora.store"));
            long suggested=metadata.optLong("suggestedVersionCode",0);
            JSONArray packages=metadata.getJSONArray("packages"); long best=0;
            for(int i=0;i<packages.length();i++) {
                long version=packages.getJSONObject(i).getLong("versionCode");
                if(version==suggested) return "https://f-droid.org/repo/com.aurora.store_" + version + ".apk";
                if(version>best) best=version;
            }
            if(best>0) return "https://f-droid.org/repo/com.aurora.store_" + best + ".apk";
            throw new IOException("No Aurora APK in F-Droid metadata");
        } catch(Exception fdroidError) {
            Http.checkInterrupted();
            return auroraGitlabUrl();
        }
    }
    private static String auroraGitlabUrl() throws Exception {
            JSONObject release=new JSONObject(Http.json("https://gitlab.com/api/v4/projects/6922885/releases/permalink/latest"));
            JSONArray links=release.getJSONObject("assets").getJSONArray("links");
            for(int i=0;i<links.length();i++) {
                String url=links.getJSONObject(i).optString("direct_asset_url",links.getJSONObject(i).optString("url"));
                if(url.matches("https://gitlab\\.com/.*AuroraStore-[0-9.]+\\.apk")) return url;
            }
            Matcher apk=Pattern.compile("\\[[^\\]]*\\]\\((/uploads/[^)]+/AuroraStore-[0-9.]+\\.apk)\\)").matcher(release.optString("description"));
            if(apk.find()) return "https://gitlab.com/AuroraOSS/AuroraStore" + apk.group(1);
            throw new IOException("Aurora download unavailable from both F-Droid and official GitLab");
    }
    static File downloadAurora(Activity activity,IntConsumer progress) throws Exception {
        String first=auroraUrl();
        try { return download(activity,first,"com.aurora.store","",progress); }
        catch(Exception firstError) {
            Http.checkInterrupted();
            if(first.startsWith("https://gitlab.com/")) throw firstError;
            return download(activity,auroraGitlabUrl(),"com.aurora.store","",progress);
        }
    }
    static File download(Activity activity,String url,String packageName,String digest,IntConsumer progress) throws Exception {
        File directory=new File(activity.getCacheDir(),"apks");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create APK cache");
        File file=File.createTempFile("download-",".apk",directory);
        boolean success=false; HttpURLConnection connection=null;
        try {
            connection=Http.get(url); long expected=connection.getContentLengthLong(),total=0;
            if(expected>160L*1024*1024) throw new IOException("APK exceeds 160 MB limit");
            MessageDigest hash=MessageDigest.getInstance("SHA-256"); int lastPercent=-2;
            try(InputStream input=connection.getInputStream(); FileOutputStream output=new FileOutputStream(file)) {
                byte[] buffer=new byte[32768]; int n;
                while((n=input.read(buffer))!=-1) {
                    Http.checkInterrupted(); total+=n;
                    if(total>160L*1024*1024) throw new IOException("APK exceeds size limit");
                    output.write(buffer,0,n); hash.update(buffer,0,n);
                    int percent=expected>0 ? (int)Math.min(99,total*100/expected) : -1;
                    if(percent!=lastPercent) { progress.accept(percent); lastPercent=percent; }
                }
            }
            if(expected>0 && total!=expected) throw new IOException("Incomplete APK download");
            StringBuilder actual=new StringBuilder(); for(byte b:hash.digest()) actual.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            if(digest.startsWith("sha256:") && !actual.toString().equalsIgnoreCase(digest.substring(7))) throw new IOException("APK checksum mismatch");
            validate(activity,file,packageName); progress.accept(100); success=true; return file;
        } finally { if(connection!=null) connection.disconnect(); if(!success) file.delete(); }
    }
    private static void validate(Activity activity,File file,String packageName) throws Exception {
        PackageManager manager=activity.getPackageManager();
        ApkSignatures archive=ApkSignatures.read(manager,packageName,file.getAbsolutePath());
        if(archive.info==null || !packageName.equals(archive.info.packageName)) throw new IOException("Downloaded APK has the wrong package or cannot be read");
        if(packageName.equals(activity.getPackageName())) {
            ApkSignatures installed=ApkSignatures.read(manager,packageName,null);
            String details="Version "+BuildConfig.VERSION_NAME+" · "+new java.util.Date()+"\nInstalled: "+installed.describe()+"\nDownloaded: "+archive.describe();
            DiagnosticHistory.update(details+"\nValidation started");
            try {
                if(installed.info==null || !packageName.equals(installed.info.packageName)) throw new IOException("Installed app cannot be inspected");
                if(archive.info.getLongVersionCode()<=installed.info.getLongVersionCode()) throw new IOException("Update version is not newer");
                if(archive.fingerprints.isEmpty() || installed.fingerprints.isEmpty()) throw new IOException("Android could not read APK certificates using either API. Send feedback for signing details.");
                if(!CertificateTrust.matches(installed.fingerprints,archive.fingerprints)) throw new IOException("Update signing certificate differs from this installation");
                DiagnosticHistory.update(details+"\nResult: package/version and signer checks passed");
            } catch(IOException error) { DiagnosticHistory.update(details+"\nResult: "+error.getMessage()); throw error; }
        }
    }
    static boolean install(Activity activity,File apk) {
        if(!activity.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return false;
        }
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",apk);
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        activity.startActivity(intent); return true;
    }
    static void prune(Activity activity) {
        File directory=new File(activity.getCacheDir(),"apks"); File[] files=directory.listFiles();
        if(files!=null) for(File file:files) if(System.currentTimeMillis()-file.lastModified()>24*60*60*1000L) file.delete();
    }
}
