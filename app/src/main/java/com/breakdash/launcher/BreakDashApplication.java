package com.breakdash.launcher;

import android.app.Application;

public final class BreakDashApplication extends Application {
    @Override public void onCreate() { super.onCreate(); DiagnosticHistory.install(this); }
}
