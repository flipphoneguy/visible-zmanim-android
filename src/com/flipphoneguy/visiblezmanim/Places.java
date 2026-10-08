package com.flipphoneguy.visiblezmanim;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Favorites and recent places, kept on the device only. Each one remembers its height and ground too. */
final class Places {
    static final int MAX_RECENT = 8;
    static final int MAX_FAV = 20;

    static final class Place {
        String label = "", name = "";
        double lat, lon, heightM = Prefs.DEFAULT_HEIGHT_M, groundM = Double.NaN;
        boolean fav;
        long t;

        String title() {
            if (name.length() > 0) return name;
            if (label.length() > 0) return label;
            return String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon);
        }

        boolean same(Place o) {
            double dy = (lat - o.lat) * 111320;
            double dx = (lon - o.lon) * 111320 * Math.cos(Math.toRadians(lat));
            boolean groundSame = Double.isNaN(groundM) ? Double.isNaN(o.groundM) : Math.abs(groundM - o.groundM) < 0.1;
            return Math.sqrt(dx * dx + dy * dy) < 10 && Math.abs(heightM - o.heightM) < 0.3 && groundSame;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("label", label).put("name", name).put("lat", lat).put("lon", lon).put("heightM", heightM).put("fav", fav).put("t", t);
            if (!Double.isNaN(groundM)) o.put("groundM", groundM);
            return o;
        }

        static Place fromJson(JSONObject o) {
            Place p = new Place();
            p.label = o.optString("label", "");
            p.name = o.optString("name", "");
            p.lat = o.optDouble("lat");
            p.lon = o.optDouble("lon");
            p.heightM = o.optDouble("heightM", Prefs.DEFAULT_HEIGHT_M);
            p.groundM = o.optDouble("groundM", Double.NaN);
            p.fav = o.optBoolean("fav");
            p.t = o.optLong("t");
            return p;
        }
    }

    private final SharedPreferences sp;

    Places(Context c) { sp = c.getSharedPreferences("places", Context.MODE_PRIVATE); }

    /** Favorites first, then recents, newest first. */
    List<Place> list() {
        List<Place> favs = new ArrayList<Place>(), recents = new ArrayList<Place>();
        try {
            JSONArray a = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Place p = Place.fromJson(a.getJSONObject(i));
                (p.fav ? favs : recents).add(p);
            }
        } catch (JSONException e) {
            return favs;
        }
        favs.addAll(recents);
        return favs;
    }

    void save(List<Place> places) {
        JSONArray a = new JSONArray();
        int favs = 0, recents = 0;
        for (Place p : places) {
            if (p.fav ? favs++ >= MAX_FAV : recents++ >= MAX_RECENT) continue;
            try { a.put(p.toJson()); } catch (JSONException ignored) { }
        }
        sp.edit().putString("list", a.toString()).apply();
    }

    /** Puts the place first, merging it with an existing entry for the same spot. */
    void remember(Place cur) {
        List<Place> list = list();
        for (int i = 0; i < list.size(); i++) {
            Place p = list.get(i);
            if (p.same(cur)) {
                cur.fav = p.fav;
                cur.name = p.name;
                if (cur.label.length() == 0) cur.label = p.label;
                list.remove(i);
                break;
            }
        }
        cur.t = System.currentTimeMillis();
        list.add(0, cur);
        save(sortFavsFirst(list));
    }

    static List<Place> sortFavsFirst(List<Place> list) {
        List<Place> out = new ArrayList<Place>();
        for (Place p : list) if (p.fav) out.add(p);
        for (Place p : list) if (!p.fav) out.add(p);
        return out;
    }
}
