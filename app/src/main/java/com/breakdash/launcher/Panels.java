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
    private TextView monitor;
    private String packageActionStatus="",packageActionDetails="";
    private boolean monitorPending;
    private final Runnable sample=this::sample;
    Panels(MainActivity main) { this.main=main; ui=main.ui; }
    final class Panel {
        final Dialog dialog;
        final LinearLayout body;
        final Tasks.Scope scope=main.tasks.scope();
        boolean busy;
        Runnable revealed=()->{};
        Panel(String title,boolean scroll) {
            this(title,scroll,false);
        }
        Panel(String title,boolean scroll,boolean fullScreen) {
            dialog=new Dialog(main); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root=ui.column(); root.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12)); root.setBackground(ui.surface(ui.bg,ui.border,fullScreen ? 0 : 24));
            LinearLayout header=ui.row();
            if(!stack.isEmpty()) { Button back=ui.button("‹ Back",dialog::dismiss); back.setTextSize(18); header.addView(back,new LinearLayout.LayoutParams(ui.dp(100),ui.dp(72))); }
            TextView name=ui.text(title,26,ui.text); name.setPadding(ui.dp(12),0,ui.dp(8),0); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(ui.button("Close",Panels.this::close),new LinearLayout.LayoutParams(ui.dp(90),ui.dp(72))); root.addView(header); ui.gap(root,12);
            body=ui.column();
            if(scroll) { ScrollView scroller=new ScrollView(main); scroller.setFillViewport(false); scroller.addView(body); root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1)); }
            else root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            dialog.setContentView(root); dialog.setOnDismissListener(ignored->{ scope.close(); stack.remove(this); if(settingsPanel==this) { settingsPanel=null; monitorPending=false; main.tasks.main.removeCallbacks(sample); } main.visibilityChanged(); });
            stack.add(this); dialog.show();
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(fullScreen ? -1 : Math.min(main.getResources().getDisplayMetrics().widthPixels-ui.dp(48),ui.dp(1120)),fullScreen ? -1 : main.getResources().getDisplayMetrics().heightPixels-ui.dp(48));
                window.getDecorView().setSystemUiVisibility(main.getWindow().getDecorView().getSystemUiVisibility());
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
        if(!stack.isEmpty() && stack.get(stack.size()-1).top()) stack.get(stack.size()-1).revealed.run();
        if(settingsPanel!=null && settingsPanel.top() && !monitorPending) main.tasks.main.post(sample);
    }
    @Override public void close() { for(Panel panel:new ArrayList<>(stack)) panel.dismiss(); main.tasks.main.removeCallbacks(sample); }
    private void copy(String value) {
        ((ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BreakDash",value)); main.toast("Copied");
    }
    private void external(String address) {
        try { main.openExternal(new Intent(Intent.ACTION_VIEW,Uri.parse(address))); }
        catch(Exception error) { main.toast("No browser is available"); }
    }
    private void explain(LinearLayout body,String value) { TextView text=ui.text(value,15,ui.muted); text.setPadding(0,ui.dp(6),0,ui.dp(12)); body.addView(text); }
    private void fullButton(LinearLayout body,String label,Runnable action) { Button button=ui.button(label,action); button.setTextSize(18); body.addView(button,new LinearLayout.LayoutParams(-1,ui.dp(80))); ui.gap(body,8); }
    private TextView compactLink(LinearLayout body,String label,Runnable action) {
        TextView link=ui.text(label,16,ui.muted); link.setMinHeight(ui.dp(60)); link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(ui.dp(12),0,ui.dp(12),0); link.setSingleLine(true); link.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        link.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3342D6CB),null,null));
        link.setFocusable(true); link.setOnClickListener(v->action.run()); body.addView(link,new LinearLayout.LayoutParams(-1,ui.dp(60))); return link;
    }

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
        Panel panel=new Panel("Connect online maps",true,true);
        explain(panel.body,"Internet required. Maps load around your location. Search shares text and nearby coordinates; routes share your start and destination with the configured providers.");
        fullButton(panel.body,"Connect maps",()->{ main.prefs.data.edit().putBoolean("online_maps",true).apply(); main.portal.reloadOnlineChoice(); panel.dismiss(); });
        fullButton(panel.body,"Keep GPS only",panel::dismiss);
        fullButton(panel.body,"Provider details",()->{
            Panel details=new Panel("Map providers",true,true);
            explain(details.body,"Map (day): "+main.prefs.get("map_day","https://tiles.openfreemap.org/styles/positron")+"\nMap (night): "+main.prefs.get("map_night","https://tiles.openfreemap.org/styles/dark")+"\nSearch: "+main.prefs.get("geocoder","https://photon.komoot.io/api/")+"\nRoutes: "+main.prefs.get("router","https://router.project-osrm.org"));
            explain(details.body,"Styles can reference additional tile, font and image hosts. These services do not provide offline navigation. Disconnect at any time in Settings → Maps.");
        });
    }
    void attribution() {
        Panel panel=new Panel("Credits",true,true);
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
                        panel.scope.run(()->Navigation.route(main.prefs,origin,place),route->{ main.showRoute(route); panel.dismiss(); },error->{ panel.busy=false; query.setEnabled(true); state.setText(error.getMessage()); });
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
        Panel panel=new Panel("Settings",false,true);
        LinearLayout layout=ui.row(); layout.setGravity(Gravity.TOP); panel.body.addView(layout,new LinearLayout.LayoutParams(-1,-1));
        ScrollView railScroll=new ScrollView(main); LinearLayout rail=ui.column(); railScroll.addView(rail);
        int railWidth=main.getResources().getConfiguration().screenWidthDp<700 ? 152 : 208;
        layout.addView(railScroll,new LinearLayout.LayoutParams(ui.dp(railWidth),-1));
        ScrollView pageScroll=new ScrollView(main); pageScroll.setFillViewport(true); LinearLayout page=ui.column(); page.setPadding(ui.dp(20),0,0,0); pageScroll.addView(page);
        layout.addView(pageScroll,new LinearLayout.LayoutParams(0,-1,1));
        String[] tabs={"Appearance","Maps","Apps","Device"}; Button[] buttons=new Button[tabs.length]; final int[] selected={0};
        Runnable render=()->{
            if(panel.busy) return;
            page.removeAllViews(); pageScroll.scrollTo(0,0);
            for(int i=0;i<buttons.length;i++) {
                buttons[i].setTextColor(i==selected[0] ? ui.accent : ui.text); buttons[i].setSelected(i==selected[0]);
                buttons[i].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3342D6CB),ui.surface(ui.card,i==selected[0] ? ui.accent : ui.border,16),null));
            }
            page.addView(ui.text(tabs[selected[0]],28,ui.text)); ui.gap(page,20);
            switch(selected[0]) {
                case 0:
                    ui.toggle(page,"Night mode",!main.prefs.light(),main.prefs.light() ? "Off · Day theme" : "On · Dark theme",night->setTheme(!night));
                    break;
                case 1:
                    boolean connected=main.prefs.data.getBoolean("online_maps",false);
                    ui.toggle(page,"Online maps",connected,connected ? "On · Connected" : "Off · GPS only",enabled->{
                        if(!enabled) {
                            main.prefs.data.edit().putBoolean("online_maps",false).apply(); main.clearNavigation(); main.portal.reloadOnlineChoice(); panel.revealed.run();
                        } else { panel.revealed.run(); onlineMaps(); }
                    });
                    compactLink(page,"Map providers  ›",()->connections("maps")); break;
                case 2:
                    appsSettings(panel,page); break;
                default:
                    explain(page,NavigationDiagnostics.defaultHome(main).startsWith(main.getPackageName()+"/") ? "BreakDash is the default Home" : "Choose BreakDash as the default Home");
                    fullButton(page,"Default launcher",()->openSettings(Settings.ACTION_HOME_SETTINGS));
                    fullButton(page,"Android settings",()->openSettings(Settings.ACTION_SETTINGS));
                    fullButton(page,"Device health",this::health);
                    fullButton(page,"Advanced tools",this::advanced); break;
            }
        };
        for(int i=0;i<tabs.length;i++) {
            final int index=i; buttons[i]=ui.button(tabs[i],()->{ if(!panel.busy) { selected[0]=index; render.run(); } }); buttons[i].setTextSize(20);
            rail.addView(buttons[i],new LinearLayout.LayoutParams(-1,ui.dp(80))); ui.gap(rail,10);
        }
        panel.revealed=render; render.run();
    }
    private void setTheme(boolean light) { if(main.prefs.light()!=light) { main.prefs.light(light); main.recreateTheme(); } }
    private void openSettings(String action) {
        try { main.openExternal(new Intent(action)); } catch(Exception error) { main.toast("Android settings unavailable"); }
    }
    private void advanced() {
        Panel panel=new Panel("Advanced tools",true,true);
        fullButton(panel.body,"Local ADB",this::adb);
        fullButton(panel.body,"System & radio inspector",()->diagnostics(false));
    }
    private void health() {
        Panel panel=new Panel("Device health",true,true); settingsPanel=panel; main.system.resetMonitor();
        monitor=ui.text("Sampling…",22,ui.text); panel.body.addView(monitor);
        explain(panel.body,"Live while this page is open"); visibilityChanged(resumed);
    }
    private void adb() {
        Panel panel=new Panel("Local ADB",true,true);
        TextView state=ui.text("Not connected",20,ui.text); panel.body.addView(state);
        fullButton(panel.body,"Connect",()->{
            if(panel.busy) return; panel.busy=true; state.setText("Connecting · allow Android's authorization prompt");
            panel.scope.run(main.system::testAdb,result->{ panel.busy=false; state.setText(result); },error->{ panel.busy=false; state.setText("ADB: "+error.getMessage()); });
        });
        fullButton(panel.body,"Details",()->{
            Panel details=new Panel("ADB details",true,true);
            explain(details.body,"Requires TCP debugging on 127.0.0.1:5555 and Android RSA authorization. BreakDash cannot start the daemon itself. Google controls also try root and system APIs when available.");
            details.scope.run(()->"Root binary: "+(main.system.rootBinaryPresent() ? "detected (not tested)" : "not found")+"\nTCP 5555: "+(main.system.portOpen() ? "open" : "closed"),value->explain(details.body,value),error->details.message(error.getMessage()));
        });
    }
    private void appsSettings(Panel panel,LinearLayout page) {
        boolean installed=main.system.packageInstalled("com.aurora.store");
        Ui.ToggleRow aurora=ui.toggle(page,"Aurora Store",installed,installed ? "Installed" : "Not installed",null);
        Button manage=ui.button(installed ? "Open" : "Install",()->{ if(installed) main.launchPackage("com.aurora.store"); else aurora(); });
        aurora.addView(manage,new LinearLayout.LayoutParams(ui.dp(108),ui.dp(64)));
        compactLink(page,"Aurora details  ›",this::aurora);
        ui.heading(page,"GOOGLE SERVICES");
        List<Ui.ToggleRow> rows=new ArrayList<>();
        TextView status=ui.text("",16,ui.muted);
        Runnable refresh=()->{
            for(int i=0;i<rows.size();i++) {
                String pkg=SystemTools.GOOGLE[i],state=main.system.packageStatus(pkg);
                rows.get(i).state("Enabled".equals(state),"Enabled".equals(state) ? "On" : "Disabled".equals(state) ? "Off" : state,!panel.busy);
            }
            status.setText(packageActionStatus); status.setVisibility(packageActionStatus.isEmpty() ? View.GONE : View.VISIBLE);
        };
        String[] names={"Services Framework","Play Services","Play Store"};
        for(int i=0;i<SystemTools.GOOGLE.length;i++) {
            String pkg=SystemTools.GOOGLE[i];
            rows.add(ui.toggle(page,names[i],false,"",enabled->changeGoogle(panel,new String[]{pkg},enabled,refresh)));
        }
        page.addView(status); compactLink(page,"Google service tools · Local ADB  ›",this::googleTools); refresh.run();
    }
    private void googleTools() {
        Panel panel=new Panel("Google service tools",true,true);
        Runnable[] refresh={null};
        Ui.ToggleRow all=ui.toggle(panel.body,"All Google services",false,"",enabled->changeGoogle(panel,SystemTools.GOOGLE,enabled,refresh[0]));
        TextView status=ui.text("",16,ui.muted); panel.body.addView(status);
        refresh[0]=()->{
            int enabled=0; for(String pkg:SystemTools.GOOGLE) if(main.system.packageEnabled(pkg)) enabled++;
            all.state(enabled==SystemTools.GOOGLE.length,enabled==3 ? "On · All enabled" : enabled==0 ? "Off · All disabled or missing" : "Mixed · "+enabled+" of 3 enabled",!panel.busy);
            status.setText(packageActionStatus); status.setVisibility(packageActionStatus.isEmpty() ? View.GONE : View.VISIBLE);
        };
        fullButton(panel.body,"Local ADB",this::adb);
        compactLink(panel.body,"Last action details  ›",()->{
            Panel details=new Panel("Last service action",true,true); explain(details.body,packageActionDetails.isEmpty() ? "No service changes yet" : packageActionDetails);
        });
        String[] names={"Services Framework","Play Services","Play Store"};
        for(int i=0;i<SystemTools.GOOGLE.length;i++) {
            String pkg=SystemTools.GOOGLE[i];
            compactLink(panel.body,names[i]+" commands  ›",()->{
                Panel details=new Panel("Service details",true,true); explain(details.body,pkg);
                fullButton(details.body,"Copy enable command",()->copy(SystemTools.command(pkg,true)));
                fullButton(details.body,"Copy disable command",()->copy(SystemTools.command(pkg,false)));
            });
        }
        panel.revealed=refresh[0]; refresh[0].run();
    }
    private void aurora() {
        Panel panel=new Panel("Aurora Store",true,true);
        panel.revealed=()->{
            panel.body.removeAllViews(); boolean installed=main.system.packageInstalled("com.aurora.store");
            ui.toggle(panel.body,"Aurora Store",installed,installed ? main.system.auroraStatus() : "Not installed",null);
            if(installed) fullButton(panel.body,"Open Aurora Store",()->main.launchPackage("com.aurora.store"));
            else fullButton(panel.body,"Install Aurora Store",()->download(panel,null,true));
            compactLink(panel.body,"Installation options  ›",()->{
                Panel details=new Panel("Aurora downloads",true,true);
                explain(details.body,"Downloads use F-Droid, with official Aurora GitLab as fallback. Android asks before installation. These sources may use different signing certificates.");
                fullButton(details.body,"Download latest APK",()->download(details,null,true));
            });
        }; panel.revealed.run();
    }
    private void sample() {
        Panel panel=settingsPanel; if(panel==null || !panel.top() || monitorPending) return;
        monitorPending=true;
        panel.scope.run(main.system::monitor,value->{
            monitorPending=false;
            if(panel.top()) { monitor.setText(value); main.tasks.main.postDelayed(sample,2000); }
        },error->{ monitorPending=false; if(panel.top()) { monitor.setText(error.getMessage()); main.tasks.main.postDelayed(sample,2000); } });
    }
    private void changeGoogle(Panel panel,String[] packages,boolean enable,Runnable refresh) {
        if(panel.busy) { panel.message("A system action is already in progress"); return; }
        panel.busy=true; packageActionStatus=(enable ? "Enabling" : "Disabling")+"…"; refresh.run();
        final boolean[] failed={false};
        panel.scope.run(()->{
            StringBuilder results=new StringBuilder();
            for(String pkg:packages) { Http.checkInterrupted(); try { results.append(main.system.toggle(pkg,enable)); } catch(Exception error) { failed[0]=true; results.append(pkg).append(": ").append(error.getMessage()); } results.append('\n'); }
            return results.toString();
        },value->{
            panel.busy=false; packageActionDetails=value;
            boolean confirmed=!failed[0]; for(String pkg:packages) if(main.system.packageEnabled(pkg)!=enable) confirmed=false;
            packageActionStatus=confirmed ? "Services updated" : "Couldn't change every service · open tools"; refresh.run();
        },error->{ panel.busy=false; packageActionDetails=error.getMessage(); packageActionStatus="Couldn't change services · open tools"; refresh.run(); });
    }

    void about() {
        Panel panel=new Panel("About",true,true);
        TextView name=ui.text("BreakDash",38,ui.text); name.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); panel.body.addView(name);
        explain(panel.body,"Version "+BuildConfig.VERSION_NAME+" · "+(BuildConfig.DEBUG ? "Development build" : "Release")+"\nBuilt for a lighter drive.");
        LinearLayout actions=ui.row();
        ui.weighted(actions,ui.actionTile("Report a problem","info",20,this::report),144);
        ui.weighted(actions,ui.actionTile("Device info","settings",20,this::deviceInfo),144);
        ui.weighted(actions,ui.actionTile("Credits","info",20,this::attribution),144); panel.body.addView(actions);
    }
    private void deviceInfo() {
        Panel panel=new Panel("Device info",true,true);
        explain(panel.body,android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid API "+android.os.Build.VERSION.SDK_INT+" · "+(android.os.Process.is64Bit() ? "64-bit ARM" : "32-bit ARM"));
        fullButton(panel.body,"Full system & radio report",()->diagnostics(false));
    }
    void updates() {
        Panel panel=new Panel("Updates",true,true);
        explain(panel.body,"Installed: "+BuildConfig.VERSION_NAME+(BuildConfig.DEBUG ? " · Development build" : ""));
        TextView available=ui.text("",18,ui.muted); panel.body.addView(available); ui.gap(panel.body,16);
        TextView status=ui.text("",26,ui.text); panel.body.addView(status);
        TextView reason=ui.text("",17,ui.muted); reason.setPadding(0,ui.dp(8),0,ui.dp(20)); panel.body.addView(reason);
        LinearLayout downloadAction=ui.column();
        Button install=ui.button(BuildConfig.DEBUG ? "Get signed release" : "Download & install",()->{
            if(panel.busy || main.release==null) return;
            if(BuildConfig.DEBUG) { if(main.release!=null && !main.release.url.isEmpty()) external(main.release.url); }
            else if(main.release.newer && !main.release.url.isEmpty()) download(panel,main.release,false);
        }); install.setTextSize(20); downloadAction.addView(install,new LinearLayout.LayoutParams(-1,ui.dp(80))); ui.gap(downloadAction,12); panel.body.addView(downloadAction);
        LinearLayout secondary=ui.row(); Button check=ui.button("Check for updates",()->{}),notes=ui.button("What's new",()->{
            Downloads.Release release=main.release; if(release==null) return;
            Panel details=new Panel("What's new · "+release.tag,true,true); explain(details.body,release.notes.isEmpty() ? "No release notes" : release.notes);
        }); ui.weighted(secondary,check,80); ui.weighted(secondary,notes,80); panel.body.addView(secondary);
        TextView source=compactLink(panel.body,"",()->{ if(!panel.busy) connections("updates"); });
        final String[] checkError={null};
        final String[] renderedRepository={main.prefs.get("repository",BuildConfig.GITHUB_REPOSITORY)};
        Runnable render=()->{
            String repository=main.prefs.get("repository",BuildConfig.GITHUB_REPOSITORY);
            if(!repository.equals(renderedRepository[0])) { checkError[0]=null; renderedRepository[0]=repository; }
            Downloads.Release release=main.release; available.setText(release==null ? "" : "Latest release: "+release.tag);
            available.setVisibility(release==null ? View.GONE : View.VISIBLE);
            boolean ready=!panel.busy && checkError[0]==null && release!=null && !release.url.isEmpty() && (BuildConfig.DEBUG || release.newer);
            downloadAction.setVisibility(ready ? View.VISIBLE : View.GONE); install.setEnabled(ready);
            notes.setVisibility(release!=null && !panel.busy ? View.VISIBLE : View.GONE);
            check.setEnabled(!panel.busy); check.setText(release==null ? "Check for updates" : "Check again");
            source.setText("Source · "+repository+"  ›"); source.setEnabled(!panel.busy);
            if(panel.busy) { status.setText("Checking…"); reason.setText(""); return; }
            if(checkError[0]!=null) { status.setText("Couldn't check for updates"); reason.setText(checkError[0]); return; }
            if(BuildConfig.DEBUG) {
                status.setText("Development build"); reason.setText(release==null ? "Check for a signed release to use regular updates." : release.url.isEmpty() ? "No APK is available for this device." : "Installs as BreakDash alongside BreakDash Dev. Open BreakDash for future updates.");
            } else if(release==null) { status.setText("Check for updates"); reason.setText("Find the latest release on GitHub."); }
            else if(!release.newer) { status.setText("Up to date"); reason.setText("You're using the latest version available for you."); }
            else if(release.url.isEmpty()) { status.setText("Update unavailable"); reason.setText("No APK is available for this device."); }
            else { status.setText("Update available"); reason.setText("Ready to download "+release.tag); }
        };
        check.setOnClickListener(v->{
            if(panel.busy) return; panel.busy=true; checkError[0]=null; render.run();
            panel.scope.run(()->Downloads.latest(main.prefs.get("repository",BuildConfig.GITHUB_REPOSITORY)),value->{
                panel.busy=false; check.setEnabled(true); main.release=value; main.updateBadge(); main.prefs.data.edit().putLong("update_checked",System.currentTimeMillis()).apply(); render.run();
            },error->{ panel.busy=false; checkError[0]=error.getMessage()==null ? "Try again when connected to the internet." : error.getMessage(); render.run(); });
        });
        panel.revealed=()->{ if(!panel.busy) render.run(); }; render.run(); if(main.release==null) check.performClick();
    }
    private void report() {
        Panel panel=new Panel("Report a problem",true,true);
        explain(panel.body,"Add a note, then review your report before sending.");
        EditText note=ui.input("Optional note about a problem or request"); note.setSingleLine(false); note.setMinLines(2); note.setMaxLines(5); note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)}); panel.body.addView(note);
        fullButton(panel.body,"Preview report",()->reportPreview(note.getText().toString()));
        fullButton(panel.body,"Own server setup",()->connections("reports"));
    }
    private void reportPreview(String note) {
        Panel panel=new Panel("Review report",false,true);
        explain(panel.body,"Review firmware, memory and radio details. Common identifiers are masked; check for personal data. Send feedback encrypts to your fixed ntfy inbox. Ask to check it within 3 hours; no link copying needed.");
        TextView state=ui.text("Collecting…",17,ui.muted); panel.body.addView(state);
        TextView terminal=ui.text("",13,ui.text); terminal.setTypeface(Typeface.MONOSPACE); terminal.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(main); scroll.addView(terminal); panel.body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button feedback=ui.button("Send feedback",()->{}); feedback.setTextColor(ui.accent); feedback.setEnabled(false); panel.body.addView(feedback,new LinearLayout.LayoutParams(-1,ui.dp(80)));
        LinearLayout actions=ui.row(); Button temporary=ui.button("Temporary link",()->{}),send=ui.button("My server",()->{});
        temporary.setEnabled(false); send.setEnabled(false);
        ui.weighted(actions,temporary,64); ui.weighted(actions,send,64); panel.body.addView(actions);
        final String[] report={null},shareLink={null}; final boolean[] sent={false};
        Button share=ui.button("Share link",()->temporaryShareLink(shareLink[0])); share.setVisibility(View.GONE); panel.body.addView(share,new LinearLayout.LayoutParams(-1,ui.dp(80)));
        Runnable ready=()->{
            boolean configured=!main.prefs.get("diagnostics_url",BuildConfig.DIAGNOSTICS_URL).isEmpty() && !main.prefs.get("diagnostics_key",BuildConfig.DIAGNOSTICS_PUBLIC_KEY).isEmpty();
            send.setEnabled(report[0]!=null && configured && !panel.busy && !sent[0]);
            feedback.setEnabled(report[0]!=null && Diagnostics.feedbackConfigured() && !panel.busy && !sent[0]);
            temporary.setEnabled(report[0]!=null && !panel.busy && !sent[0]);
            if(!panel.busy && !sent[0] && report[0]!=null) state.setText(Diagnostics.feedbackConfigured() ? "Ready to send feedback" : "Inbox not configured in this build · temporary sharing is available");
        };
        feedback.setOnClickListener(v->{
            if(report[0]==null || panel.busy || sent[0]) return; panel.busy=true; ready.run(); state.setText("Encrypting & sending feedback…");
            panel.scope.run(()->Diagnostics.uploadFeedback(report[0],note),value->{ panel.busy=false; sent[0]=true; state.setText(value); },error->{ panel.busy=false; ready.run(); state.setText(error.getMessage()); });
        });
        temporary.setOnClickListener(v->{
            if(report[0]==null || panel.busy || sent[0]) return; panel.busy=true; ready.run(); state.setText("Encrypting & uploading to Uguu…");
            panel.scope.run(()->Diagnostics.uploadTemporary(report[0],note),value->{
                panel.busy=false; sent[0]=true; shareLink[0]=value; share.setVisibility(View.VISIBLE); state.setText("Uploaded · copy your share link before closing"); temporaryShareLink(value);
            },error->{ panel.busy=false; ready.run(); state.setText(error.getMessage()); });
        });
        send.setOnClickListener(v->{
            if(report[0]==null || panel.busy || sent[0]) return; panel.busy=true; ready.run(); state.setText("Encrypting & sending to your server…");
            panel.scope.run(()->Diagnostics.upload(main.prefs,report[0],note),value->{ panel.busy=false; sent[0]=true; state.setText(value); },error->{ panel.busy=false; ready.run(); state.setText(error.getMessage()); });
        });
        panel.revealed=ready;
        String radio=main.radio.diagnostics(); panel.scope.run(()->Diagnostics.collect(main,radio,true),value->{ report[0]=value; terminal.setText("Note: "+note+"\n\n"+value); ready.run(); },error->state.setText(error.getMessage()));
    }
    private void temporaryShareLink(String link) {
        if(link==null) return;
        Panel panel=new Panel("Report uploaded",true,true);
        explain(panel.body,"Copy the full link and paste it in your support chat within 3 hours. It includes the decryption key: anyone with this link can read the report. Copy it before closing.");
        TextView address=ui.text(link,16,ui.muted); address.setTextIsSelectable(true); panel.body.addView(address); ui.gap(panel.body,20);
        fullButton(panel.body,"Copy share link",()->{
            ClipData clip=ClipData.newPlainText("BreakDash report link",link);
            android.os.PersistableBundle extras=new android.os.PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE",true); clip.getDescription().setExtras(extras);
            ((ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip); main.toast("Share link copied");
        });
        fullButton(panel.body,"Share…",()->{
            Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { main.openExternal(Intent.createChooser(share,"Share BreakDash report")); } catch(Exception error) { panel.message("No sharing app is available · use Copy share link"); }
        });
    }
    private void download(Panel parent,Downloads.Release release,boolean aurora) {
        if(!aurora && (release==null || release.url.isEmpty() || !release.newer || BuildConfig.DEBUG)) { parent.message("No compatible release update is available"); return; }
        Panel panel=new Panel(aurora ? "Install Aurora Store" : "Update BreakDash",true,true);
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
        Panel panel=new Panel(cloud ? "Cloud report preview" : "System & radio inspector",false,true);
        TextView terminal=ui.text("Collecting…",13,ui.accent); terminal.setTypeface(Typeface.MONOSPACE); terminal.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(main); scroll.addView(terminal); panel.body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button copy=ui.button("Copy report",()->copy(terminal.getText().toString())); copy.setEnabled(false); panel.body.addView(copy);
        String radio=main.radio.diagnostics();
        panel.scope.run(()->Diagnostics.collect(main,radio,cloud),report->{ terminal.setText(report); copy.setEnabled(true); },error->terminal.setText(error.getMessage()));
    }
    private void connections(String section) {
        Panel panel=new Panel(section.equals("maps") ? "Map providers" : section.equals("updates") ? "Update source" : "Report setup",true,true);
        if(section.equals("reports")) explain(panel.body,"Your HTTPS receiver and RSA public key. Keep private keys on your server.");
        Map<String,EditText> fields=new LinkedHashMap<>();
        String[][] definitions=section.equals("updates") ? new String[][]{
                {"repository","GitHub owner/repository",BuildConfig.GITHUB_REPOSITORY}} : section.equals("reports") ? new String[][]{
                {"diagnostics_url","HTTPS endpoint",BuildConfig.DIAGNOSTICS_URL},
                {"diagnostics_key","RSA public key (PEM)",BuildConfig.DIAGNOSTICS_PUBLIC_KEY}} : new String[][]{
                {"map_day","Day map style URL","https://tiles.openfreemap.org/styles/positron"},
                {"map_night","Night map style URL","https://tiles.openfreemap.org/styles/dark"},
                {"geocoder","Photon search endpoint","https://photon.komoot.io/api/"},
                {"router","OSRM server base URL","https://router.project-osrm.org"}};
        for(String[] definition:definitions) {
            ui.heading(panel.body,definition[1].toUpperCase(Locale.ROOT)); EditText field=ui.input(definition[1]); field.setText(main.prefs.get(definition[0],definition[2]));
            if(definition[0].equals("diagnostics_key")) { field.setSingleLine(false); field.setMinLines(3); field.setMaxLines(6); }
            panel.body.addView(field); fields.put(definition[0],field);
        }
        fullButton(panel.body,"Save",()->{
            try {
                String repository=fields.containsKey("repository") ? fields.get("repository").getText().toString().trim() : "";
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
                if(section.equals("updates")) { main.prefs.data.edit().putLong("update_checked",0).apply(); main.release=null; main.updateBadge(); }
                if(mapsChanged) { main.prefs.data.edit().putBoolean("online_maps",false).apply(); main.clearNavigation(); main.portal.reloadOnlineChoice(); }
                panel.message("Connections saved" + (mapsChanged ? " · reconnect maps to use the new providers" : "")); panel.dismiss();
            } catch(Exception error) { panel.message(error.getMessage()); }
        });
    }
}
