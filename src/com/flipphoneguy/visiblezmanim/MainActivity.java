package com.flipphoneguy.visiblezmanim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    private int appliedMode = -1;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedMode = ThemeHelper.getMode(this);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_info).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InfoActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedMode != ThemeHelper.getMode(this)) {
            recreate();
        }
    }
}
