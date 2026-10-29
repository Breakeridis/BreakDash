package com.breakdash.launcher.core;

/** Canonical, read-only factory files only; a user-installed package with the same name is not trusted code. */
public final class FactoryApkTrust {
    private FactoryApkTrust() {}
    public static boolean accepts(boolean system,String canonicalPath,boolean regularFile,boolean readable,boolean writable) {
        if(!system || canonicalPath==null || !regularFile || !readable || writable || !canonicalPath.endsWith(".apk")
                || canonicalPath.contains("/../") || canonicalPath.contains("/./")) return false;
        for(String root:new String[]{"/system/","/system_ext/","/product/","/vendor/","/odm/","/oem/"}) if(canonicalPath.startsWith(root)) return true;
        return false;
    }
}
