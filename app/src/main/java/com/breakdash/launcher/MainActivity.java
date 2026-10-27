package com.breakdash.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.breakdash.launcher.core.RadioFrequency;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    final Tasks tasks=new Tasks();
    Prefs prefs; Ui ui; Panels panels; AppCatalog catalog; SystemTools system;
    RadioController radio; MapPortal portal;
    List<AppCatalog.App> apps=new ArrayList<>();
    Downloads.Release release;
    private Tasks.Scope life,visible,pinnedRouteScope;
    private boolean resumed,dashboardActive,updateAttempted,pinRouting;
    private TextView clock,seconds,date,frequency,radioStatus,metadata,heading,speed,unit,gps,guidance,about;
    private Button radioPower,recenter,navigate,cancelNavigation;
    private final Button[] presets=new Button[6];
    private LinearLayout favorites;
    private LinearLayout navigationFooter;
    private final java.util.Map<String,TextView> tileCaptions=new java.util.HashMap<>(),tileHints=new java.util.HashMap<>();
    private String gpsMessage="Acquiring satellites";
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(!dashboardActive) return;
            Date now=new Date(); boolean is24=DateFormat.is24HourFormat(MainActivity.this);
            clock.setText(DateFormat.format(is24 ? "HH:mm" : "h:mm",now));
            seconds.setText(DateFormat.format(is24 ? "ss" : "ss a",now));
            date.setText(new java.text.SimpleDateFormat("EEEE, MMM d",Locale.getDefault()).format(now));
            renderLocation(); tasks.main.postDelayed(this,1000-System.currentTimeMillis()%1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=new Prefs(this); ui=new Ui(this,prefs.light()); catalog=new AppCatalog(this); system=new SystemTools(this);
        panels=new Panels(this); life=tasks.scope();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        radio=new RadioController(this,tasks,ignored->renderRadio(),this::toast);
        buildDashboard(state);
        life.run(()->{ Downloads.prune(this); return true; },ignored->{},error->{});
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused) immersive(); }
    private void buildDashboard(Bundle state) {
        LinearLayout left=ui.column(),right=ui.column(); FrameLayout middle=new FrameLayout(this);
        CockpitLayout root=new CockpitLayout(this,left,middle,right); root.setBackgroundColor(ui.bg);
        setContentView(new CockpitViewport(this,root,ui.bg));

        TextView brand=ui.label("B R E A K D A S H   /   COCKPIT"); brand.setTextColor(ui.accent); left.addView(brand); ui.gap(left,18);
        LinearLayout timeRow=ui.row(); timeRow.setGravity(Gravity.BOTTOM);
        clock=ui.text("--:--",70,ui.text); clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        seconds=ui.text("--",18,ui.accent); seconds.setPadding(ui.dp(8),0,0,ui.dp(12));
        timeRow.addView(clock); timeRow.addView(seconds); left.addView(timeRow);
        date=ui.text("",17,ui.muted); left.addView(date); ui.gap(left,20);

        LinearLayout tuner=ui.column(); tuner.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12)); tuner.setBackground(ui.surface(ui.card,ui.border,22));
        LinearLayout stationHeader=ui.row();
        Button station=ui.button("RADIO  ↗",()->launchPackage("com.nwd.radio")); station.setTextColor(ui.accent); station.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        stationHeader.addView(station,new LinearLayout.LayoutParams(0,ui.dp(60),1));
        radioPower=ui.button("○",()->radio.power(!radio.state.playing)); radioPower.setContentDescription("Toggle radio audio"); stationHeader.addView(radioPower,new LinearLayout.LayoutParams(ui.dp(60),ui.dp(60)));
        tuner.addView(stationHeader);
        LinearLayout frequencyRow=ui.row(); frequency=ui.text("—",46,ui.text); frequency.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        frequencyRow.addView(frequency,new LinearLayout.LayoutParams(0,ui.dp(58),1));
        Button band=ui.button("Band",()->radio.changeBand()); frequencyRow.addView(band,new LinearLayout.LayoutParams(ui.dp(60),ui.dp(64))); tuner.addView(frequencyRow);
        radioStatus=ui.text("Connecting to hardware…",12,ui.amber); radioStatus.setSingleLine(true); tuner.addView(radioStatus);
        metadata=ui.text("NWD hardware tuner",14,ui.muted); metadata.setMaxLines(2); tuner.addView(metadata,new LinearLayout.LayoutParams(-1,ui.dp(38)));
        LinearLayout seek=ui.row(); ui.weighted(seek,ui.button("‹  Seek",()->radio.seek(false)),64); ui.weighted(seek,ui.button("Seek  ›",()->radio.seek(true)),64); tuner.addView(seek);
        for(int row=0;row<2;row++) {
            LinearLayout bank=ui.row();
            for(int column=0;column<3;column++) {
                int slot=row*3+column;
                Button preset=ui.button((slot+1)+"\n—",()->radio.tune(prefs.frequency(slot),prefs.band(slot),slot)); preset.setTextSize(13); preset.setMaxLines(2); preset.setContentDescription("Preset " + (slot+1) + ", hold to save");
                preset.setOnLongClickListener(view->{
                    int value=radio.state.frequency; byte bandValue=radio.state.band;
                    if(!RadioFrequency.valid(value)) { toast("No current station to save"); return true; }
                    radio.save(slot,()->{ prefs.preset(slot,value,bandValue); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); renderRadio(); toast("Saved preset " + (slot+1)); }); return true;
                });
                presets[slot]=preset; ui.weighted(bank,preset,64);
            }
            tuner.addView(bank);
        }
        left.addView(tuner,new LinearLayout.LayoutParams(-1,0,1)); ui.gap(left,14);
        LinearLayout dock=ui.row(); Button drawer=ui.button("▦",()->panels.appPicker("All apps",null,null)); drawer.setTextSize(28); drawer.setContentDescription("All apps"); dock.addView(drawer,new LinearLayout.LayoutParams(ui.dp(72),ui.dp(80)));
        HorizontalScrollView shelf=new HorizontalScrollView(this); shelf.setHorizontalScrollBarEnabled(false); favorites=ui.row(); shelf.addView(favorites); dock.addView(shelf,new LinearLayout.LayoutParams(0,ui.dp(80),1)); left.addView(dock);

        portal=new MapPortal(this,prefs,state,ignored->renderLocation(),message->{ gpsMessage=message; renderLocation(); },this::pinDestination);
        middle.addView(portal,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER)); portal.theme(prefs.light());

        heading=ui.label("—° / FOLLOW"); heading.setLetterSpacing(.05f); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6)); heading.setBackground(ui.surface(ui.card,ui.border,16));
        heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams headingPosition=new FrameLayout.LayoutParams(ui.dp(210),ui.dp(36),Gravity.TOP|Gravity.LEFT); headingPosition.setMargins(ui.dp(8),ui.dp(8),0,0); middle.addView(heading,headingPosition);

        LinearLayout mapTools=ui.row();
        recenter=ui.mapButton("◎",()->{ portal.recenter(); if(portal.fix()==null) toast("Waiting for a fresh GPS fix"); }); recenter.setTextSize(28);
        recenter.setContentDescription("Recenter map and resume heading-up GPS tracking"); recenter.setTooltipText("Recenter"); mapTools.addView(recenter,new LinearLayout.LayoutParams(ui.dp(60),ui.dp(60)));
        Button search=ui.mapButton("⌕",panels::search); search.setTextSize(30); search.setContentDescription("Search destination"); search.setTooltipText("Search destination");
        LinearLayout.LayoutParams searchPosition=new LinearLayout.LayoutParams(ui.dp(60),ui.dp(60)); searchPosition.leftMargin=ui.dp(6); mapTools.addView(search,searchPosition);
        FrameLayout.LayoutParams toolsPosition=new FrameLayout.LayoutParams(-2,ui.dp(60),Gravity.TOP|Gravity.RIGHT); toolsPosition.setMargins(0,ui.dp(8),ui.dp(8),0); middle.addView(mapTools,toolsPosition);

        LinearLayout speedCard=ui.row(); speedCard.setGravity(Gravity.CENTER); speedCard.setPadding(ui.dp(12),ui.dp(2),ui.dp(6),ui.dp(2)); speedCard.setBackground(ui.surface(ui.card,ui.border,20));
        speedCard.setClickable(true);
        speed=ui.text("—",46,ui.text); speed.setMinWidth(ui.dp(84)); speed.setGravity(Gravity.CENTER); speed.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); speedCard.addView(speed);
        Button units=ui.mapButton(prefs.mph() ? "MPH" : "KM/H",()->{ prefs.toggleUnits(); renderLocation(); }); unit=units; units.setTextSize(12); units.setTextColor(ui.accent); units.setContentDescription("Toggle speed units"); speedCard.addView(units,new LinearLayout.LayoutParams(ui.dp(60),ui.dp(60)));
        FrameLayout.LayoutParams speedPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM); speedPosition.bottomMargin=ui.dp(8); middle.addView(speedCard,speedPosition);

        gps=ui.mapButton("● GPS",this::gpsAction); gps.setTextSize(12);
        FrameLayout.LayoutParams gpsPosition=new FrameLayout.LayoutParams(ui.dp(106),ui.dp(60),Gravity.BOTTOM|Gravity.LEFT); gpsPosition.setMargins(ui.dp(8),0,0,ui.dp(8)); middle.addView(gps,gpsPosition);

        navigationFooter=ui.row(); navigationFooter.setPadding(ui.dp(12),ui.dp(6),ui.dp(8),ui.dp(6)); navigationFooter.setBackground(ui.surface(ui.card,ui.border,18)); navigationFooter.setClickable(true);
        guidance=ui.text("",13,ui.text); guidance.setMaxLines(2); guidance.setEllipsize(android.text.TextUtils.TruncateAt.END); navigationFooter.addView(guidance,new LinearLayout.LayoutParams(0,-2,1));
        navigate=ui.mapButton("Navigate",this::startPinnedNavigation); navigate.setTextColor(ui.accent); navigationFooter.addView(navigate,new LinearLayout.LayoutParams(ui.dp(88),ui.dp(60)));
        cancelNavigation=ui.mapButton("Stop",this::clearMapTarget); cancelNavigation.setTextColor(ui.amber); LinearLayout.LayoutParams cancelPosition=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(60)); cancelPosition.leftMargin=ui.dp(6); navigationFooter.addView(cancelNavigation,cancelPosition);
        FrameLayout.LayoutParams navigationPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); navigationPosition.setMargins(ui.dp(64),0,ui.dp(64),ui.dp(88)); middle.addView(navigationFooter,navigationPosition);

        TextView attribution=ui.text("© OpenStreetMap · OpenMapTiles · OpenFreeMap",10,ui.muted); attribution.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); attribution.setMinHeight(ui.dp(24)); attribution.setContentDescription("Map credits"); attribution.setOnClickListener(v->panels.attribution());
        FrameLayout.LayoutParams creditsPosition=new FrameLayout.LayoutParams(-2,ui.dp(24),Gravity.BOTTOM|Gravity.RIGHT); creditsPosition.setMargins(0,0,ui.dp(8),ui.dp(8)); middle.addView(attribution,creditsPosition);

        right.addView(ui.label("YOUR DRIVE. IN FOCUS.")); ui.gap(right,18);
        TextView title=ui.text("Make yourself\nat home.",30,ui.text); title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); right.addView(title); ui.gap(right,8);
        right.addView(ui.text("Your essentials, one touch away.",14,ui.muted)); ui.gap(right,24);
        LinearLayout cards=ui.column();
        for(int row=0;row<2;row++) {
            LinearLayout cardRow=ui.row();
            for(int column=0;column<2;column++) {
                int index=row*2+column; String key=new String[]{"dvr","projection","nav","music"}[index];
                String label=new String[]{"Dashcam","Phone link","Navigate","Music"}[index];
                LinearLayout tile=ui.column(); tile.setGravity(Gravity.CENTER); tile.setPadding(ui.dp(8),ui.dp(14),ui.dp(8),ui.dp(14));
                tile.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3342D6CB),ui.surface(ui.card,ui.border,22),null));
                tile.addView(ui.glyph(key,index==1 ? ui.amber : ui.accent),new LinearLayout.LayoutParams(ui.dp(38),ui.dp(38))); ui.gap(tile,18);
                TextView caption=ui.text(label,18,ui.text); caption.setGravity(Gravity.CENTER); tile.addView(caption); tileCaptions.put(key,caption);
                TextView hint=ui.text("Hold to choose app",11,ui.muted); hint.setGravity(Gravity.CENTER); hint.setMaxLines(1); hint.setEllipsize(android.text.TextUtils.TruncateAt.END); ui.gap(tile,8); tile.addView(hint); tileHints.put(key,hint);
                tile.setContentDescription(label + ", hold to choose app"); tile.setFocusable(true); tile.setOnClickListener(v->launchSlot(key)); tile.setOnLongClickListener(v->{ chooseSlot(key); return true; });
                LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-1,1); tileParams.setMargins(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4)); cardRow.addView(tile,tileParams);
            }
            cards.addView(cardRow,new LinearLayout.LayoutParams(-1,0,1));
        }
        right.addView(cards,new LinearLayout.LayoutParams(-1,0,1)); ui.gap(right,14);
        LinearLayout utility=ui.row(); about=ui.button("About",panels::about); ui.weighted(utility,about,80);
        Button settings=ui.button("Settings",panels::settings); settings.setOnLongClickListener(v->{ panels.diagnostics(false); return true; }); settings.setContentDescription("Settings, hold for diagnostics"); ui.weighted(utility,settings,80); right.addView(utility);
        renderRadio(); renderFavorites(); renderLocation();
    }
    void defaults() {
        if(prefs.get("slot_projection","").isEmpty()) prefs.put("slot_projection",AppCatalog.projection(apps));
        if(prefs.get("slot_music","").isEmpty()) assignPackage("music","com.nwd.android.music.ui");
        if(prefs.get("slot_nav","").isEmpty()) for(String name:new String[]{"com.google.android.apps.maps","net.osmand.plus","net.osmand","com.waze"}) if(assignPackage("nav",name)) break;
        renderTiles();
    }
    private boolean assignPackage(String key,String name) { for(AppCatalog.App app:apps) if(app.packageName.equals(name)) { prefs.put("slot_"+key,app.component); return true; } return false; }
    void chooseSlot(String key) { panels.appPicker("Choose " + key,null,app->{ prefs.put("slot_"+key,app.component); renderTiles(); toast(app.label + " selected"); }); }
    private void renderTiles() {
        for(String key:new String[]{"dvr","projection","nav","music"}) {
            AppCatalog.App app=find(prefs.get("slot_"+key,""));
            TextView hint=tileHints.get(key); if(hint!=null) hint.setText(app==null ? "Hold to choose app" : app.label);
            if(key.equals("projection") && tileCaptions.get(key)!=null) tileCaptions.get(key).setText(app==null ? "Phone link" : app.label);
        }
    }
    void launchSlot(String key) { String component=prefs.get("slot_"+key,""); if(component.isEmpty()) chooseSlot(key); else launch(component); }
    void launch(String value) {
        try {
            ComponentName component=ComponentName.unflattenFromString(value);
            if(component==null) throw new IllegalArgumentException("App shortcut is unavailable");
            startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch(Exception error) { toast("App unavailable. Hold its shortcut to choose another."); }
    }
    void launchPackage(String name) {
        Intent launch=getPackageManager().getLaunchIntentForPackage(name);
        if(launch==null && name.equals("com.nwd.radio")) launch=new Intent().setComponent(new ComponentName(name,"com.nwd.radio.home_horizontalActivity"));
        if(launch==null) { toast("App is not installed or is disabled"); return; }
        try { startActivity(launch); } catch(Exception error) { toast("Unable to open " + name); }
    }
    AppCatalog.App find(String component) { for(AppCatalog.App app:apps) if(app.component.equals(component)) return app; return null; }
    void pin(AppCatalog.App app) {
        List<String> values=prefs.favorites(); if(values.contains(app.component)) return;
        if(values.size()>=4) { toast("Dock is full. Hold an app to replace it."); return; }
        values.add(app.component); prefs.favorites(values); renderFavorites(); toast("Pinned " + app.label);
    }
    void renderFavorites() {
        if(favorites==null) return; favorites.removeAllViews(); List<String> values=prefs.favorites();
        for(String value:values) {
            AppCatalog.App app=find(value); String label=app==null ? "Missing" : app.label;
            Button button=ui.button(label,()->launch(value)); button.setTextSize(11); button.setMaxLines(2);
            button.setContentDescription(label);
            if(app!=null) {
                android.graphics.drawable.Drawable icon=catalog.icon(app).getConstantState().newDrawable();
                icon.setBounds(0,0,ui.dp(28),ui.dp(28)); button.setCompoundDrawables(null,icon,null,null); button.setCompoundDrawablePadding(ui.dp(4));
                button.setMaxLines(1); button.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            button.setOnLongClickListener(v->{ panels.dockAction(value); return true; });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ui.dp(80),ui.dp(80)); params.setMargins(ui.dp(4),0,0,0); favorites.addView(button,params);
        }
        if(values.size()<4) favorites.addView(ui.button("+",()->panels.appPicker("Pin an app",prefs.favorites(),this::pin)),new LinearLayout.LayoutParams(ui.dp(64),ui.dp(80)));
    }
    private void renderRadio() {
        if(frequency==null || radio==null) return;
        RadioController.State state=radio.state;
        frequency.setText(RadioFrequency.number(state.frequency));
        radioPower.setText(state.playing ? "●" : "○"); radioPower.setTextColor(state.playing ? ui.accent : ui.muted);
        radioStatus.setText((RadioFrequency.valid(state.frequency) ? RadioFrequency.unit(state.frequency)+"  ·  " : "") + (state.connected ? state.playing ? "ON AIR" : "STANDBY" : "TUNER DISCONNECTED"));
        radioStatus.setTextColor(state.playing ? ui.accent : ui.amber);
        metadata.setText(!state.rt.isEmpty() ? state.ps + "  " + state.rt : !state.ps.isEmpty() ? state.ps : "Hold a preset to save your station");
        for(int i=0;i<6;i++) if(presets[i]!=null) {
            int saved=prefs.frequency(i); presets[i].setText((i+1)+"\n"+RadioFrequency.number(saved));
            presets[i].setTextColor(saved!=0 && saved==state.frequency && prefs.band(i)==state.band ? ui.accent : ui.muted);
        }
    }
    private void renderLocation() {
        if(portal==null || speed==null) return;
        Location fix=portal.fix(); boolean valid=fix!=null;
        speed.setText(valid && fix.hasSpeed() ? String.format(Locale.US,"%.0f",fix.getSpeed()*(prefs.mph() ? 2.236936292 : 3.6)) : "—");
        unit.setText(prefs.mph() ? "MPH" : "KM/H");
        String[] directions={"N","NE","E","SE","S","SW","W","NW"}; int bearing=(int)Math.round(portal.heading())%360;
        String mode=portal.following() ? "FOLLOW" : "EXPLORE";
        heading.setText(valid ? String.format(Locale.US,"%03d° %s / %s",bearing,directions[(int)Math.round(bearing/45.0)%8],mode) : "—° / "+mode);
        recenter.setTextColor(portal.following() ? ui.muted : ui.accent);
        String gpsStatus=valid ? "GPS lock, accuracy ±"+(int)fix.getAccuracy()+" m" : gpsMessage;
        gps.setText(valid ? "● ±"+(int)fix.getAccuracy()+" m" : "● GPS"); gps.setTextColor(valid ? ui.accent : ui.amber); gps.setContentDescription(gpsStatus+". Tap for location settings."); gps.setTooltipText(gpsStatus);
        Navigation.Place selected=portal.selection();
        boolean hasNavigation=selected!=null || portal.route!=null;
        navigationFooter.setVisibility(hasNavigation || !prefs.data.getBoolean("online_maps",false) ? View.VISIBLE : View.GONE);
        cancelNavigation.setVisibility(selected==null && portal.route==null ? View.GONE : View.VISIBLE);
        navigate.setVisibility(selected==null ? View.GONE : View.VISIBLE);
        navigate.setEnabled(selected!=null && valid && !pinRouting && prefs.data.getBoolean("online_maps",false));
        navigate.setAlpha(navigate.isEnabled() ? 1f : .45f);
        cancelNavigation.setText(selected==null ? "Stop" : "Clear");
        if(selected!=null) {
            String coordinates=String.format(Locale.US,"%.5f, %.5f",selected.lat,selected.lon);
            String distance=valid ? Navigation.distance(com.breakdash.launcher.core.RouteProgress.distance(fix.getLatitude(),fix.getLongitude(),selected.lat,selected.lon),prefs.mph())+" away" : "Waiting for GPS";
            guidance.setText((pinRouting ? "Calculating route…" : "Pinned location · "+distance)+"\n"+coordinates);
        } else if(portal.route!=null) guidance.setText(valid ? portal.route.guidance(fix,prefs.mph()) : "Waiting for GPS\n"+portal.route.destination.name);
        else guidance.setText("Connect online maps in Settings to explore");
    }
    private void pinDestination(Navigation.Place place) {
        cancelPinnedRoute(); portal.pin(place); renderLocation();
    }
    private void startPinnedNavigation() {
        Navigation.Place destination=portal.selection(); Location origin=portal.fix();
        if(destination==null || pinRouting) return;
        if(!prefs.data.getBoolean("online_maps",false)) { panels.onlineMaps(); return; }
        if(origin==null) { toast("Wait for a fresh GPS fix before starting navigation"); return; }
        cancelPinnedRoute(); pinRouting=true; pinnedRouteScope=tasks.scope(); renderLocation();
        pinnedRouteScope.run(()->Navigation.route(prefs,origin,destination),this::showRoute,error->{
            cancelPinnedRoute(); renderLocation(); toast("Route unavailable: "+error.getMessage());
        });
    }
    private void cancelPinnedRoute() {
        if(pinnedRouteScope!=null) { pinnedRouteScope.close(); pinnedRouteScope=null; }
        pinRouting=false;
    }
    private void clearMapTarget() {
        cancelPinnedRoute();
        if(portal.selection()!=null) portal.clearSelection(); else portal.route(null);
        renderLocation();
    }
    void showRoute(Navigation.Route route) {
        cancelPinnedRoute(); portal.route(route); renderLocation();
    }
    void clearNavigation() {
        cancelPinnedRoute(); portal.route(null); renderLocation();
    }
    private void gpsAction() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},42); return;
        }
        LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return; }
        if(!prefs.data.getBoolean("online_maps",false)) panels.onlineMaps(); else toast("GPS works while the dashboard is visible");
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==42) { portal.active(false); if(dashboardActive) portal.active(true); }
    }
    void visibilityChanged() {
        boolean show=resumed && panels.empty();
        if(show!=dashboardActive) {
            dashboardActive=show; portal.active(show);
            if(show) {
                visible=tasks.scope(); radio.start(); tasks.main.post(tick); maybeCheckUpdate();
            } else {
                if(pinRouting) { cancelPinnedRoute(); renderLocation(); }
                radio.stop(); tasks.main.removeCallbacks(tick); if(visible!=null) visible.close();
            }
        }
        panels.visibilityChanged(resumed);
    }
    private void maybeCheckUpdate() {
        String repository=prefs.get("repository",BuildConfig.GITHUB_REPOSITORY);
        if(updateAttempted || repository.isEmpty() || System.currentTimeMillis()-prefs.data.getLong("update_checked",0)<86400000L) return;
        updateAttempted=true;
        prefs.data.edit().putLong("update_checked",System.currentTimeMillis()).apply();
        visible.run(()->Downloads.latest(repository),value->{
            release=value; prefs.data.edit().putLong("update_checked",System.currentTimeMillis()).apply(); updateBadge();
        },error->{ /* A failed automatic check does not interrupt the cockpit. Manual check shows details. */ });
    }
    void updateBadge() { if(about!=null) about.setText(release!=null && release.newer ? "About  ●" : "About"); }
    void toast(String message) { if(message!=null && !isFinishing() && !isDestroyed()) Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    void recreateTheme() { recreate(); }
    @Override protected void onStart() { super.onStart(); portal.mapView.onStart(); if(life!=null) life.run(catalog::load,result->{ apps=result; defaults(); renderFavorites(); },error->{}); }
    @Override protected void onResume() { super.onResume(); resumed=true; visibilityChanged(); }
    @Override protected void onPause() { resumed=false; visibilityChanged(); super.onPause(); }
    @Override protected void onStop() { portal.mapView.onStop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); portal.mapView.onSaveInstanceState(state); }
    @Override public void onLowMemory() { super.onLowMemory(); portal.mapView.onLowMemory(); catalog.clearIcons(); }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if(level>=TRIM_MEMORY_RUNNING_LOW) { portal.mapView.onLowMemory(); catalog.clearIcons(); } }
    @Override public void onBackPressed() { if(!panels.empty()) panels.closeTop(); else panels.settings(); }
    @Override protected void onDestroy() { resumed=false; cancelPinnedRoute(); panels.close(); radio.stop(); portal.destroy(); life.close(); if(visible!=null) visible.close(); tasks.close(); super.onDestroy(); }
}
