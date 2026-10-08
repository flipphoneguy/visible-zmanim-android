package com.flipphoneguy.visiblezmanim;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private Prefs p;
    private EditText height, ground, mind, tz, refr, kk;
    private Spinner mindUnit;
    private boolean filling;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        p = new Prefs(this);
        height = (EditText) findViewById(R.id.height);
        ground = (EditText) findViewById(R.id.ground);
        mind = (EditText) findViewById(R.id.mind);
        tz = (EditText) findViewById(R.id.tz);
        refr = (EditText) findViewById(R.id.refr);
        kk = (EditText) findViewById(R.id.kk);
        mindUnit = (Spinner) findViewById(R.id.mind_unit);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        ((RadioGroup) findViewById(R.id.units)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                if (filling) return;
                read();
                p.setImperial(id == R.id.units_imperial);
                fill();
            }
        });
        int[] floors = {R.id.floor0, R.id.floor1, R.id.floor4, R.id.floor9};
        final int[] counts = {0, 1, 4, 9};
        for (int i = 0; i < floors.length; i++) {
            final int n = counts[i];
            findViewById(floors[i]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    height.setText(Fmt.num(p.toDisplay(Prefs.DEFAULT_HEIGHT_M + 3 * n), 1));
                }
            });
        }
        mindUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (filling) return;
                // Keep the same distance when switching between e.g. m and km.
                double meters = parse(mind, Double.NaN) * Prefs.unitMeters(p.minDistUnit);
                p.minDistUnit = p.minDistUnits()[pos];
                if (!Double.isNaN(meters)) mind.setText(Fmt.num(meters / Prefs.unitMeters(p.minDistUnit), 3));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { p.reset(); fill(); }
        });
        fill();
    }

    private void fill() {
        filling = true;
        ((RadioGroup) findViewById(R.id.units)).check(p.imperial ? R.id.units_imperial : R.id.units_metric);
        height.setText(Fmt.num(p.toDisplay(p.heightM), 1));
        ground.setText(Double.isNaN(p.groundM) ? "" : Fmt.num(p.toDisplay(p.groundM), 1));
        ((TextView) findViewById(R.id.height_unit)).setText(p.lengthUnit());
        ((TextView) findViewById(R.id.ground_unit)).setText(p.lengthUnit());
        String[] units = p.minDistUnits();
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, units);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mindUnit.setAdapter(a);
        for (int i = 0; i < units.length; i++) if (units[i].equals(p.minDistUnit)) mindUnit.setSelection(i);
        mind.setText(Fmt.num(p.minDistM / Prefs.unitMeters(p.minDistUnit), 3));
        ((CheckBox) findViewById(R.id.col_visible)).setChecked(p.showVisible);
        ((CheckBox) findViewById(R.id.col_elevation)).setChecked(p.showElevation);
        ((CheckBox) findViewById(R.id.col_sea)).setChecked(p.showSeaLevel);
        ((RadioGroup) findViewById(R.id.clock)).check(p.clock24 ? R.id.clock24 : R.id.clock12);
        ((RadioGroup) findViewById(R.id.secs)).check(p.roundSecs ? R.id.secs_round : R.id.secs_show);
        tz.setText(p.tz);
        refr.setText(Fmt.num(p.refraction, 2));
        kk.setText(Fmt.num(p.k, 3));
        filling = false;
    }

    private void read() {
        double h = parse(height, Double.NaN);
        if (!Double.isNaN(h) && h >= 0) p.heightM = p.fromDisplay(h);
        double g = parse(ground, Double.NaN);
        p.groundM = Double.isNaN(g) ? Double.NaN : p.fromDisplay(g);
        double md = parse(mind, Double.NaN);
        if (!Double.isNaN(md) && md >= 0) p.minDistM = md * Prefs.unitMeters(p.minDistUnit);
        p.showVisible = ((CheckBox) findViewById(R.id.col_visible)).isChecked();
        p.showElevation = ((CheckBox) findViewById(R.id.col_elevation)).isChecked();
        p.showSeaLevel = ((CheckBox) findViewById(R.id.col_sea)).isChecked();
        if (!p.showVisible && !p.showElevation && !p.showSeaLevel) p.showVisible = true;
        p.clock24 = ((RadioGroup) findViewById(R.id.clock)).getCheckedRadioButtonId() == R.id.clock24;
        p.roundSecs = ((RadioGroup) findViewById(R.id.secs)).getCheckedRadioButtonId() == R.id.secs_round;
        p.tz = tz.getText().toString().trim();
        p.refraction = parse(refr, Double.NaN);
        p.k = parse(kk, Double.NaN);
    }

    private static double parse(EditText e, double def) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (NumberFormatException ex) { return def; }
    }

    @Override
    protected void onPause() {
        super.onPause();
        read();
        p.save();
    }
}
