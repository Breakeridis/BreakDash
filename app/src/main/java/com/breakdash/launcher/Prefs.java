package com.breakdash.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import com.breakdash.launcher.core.MapStyles;

final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) {
        data = context.getSharedPreferences("breakdash", Context.MODE_PRIVATE);
        // Only the old defaults migrate. Consent and all other providers remain untouched.
        SharedPreferences.Editor migration = data.edit();
        for (String key : new String[]{"map_day", "map_night"}) {
            if (data.contains(key) && MapStyles.override(key.equals("map_day"), get(key, "")).isEmpty()) migration.remove(key);
        }
        migration.apply();
    }
    String mapOverride(boolean light) { return MapStyles.override(light, get(light ? "map_day" : "map_night", "")); }
    String mapStyle(boolean light) { return MapStyles.uri(light, mapOverride(light)); }
    void recommendedMaps() { data.edit().remove("map_day").remove("map_night").apply(); }
    String get(String key, String fallback) { return data.getString(key, fallback); }
    void put(String key, String value) { data.edit().putString(key, value).apply(); }
    boolean light() { return data.getBoolean("light", false); }
    void light(boolean value) { data.edit().putBoolean("light", value).apply(); }
    boolean mph() { return data.getBoolean("mph", false); }
    void toggleUnits() { data.edit().putBoolean("mph", !mph()).apply(); }
    List<String> favorites() {
        List<String> result = new ArrayList<>();
        String saved = get("favorites", "");
        for (String value : saved.split("\\|")) if (!value.isEmpty() && !result.contains(value)) result.add(value);
        return result;
    }
    void favorites(List<String> values) { put("favorites", String.join("|", values)); }
    int frequency(int slot) { return data.getInt("preset_freq_" + slot, 0); }
    byte band(int slot) { return (byte) data.getInt("preset_band_" + slot, 0); }
    void preset(int slot, int frequency, byte band) {
        data.edit().putInt("preset_freq_" + slot, frequency).putInt("preset_band_" + slot, band).apply();
    }
}
