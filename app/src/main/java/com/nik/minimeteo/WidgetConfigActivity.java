package com.nik.minimeteo;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WidgetConfigActivity extends Activity {
    private static final String PREFS = "mini_weather";
    private static final int REQ_LOCATION = 71;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private EditText cityInput;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        buildUi();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Mini Météo");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Choisis la météo à afficher. Le widget montrera la date, la météo et la localité.");
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(12), 0, dp(24));
        root.addView(sub, subLp);

        cityInput = new EditText(this);
        cityInput.setHint("Ville (ex. Lyon, Tours…)");
        cityInput.setSingleLine(true);
        root.addView(cityInput, new LinearLayout.LayoutParams(-1, dp(56)));

        Button cityButton = new Button(this);
        cityButton.setText("Utiliser cette ville");
        cityButton.setOnClickListener(v -> geocodeCity());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(52));
        buttonLp.setMargins(0, dp(12), 0, 0);
        root.addView(cityButton, buttonLp);

        Button locButton = new Button(this);
        locButton.setText("Utiliser ma position actuelle");
        locButton.setOnClickListener(v -> requestCurrentLocation());
        LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(-1, dp(52));
        locLp.setMargins(0, dp(8), 0, 0);
        root.addView(locButton, locLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        prLp.setMargins(0, dp(20), 0, 0);
        root.addView(progress, prLp);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.setMargins(0, dp(12), 0, 0);
        root.addView(status, stLp);

        setContentView(root);
    }

    private void geocodeCity() {
        String city = cityInput.getText().toString().trim();
        if (city.isEmpty()) {
            cityInput.setError("Saisis une ville");
            return;
        }
        setBusy(true, "Recherche de la ville…");
        executor.execute(() -> {
            try {
                String q = URLEncoder.encode(city, StandardCharsets.UTF_8.toString());
                String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=1&language=fr&format=json";
                JSONObject json = new JSONObject(readUrl(url));
                JSONArray results = json.optJSONArray("results");
                if (results == null || results.length() == 0) throw new Exception("Ville introuvable");
                JSONObject r = results.getJSONObject(0);
                double lat = r.getDouble("latitude");
                double lon = r.getDouble("longitude");
                String locality = r.optString("name", city);
                runOnUiThread(() -> saveAndFinish(lat, lon, locality));
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, "Ville introuvable. Essaie avec une autre écriture."));
            }
        });
    }

    private void requestCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        fetchCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
        } else {
            status.setText("Autorisation refusée : tu peux simplement saisir une ville.");
        }
    }

    private void fetchCurrentLocation() {
        setBusy(true, "Recherche de ta position…");
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            lm.getCurrentLocation(provider, new CancellationSignal(), getMainExecutor(), location -> {
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    executor.execute(() -> {
                        String locality = resolveLocality(lat, lon);
                        runOnUiThread(() -> saveAndFinish(lat, lon, locality));
                    });
                } else {
                    setBusy(false, "Position indisponible. Saisis une ville.");
                }
            });
        } catch (SecurityException e) {
            setBusy(false, "Position indisponible. Saisis une ville.");
        } catch (Exception e) {
            setBusy(false, "Active la localisation ou saisis une ville.");
        }
    }

    private String resolveLocality(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.FRENCH);
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address a = addresses.get(0);
                if (a.getLocality() != null && !a.getLocality().isEmpty()) return a.getLocality();
                if (a.getSubAdminArea() != null && !a.getSubAdminArea().isEmpty()) return a.getSubAdminArea();
            }
        } catch (Exception ignored) { }
        return "Ma position";
    }

    private void saveAndFinish(double lat, double lon, String locality) {
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit()
            .putLong("lat_" + appWidgetId, Double.doubleToRawLongBits(lat))
            .putLong("lon_" + appWidgetId, Double.doubleToRawLongBits(lon))
            .putString("locality_" + appWidgetId, locality)
            .apply();

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        WeatherWidgetProvider.updateWidget(this, manager, appWidgetId);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    private String readUrl(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "MiniMeteoWidget/1.1");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
