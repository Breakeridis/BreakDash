package com.breakdash.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    private boolean active, light, destroyed;
    private int background = Color.rgb(10,14,23);
    private Location lastFix;
    private double bearing;
    private long frameDestinationUntil;
    Navigation.Route route;

    MapPortal(Activity activity, Prefs prefs, Bundle saved, Consumer<Location> changed, Consumer<String> status, Runnable launchNavigation) {
        super(activity);
        this.activity = activity; this.prefs = prefs; this.changed = changed; this.status = status;
        locations = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
        MapLibre.getInstance(activity);
        mapView = new MapView(activity, new MapLibreMapOptions().textureMode(false).compassEnabled(false).rotateGesturesEnabled(false)
                .scrollGesturesEnabled(false).zoomGesturesEnabled(false).tiltGesturesEnabled(false));
        addView(mapView, new LayoutParams(-1,-1)); mapView.onCreate(saved); mapView.setMaximumFps(20);
        mapView.addOnDidFailLoadingMapListener(error -> status.accept("Map unavailable · check internet"));
        // Mask outside the circle without a TextureView or extra offscreen framebuffer.
        addView(new Ring(activity), new LayoutParams(-1,-1));
        mapView.getMapAsync(ready -> {
            if (destroyed) return;
            map = ready; map.setPrefetchZoomDelta(0); map.setTileCacheEnabled(false);
            map.getUiSettings().setCompassEnabled(false); map.getUiSettings().setLogoEnabled(false); map.getUiSettings().setAttributionEnabled(false);
            map.addOnMapClickListener(point -> { launchNavigation.run(); return true; });
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
            style.addLayer(new CircleLayer("breakdash-destination-dot", "breakdash-destination").withProperties(PropertyFactory.circleColor("#F2B86B"),PropertyFactory.circleRadius(8f),PropertyFactory.circleStrokeColor("#0A0E17"),PropertyFactory.circleStrokeWidth(3f)));
            style.addSource(new GeoJsonSource("breakdash-vehicle", empty()));
            style.addLayer(new CircleLayer("breakdash-vehicle-dot", "breakdash-vehicle").withProperties(PropertyFactory.circleColor("#42D6CB"),PropertyFactory.circleRadius(7f),PropertyFactory.circleStrokeColor("#FFFFFF"),PropertyFactory.circleStrokeWidth(2f)));
            renderRoute(); renderFix();
        });
    }
    private FeatureCollection empty() { return FeatureCollection.fromFeatures(new Feature[0]); }
    void active(boolean value) {
        if (active == value) return; active = value;
        if (!value) { locations.removeUpdates(this); mapView.onPause(); return; }
        mapView.onResume();
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
    double heading() { return bearing; }
    @Override public void onLocationChanged(Location fix) {
        if (!active || !fix.hasAccuracy() || fix.getAccuracy()>100) return;
        lastFix = fix;
        if (fix.hasBearing() && fix.hasSpeed() && fix.getSpeed()>1.5) bearing=fix.getBearing();
        renderFix(); changed.accept(fix);
    }
    private void renderFix() {
        if (map==null || lastFix==null) return;
        Style style=map.getStyle(); if (style==null) return;
        GeoJsonSource source=style.getSourceAs("breakdash-vehicle");
        if (source!=null) source.setGeoJson(Point.fromLngLat(lastFix.getLongitude(),lastFix.getLatitude()));
        if (SystemClock.elapsedRealtime()>=frameDestinationUntil) map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(lastFix.getLatitude(),lastFix.getLongitude())).zoom(15.5).bearing(bearing).build()));
    }
    void route(Navigation.Route value) {
        route=value; renderRoute();
        if (map!=null && value!=null) {
            List<LatLng> points=new ArrayList<>();
            for (Point point:value.geometry) points.add(new LatLng(point.latitude(),point.longitude()));
            frameDestinationUntil=SystemClock.elapsedRealtime()+6500;
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder().includes(points).build(),80));
        } else { frameDestinationUntil=0; renderFix(); }
    }
    private void renderRoute() {
        if (map==null || map.getStyle()==null) return;
        GeoJsonSource line=map.getStyle().getSourceAs("breakdash-route"),pin=map.getStyle().getSourceAs("breakdash-destination");
        if (line==null || pin==null) return;
        if (route==null) { line.setGeoJson(empty()); pin.setGeoJson(empty()); }
        else { line.setGeoJson(LineString.fromLngLats(route.geometry)); pin.setGeoJson(Point.fromLngLat(route.destination.lon,route.destination.lat)); }
    }
    void destroy() { active(false); destroyed=true; mapView.onDestroy(); }
    @Override public void onProviderEnabled(String provider) { status.accept("Acquiring satellites"); }
    @Override public void onProviderDisabled(String provider) { lastFix=null; status.accept("GPS is switched off"); changed.accept(null); }
    @Override public void onStatusChanged(String provider,int status,Bundle extras) {}
    private final class Ring extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); final Path mask=new Path();
        Ring(Activity context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float cx=getWidth()/2f,cy=getHeight()/2f,radius=Math.min(cx,cy)-14;
            mask.reset(); mask.setFillType(Path.FillType.EVEN_ODD); mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW); mask.addCircle(cx,cy,radius,Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL); paint.setColor(background); canvas.drawPath(mask,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(Color.parseColor(light ? "#B9C8CF" : "#253342")); canvas.drawCircle(cx,cy,radius+3,paint);
            paint.setColor(Color.parseColor("#42D6CB")); paint.setStrokeWidth(3); canvas.drawArc(cx-radius-8,cy-radius-8,cx+radius+8,cy+radius+8,225,90,false,paint);
            paint.setStrokeWidth(1); paint.setColor(Color.parseColor(light ? "#7D919D" : "#405263"));
            for(int i=0;i<60;i++){ double a=i*Math.PI/30; float outer=radius+10,inner=radius+(i%5==0?3:7); canvas.drawLine(cx+(float)Math.sin(a)*inner,cy+(float)Math.cos(a)*inner,cx+(float)Math.sin(a)*outer,cy+(float)Math.cos(a)*outer,paint); }
        }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int size=Math.min(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY));
    }
}
