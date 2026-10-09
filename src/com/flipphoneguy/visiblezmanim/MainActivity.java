package com.flipphoneguy.visiblezmanim;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    // label, section ("fixed" or per variant), key, highlighted
    private static final String[][] ROWS = {
        {"Alos 16.1°", "fixed", "alos_16_1", ""},
        {"Alos 72 min", "v", "alos_72", ""},
        {"Sunrise", "v", "sunrise", "key"},
        {"Sof zman shma MGA 16.1°", "fixed", "sof_zman_shma_mga_16_1", ""},
        {"Sof zman shma MGA 72", "v", "sof_zman_shma_mga_72", ""},
        {"Sof zman shma GRA", "v", "sof_zman_shma_gra", ""},
        {"Sof zman tefila MGA 16.1°", "fixed", "sof_zman_tfila_mga_16_1", ""},
        {"Sof zman tefila MGA 72", "v", "sof_zman_tfila_mga_72", ""},
        {"Sof zman tefila GRA", "v", "sof_zman_tfila_gra", ""},
        {"Chatzos", "fixed", "chatzos", ""},
        {"Mincha gedola", "v", "mincha_gedola_gra", ""},
        {"Mincha ketana", "v", "mincha_ketana_gra", ""},
        {"Plag hamincha", "v", "plag_hamincha_gra", ""},
        {"Candle lighting", "v", "candle_lighting", ""},
        {"Sunset", "v", "sunset", "key"},
        {"Tzais 8.5°", "fixed", "tzais_8_5", ""},
        {"Tzais 72 min", "v", "tzais_72", ""},
        {"Tzais 16.1°", "fixed", "tzais_16_1", ""},
    };
    private static final String[][] VARIANTS = {{"visible", "Visible"}, {"elevation", "Elevation"}, {"sea_level", "Standard"}};
    private static final int MAX_RETRIES = 16;
    private static final int REQ_LOCATION = 1;
    private static final int REQ_MAP = 2;

    private Prefs p;
    private Places places;
    private SharedPreferences state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int appliedTheme = -1;
    private String appliedPrefs = "";

    private AutoCompleteTextView addr;
    private EditText latField, lonField;
    private TextView matchNote, statusText;
    private View statusBox, spinner;
    private Button dateBtn, favBtn;

    private double lat = Double.NaN, lon = Double.NaN;
    private String label = "";
    private final Calendar cal = Calendar.getInstance();
    private boolean month;
    private JSONObject last;
    private int seq;
    private Runnable retry;
    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedTheme = ThemeHelper.getMode(this);
        setContentView(R.layout.activity_main);
        p = new Prefs(this);
        appliedPrefs = signature(p);
        places = new Places(this);
        state = getSharedPreferences("state", MODE_PRIVATE);

        addr = (AutoCompleteTextView) findViewById(R.id.addr);
        latField = (EditText) findViewById(R.id.lat);
        lonField = (EditText) findViewById(R.id.lon);
        matchNote = (TextView) findViewById(R.id.match_note);
        statusBox = findViewById(R.id.status);
        statusText = (TextView) findViewById(R.id.status_text);
        spinner = findViewById(R.id.status_spin);
        dateBtn = (Button) findViewById(R.id.btn_date);
        favBtn = (Button) findViewById(R.id.btn_fav);

        click(R.id.btn_info, new Runnable() { public void run() { startActivity(new Intent(MainActivity.this, InfoActivity.class)); } });
        click(R.id.btn_settings, new Runnable() { public void run() { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
        click(R.id.btn_calc, new Runnable() { public void run() { calculateFromFields(); } });
        click(R.id.btn_gps, new Runnable() { public void run() { useMyLocation(); } });
        click(R.id.btn_saved, new Runnable() { public void run() { showSaved(); } });
        click(R.id.btn_map, new Runnable() { public void run() { pickOnMap(); } });
        click(R.id.btn_fav, new Runnable() { public void run() { toggleFavorite(); } });
        click(R.id.btn_prev, new Runnable() { public void run() { shift(-1); } });
        click(R.id.btn_next, new Runnable() { public void run() { shift(1); } });
        click(R.id.btn_today, new Runnable() { public void run() { cal.setTimeInMillis(System.currentTimeMillis()); dateChanged(); } });
        click(R.id.btn_day, new Runnable() { public void run() { setMonth(false); } });
        click(R.id.btn_month, new Runnable() { public void run() { setMonth(true); } });
        click(R.id.btn_date, new Runnable() { public void run() { pickDate(); } });
        click(R.id.btn_variant, new Runnable() { public void run() { pickVariant(); } });
        click(R.id.btn_share, new Runnable() { public void run() { shareCsv(); } });

        setupSuggestions();

        lat = Double.longBitsToDouble(state.getLong("lat", Double.doubleToLongBits(Double.NaN)));
        lon = Double.longBitsToDouble(state.getLong("lon", Double.doubleToLongBits(Double.NaN)));
        label = state.getString("label", "");
        month = state.getBoolean("month", false);
        showLocation(null);
        setMonth(month);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedTheme != ThemeHelper.getMode(this)) { recreate(); return; }
        Prefs fresh = new Prefs(this);
        String sig = signature(fresh);
        boolean refetch = !sig.equals(appliedPrefs);
        p = fresh;
        appliedPrefs = sig;
        if (refetch) calculate(null);
        else render();
        updateFavButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocation();
    }

    /** Settings that change the request; display-only settings just re-render. */
    private static String signature(Prefs p) {
        return p.heightM + "|" + p.groundM + "|" + p.minDistM + "|" + p.tz + "|" + p.refraction + "|" + p.k;
    }

    private void click(int id, final Runnable r) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
    }

    // ---------- location ----------

    private void setupSuggestions() {
        final SuggestAdapter adapter = new SuggestAdapter();
        addr.setAdapter(adapter);
        addr.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                Api.Suggestion s = adapter.getItem(pos);
                label = s.label();
                setLocation(s.lat, s.lon, s.exact ? null : "This match is a street or area, not an exact address. For the best accuracy pick one with a house number.");
                hideKeyboard();
                calculate(null);
            }
        });
        addr.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent e) {
                if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE) return false;
                searchTyped();
                return true;
            }
        });
        click(R.id.btn_search, new Runnable() { public void run() { searchTyped(); } });
    }

    /** Looks up the typed text on the server, which can find addresses the suggestions miss. */
    private void searchTyped() {
        String q = addr.getText().toString().trim();
        if (q.length() == 0) return;
        hideKeyboard();
        addr.dismissDropDown();
        label = q;
        lat = lon = Double.NaN;
        calculate(q);
    }

    private final class SuggestAdapter extends ArrayAdapter<Api.Suggestion> {
        private final List<Api.Suggestion> items = new ArrayList<Api.Suggestion>();

        SuggestAdapter() { super(MainActivity.this, android.R.layout.simple_dropdown_item_1line); }

        @Override public int getCount() { return items.size(); }
        @Override public Api.Suggestion getItem(int i) { return items.get(i); }

        @Override public Filter getFilter() {
            return new Filter() {
                @Override protected FilterResults performFiltering(CharSequence q) {
                    FilterResults r = new FilterResults();
                    if (q != null && q.length() >= 3) {
                        List<Api.Suggestion> found = Api.suggest(q.toString(), lat, lon);
                        r.values = found;
                        r.count = found.size();
                    }
                    return r;
                }
                @Override @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence q, FilterResults r) {
                    items.clear();
                    if (r.values != null) items.addAll((List<Api.Suggestion>) r.values);
                    notifyDataSetChanged();
                }
                @Override public CharSequence convertResultToString(Object o) { return ((Api.Suggestion) o).label(); }
            };
        }
    }

    private void setLocation(double la, double lo, String warning) {
        lat = la;
        lon = lo;
        showLocation(warning);
    }

    private void showLocation(String warning) {
        latField.setText(Double.isNaN(lat) ? "" : String.format(Locale.US, "%.6f", lat));
        lonField.setText(Double.isNaN(lon) ? "" : String.format(Locale.US, "%.6f", lon));
        if (!addr.getText().toString().equals(label)) addr.setText(label, false);
        matchNote.setText(warning == null ? "" : warning);
        matchNote.setVisibility(warning == null ? View.GONE : View.VISIBLE);
        state.edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon)).putString("label", label).apply();
        updateFavButton();
    }

    private void calculateFromFields() {
        hideKeyboard();
        try {
            double la = Double.parseDouble(latField.getText().toString().trim());
            double lo = Double.parseDouble(lonField.getText().toString().trim());
            if (la != lat || lo != lon) label = addr.getText().toString().trim().equals(label) && Math.abs(la - lat) < 1e-6 ? label : "";
            setLocation(la, lo, null);
            calculate(null);
        } catch (NumberFormatException e) {
            String q = addr.getText().toString().trim();
            if (q.length() == 0) { status("Enter an address or coordinates first.", false); return; }
            label = q;
            lat = lon = Double.NaN;
            calculate(q);
        }
    }

    private void useMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        status("Getting your location…", true);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && System.currentTimeMillis() - l.getTime() < 120000 && l.getAccuracy() < 50) { gotLocation(l); return; }
            } catch (Exception ignored) { }
        }
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location l) { gotLocation(l); }
            @Override public void onStatusChanged(String s, int i, Bundle b) { }
            @Override public void onProviderEnabled(String s) { }
            @Override public void onProviderDisabled(String s) { }
        };
        boolean any = false;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 0, 0, locationListener, Looper.getMainLooper());
                    any = true;
                }
            } catch (Exception ignored) { }
        }
        if (!any) { status("Location is turned off on this phone.", false); return; }
        handler.postDelayed(new Runnable() {
            public void run() {
                if (locationListener != null) { stopLocation(); status("Couldn't get a location fix. Try again outside, or type an address.", false); }
            }
        }, 30000);
    }

    private void gotLocation(Location l) {
        stopLocation();
        label = "My location";
        setLocation(l.getLatitude(), l.getLongitude(), l.getAccuracy() > 100 ? "Your phone gave a rough location (±" + Math.round(l.getAccuracy()) + " m)." : null);
        calculate(null);
    }

    private void pickOnMap() {
        Intent i = new Intent(this, MapPickActivity.class);
        if (!Double.isNaN(lat)) i.putExtra(MapPickActivity.EXTRA_LAT, lat).putExtra(MapPickActivity.EXTRA_LON, lon);
        startActivityForResult(i, REQ_MAP);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code != REQ_MAP || result != RESULT_OK || data == null) return;
        label = "Map pin";
        setLocation(data.getDoubleExtra(MapPickActivity.EXTRA_LAT, Double.NaN), data.getDoubleExtra(MapPickActivity.EXTRA_LON, Double.NaN), null);
        calculate(null);
    }

    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) { }
        }
        locationListener = null;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) useMyLocation();
        else if (code == REQ_LOCATION) status("Location permission was not granted.", false);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (imm != null && v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ---------- saved places ----------

    private Places.Place currentPlace() {
        Places.Place cur = new Places.Place();
        cur.lat = lat;
        cur.lon = lon;
        cur.label = label;
        cur.heightM = p.heightM;
        cur.groundM = p.groundM;
        return cur;
    }

    private Places.Place findCurrent() {
        if (Double.isNaN(lat)) return null;
        Places.Place cur = currentPlace();
        for (Places.Place pl : places.list()) if (pl.same(cur)) return pl;
        return null;
    }

    private void updateFavButton() {
        Places.Place pl = findCurrent();
        favBtn.setText(pl != null && pl.fav ? "★" : "☆");
    }

    private void toggleFavorite() {
        if (Double.isNaN(lat)) { status("Pick a location first.", false); return; }
        Places.Place pl = findCurrent();
        if (pl != null && pl.fav) {
            pl.fav = false;
            pl.name = "";
            replace(pl);
            return;
        }
        final Places.Place target = pl != null ? pl : currentPlace();
        askName("Name this place (for example Home or Shul)", target.title(), new NameCallback() {
            public void done(String name) {
                target.fav = true;
                target.name = name;
                if (findCurrent() == null) places.remember(target); else replace(target);
                updateFavButton();
            }
        });
    }

    private void replace(Places.Place updated) {
        List<Places.Place> list = places.list();
        for (int i = 0; i < list.size(); i++) if (list.get(i).same(updated)) { list.set(i, updated); break; }
        places.save(Places.sortFavsFirst(list));
        updateFavButton();
    }

    private interface NameCallback { void done(String name); }

    private void askName(String title, final String current, final NameCallback cb) {
        final EditText in = new EditText(this);
        in.setText(current);
        in.setSelectAllOnFocus(true);
        in.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle(title).setView(in)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String n = in.getText().toString().trim();
                    cb.done(n.length() > 0 ? n : current);
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void showSaved() {
        final List<Places.Place> list = places.list();
        if (list.isEmpty()) { status("Places you look up appear here. Tap ☆ to keep one as a favorite.", false); return; }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Places.Place pl = list.get(i);
            String extra = Math.abs(pl.heightM - Prefs.DEFAULT_HEIGHT_M) > 0.05 ? " · " + Fmt.height(pl.heightM, p) : "";
            names[i] = (pl.fav ? "★ " : "") + pl.title() + extra + (pl.fav && pl.label.length() > 0 && !pl.label.equals(pl.name) ? "\n" + pl.label : "");
        }
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Saved and recent places")
            .setItems(names, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int i) { usePlace(list.get(i)); }
            })
            .setNegativeButton("Close", null).create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                ListView lv = dlg.getListView();
                lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override public boolean onItemLongClick(AdapterView<?> parent, View v, int i, long id) {
                        dlg.dismiss();
                        editPlace(list.get(i));
                        return true;
                    }
                });
            }
        });
        dlg.show();
        status("Tip: long-press a place to rename, star or remove it.", false);
    }

    private void editPlace(final Places.Place pl) {
        String[] actions = pl.fav ? new String[]{"Rename", "Remove from favorites", "Delete"} : new String[]{"Save as a favorite", "Delete"};
        new AlertDialog.Builder(this).setTitle(pl.title()).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                List<Places.Place> list = places.list();
                int idx = -1;
                for (int i = 0; i < list.size(); i++) if (list.get(i).same(pl)) idx = i;
                if (idx < 0) return;
                final Places.Place target = list.get(idx);
                boolean delete = pl.fav ? which == 2 : which == 1;
                if (delete) { list.remove(idx); places.save(list); updateFavButton(); return; }
                if (pl.fav && which == 1) { target.fav = false; target.name = ""; replace(target); return; }
                askName(pl.fav ? "New name" : "Name this place", target.title(), new NameCallback() {
                    public void done(String name) { target.fav = true; target.name = name; replace(target); }
                });
            }
        }).show();
    }

    private void usePlace(Places.Place pl) {
        label = pl.label.length() > 0 ? pl.label : pl.title();
        p.heightM = pl.heightM;
        p.groundM = pl.groundM;
        p.save();
        appliedPrefs = signature(p);
        setLocation(pl.lat, pl.lon, null);
        calculate(null);
    }

    // ---------- date ----------

    private void shift(int n) {
        if (month) cal.add(Calendar.MONTH, n); else cal.add(Calendar.DAY_OF_MONTH, n);
        dateChanged();
    }

    private void pickDate() {
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker v, int y, int m, int d) { cal.set(y, m, d); dateChanged(); }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void dateChanged() { updateDateButton(); calculate(null); }

    private void updateDateButton() {
        String f = month ? "%1$tB %1$tY" : "%1$ta, %1$tb %1$te, %1$tY";
        dateBtn.setText(String.format(Locale.US, f, cal));
    }

    private void setMonth(boolean on) {
        month = on;
        state.edit().putBoolean("month", on).apply();
        styleToggle((Button) findViewById(R.id.btn_day), !on);
        styleToggle((Button) findViewById(R.id.btn_month), on);
        updateDateButton();
        findViewById(R.id.day_view).setVisibility(View.GONE);
        findViewById(R.id.month_view).setVisibility(View.GONE);
        last = null;
        calculate(null);
    }

    private void styleToggle(Button b, boolean on) {
        b.setBackgroundResource(on ? R.drawable.btn_filled : R.drawable.btn_outline);
        b.setTextColor(getResources().getColor(on ? R.color.text_on_primary : R.color.btn_outline_text));
    }

    private static String iso(Calendar c) {
        return String.format(Locale.US, "%1$tY-%1$tm-%1$td", c);
    }

    // ---------- fetching ----------

    private void status(String msg, boolean busy) {
        statusBox.setVisibility(msg == null ? View.GONE : View.VISIBLE);
        statusText.setText(msg == null ? "" : msg);
        spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void calculate(final String address) { fetch(address, 0); }

    private void fetch(final String address, final int attempt) {
        if (retry != null) { handler.removeCallbacks(retry); retry = null; }
        if (Double.isNaN(lat) && address == null) return;
        final int mySeq = ++seq;
        final String start, end;
        if (month) {
            Calendar a = (Calendar) cal.clone();
            a.set(Calendar.DAY_OF_MONTH, 1);
            Calendar b = (Calendar) a.clone();
            b.set(Calendar.DAY_OF_MONTH, a.getActualMaximum(Calendar.DAY_OF_MONTH));
            start = iso(a);
            end = iso(b);
        } else {
            start = end = iso(cal);
        }
        final double la = lat, lo = lon;
        final Prefs snapshot = p;
        if (attempt == 0) status("Calculating…", true);
        new Thread(new Runnable() {
            public void run() {
                Api.Response res = null;
                String error = null;
                try { res = Api.zmanim(la, lo, address, start, end, snapshot); } catch (Exception e) { error = "Couldn't reach the server. Check your connection and try again."; }
                final Api.Response r = res;
                final String err = error;
                runOnUiThread(new Runnable() {
                    public void run() { if (mySeq == seq) handle(r, err, address, attempt); }
                });
            }
        }).start();
    }

    private void handle(Api.Response r, String err, String address, int attempt) {
        if (err != null) { status(err, false); return; }
        if (r.code >= 400) { status(r.body.optString("error", "Something went wrong."), false); return; }
        last = r.body;
        JSONObject loc = last.optJSONObject("location");
        if (loc != null && loc.has("address")) {
            JSONObject a = loc.optJSONObject("address");
            label = a.optString("matched", label);
            String precision = a.optString("precision", "");
            boolean exact = "us_census".equals(a.optString("source")) || "house".equals(precision) || "building".equals(precision);
            setLocation(loc.optDouble("lat"), loc.optDouble("lon"), exact ? null : "This matched a " + precision + ", not an exact address. For the best accuracy use a full street address.");
        }
        if (attempt == 0 && !Double.isNaN(lat)) { places.remember(currentPlace()); updateFavButton(); }
        render();
        if (r.code == 202) {
            if (attempt >= MAX_RETRIES) { status("This area is taking unusually long. Try again in a few minutes.", false); return; }
            status("First time for this area: downloading terrain data. Standard and elevation times are ready; visible times follow in about a minute.", true);
            final int next = attempt + 1;
            retry = new Runnable() { public void run() { fetch(null, next); } };
            handler.postDelayed(retry, Math.max(5, last.optInt("retry_after_s", 15)) * 1000L);
        } else {
            status(null, false);
        }
    }

    // ---------- rendering ----------

    private List<String[]> columns() {
        List<String[]> c = new ArrayList<String[]>();
        if (p.showVisible) c.add(VARIANTS[0]);
        if (p.showElevation) c.add(VARIANTS[1]);
        if (p.showSeaLevel) c.add(VARIANTS[2]);
        return c;
    }

    private void render() {
        if (last == null) return;
        JSONArray days = last.optJSONArray("days");
        if (days == null || days.length() == 0) return;
        if (month) renderMonth(days); else renderDay(days.optJSONObject(0));
    }

    private void renderDay(JSONObject day) {
        findViewById(R.id.day_view).setVisibility(View.VISIBLE);
        findViewById(R.id.month_view).setVisibility(View.GONE);
        JSONObject vis = day.optJSONObject("visible");
        JSONObject horizon = last.optJSONObject("horizon");
        renderEvent(vis, horizon, "sunrise", R.id.rise_time, R.id.rise_why, R.id.rise_draw, R.id.rise_map);
        renderEvent(vis, horizon, "sunset", R.id.set_time, R.id.set_why, R.id.set_draw, R.id.set_map);

        TableLayout t = (TableLayout) findViewById(R.id.day_table);
        t.removeAllViews();
        List<String[]> cols = columns();
        TableRow head = new TableRow(this);
        head.addView(cell("", true, false));
        for (String[] c : cols) head.addView(cell(c[1], true, true));
        t.addView(head);
        JSONObject fixed = day.optJSONObject("fixed");
        for (String[] r : ROWS) {
            TableRow row = new TableRow(this);
            boolean key = "key".equals(r[3]);
            TextView name = cell(r[0], key, false);
            if (key) name.setTextColor(getResources().getColor(R.color.key_row));
            row.addView(name);
            if ("fixed".equals(r[1])) {
                TextView v = cell(Fmt.time(str(fixed, r[2]), r[2], p), false, true);
                v.setTextColor(getResources().getColor(R.color.text_secondary));
                TableRow.LayoutParams lp = new TableRow.LayoutParams();
                lp.span = cols.size();
                row.addView(v, lp);
            } else {
                for (String[] c : cols) {
                    JSONObject sec = day.optJSONObject(c[0]);
                    String text = sec == null ? "—" : sec.has("status") ? "…" : Fmt.time(str(sec, r[2]), r[2], p);
                    row.addView(cell(text, key, true));
                }
            }
            row.setPadding(0, dp(5), 0, dp(5));
            t.addView(row);
        }
        ((TextView) findViewById(R.id.day_meta)).setText(meta());
    }

    private void renderEvent(JSONObject vis, JSONObject horizon, String e, int timeId, int whyId, int drawId, int mapId) {
        TextView time = (TextView) findViewById(timeId), why = (TextView) findViewById(whyId);
        HorizonView draw = (HorizonView) findViewById(drawId);
        Button map = (Button) findViewById(mapId);
        map.setVisibility(View.GONE);
        draw.setData(null, null);
        if (vis == null || vis.has("status")) {
            time.setText("…");
            why.setText("Computing the horizon for this area…");
            return;
        }
        String iso = str(vis, e);
        time.setText(Fmt.time(iso, e, p));
        if (iso == null) {
            why.setText(e.equals("sunrise") ? "The sun doesn't clear the horizon today." : "The sun doesn't set behind the horizon today.");
            return;
        }
        JSONObject d = vis.optJSONObject(e + "_details");
        StringBuilder b = new StringBuilder();
        if (d != null && d.has("vs_sea_level_s") && !d.isNull("vs_sea_level_s")) b.append(Fmt.diff(d.optDouble("vs_sea_level_s")));
        final JSONObject blk = d == null ? null : d.optJSONObject("blocking");
        if (blk != null) {
            b.append(" The sun ").append(e.equals("sunrise") ? "rises over " : "sets behind ").append(describe(blk))
                .append(" to the ").append(Fmt.compass(vis.optDouble(e + "_azimuth"))).append('.');
            map.setVisibility(View.VISIBLE);
            map.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openMap(blk.optDouble("lat"), blk.optDouble("lon")); }
            });
        }
        why.setText(b.toString().trim());
        draw.setData(horizon == null ? null : horizon.optJSONObject(e), iso);
    }

    private String describe(JSONObject blk) {
        if ("sea_level".equals(blk.optString("source"))) return "the sea horizon, " + Fmt.distance(blk.optDouble("distance_m"), p) + " away,";
        return "land " + Fmt.distance(blk.optDouble("distance_m"), p) + " away, " + Fmt.height(blk.optDouble("height_m"), p) + " above sea level,";
    }

    private void openMap(double la, double lo) {
        String coords = String.format(Locale.US, "%.6f,%.6f", la, lo);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + coords + "?q=" + coords + "(Horizon point)")));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=" + la + "&mlon=" + lo + "#map=15/" + la + "/" + lo)));
        }
    }

    private String meta() {
        JSONObject l = last.optJSONObject("location");
        if (l == null) return "";
        String src = l.optString("ground_source", "");
        String srcName = "usgs_3dep_1m".equals(src) ? "USGS 1 m lidar" : "usgs_3dep_13".equals(src) ? "USGS 10 m" : "ea_lidar_1m".equals(src) ? "Environment Agency 1 m lidar" : "gedtm30".equals(src) ? "GEDTM30" : "given".equals(src) ? "entered" : src;
        StringBuilder b = new StringBuilder();
        if (!l.isNull("ground_m")) b.append("Ground ").append(Fmt.height(l.optDouble("ground_m"), p)).append(" (").append(srcName).append(")  ·  ");
        b.append("Eye level ").append(Fmt.height(l.optDouble("eye_elevation_m"), p)).append("  ·  ").append(l.optString("timezone"));
        return b.toString();
    }

    private void renderMonth(JSONArray days) {
        findViewById(R.id.day_view).setVisibility(View.GONE);
        findViewById(R.id.month_view).setVisibility(View.VISIBLE);
        String variant = p.monthVariant;
        ((Button) findViewById(R.id.btn_variant)).setText(variantName(variant) + " times ▾");
        TableLayout t = (TableLayout) findViewById(R.id.month_table);
        t.removeAllViews();
        List<String[]> fields = new ArrayList<String[]>();
        for (String[] r : ROWS) if ("v".equals(r[1]) || "chatzos".equals(r[2]) || "tzais_8_5".equals(r[2])) fields.add(r);
        TableRow head = new TableRow(this);
        head.addView(cell("Date", true, false));
        for (String[] r : fields) head.addView(cell(r[0], true, true));
        t.addView(head);
        String[] dn = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Shabbos"};
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            String date = day.optString("date");
            c.set(Integer.parseInt(date.substring(0, 4)), Integer.parseInt(date.substring(5, 7)) - 1, Integer.parseInt(date.substring(8, 10)));
            TableRow row = new TableRow(this);
            int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
            row.addView(cell(dn[dow] + " " + c.get(Calendar.DAY_OF_MONTH), dow == 5 || dow == 6, false));
            for (String[] r : fields) {
                JSONObject sec = "fixed".equals(r[1]) ? day.optJSONObject("fixed") : day.optJSONObject(variant);
                row.addView(cell(sec == null ? "—" : sec.has("status") ? "…" : Fmt.time(str(sec, r[2]), r[2], p), false, true));
            }
            if (dow == 5) row.setBackgroundColor(getResources().getColor(R.color.item_focused_bg));
            row.setPadding(0, dp(4), 0, dp(4));
            t.addView(row);
        }
        ((TextView) findViewById(R.id.month_meta)).setText(meta());
    }

    private static String variantName(String key) {
        for (String[] v : VARIANTS) if (v[0].equals(key)) return v[1];
        return key;
    }

    private void pickVariant() {
        String[] names = {"Visible times", "Elevation times", "Standard times"};
        new AlertDialog.Builder(this).setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int i) { p.monthVariant = VARIANTS[i][0]; p.save(); render(); }
        }).show();
    }

    private void shareCsv() {
        if (last == null) return;
        JSONArray days = last.optJSONArray("days");
        if (days == null) return;
        StringBuilder b = new StringBuilder("date");
        for (String[] r : ROWS) if ("fixed".equals(r[1])) b.append(',').append(r[2]);
        for (String[] v : VARIANTS) for (String[] r : ROWS) if ("v".equals(r[1])) b.append(',').append(v[0]).append(':').append(r[2]);
        b.append('\n');
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            b.append(day.optString("date"));
            for (String[] r : ROWS) if ("fixed".equals(r[1])) b.append(',').append(Fmt.time(str(day.optJSONObject("fixed"), r[2]), r[2], p));
            for (String[] v : VARIANTS) {
                JSONObject sec = day.optJSONObject(v[0]);
                for (String[] r : ROWS) if ("v".equals(r[1])) b.append(',').append(sec == null || sec.has("status") ? "" : Fmt.time(str(sec, r[2]), r[2], p));
            }
            b.append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "Zmanim " + String.format(Locale.US, "%1$tB %1$tY", cal));
        send.putExtra(Intent.EXTRA_TEXT, b.toString());
        startActivity(Intent.createChooser(send, "Share zmanim"));
    }

    private TextView cell(String text, boolean bold, boolean center) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(getResources().getColor(R.color.text_primary));
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(4), 0, dp(6), 0);
        if (center) v.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        return v;
    }

    private static String str(JSONObject o, String k) {
        return o == null || o.isNull(k) ? null : o.optString(k, null);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
