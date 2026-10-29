package com.android.utils.utils;

/** Synthetic inheritance/callback fixture, unrelated to any installed vendor implementation. */
public class AbsServiceControllerHelper {
    public Object getFeature() { throw new AssertionError("Must not invoke"); }
    public void disconnectService() { throw new AssertionError("Must not invoke"); }
    public void connectService(OnServiceConnectSuccessListener listener) { throw new AssertionError("Must not invoke"); }
    public interface OnServiceConnectSuccessListener { void onServiceConnectSuccess(); }
}
