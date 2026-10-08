package com.flipphoneguy.visiblezmanim;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Fmt {
    // Luach-style rounding: deadlines round down, everything else rounds up.
    private static final Set<String> ROUND_DOWN = new HashSet<String>(Arrays.asList(
        "sof_zman_shma_gra", "sof_zman_shma_mga_72", "sof_zman_shma_mga_16_1",
        "sof_zman_tfila_gra", "sof_zman_tfila_mga_72", "sof_zman_tfila_mga_16_1",
        "candle_lighting", "sunset"));
    private static final String[] COMPASS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    private Fmt() {}

    /** An ISO time from the API ("2026-10-07T06:58:44-04:00") as shown to the user, in the location's own time. */
    static String time(String iso, String key, Prefs p) {
        if (iso == null || iso.length() < 19 || "null".equals(iso)) return "—";
        int h = Integer.parseInt(iso.substring(11, 13));
        int m = Integer.parseInt(iso.substring(14, 16));
        int s = Integer.parseInt(iso.substring(17, 19));
        if (p.roundSecs) {
            if (ROUND_DOWN.contains(key)) s = 0;
            else if (s > 0) { m++; s = 0; }
            if (m == 60) { m = 0; h = (h + 1) % 24; }
        }
        String suffix = "";
        int shown = h;
        if (!p.clock24) {
            suffix = h < 12 ? " AM" : " PM";
            shown = h % 12 == 0 ? 12 : h % 12;
        }
        StringBuilder b = new StringBuilder();
        b.append(p.clock24 ? pad(shown) : String.valueOf(shown)).append(':').append(pad(m));
        if (!p.roundSecs) b.append(':').append(pad(s));
        return b.append(suffix).toString();
    }

    static String hms(String iso) {
        return iso == null || iso.length() < 19 ? "" : iso.substring(11, 19);
    }

    static String height(double meters, Prefs p) {
        return Math.round(p.toDisplay(meters)) + " " + p.lengthUnit();
    }

    static String distance(double meters, Prefs p) {
        if (p.imperial) {
            double mi = meters / Prefs.MI;
            if (mi < 0.5) return Math.round(meters / Prefs.FT) + " ft";
            return (mi < 10 ? String.format(Locale.US, "%.1f", mi) : String.valueOf(Math.round(mi))) + " mi";
        }
        if (meters < 1000) return Math.round(meters) + " m";
        return (meters < 10000 ? String.format(Locale.US, "%.1f", meters / 1000) : String.valueOf(Math.round(meters / 1000))) + " km";
    }

    static String compass(double az) {
        int i = (int) Math.round((((az % 360) + 360) % 360) / 22.5) % 16;
        return COMPASS[i];
    }

    /** A number for an input field: no trailing ".0", at most the given decimals. */
    static String num(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        String s = String.format(Locale.US, "%." + decimals + "f", v);
        if (s.indexOf('.') >= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    static String diff(double seconds) {
        double a = Math.abs(seconds);
        if (a < 1) return "Same as the standard time.";
        long m = (long) (a / 60), s = Math.round(a % 60);
        return (m > 0 ? m + " min " : "") + s + " s " + (seconds < 0 ? "earlier" : "later") + " than the standard time.";
    }

    private static String pad(int v) { return v < 10 ? "0" + v : String.valueOf(v); }
}
