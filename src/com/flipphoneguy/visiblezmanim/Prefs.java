package com.flipphoneguy.visiblezmanim;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings. Lengths are kept in meters and only converted for display, so switching units never drifts. */
final class Prefs {
    static final double FT = 0.3048;
    static final double MI = 1609.344;
    static final double DEFAULT_HEIGHT_M = 1.7;
    static final double DEFAULT_MIN_DIST_M = 100;

    boolean imperial;
    double heightM = DEFAULT_HEIGHT_M;
    double groundM = Double.NaN;
    double minDistM = DEFAULT_MIN_DIST_M;
    String minDistUnit = "m";
    boolean showVisible = true, showElevation = true, showSeaLevel = true;
    boolean clock24;
    boolean roundSecs;
    String tz = "";
    double refraction = Double.NaN;
    double k = Double.NaN;
    String monthVariant = "visible";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        imperial = sp.getBoolean("imperial", false);
        heightM = getD("heightM", DEFAULT_HEIGHT_M);
        groundM = getD("groundM", Double.NaN);
        minDistM = getD("minDistM", DEFAULT_MIN_DIST_M);
        minDistUnit = sp.getString("minDistUnit", imperial ? "ft" : "m");
        showVisible = sp.getBoolean("showVisible", true);
        showElevation = sp.getBoolean("showElevation", true);
        showSeaLevel = sp.getBoolean("showSeaLevel", true);
        clock24 = sp.getBoolean("clock24", false);
        roundSecs = sp.getBoolean("roundSecs", false);
        tz = sp.getString("tz", "");
        refraction = getD("refraction", Double.NaN);
        k = getD("k", Double.NaN);
        monthVariant = sp.getString("monthVariant", "visible");
    }

    private double getD(String key, double def) {
        String s = sp.getString(key, null);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    void save() {
        sp.edit()
            .putBoolean("imperial", imperial)
            .putString("heightM", String.valueOf(heightM))
            .putString("groundM", String.valueOf(groundM))
            .putString("minDistM", String.valueOf(minDistM))
            .putString("minDistUnit", minDistUnit)
            .putBoolean("showVisible", showVisible)
            .putBoolean("showElevation", showElevation)
            .putBoolean("showSeaLevel", showSeaLevel)
            .putBoolean("clock24", clock24)
            .putBoolean("roundSecs", roundSecs)
            .putString("tz", tz)
            .putString("refraction", String.valueOf(refraction))
            .putString("k", String.valueOf(k))
            .putString("monthVariant", monthVariant)
            .apply();
    }

    String lengthUnit() { return imperial ? "ft" : "m"; }

    double toDisplay(double meters) { return imperial ? meters / FT : meters; }

    double fromDisplay(double value) { return imperial ? value * FT : value; }

    static double unitMeters(String unit) {
        if ("km".equals(unit)) return 1000;
        if ("ft".equals(unit)) return FT;
        if ("mi".equals(unit)) return MI;
        return 1;
    }

    /** The minimum-distance units offered for the current system. */
    String[] minDistUnits() { return imperial ? new String[]{"ft", "mi"} : new String[]{"m", "km"}; }

    void setImperial(boolean on) {
        if (on == imperial) return;
        imperial = on;
        // Same distance in the matching unit: m <-> ft, km <-> mi.
        if ("m".equals(minDistUnit)) minDistUnit = "ft";
        else if ("km".equals(minDistUnit)) minDistUnit = "mi";
        else if ("ft".equals(minDistUnit)) minDistUnit = "m";
        else minDistUnit = "km";
    }

    void reset() {
        heightM = DEFAULT_HEIGHT_M;
        groundM = Double.NaN;
        minDistM = DEFAULT_MIN_DIST_M;
        minDistUnit = imperial ? "ft" : "m";
        showVisible = showElevation = showSeaLevel = true;
        clock24 = false;
        roundSecs = false;
        tz = "";
        refraction = Double.NaN;
        k = Double.NaN;
    }
}
