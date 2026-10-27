package com.breakdash.launcher;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class Panels implements AutoCloseable {
    private final MainActivity main;
    private final Ui ui;
    private final List<Panel> stack=new ArrayList<>();
    private boolean resumed;
    private Panel settingsPanel;
    private TextView monitor,privileges,connectionStatus,auroraStatus;
    private final List<TextView> googleStatus=new ArrayList<>();
    private boolean monitorPending;
    private final Runnable sample=this::sample;
    Panels(MainActivity main) { this.main=main; ui=main.ui; }
    final class Panel {
        final Dialog dialog;
        final LinearLayout body;
        final Tasks.Scope scope=main.tasks.scope();
        boolean busy;
        Panel(String title,boolean scroll) {
            dialog=new Dialog(main); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root=ui.column(); root.setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(20)); root.setBackground(ui.surface(ui.bg,ui.border,24));
            LinearLayout header=ui.row(); TextView name=ui.text(title,26,ui.text); header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(ui.button("Close",dialog::dismiss),new LinearLayout.LayoutParams(ui.dp(90),ui.dp(64))); root.addView(header); ui.gap(root,12);
            body=ui.column();
            if(scroll) { ScrollView scroller=new ScrollView(main); scroller.setFillViewport(false); scroller.addView(body); root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1)); }
            else root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            dialog.setContentView(root); dialog.setOnDismissListener(ignored->{ scope.close(); stack.remove(this); if(settingsPanel==this) { settingsPanel=null; monitorPending=false; main.tasks.main.removeCallbacks(sample); } main.visibilityChanged(); });
            stack.add(this); dialog.show();
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(Math.min(main.getResources().getDisplayMetrics().widthPixels-ui.dp(48),ui.dp(1120)),main.getResources().getDisplayMetrics().heightPixels-ui.dp(48));
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params=window.getAttributes(); params.dimAmount=.8f; window.setAttributes(params);
            }
            main.visibilityChanged();
        }
        boolean top() { return !stack.isEmpty() && stack.get(stack.size()-1)==this && resumed && scope.active(); }
        void dismiss() { dialog.dismiss(); }
        void message(String value) { if(scope.active()) main.toast(value); }
    }
    boolean empty() { return stack.isEmpty(); }
    void closeTop() { if(!stack.isEmpty()) stack.get(stack.size()-1).dismiss(); }
    void visibilityChanged(boolean value) {
        resumed=value; main.tasks.main.removeCallbacks(sample);
        if(settingsPanel!=null && settingsPanel.top() && !monitorPending) main.tasks.main.post(sample);
    }
    @Override public void close() { for(Panel panel:new ArrayList<>(stack)) panel.dismiss(); main.tasks.main.removeCallbacks(sample); }
    private void copy(String value) {
        ((ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BreakDash",value)); main.toast("Copied");
    }
    private void external(String address) {
        try { main.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address))); }
        catch(Exception error) { main.toast("No browser is available"); }
    }
    private void explain(LinearLayout body,String value) { TextView text=ui.text(value,15,ui.muted); text.setPadding(0,ui.dp(6),0,ui.dp(12)); body.addView(text); }
    private void fullButton(LinearLayout body,String label,Runnable action) { body.addView(ui.button(label,action),new LinearLayout.LayoutParams(-1,ui.dp(72))); ui.gap(body,8); }

    void appPicker(String title,List<String> excluded,Consumer<AppCatalog.App> selected) {
        Panel panel=new Panel(title,false); EditText query=ui.input("Search installed apps"); panel.body.addView(query);
        TextView hint=ui.text(selected==null ? "Tap to open · hold to pin to the dock" : "Choose an app",14,ui.muted); panel.body.addView(hint); ui.gap(panel.body,12);
        GridView grid=new GridView(main); grid.setNumColumns(4); grid.setVerticalSpacing(ui.dp(8)); grid.setHorizontalSpacing(ui.dp(8)); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        panel.body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        List<AppCatalog.App> all=new ArrayList<>(),filtered=new ArrayList<>();
        BaseAdapter adapter=new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public Object getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position,View recycled,ViewGroup parent) {
                LinearLayout tile;
                if(recycled instanceof LinearLayout) tile=(LinearLayout)recycled;
                else {
                    tile=ui.row(); tile.setPadding(ui.dp(12),ui.dp(8),ui.dp(10),ui.dp(8)); tile.setGravity(Gravity.CENTER_VERTICAL); tile.setBackground(ui.surface(ui.card,ui.border,14));
                    tile.addView(new ImageView(main),new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
                    TextView label=ui.text("",16,ui.text); label.setPadding(ui.dp(12),0,0,0); label.setMaxLines(2); tile.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                    tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,ui.dp(88)));
                }
                AppCatalog.App app=filtered.get(position); ((ImageView)tile.getChildAt(0)).setImageDrawable(main.catalog.icon(app)); ((TextView)tile.getChildAt(1)).setText(app.label); tile.setContentDescription(app.label); return tile;
            }
        };
        grid.setAdapter(adapter);
        Runnable filter=()->{
            String needle=query.getText().toString().toLowerCase(Locale.getDefault()); filtered.clear();
            for(AppCatalog.App app:all) if((excluded==null || !excluded.contains(app.component)) && (app.label.toLowerCase(Locale.getDefault()).contains(needle) || app.packageName.contains(needle))) filtered.add(app);
            adapter.notifyDataSetChanged(); hint.setText(filtered.isEmpty() ? "No matching apps" : selected==null ? "Tap to open · hold to pin to the dock" : "Choose an app");
        };
        query.addTextChangedListener(watcher(filter));
        panel.scope.run(main.catalog::load,apps->{ main.apps=apps; all.addAll(apps); filter.run(); },error->panel.message("Cannot list installed apps"));
        grid.setOnItemClickListener((parent,view,position,id)->{ AppCatalog.App app=filtered.get(position); panel.dismiss(); if(selected==null) main.launch(app.component); else selected.accept(app); });
        grid.setOnItemLongClickListener((parent,view,position,id)->{ if(selected==null) main.pin(filtered.get(position)); return true; });
    }
    void dockAction(String component) {
        Panel panel=new Panel("Dock shortcut",true); AppCatalog.App app=main.find(component); explain(panel.body,app==null ? "App is unavailable" : app.label);
        fullButton(panel.body,"Remove from dock",()->{ List<String> values=main.prefs.favorites(); values.remove(component); main.prefs.favorites(values); main.renderFavorites(); panel.dismiss(); });
        fullButton(panel.body,"Replace app",()->{ panel.dismiss(); appPicker("Replace shortcut",main.prefs.favorites(),replacement->{ List<String> values=main.prefs.favorites(); int position=values.indexOf(component); if(position>=0) values.set(position,replacement.component); main.prefs.favorites(values); main.renderFavorites(); }); });
    }
    void onlineMaps() {
        Panel panel=new Panel("Connect online maps",true);
        explain(panel.body,"GPS speed and heading work without an internet map. Connecting downloads map tiles around your location; searches send the text and nearby coordinates to the geocoder; routing sends your start and destination to the route provider.");
        explain(panel.body,"Map (day): " + main.prefs.get("map_day","https://tiles.openfreemap.org/styles/positron") + "\nMap (night): " + main.prefs.get("map_night","https://tiles.openfreemap.org/styles/dark") + "\nSearch: " + main.prefs.get("geocoder","https://photon.komoot.io/api/") + "\nRoutes: " + main.prefs.get("router","https://router.project-osrm.org"));
        explain(panel.body,"Map styles can reference tile, font and image hosts. These services need internet and do not provide offline navigation. You can disconnect in Settings at any time.");
        fullButton(panel.body,"Connect to these providers",()->{ main.prefs.data.edit().putBoolean("online_maps",true).apply(); main.portal.reloadOnlineChoice(); panel.dismiss(); });
        fullButton(panel.body,"Keep GPS only",panel::dismiss);
    }
    void attribution() {
        Panel panel=new Panel("Map credits",true);
        explain(panel.body,"Map rendering: MapLibre Native\nMap tiles and styles: OpenFreeMap / OpenMapTiles\nMap data © OpenStreetMap contributors (ODbL)\nAddress search: Photon / Komoot\nDriving routes: OSRM");
        fullButton(panel.body,"OpenStreetMap copyright & license",()->external("https://www.openstreetmap.org/copyright"));
        fullButton(panel.body,"MapLibre attribution & feedback",()->external("https://maplibre.org/"));
    }
    void search() {
        if(!main.prefs.data.getBoolean("online_maps",false)) { onlineMaps(); return; }
        Location origin=main.portal.fix();
        if(origin==null) { main.toast("Wait for a fresh GPS fix before searching for a route"); return; }
        Panel panel=new Panel("Where to?",false); EditText query=ui.input("Street, place or address"); panel.body.addView(query);
        TextView state=ui.text("Type at least 3 characters",14,ui.muted); panel.body.addView(state); ui.gap(panel.body,10);
        ScrollView scroller=new ScrollView(main); LinearLayout results=ui.column(); scroller.addView(results); panel.body.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        final int[] generation={0}; final boolean[] searching={false}; final String[] queued={null};
        Runnable[] perform={null};
        perform[0]=()->{
            if(!panel.scope.active() || searching[0]) return;
            String term=query.getText().toString().trim(); if(term.length()<3) return;
            int revision=generation[0]; searching[0]=true; queued[0]=null; state.setText("Searching…");
            panel.scope.run(()->Navigation.search(main.prefs,term,origin),places->{
                searching[0]=false;
                if(revision==generation[0]) {
                    results.removeAllViews(); state.setText(places.isEmpty() ? "No places found" : "Select a destination");
                    for(Navigation.Place place:places) fullButton(results,place.name+"\n"+Navigation.distance(com.breakdash.launcher.core.RouteProgress.distance(origin.getLatitude(),origin.getLongitude(),place.lat,place.lon),main.prefs.mph()),()->{
                        if(panel.busy) return; panel.busy=true; generation[0]++; main.tasks.main.removeCallbacks(perform[0]); query.setEnabled(false); results.removeAllViews(); state.setText("Calculating driving route…");
                        panel.scope.run(()->Navigation.route(main.prefs,origin,place),route->{ main.portal.route(route); panel.dismiss(); },error->{ panel.busy=false; query.setEnabled(true); state.setText(error.getMessage()); });
                    });
                }
                if(queued[0]!=null) main.tasks.main.postDelayed(perform[0],700);
            },error->{ searching[0]=false; if(revision==generation[0]) state.setText(error.getMessage()); if(queued[0]!=null) main.tasks.main.postDelayed(perform[0],700); });
        };
        query.addTextChangedListener(watcher(()->{
            generation[0]++; results.removeAllViews(); main.tasks.main.removeCallbacks(perform[0]);
            if(query.getText().toString().trim().length()<3) { queued[0]=null; state.setText("Type at least 3 characters"); return; }
            queued[0]=query.getText().toString(); main.tasks.main.postDelayed(perform[0],700);
        }));
    }
    private TextWatcher watcher(Runnable changed) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { changed.run(); }
            @Override public void afterTextChanged(Editable value) {}
        };
    }

    void settings() {
        Panel panel=new Panel("BreakDash settings",true); settingsPanel=panel; main.system.resetMonitor();
        ui.heading(panel.body,"HEAD UNIT HEALTH"); monitor=ui.text("Sampling…",17,ui.text); panel.body.addView(monitor); explain(panel.body,"Readings refresh every 2 seconds while this screen is visible.");
        fullButton(panel.body,"Open Android system settings",()->{ try { main.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch(Exception error) { main.toast("System Settings unavailable"); } });
        fullButton(panel.body,"Choose default Home app",()->{ try { main.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); } catch(Exception error) { main.toast("Choose BreakDash when Android asks for a Home app"); } });
        ui.heading(panel.body,"GOOGLE SERVICES & LOCAL ADB");
        connectionStatus=ui.text("Checking local privileges…",15,ui.amber); panel.body.addView(connectionStatus);
        privileges=ui.text("ADB has not been authorized by BreakDash yet",15,ui.muted); panel.body.addView(privileges); ui.gap(panel.body,10);
        fullButton(panel.body,"Test / connect local ADB",()->{
            if(panel.busy) return; panel.busy=true; privileges.setText("Connecting to 127.0.0.1:5555 · allow the Android RSA prompt if shown");
            panel.scope.run(main.system::testAdb,result->{ panel.busy=false; privileges.setText(result); },error->{ panel.busy=false; privileges.setText("ADB: "+error.getMessage()); });
        });
        googleStatus.clear();
        String[] names={"Google Services Framework","Google Play Services","Google Play Store"};
        for(int i=0;i<SystemTools.GOOGLE.length;i++) {
            String pkg=SystemTools.GOOGLE[i]; LinearLayout packageCard=ui.column(); ui.heading(packageCard,names[i].toUpperCase(Locale.ROOT));
            TextView status=ui.text("",14,ui.muted); googleStatus.add(status); packageCard.addView(status);
            LinearLayout controls=ui.row(); ui.weighted(controls,ui.button("Enable",()->changeGoogle(panel,new String[]{pkg},true)),64); ui.weighted(controls,ui.button("Disable",()->changeGoogle(panel,new String[]{pkg},false)),64);
            ui.weighted(controls,ui.button("Copy command",()->copy(SystemTools.command(pkg,!"Enabled".equals(main.system.packageStatus(pkg))))),64); packageCard.addView(controls); panel.body.addView(packageCard);
        }
        LinearLayout all=ui.row(); ui.weighted(all,ui.button("Enable all",()->changeGoogle(panel,SystemTools.GOOGLE,true)),72); ui.weighted(all,ui.button("Disable all",()->changeGoogle(panel,SystemTools.GOOGLE,false)),72); panel.body.addView(all);
        explain(panel.body,"Local ADB needs TCP debugging on port 5555 and Android RSA authorization. BreakDash cannot turn the daemon on by itself. Root and system APIs are fallbacks when available.");
        ui.heading(panel.body,"AURORA STORE"); auroraStatus=ui.text(main.system.auroraStatus(),16,ui.text); panel.body.addView(auroraStatus);
        fullButton(panel.body,"Open Aurora Store",()->main.launchPackage("com.aurora.store"));
        fullButton(panel.body,"Download & install Aurora Store",()->download(panel,null,true));
        explain(panel.body,"Downloads use F-Droid with official Aurora GitLab as fallback. Android asks before installation. F-Droid and upstream builds may use different signing certificates.");
        ui.heading(panel.body,"MAP & CONNECTIONS");
        fullButton(panel.body,main.prefs.data.getBoolean("online_maps",false) ? "Disconnect online maps" : "Connect online maps",()->{
            if(main.prefs.data.getBoolean("online_maps",false)) { main.prefs.data.edit().putBoolean("online_maps",false).apply(); main.portal.route(null); main.portal.reloadOnlineChoice(); panel.dismiss(); }
            else onlineMaps();
        });
        fullButton(panel.body,"Connections & provider settings",this::connections);
        fullButton(panel.body,"System & radio diagnostics",()->diagnostics(false));
        visibilityChanged(resumed);
    }
    private void sample() {
        Panel panel=settingsPanel; if(panel==null || !panel.top() || monitorPending) return;
        monitorPending=true;
        panel.scope.run(()->new String[]{main.system.monitor(),"Root binary: " + (main.system.rootBinaryPresent() ? "detected (not tested)" : "not found") + " · TCP 5555: " + (main.system.portOpen() ? "open" : "closed")},value->{
            monitorPending=false;
            if(panel.top()) { monitor.setText(value[0]); connectionStatus.setText(value[1]); refreshStatuses(); main.tasks.main.postDelayed(sample,2000); }
        },error->{ monitorPending=false; if(panel.top()) { monitor.setText(error.getMessage()); main.tasks.main.postDelayed(sample,2000); } });
    }
    private void refreshStatuses() {
        for(int i=0;i<googleStatus.size();i++) googleStatus.get(i).setText(SystemTools.GOOGLE[i]+" · "+main.system.packageStatus(SystemTools.GOOGLE[i]));
        if(auroraStatus!=null) auroraStatus.setText(main.system.auroraStatus());
    }
    private void changeGoogle(Panel panel,String[] packages,boolean enable) {
        if(panel.busy) { panel.message("A system action is already in progress"); return; }
        panel.busy=true; privileges.setText((enable ? "Enabling" : "Disabling")+" Google packages…");
        panel.scope.run(()->{
            StringBuilder results=new StringBuilder();
            for(String pkg:packages) { Http.checkInterrupted(); try { results.append(main.system.toggle(pkg,enable)); } catch(Exception error) { results.append(pkg).append(": ").append(error.getMessage()); } results.append('\n'); }
            return results.toString();
        },value->{ panel.busy=false; privileges.setText(value); refreshStatuses(); },error->{ panel.busy=false; privileges.setText(error.getMessage()); refreshStatuses(); });
    }

    void about() {
        Panel panel=new Panel("About BreakDash",true);
        TextView name=ui.text("BreakDash",38,ui.text); name.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); panel.body.addView(name);
        explain(panel.body,"Version "+BuildConfig.VERSION_NAME+" · Android "+android.os.Build.VERSION.SDK_INT+" · 32-bit ARM\nBuilt for a lighter drive.");
        fullButton(panel.body,main.prefs.light() ? "Switch to obsidian night theme" : "Switch to daylight theme",()->{ main.prefs.light(!main.prefs.light()); main.recreateTheme(); });
        ui.heading(panel.body,"SOFTWARE UPDATES");
        TextView update=ui.text(main.release==null ? "Check the latest GitHub release" : main.release.tag+"\n"+main.release.notes,16,ui.text); panel.body.addView(update);
        Button install=ui.button("Download & install update",()->download(panel,main.release,false)); install.setEnabled(main.release!=null && main.release.newer && !main.release.url.isEmpty() && !BuildConfig.DEBUG); panel.body.addView(install);
        fullButton(panel.body,"Check for updates",()->{
            update.setText("Checking GitHub…");
            panel.scope.run(()->Downloads.latest(main.prefs.get("repository",BuildConfig.GITHUB_REPOSITORY)),value->{
                main.release=value; main.updateBadge(); main.prefs.data.edit().putLong("update_checked",System.currentTimeMillis()).apply();
                update.setText((value.newer ? "New release: " : "Latest release: ")+value.tag+"\n"+value.notes+(BuildConfig.DEBUG ? "\nInstall a signed release APK to use in-app updates." : ""));
                install.setEnabled(value.newer && !value.url.isEmpty() && !BuildConfig.DEBUG);
            },error->update.setText(error.getMessage()));
        });
        fullButton(panel.body,"Connections & repository",this::connections);
        ui.heading(panel.body,"DIAGNOSTICS & FEEDBACK");
        explain(panel.body,"Reports include firmware, MCU settings, memory and tuner state. Preview the cloud report before sending. Common device identifiers are redacted; uploads are encrypted to your configured diagnostics service.");
        EditText note=ui.input("Optional note about a problem or request"); note.setSingleLine(false); note.setMinLines(2); note.setMaxLines(5); note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)}); panel.body.addView(note);
        fullButton(panel.body,"Preview cloud report",()->diagnostics(true));
        TextView uploadState=ui.text("",15,ui.muted); panel.body.addView(uploadState);
        Button upload=ui.button("Encrypt & upload report",()->{}); upload.setOnClickListener(v->{
            upload.setEnabled(false); uploadState.setText("Collecting and encrypting…"); String radio=main.radio.diagnostics(),message=note.getText().toString();
            panel.scope.run(()->Diagnostics.upload(main.prefs,Diagnostics.collect(main,radio,true),message),value->{ uploadState.setText(value); upload.setEnabled(true); },error->{ uploadState.setText(error.getMessage()); upload.setEnabled(true); });
        }); panel.body.addView(upload,new LinearLayout.LayoutParams(-1,ui.dp(72)));
        fullButton(panel.body,"Full system info",()->diagnostics(false));
    }
    private void download(Panel parent,Downloads.Release release,boolean aurora) {
        if(!aurora && (release==null || release.url.isEmpty() || !release.newer || BuildConfig.DEBUG)) { parent.message("No compatible release update is available"); return; }
        Panel panel=new Panel(aurora ? "Install Aurora Store" : "Update BreakDash",true);
        TextView state=ui.text("Preparing download…",18,ui.text); panel.body.addView(state);
        ProgressBar progress=new ProgressBar(main,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setMax(100); panel.body.addView(progress,new LinearLayout.LayoutParams(-1,ui.dp(24)));
        Button install=ui.button("Install APK",()->{}); install.setEnabled(false); panel.body.addView(install);
        explain(panel.body,"Android will ask you to allow installation from BreakDash. If prompted, enable that permission, return here, and tap Install APK again. Closing this dialog cancels an active download.");
        panel.scope.run(()->{
            java.util.function.IntConsumer changed=percent->panel.scope.post(()->{ progress.setIndeterminate(percent<0); if(percent>=0) progress.setProgress(percent); state.setText(percent<0 ? "Downloading…" : "Downloading · "+percent+"%"); });
            return aurora ? Downloads.downloadAurora(main,changed) : Downloads.download(main,release.url,main.getPackageName(),release.digest,changed);
        },file->{
            state.setText("Download checked · ready to install"); progress.setIndeterminate(false); progress.setProgress(100); install.setEnabled(true);
            install.setOnClickListener(v->{ try { Downloads.install(main,file); } catch(Exception error) { panel.message("Installer: "+error.getMessage()); } });
            try { Downloads.install(main,file); } catch(Exception error) { panel.message("Installer: "+error.getMessage()); }
        },error->{ state.setText(error.getMessage()); progress.setIndeterminate(false); });
    }
    void diagnostics(boolean cloud) {
        Panel panel=new Panel(cloud ? "Cloud report preview" : "System & radio inspector",false);
        TextView terminal=ui.text("Collecting…",13,ui.accent); terminal.setTypeface(Typeface.MONOSPACE); terminal.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(main); scroll.addView(terminal); panel.body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button copy=ui.button("Copy report",()->copy(terminal.getText().toString())); copy.setEnabled(false); panel.body.addView(copy);
        String radio=main.radio.diagnostics();
        panel.scope.run(()->Diagnostics.collect(main,radio,cloud),report->{ terminal.setText(report); copy.setEnabled(true); },error->terminal.setText(error.getMessage()));
    }
    private void connections() {
        Panel panel=new Panel("Connections",true);
        explain(panel.body,"Use your GitHub owner/repository for releases. Cloud reporting also needs your deployed diagnostics endpoint and its RSA public key. Private keys and GitHub tokens belong on the backend.");
        Map<String,EditText> fields=new LinkedHashMap<>();
        String[][] definitions={
                {"repository","GitHub owner/repository",BuildConfig.GITHUB_REPOSITORY},
                {"diagnostics_url","Diagnostics HTTPS endpoint",BuildConfig.DIAGNOSTICS_URL},
                {"diagnostics_key","Diagnostics RSA public key (PEM)",BuildConfig.DIAGNOSTICS_PUBLIC_KEY},
                {"map_day","Day map style URL","https://tiles.openfreemap.org/styles/positron"},
                {"map_night","Night map style URL","https://tiles.openfreemap.org/styles/dark"},
                {"geocoder","Photon search endpoint","https://photon.komoot.io/api/"},
                {"router","OSRM server base URL","https://router.project-osrm.org"}};
        for(String[] definition:definitions) {
            ui.heading(panel.body,definition[1].toUpperCase(Locale.ROOT)); EditText field=ui.input(definition[1]); field.setText(main.prefs.get(definition[0],definition[2]));
            if(definition[0].equals("diagnostics_key")) { field.setSingleLine(false); field.setMinLines(3); field.setMaxLines(6); }
            panel.body.addView(field); fields.put(definition[0],field);
        }
        fullButton(panel.body,"Save connections",()->{
            try {
                String repository=fields.get("repository").getText().toString().trim();
                if(!repository.isEmpty() && !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Use owner/repository for GitHub");
                boolean mapsChanged=false;
                for(String[] definition:definitions) {
                    String key=definition[0],value=fields.get(key).getText().toString().trim();
                    if(!key.equals("repository") && !key.equals("diagnostics_key") && !value.isEmpty()) {
                        URI uri=new URI(value); if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Use an HTTPS URL for " + definition[1]);
                    }
                    if((key.startsWith("map_") || key.equals("geocoder") || key.equals("router")) && !value.equals(main.prefs.get(key,definition[2]))) mapsChanged=true;
                }
                for(String[] definition:definitions) main.prefs.put(definition[0],fields.get(definition[0]).getText().toString().trim());
                main.prefs.data.edit().putLong("update_checked",0).apply(); main.release=null; main.updateBadge();
                if(mapsChanged) { main.prefs.data.edit().putBoolean("online_maps",false).apply(); main.portal.route(null); main.portal.reloadOnlineChoice(); }
                panel.message("Connections saved" + (mapsChanged ? " · reconnect maps to use the new providers" : "")); panel.dismiss();
            } catch(Exception error) { panel.message(error.getMessage()); }
        });
    }
}
