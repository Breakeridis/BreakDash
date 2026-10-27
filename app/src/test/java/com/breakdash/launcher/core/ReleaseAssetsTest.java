package com.breakdash.launcher.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseAssetsTest {
    @Test public void arm64OnlyDeviceNeverSelectsAnIncompatible32BitApk() {
        String[] supported = {"arm64-v8a"};
        assertEquals(0, ReleaseAssets.priority("BreakDash-v1.2.0-arm64-v8a.apk", supported));
        assertEquals(Integer.MAX_VALUE, ReleaseAssets.priority("BreakDash-v1.2.0-armeabi-v7a.apk", supported));
    }

    @Test public void headUnitStillSelectsIts32BitApk() {
        String[] headUnit = {"armeabi-v7a", "armeabi"};
        assertEquals(0, ReleaseAssets.priority("BreakDash-v1.2.0-armeabi-v7a.apk", headUnit));
        assertEquals(Integer.MAX_VALUE, ReleaseAssets.priority("BreakDash-v1.2.0-arm64-v8a.apk", headUnit));
    }

    @Test public void prefersNative64BitWhenBothAreSupported() {
        String[] supported = {"arm64-v8a", "armeabi-v7a"};
        assertTrue(ReleaseAssets.priority("BreakDash-v2.0.0-arm64-v8a.apk", supported)
                < ReleaseAssets.priority("BreakDash-v2.0.0-armeabi-v7a.apk", supported));
    }

    @Test public void ignoresChecksumsAndUnrelatedAssets() {
        String[] supported = {"arm64-v8a"};
        for (String name : new String[]{"OtherApp-v1.0.0-arm64-v8a.apk", "BreakDash-v1.0.0-arm64-v8a.apk.sha256", "BreakDash-debug-arm64-v8a.apk"}) {
            assertEquals(Integer.MAX_VALUE, ReleaseAssets.priority(name, supported));
        }
    }
}
