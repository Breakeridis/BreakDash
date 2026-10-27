package com.breakdash.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Small in-memory lifecycle trace, captured on request; no usage permission, extras, or external task surveillance. */
final class NavigationDiagnostics {
    private final Deque<String> events=new ArrayDeque<>();
    synchronized void event(String name,Intent intent) {
        String component=intent==null || intent.getComponent()==null ? "implicit" : intent.getComponent().flattenToShortString();
        String action=intent==null ? "none" : intent.getAction();
        events.addLast(String.format(Locale.ROOT,"%tT · %s · action %s · component %s · flags 0x%08X",System.currentTimeMillis(),name,action,component,intent==null ? 0 : intent.getFlags()));
        DiagnosticHistory.event(name+" · action "+action+" · component "+component+String.format(Locale.ROOT," · flags 0x%08X",intent==null ? 0 : intent.getFlags()));
        while(events.size()>16) events.removeFirst();
    }
    static String defaultHome(Context context) {
        try {
            ResolveInfo resolved=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
            return resolved==null || resolved.activityInfo==null ? "No preferred Home returned by Android" : new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name).flattenToShortString();
        } catch(Exception error) { return "Unavailable"; }
    }
    synchronized String collect(Activity activity) {
        StringBuilder out=new StringBuilder("HOME & APP NAVIGATION\nDefault Home: ").append(defaultHome(activity))
                .append("\nBreakDash task: ").append(activity.getTaskId()).append(" · activity is task root: ").append(activity.isTaskRoot()).append('\n');
        try {
            ActivityManager manager=(ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null) for(ActivityManager.AppTask task:manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info=task.getTaskInfo();
                out.append("Own task ").append(info.taskId).append(" · base ").append(info.baseActivity).append(" · top ").append(info.topActivity).append('\n');
            }
        } catch(Exception error) { out.append("Own task details unavailable\n"); }
        out.append("Recent BreakDash lifecycle/launch events (no intent extras or URLs):\n");
        for(String event:events) out.append(event).append('\n');
        return out.toString();
    }
}
