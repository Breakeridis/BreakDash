package com.breakdash.launcher;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Public Android APIs only. Legacy certificate collection is a compatibility fallback, never a bypass. */
final class ApkSignatures {
    final PackageInfo info;
    final List<String> fingerprints;
    final String source;
    private ApkSignatures(PackageInfo info,Signature[] signatures,String source) throws Exception {
        this.info=info; this.source=source; fingerprints=new ArrayList<>();
        if(signatures!=null) for(Signature signature:signatures) {
            if(signature==null) { fingerprints.clear(); break; }
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex=new StringBuilder(); for(byte b:digest) hex.append(String.format(Locale.ROOT,"%02x",b & 255));
            fingerprints.add(hex.toString());
        }
    }
    @SuppressWarnings("deprecation")
    static ApkSignatures read(PackageManager manager,String packageName,String archivePath) throws Exception {
        PackageInfo modern=archivePath==null ? manager.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES)
                : manager.getPackageArchiveInfo(archivePath,PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] current=modern==null || modern.signingInfo==null ? null : modern.signingInfo.getApkContentsSigners();
        if(current!=null && current.length>0) return new ApkSignatures(modern,current,"SigningInfo");
        PackageInfo legacy=archivePath==null ? manager.getPackageInfo(packageName,PackageManager.GET_SIGNATURES)
                : manager.getPackageArchiveInfo(archivePath,PackageManager.GET_SIGNATURES);
        if(modern!=null && legacy!=null && (!modern.packageName.equals(legacy.packageName) || modern.getLongVersionCode()!=legacy.getLongVersionCode()))
            throw new IOException("Package changed during signature inspection; check for updates again");
        return new ApkSignatures(legacy==null ? modern : legacy,legacy==null ? null : legacy.signatures,"GET_SIGNATURES fallback");
    }
    String describe() {
        return (info==null ? "Package unreadable" : info.packageName+" · version code "+info.getLongVersionCode())
                +" · "+source+" · signer SHA-256: "+(fingerprints.isEmpty() ? "unavailable" : String.join(", ",fingerprints));
    }
}
