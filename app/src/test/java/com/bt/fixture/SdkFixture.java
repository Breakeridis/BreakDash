package com.bt.fixture;

/** Synthetic definitions only; no factory APK or report data. */
public final class SdkFixture extends FixtureParent {
    public static final class Tracker { public static boolean initialized; }
    static { Tracker.initialized=true; }
    private static SdkFixture instance;
    private SdkFixture(Object context) { throw new AssertionError("Must not construct"); }
    public static SdkFixture getInstance(Object context) { throw new AssertionError("Must not invoke"); }
    public static SdkFixture forContext(Object context) { throw new AssertionError("Must not invoke"); }
    public void startBleScan(ScanCallback callback) { throw new AssertionError("Must not invoke"); }
    public void btReset() { throw new AssertionError("Must not invoke"); }
    public interface ScanCallback { void onScanResult(int status); }
}
