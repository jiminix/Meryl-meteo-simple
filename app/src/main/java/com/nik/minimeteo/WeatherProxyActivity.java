package com.nik.minimeteo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Toast;

public class WeatherProxyActivity extends Activity {
    private static final String SAMSUNG_WEATHER = "com.sec.android.daemonapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openSamsungWeather();
        finish();
    }

    private void openSamsungWeather() {
        PackageManager pm = getPackageManager();

        Intent explicit = new Intent();
        explicit.setComponent(new ComponentName(
            SAMSUNG_WEATHER,
            "com.samsung.android.weather.app.particulars.ParticularsActivity"
        ));
        explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(explicit);
            return;
        } catch (Exception ignored) { }

        try {
            Intent launch = pm.getLaunchIntentForPackage(SAMSUNG_WEATHER);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return;
            }
        } catch (Exception ignored) { }

        try {
            Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + SAMSUNG_WEATHER));
            startActivity(settings);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d’ouvrir Météo Samsung", Toast.LENGTH_SHORT).show();
        }
    }
}
