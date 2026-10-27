package com.breakdash.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppCatalog {
    static final class App {
        final String label, component, packageName;
        App(String label, ComponentName component) { this.label=label; this.component=component.flattenToString(); packageName=component.getPackageName(); }
    }
    private final Context context;
    private final LruCache<String,Drawable> icons=new LruCache<>(20);
    AppCatalog(Context context) { this.context=context; }
    List<App> load() {
        PackageManager manager=context.getPackageManager(); List<App> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo info:manager.queryIntentActivities(query,0)) {
            if(info.activityInfo.packageName.equals(context.getPackageName())) continue;
            App app=new App(info.loadLabel(manager).toString(),new ComponentName(info.activityInfo.packageName,info.activityInfo.name));
            if(seen.add(app.component)) result.add(app);
        }
        Collator collator=Collator.getInstance(); result.sort((a,b)->collator.compare(a.label,b.label)); return result;
    }
    Drawable icon(App app) {
        Drawable icon=icons.get(app.component); if(icon!=null) return icon;
        try { icon=context.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(app.component)); }
        catch(Exception error) { icon=context.getPackageManager().getDefaultActivityIcon(); }
        int size=Math.round(48*context.getResources().getDisplayMetrics().density);
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        icon.setBounds(0,0,size,size); icon.draw(new Canvas(bitmap));
        Drawable scaled=new BitmapDrawable(context.getResources(),bitmap); icons.put(app.component,scaled); return scaled;
    }
    void clearIcons() { icons.evictAll(); }
    static String projection(List<App> apps) {
        for(String name:new String[]{"zlink","autokit","tlink"}) for(App app:apps) if(app.label.toLowerCase(Locale.ROOT).contains(name)) return app.component;
        return "";
    }
}
