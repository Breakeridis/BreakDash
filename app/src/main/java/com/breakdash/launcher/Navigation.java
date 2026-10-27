package com.breakdash.launcher;

import android.location.Location;
import com.breakdash.launcher.core.RouteProgress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

final class Navigation {
    static final class Place {
        final String name; final double lat, lon;
        Place(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }
    static final class Step {
        final double remaining; final String instruction;
        Step(double remaining, String instruction) { this.remaining = remaining; this.instruction = instruction; }
    }
    static final class Route {
        final Place destination; final List<Point> geometry; final List<Step> steps;
        final double[][] coordinates; final double[] remaining;
        final double distance, duration; int progress;
        Route(Place destination, List<Point> geometry, List<Step> steps, double distance, double duration) {
            this.destination = destination; this.geometry = geometry; this.steps = steps; this.distance = distance; this.duration = duration;
            coordinates = new double[geometry.size()][2]; remaining = new double[geometry.size()];
            for (int i = geometry.size()-1; i >= 0; i--) {
                Point p = geometry.get(i); coordinates[i] = new double[]{p.latitude(), p.longitude()};
                if (i < geometry.size()-1) remaining[i] = remaining[i+1] + RouteProgress.distance(coordinates[i][0], coordinates[i][1], coordinates[i+1][0], coordinates[i+1][1]);
            }
        }
        String guidance(Location fix, boolean mph) {
            RouteProgress position = RouteProgress.locate(coordinates, remaining, fix.getLatitude(), fix.getLongitude(), progress);
            progress = position.segment;
            if (position.offRouteMeters > Math.max(80, fix.getAccuracy()*2)) return "Off route · search again to recalculate";
            if (position.remainingMeters < 35 && RouteProgress.distance(fix.getLatitude(), fix.getLongitude(), destination.lat, destination.lon) < 50) return "You have arrived · " + destination.name;
            Step next = steps.get(steps.size()-1);
            double normalized = position.remainingMeters * distance / Math.max(1, remaining[0]);
            for (Step step : steps) if (step.remaining < normalized - 15) { next = step; break; }
            return distance(Math.max(0, normalized - next.remaining), mph) + " · " + next.instruction + "\n" + distance(normalized, mph) + " to " + destination.name;
        }
    }
    private static void requireOnline(Prefs prefs) {
        if (!prefs.data.getBoolean("online_maps", false)) throw new IllegalStateException("Connect online maps first");
    }
    static String distance(double meters, boolean mph) {
        if (mph) return meters < 160.9 ? String.format(Locale.US, "%.0f ft", meters*3.28084) : String.format(Locale.US, "%.1f mi", meters/1609.344);
        return meters < 1000 ? String.format(Locale.US, "%.0f m", meters) : String.format(Locale.US, "%.1f km", meters/1000);
    }
    static List<Place> search(Prefs prefs, String query, Location fix) throws Exception {
        requireOnline(prefs);
        String endpoint = prefs.get("geocoder", "https://photon.komoot.io/api/");
        String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()) + "&limit=5";
        if (fix != null) url += "&lat=" + fix.getLatitude() + "&lon=" + fix.getLongitude();
        JSONArray features = new JSONObject(Http.json(url)).getJSONArray("features");
        List<Place> places = new ArrayList<>();
        for (int i=0; i<features.length(); i++) {
            JSONObject feature = features.getJSONObject(i), props = feature.getJSONObject("properties");
            JSONArray point = feature.getJSONObject("geometry").getJSONArray("coordinates");
            List<String> text = new ArrayList<>();
            for (String key : new String[]{"name", "street", "housenumber", "city", "state", "country"}) {
                String value = props.optString(key, ""); if (!value.isEmpty() && !text.contains(value)) text.add(value);
            }
            places.add(new Place(String.join(", ", text), point.getDouble(1), point.getDouble(0)));
        }
        return places;
    }
    static Route route(Prefs prefs, Location origin, Place destination) throws Exception {
        requireOnline(prefs);
        String base = prefs.get("router", "https://router.project-osrm.org").replaceAll("/+$", "");
        String url = String.format(Locale.US, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true", base, origin.getLongitude(), origin.getLatitude(), destination.lon, destination.lat);
        JSONObject response = new JSONObject(Http.json(url));
        if (!"Ok".equals(response.optString("code"))) throw new IllegalStateException("No driving route found");
        JSONObject route = response.getJSONArray("routes").getJSONObject(0);
        List<Point> geometry = LineString.fromJson(route.getJSONObject("geometry").toString()).coordinates();
        if (geometry.size()<2 || geometry.size()>60000) throw new IllegalStateException("Route is empty or too large");
        List<Step> steps = new ArrayList<>(); double remaining = route.getDouble("distance");
        JSONArray legs = route.getJSONArray("legs");
        for (int leg=0; leg<legs.length(); leg++) {
            JSONArray parts = legs.getJSONObject(leg).getJSONArray("steps");
            for (int i=0; i<parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i), maneuver = part.getJSONObject("maneuver");
                String type = maneuver.optString("type", "continue").replace('_',' '), modifier = maneuver.optString("modifier", ""), road = part.optString("name", "");
                String instruction = type.equals("arrive") ? "Arrive at destination" : type.equals("depart") ? "Continue" : type.equals("roundabout") || type.equals("rotary") ? "Roundabout · exit " + maneuver.optInt("exit",1) : type + (modifier.isEmpty() ? "" : " " + modifier);
                if (!road.isEmpty() && !type.equals("arrive")) instruction += " onto " + road;
                steps.add(new Step(remaining, instruction)); remaining = Math.max(0, remaining - part.getDouble("distance"));
            }
        }
        if (steps.isEmpty()) steps.add(new Step(0,"Arrive at destination"));
        return new Route(destination, geometry, steps, route.getDouble("distance"), route.getDouble("duration"));
    }
}
