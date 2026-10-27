package com.breakdash.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Locale;
import com.breakdash.launcher.core.MapTracking;
import com.breakdash.launcher.core.VehicleHeading;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

final class MapPortal extends FrameLayout implements LocationListener {
    final MapView mapView;
    private MapLibreMap map;
    private final Activity activity;
    private final Prefs prefs;
    private final LocationManager locations;
    private final Consumer<Location> changed;
    private final Consumer<String> status;
    private final float density;
    private boolean active, light, destroyed;
    private int background = Color.rgb(10,14,23);
    private Location lastFix;
    private final VehicleHeading vehicleHeading = new VehicleHeading();
    private final MapTracking tracking = new MapTracking();
    private Navigation.Place selection;
    private final Runnable finishPreview = () -> { if (active) renderFix(); };
    Navigation.Route route;

    MapPortal(Activity activity, Prefs prefs, Bundle saved, Consumer<Location> changed, Consumer<String> status, Consumer<Navigation.Place> selected) {
        super(activity);
        this.activity = activity; this.prefs = prefs; this.changed = changed; this.status = status;
        density=getResources().getDisplayMetrics().density;
        locations = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
        MapLibre.getInstance(activity);
        mapView = new MapView(activity, new MapLibreMapOptions().textureMode(false).compassEnabled(false).rotateGesturesEnabled(true)
                .scrollGesturesEnabled(true).zoomGesturesEnabled(true).tiltGesturesEnabled(false));
        addView(mapView, new LayoutParams(-1,-1)); mapView.onCreate(saved); mapView.setMaximumFps(20);
        mapView.addOnDidFailLoadingMapListener(error -> status.accept("Map unavailable · check internet"));
        // Mask outside the circle without a TextureView or extra offscreen framebuffer.
        addView(new Ring(activity), new LayoutParams(-1,-1));
        mapView.getMapAsync(ready -> {
            if (destroyed) return;
            map = ready; map.setPrefetchZoomDelta(0); map.setTileCacheEnabled(false);
            map.getUiSettings().setCompassEnabled(false); map.getUiSettings().setLogoEnabled(false); map.getUiSettings().setAttributionEnabled(false);
            map.addOnCameraMoveStartedListener(reason -> {
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    tracking.explore(); removeCallbacks(finishPreview); changed.accept(fix());
                }
            });
            map.addOnMapLongClickListener(point -> {
                if (!prefs.data.getBoolean("online_maps",false)) { status.accept("Connect online maps to choose a destination"); return true; }
                double lon = ((point.getLongitude()+180)%360+360)%360-180;
                Navigation.Place place = new Navigation.Place(String.format(Locale.US,"Pinned location (%.5f, %.5f)",point.getLatitude(),lon),point.getLatitude(),lon);
                mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                selected.accept(place); return true;
            });
            applyStyle();
        });
    }
    void theme(boolean value) {
        light = value; background = Color.parseColor(light ? "#EEF3F5" : "#0A0E17");
        getChildAt(1).invalidate(); if (map != null) applyStyle();
    }
    void reloadOnlineChoice() { if (map != null) applyStyle(); }
    private void applyStyle() {
        Style.Builder builder = new Style.Builder();
        if (prefs.data.getBoolean("online_maps",false)) builder.fromUri(prefs.get(light ? "map_day" : "map_night", light ? "https://tiles.openfreemap.org/styles/positron" : "https://tiles.openfreemap.org/styles/dark"));
        else builder.fromJson("{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"" + (light ? "#DCE6E9" : "#101E29") + "\"}}]}");
        map.setStyle(builder, style -> {
            if (destroyed) return;
            style.addSource(new GeoJsonSource("breakdash-route", empty()));
            style.addLayer(new LineLayer("breakdash-route-halo", "breakdash-route").withProperties(PropertyFactory.lineColor("#0A0E17"),PropertyFactory.lineWidth(10f)));
            style.addLayer(new LineLayer("breakdash-route-line", "breakdash-route").withProperties(PropertyFactory.lineColor("#42D6CB"),PropertyFactory.lineWidth(5f)));
            style.addSource(new GeoJsonSource("breakdash-destination", empty()));
            style.addImage("breakdash-pin",pinImage());
            style.addLayer(pinLayer("breakdash-destination-pin","breakdash-destination"));
            style.addSource(new GeoJsonSource("breakdash-selection", empty()));
            style.addLayer(pinLayer("breakdash-selection-pin","breakdash-selection"));
            style.addSource(new GeoJsonSource("breakdash-vehicle", empty()));
            style.addLayer(new CircleLayer("breakdash-vehicle-dot", "breakdash-vehicle").withProperties(PropertyFactory.circleColor("#42D6CB"),PropertyFactory.circleRadius(7f),PropertyFactory.circleStrokeColor("#FFFFFF"),PropertyFactory.circleStrokeWidth(2f)));
            style.addImage("breakdash-arrow",vehicleImage());
            style.addLayer(new SymbolLayer("breakdash-vehicle-arrow","breakdash-vehicle").withProperties(
                    PropertyFactory.iconImage("breakdash-arrow"),PropertyFactory.iconAllowOverlap(true),PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT)));
            renderRoute(); renderSelection(); renderFix();
        });
    }
    private SymbolLayer pinLayer(String id,String source) {
        return new SymbolLayer(id,source).withProperties(PropertyFactory.iconImage("breakdash-pin"),PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),PropertyFactory.iconIgnorePlacement(true));
    }
    private Bitmap pinImage() {
        Bitmap image = Bitmap.createBitmap(32,44,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); Path pin = new Path();
        pin.moveTo(16,43); pin.cubicTo(12,35,1,25,1,16); pin.cubicTo(1,-4,31,-4,31,16); pin.cubicTo(31,25,20,35,16,43); pin.close();
        paint.setColor(Color.parseColor("#F2B86B")); canvas.drawPath(pin,paint);
        paint.setColor(Color.parseColor("#0A0E17")); canvas.drawCircle(16,15,6,paint); return image;
    }
    private Bitmap vehicleImage() {
        Bitmap image=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); Path arrow=new Path();
        arrow.moveTo(20,3); arrow.lineTo(35,35); arrow.lineTo(20,28); arrow.lineTo(5,35); arrow.close();
        paint.setColor(Color.parseColor("#42D6CB")); canvas.drawPath(arrow,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setStrokeJoin(Paint.Join.ROUND); paint.setColor(Color.WHITE); canvas.drawPath(arrow,paint);
        return image;
    }
    void pin(Navigation.Place place) {
        if(map!=null) { map.cancelAllVelocityAnimations(); map.cancelTransitions(); }
        selection=place; tracking.explore(); removeCallbacks(finishPreview); renderSelection(); changed.accept(fix());
    }
    Navigation.Place selection() { return selection; }
    void clearSelection() { selection=null; renderSelection(); changed.accept(fix()); }
    boolean following() { return tracking.following(); }
    void recenter() {
        if(map!=null) { map.cancelAllVelocityAnimations(); map.cancelTransitions(); }
        tracking.recenter(); removeCallbacks(finishPreview); renderFix(); changed.accept(fix());
    }
    private void renderSelection() {
        if(map==null || map.getStyle()==null) return;
        GeoJsonSource source=map.getStyle().getSourceAs("breakdash-selection");
        if(source==null) return;
        if(selection==null) source.setGeoJson(empty());
        else source.setGeoJson(Point.fromLngLat(selection.lon,selection.lat));
    }
    private FeatureCollection empty() { return FeatureCollection.fromFeatures(new Feature[0]); }
    void active(boolean value) {
        if (active == value) return; active = value;
        if (!value) { removeCallbacks(finishPreview); locations.removeUpdates(this); if(map!=null) map.cancelTransitions(); mapView.onPause(); return; }
        mapView.onResume();
        long previewLeft=tracking.previewRemaining(SystemClock.elapsedRealtime());
        if(previewLeft>0) postDelayed(finishPreview,previewLeft);
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { status.accept("Tap GPS to allow location"); return; }
        try {
            if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { status.accept("GPS is switched off"); return; }
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,3,this);
            Location cached = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (cached != null && SystemClock.elapsedRealtimeNanos()-cached.getElapsedRealtimeNanos()<15_000_000_000L) onLocationChanged(cached);
            else status.accept("Acquiring satellites");
        } catch (RuntimeException error) { status.accept("GPS unavailable"); }
    }
    Location fix() { return lastFix != null && SystemClock.elapsedRealtimeNanos()-lastFix.getElapsedRealtimeNanos()<15_000_000_000L ? lastFix : null; }
    double heading() { return vehicleHeading.degrees(); }
    boolean hasHeading() { return vehicleHeading.known(); }
    @Override public void onLocationChanged(Location fix) {
        if (!active || !fix.hasAccuracy() || fix.getAccuracy()>100) return;
        lastFix = fix;
        if (fix.hasBearing() && fix.hasSpeed()) vehicleHeading.update(fix.getBearing(),fix.getSpeed(),fix.hasBearingAccuracy() ? fix.getBearingAccuracyDegrees() : Double.NaN);
        renderFix(); changed.accept(fix);
    }
    private void renderFix() {
        if (map==null || lastFix==null) return;
        Style style=map.getStyle(); if (style==null) return;
        GeoJsonSource source=style.getSourceAs("breakdash-vehicle");
        if (source!=null) source.setGeoJson(Point.fromLngLat(lastFix.getLongitude(),lastFix.getLatitude()));
        SymbolLayer arrow=style.getLayerAs("breakdash-vehicle-arrow");
        if(arrow!=null) arrow.setProperties(PropertyFactory.iconRotate((float)heading()),PropertyFactory.iconOpacity(hasHeading() ? 1f : 0f));
        if (active && fix()!=null && tracking.shouldFollow(SystemClock.elapsedRealtime())) {
            boolean navigating=route!=null;
            CameraPosition camera=new CameraPosition.Builder().target(new LatLng(lastFix.getLatitude(),lastFix.getLongitude()))
                    .zoom(navigating ? 16.5 : 15.5).bearing(heading()).tilt(navigating ? 45 : 0)
                    .padding(0,navigating ? getHeight()*.18 : 0,0,0).build();
            // Interpolate only between GPS updates; no extra sensor or animation loop.
            map.easeCamera(CameraUpdateFactory.newCameraPosition(camera),700,false);
        }
    }
    void route(Navigation.Route value) {
        route=value; selection=null; removeCallbacks(finishPreview); renderSelection(); renderRoute();
        if (map!=null && value!=null) {
            List<LatLng> points=new ArrayList<>();
            for (Point point:value.geometry) points.add(new LatLng(point.latitude(),point.longitude()));
            map.cancelAllVelocityAnimations();
            map.cancelTransitions();
            tracking.previewRoute(SystemClock.elapsedRealtime());
            int roundPadding=Math.round(Math.min(getWidth(),getHeight())*.20f);
            int bottomPadding=Math.max(roundPadding,Math.round(176*density));
            // Follow-camera padding persists in MapLibre, so clear it before fitting a flat overview.
            map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder(map.getCameraPosition()).padding(0,0,0,0).tilt(0).bearing(0).build()));
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder().includes(points).build(),0,0,roundPadding,roundPadding,roundPadding,bottomPadding));
            if(active) postDelayed(finishPreview,MapTracking.ROUTE_PREVIEW_MS);
        } else { tracking.recenter(); renderFix(); }
        changed.accept(fix());
    }
    private void renderRoute() {
        if (map==null || map.getStyle()==null) return;
        GeoJsonSource line=map.getStyle().getSourceAs("breakdash-route"),pin=map.getStyle().getSourceAs("breakdash-destination");
        if (line==null || pin==null) return;
        if (route==null) { line.setGeoJson(empty()); pin.setGeoJson(empty()); }
        else { line.setGeoJson(LineString.fromLngLats(route.geometry)); pin.setGeoJson(Point.fromLngLat(route.destination.lon,route.destination.lat)); }
    }
    void destroy() { removeCallbacks(finishPreview); active(false); destroyed=true; mapView.onDestroy(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
            float dx=event.getX()-getWidth()/2f,dy=event.getY()-getHeight()/2f,radius=mapRadius();
            if(dx*dx+dy*dy>radius*radius) return false;
        }
        return super.dispatchTouchEvent(event);
    }
    private float mapRadius() { return Math.max(0,Math.min(getWidth(),getHeight())/2f-5*density); }
    @Override public void onProviderEnabled(String provider) { status.accept("Acquiring satellites"); }
    @Override public void onProviderDisabled(String provider) { lastFix=null; status.accept("GPS is switched off"); changed.accept(null); }
    @Override public void onStatusChanged(String provider,int status,Bundle extras) {}
    private final class Ring extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); final Path mask=new Path();
        Ring(Activity context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float cx=getWidth()/2f,cy=getHeight()/2f,radius=mapRadius();
            mask.reset(); mask.setFillType(Path.FillType.EVEN_ODD); mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW); mask.addCircle(cx,cy,radius,Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL); paint.setColor(background); canvas.drawPath(mask,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density); paint.setColor(Color.parseColor(light ? "#B9C8CF" : "#253342")); canvas.drawCircle(cx,cy,radius+density,paint);
            paint.setColor(Color.parseColor("#42D6CB")); paint.setStrokeWidth(2*density); float arcRadius=radius+3*density; canvas.drawArc(cx-arcRadius,cy-arcRadius,cx+arcRadius,cy+arcRadius,225,90,false,paint);
            paint.setStrokeWidth(density); paint.setColor(Color.parseColor(light ? "#7D919D" : "#405263"));
            for(int i=0;i<60;i++){ double a=i*Math.PI/30; float outer=radius+3*density,inner=radius+(i%5==0?-3:1)*density; canvas.drawLine(cx+(float)Math.sin(a)*inner,cy+(float)Math.cos(a)*inner,cx+(float)Math.sin(a)*outer,cy+(float)Math.cos(a)*outer,paint); }
        }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int size=Math.min(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY));
    }
}
