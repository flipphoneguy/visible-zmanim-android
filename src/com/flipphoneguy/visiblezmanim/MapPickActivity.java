package com.flipphoneguy.visiblezmanim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Pick a spot on a map: the pin stays in the center and the map moves under it. Returns "lat" and "lon". */
public class MapPickActivity extends Activity {
    static final String EXTRA_LAT = "lat", EXTRA_LON = "lon";
    // Also sent as the Referer the OSM tile servers ask for.
    private static final String BASE_URL = "https://tools.flipphoneguy.duckdns.org/zmanim";

    private WebView web;
    private TextView coords;
    private View useBtn;
    private double lat = Double.NaN, lon = Double.NaN;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        coords = (TextView) findViewById(R.id.coords);
        useBtn = findViewById(R.id.btn_use);
        View.OnClickListener cancel = new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        };
        findViewById(R.id.btn_back).setOnClickListener(cancel);
        findViewById(R.id.btn_cancel).setOnClickListener(cancel);
        useBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LON, lon));
                finish();
            }
        });

        web = (WebView) findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " VisibleZmanim/" + BuildConfig.VERSION_NAME);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) { gotCenter(title); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return openOutside(req.getUrl().toString()); }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return openOutside(url); }
        });

        double startLat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        double startLon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
        boolean known = !Double.isNaN(startLat) && !Double.isNaN(startLon);
        String layer = getSharedPreferences("state", MODE_PRIVATE).getString("map_layer", "Map");
        String start = String.format(Locale.US, "[%f,%f,%d,\"%s\"]", known ? startLat : 30.0, known ? startLon : 0.0, known ? 17 : 2, "Satellite".equals(layer) ? "Satellite" : "Map");
        String html = readMapPage()
            .replace("__START__", start)
            .replace("__BG__", hex(R.color.bg_page))
            .replace("__FG__", hex(R.color.text_primary));
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null);
    }

    /** The page puts "lat,lon,layer" in its title whenever the map moves. */
    private void gotCenter(String title) {
        String[] parts = title == null ? new String[0] : title.split(",");
        if (parts.length != 3) return;
        try {
            lat = Double.parseDouble(parts[0]);
            lon = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        coords.setText(String.format(Locale.US, "%.5f, %.5f", lat, lon));
        useBtn.setEnabled(true);
        getSharedPreferences("state", MODE_PRIVATE).edit().putString("map_layer", parts[2]).apply();
    }

    private boolean openOutside(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
        return true;
    }

    private String readMapPage() {
        InputStream in = getResources().openRawResource(R.raw.map);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    @SuppressWarnings("deprecation")
    private String hex(int colorRes) {
        return String.format("#%06X", getResources().getColor(colorRes) & 0xFFFFFF);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
