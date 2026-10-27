package com.breakdash.launcher.core;

public final class ReleaseAssets {
    private ReleaseAssets() {}

    /** Lower ranks follow Android's preferred ABI order; unsupported assets never match. */
    public static int priority(String filename, String[] supportedAbis) {
        for (int i = 0; i < supportedAbis.length; i++) {
            String abi = supportedAbis[i];
            if (!abi.equals("arm64-v8a") && !abi.equals("armeabi-v7a")) continue;
            if (filename.matches("(?i)BreakDash-v?[0-9]+\\.[0-9]+\\.[0-9]+-" + abi + "\\.apk")) return i;
        }
        return Integer.MAX_VALUE;
    }
}
