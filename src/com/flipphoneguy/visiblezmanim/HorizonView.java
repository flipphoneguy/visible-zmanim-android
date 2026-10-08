package com.flipphoneguy.visiblezmanim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** The skyline around the sun's direction and the sun's path past it, drawn from the API's horizon data. */
public class HorizonView extends View {
    private static final int SUN = 0xFFF59E0B;

    private JSONObject data;
    private String eventHms = "";
    private final Paint land = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint path = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public HorizonView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        land.setColor(c.getResources().getColor(R.color.land));
        grid.setColor(c.getResources().getColor(R.color.card_border));
        grid.setStrokeWidth(dp);
        text.setColor(c.getResources().getColor(R.color.text_secondary));
        text.setTextSize(10 * dp);
        path.setColor(SUN);
        path.setStyle(Paint.Style.STROKE);
        path.setStrokeWidth(1.2f * dp);
        path.setPathEffect(new DashPathEffect(new float[]{3 * dp, 3 * dp}, 0));
        sunFill.setColor(SUN);
        sunEdge.setColor(SUN);
        sunEdge.setStyle(Paint.Style.STROKE);
        sunEdge.setStrokeWidth(1.2f * dp);
        sunEdge.setPathEffect(new DashPathEffect(new float[]{2 * dp, 2 * dp}, 0));
    }

    void setData(JSONObject horizon, String eventIso) {
        data = horizon;
        eventHms = Fmt.hms(eventIso);
        setVisibility(horizon == null || eventHms.length() == 0 ? GONE : VISIBLE);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * 0.47f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (data == null) return;
        try { draw(canvas, getWidth(), getHeight()); } catch (Exception ignored) { }
    }

    private void draw(Canvas c, float W, float H) throws Exception {
        float padL = 26 * dp, padB = 16 * dp, padT = 6 * dp;
        double az0 = data.getDouble("azimuth_start"), step = data.getDouble("azimuth_step");
        JSONArray terr = data.getJSONArray("terrain_deg");
        JSONObject sp = data.getJSONObject("sun_path");
        JSONArray times = sp.getJSONArray("time"), saz = sp.getJSONArray("azimuth"), salt = sp.getJSONArray("altitude_deg");
        double r = data.getDouble("sun_radius_deg");
        int n = terr.length();
        double span = step * (n - 1);
        if (n < 2 || times.length() == 0) return;

        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) if (!terr.isNull(i)) { double v = terr.getDouble(i); lo = Math.min(lo, v); hi = Math.max(hi, v); }
        int ev = 0;
        for (int i = 0; i < times.length(); i++) if (times.getString(i).compareTo(eventHms) <= 0) ev = i;
        if (salt.isNull(ev)) return;
        double evAlt = salt.getDouble(ev);
        lo = Math.min(lo, evAlt - 3 * r) - 0.3;
        hi = Math.max(hi, evAlt + 3 * r) + 0.6;

        // Same scale both ways unless the skyline is very flat; then heights are stretched and the sun becomes an ellipse to match.
        double sx = (W - padL) / span, sy = Math.max(sx, (H - padB - padT) / (hi - lo));
        double plotH = (hi - lo) * sy;
        if (plotH < H - padB - padT) { double extra = (H - padB - padT - plotH) / sy; lo -= extra / 3; hi += extra * 2 / 3; }

        for (int a = (int) Math.ceil(lo); a <= Math.floor(hi); a++) {
            float y = y(a, hi, sy, padT);
            c.drawLine(padL, y, W, y, grid);
            c.drawText(a + "°", 2 * dp, y + 3 * dp, text);
        }

        Path sun = new Path();
        boolean started = false;
        for (int i = 0; i < times.length(); i++) {
            if (salt.isNull(i)) continue;
            float x = x(saz.getDouble(i), az0, sx, padL), y = y(salt.getDouble(i), hi, sy, padT);
            if (!started) { sun.moveTo(x, y); started = true; } else sun.lineTo(x, y);
        }
        c.drawPath(sun, path);
        float cx = x(saz.getDouble(ev), az0, sx, padL), cy = y(evAlt, hi, sy, padT);
        RectF disk = new RectF(cx - (float) (r * sx), cy - (float) (r * sy), cx + (float) (r * sx), cy + (float) (r * sy));
        c.drawOval(disk, sunFill);

        Path ground = new Path();
        float first = -1, last = -1;
        for (int i = 0; i < n; i++) {
            if (terr.isNull(i)) continue;
            float x = x(az0 + i * step, az0, sx, padL), y = y(terr.getDouble(i), hi, sy, padT);
            if (first < 0) { first = x; ground.moveTo(x, H - padB); }
            ground.lineTo(x, y);
            last = x;
        }
        if (first >= 0) { ground.lineTo(last, H - padB); ground.close(); c.drawPath(ground, land); }
        c.drawOval(disk, sunEdge);

        double mid = Math.round((az0 + span / 2) / 2) * 2;
        for (int t = -4; t <= 4; t++) {
            double az = mid + t * 2;
            float x = x(az, az0, sx, padL);
            if (x < padL + 4 * dp || x > W - 12 * dp) continue;
            c.drawLine(x, H - padB, x, H - padB + 3 * dp, text);
            String label = (int) (((az % 360) + 360) % 360) + "°";
            c.drawText(label, x - text.measureText(label) / 2, H - 3 * dp, text);
        }
    }

    private static float x(double az, double az0, double sx, float padL) {
        double d = ((az - az0) % 360 + 360) % 360;
        if (d > 300) d -= 360;
        return (float) (padL + d * sx);
    }

    private static float y(double alt, double hi, double sy, float padT) {
        return (float) (padT + (hi - alt) * sy);
    }
}
