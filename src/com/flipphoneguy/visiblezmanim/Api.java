package com.flipphoneguy.visiblezmanim;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Api {
    static final String ZMANIM = "https://api.flipphoneguy.duckdns.org/zmanim";
    static final String PHOTON = "https://photon.komoot.io/api/";
    static final String WEBSITE = "https://tools.flipphoneguy.duckdns.org/zmanim";
    static final String METHOD = "https://tools.flipphoneguy.duckdns.org/zmanim/method";
    static final String ENGINE_REPO = "https://github.com/flipphoneguy/visible-zmanim";

    static final class Response {
        final int code;
        final JSONObject body;
        Response(int code, JSONObject body) { this.code = code; this.body = body; }
    }

    static final class Suggestion {
        final String line1, line2;
        final double lat, lon;
        final boolean exact;
        Suggestion(String line1, String line2, double lat, double lon, boolean exact) {
            this.line1 = line1; this.line2 = line2; this.lat = lat; this.lon = lon; this.exact = exact;
        }
        String label() { return line2.length() > 0 ? line1 + ", " + line2 : line1; }
        @Override public String toString() { return label(); }
    }

    private Api() {}

    /** One day (start == end) or a range. Location is lat/lon, or an address when lat is NaN. */
    static Response zmanim(double lat, double lon, String address, String start, String end, Prefs p) throws IOException {
        StringBuilder q = new StringBuilder(ZMANIM).append('?');
        if (!Double.isNaN(lat)) q.append("lat=").append(fmt(lat)).append("&lon=").append(fmt(lon));
        else q.append("address=").append(enc(address));
        if (start.equals(end)) q.append("&date=").append(start).append("&include=horizon");
        else q.append("&start=").append(start).append("&end=").append(end);
        q.append("&height=").append(fmt(p.heightM));
        if (!Double.isNaN(p.groundM)) q.append("&ground=").append(fmt(p.groundM));
        q.append("&min_distance=").append(fmt(p.minDistM));
        if (p.tz.length() > 0) q.append("&tz=").append(enc(p.tz));
        if (!Double.isNaN(p.refraction)) q.append("&refraction=").append(fmt(p.refraction));
        if (!Double.isNaN(p.k)) q.append("&k=").append(fmt(p.k));
        HttpURLConnection c = open(q.toString());
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "{}" : read(in);
            try { return new Response(code, new JSONObject(text)); } catch (Exception e) { return new Response(code, new JSONObject()); }
        } finally {
            c.disconnect();
        }
    }

    /** Address suggestions while typing, from Photon (OpenStreetMap data). */
    static List<Suggestion> suggest(String query, double nearLat, double nearLon) {
        List<Suggestion> out = new ArrayList<Suggestion>();
        try {
            String url = PHOTON + "?limit=6&lang=en&q=" + enc(query);
            if (!Double.isNaN(nearLat)) url += "&lat=" + fmt(nearLat) + "&lon=" + fmt(nearLon);
            HttpURLConnection c = open(url);
            try {
                JSONArray features = new JSONObject(read(c.getInputStream())).optJSONArray("features");
                if (features == null) return out;
                List<String> seen = new ArrayList<String>();
                for (int i = 0; i < features.length(); i++) {
                    JSONObject f = features.getJSONObject(i), pr = f.getJSONObject("properties");
                    JSONArray xy = f.getJSONObject("geometry").getJSONArray("coordinates");
                    String house = pr.optString("housenumber", ""), street = pr.optString("street", ""), name = pr.optString("name", "");
                    String line1 = (house + " " + street).trim();
                    if (line1.length() == 0 || house.length() == 0 && name.length() > 0) line1 = name;
                    String place = firstOf(pr, "city", "town", "village", "county");
                    String line2 = join(place, pr.optString("state", ""), pr.optString("country", ""));
                    if (line1.length() == 0) { line1 = line2; line2 = ""; }
                    Suggestion s = new Suggestion(line1, line2, xy.getDouble(1), xy.getDouble(0), house.length() > 0);
                    if (seen.contains(s.label())) continue;
                    seen.add(s.label());
                    out.add(s);
                }
            } finally {
                c.disconnect();
            }
        } catch (Exception ignored) {
            // Suggestions are optional; typing still works without them.
        }
        return out;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", BuildConfig.APP_NAME.replace(" ", "") + "/" + BuildConfig.VERSION_NAME + " (" + BuildConfig.GITHUB_PROFILE + "/" + BuildConfig.REPO.split("/")[1] + ")");
        return c;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }

    private static String firstOf(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k, ""); if (v.length() > 0) return v; }
        return "";
    }

    private static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String s : parts) if (s.length() > 0) b.append(b.length() > 0 ? ", " : "").append(s);
        return b.toString();
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String fmt(double v) { return String.format(Locale.US, "%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }
}
